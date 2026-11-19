package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsHubRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private fun adultDobMillis(): Long = 915_148_800_000L // Jan 1, 1999

  @Test
  fun onboarding_availability_discovery_and_connection_workflow_succeeds() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceEmail = "alice_$suffix@test.com"
    val bobEmail = "bob_$suffix@test.com"

    // 1. Alice signs in & completes 18+ onboarding
    val aliceUid = signInTestUser(aliceEmail)
    val aliceRepo = FriendsHubRepository(firestore)
    val aliceOnboard = withTimeout(TIMEOUT_MS) {
      aliceRepo.completeOnboarding(
        displayName = "Alice Verma",
        email = aliceEmail,
        phoneNote = "",
        dateOfBirthMillis = adultDobMillis(),
        gender = "Female",
        bio = "Love chai & walks in Gurugram",
        cityArea = "Sector 29, Gurugram",
        photoData = "",
        interests = listOf("Have tea or coffee", "Go for a walk"),
      )
    }
    assertTrue(aliceOnboard.isSuccess)

    // 2. Bob signs in, completes onboarding, and turns ON availability
    val bobUid = signInTestUser(bobEmail)
    val bobRepo = FriendsHubRepository(firestore)
    val bobOnboard = withTimeout(TIMEOUT_MS) {
      bobRepo.completeOnboarding(
        displayName = "Bob Sharma",
        email = bobEmail,
        phoneNote = "",
        dateOfBirthMillis = adultDobMillis(),
        gender = "Male",
        bio = "Looking for coffee company",
        cityArea = "DLF CyberHub, Gurugram",
        photoData = "",
        interests = listOf("Have tea or coffee"),
      )
    }
    assertTrue(bobOnboard.isSuccess)

    val availResult = withTimeout(TIMEOUT_MS) {
      bobRepo.startAvailability("Have tea or coffee", 60, 28.4595, 77.0266)
    }
    assertTrue(availResult.isSuccess)

    // 3. Alice discovers Bob in nearby available profiles
    signInTestUser(aliceEmail)
    val availableForAlice = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeAvailableProfiles(aliceUid).first { list -> list.any { it.uid == bobUid } }
    }
    val bobProfileUnverified = availableForAlice.first { it.uid == bobUid }
    assertTrue("isAvailable must deserialize to true from Firestore", bobProfileUnverified.isAvailable)
    assertTrue(bobProfileUnverified.isCurrentlyAvailable())
    assertEquals("Have tea or coffee", bobProfileUnverified.preferredActivity)
    val aliceProfileUnverified = withTimeout(TIMEOUT_MS) { aliceRepo.getProfileById(aliceUid).getOrThrow()!! }

    // 3b. Unverified users cannot connect ("Only verified users can connect")
    val unverifiedConnect = withTimeout(TIMEOUT_MS) {
      aliceRepo.sendConnectionRequest(
        fromProfile = aliceProfileUnverified,
        toProfile = bobProfileUnverified,
        activity = "Tea",
        message = "Unverified attempt",
      )
    }
    assertTrue("Unverified users must not be allowed to connect", unverifiedConnect.isFailure)

    // 3c. Level 1 (Gmail Verification) & Level 2 (Live Selfie submission) + Trusted Contact setup
    assertTrue(
      withTimeout(TIMEOUT_MS) { aliceRepo.recordLevel1GmailVerified("alice@gmail.com") }.isSuccess
    )
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        aliceRepo.submitLevel2LiveSelfie("data:image/jpeg;base64,/9j/4AAQSkZJRg==")
      }.isSuccess
    )
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        aliceRepo.updateTrustedContactSettings("", "+919811112222", true)
      }.isSuccess
    )
    val savedPhoneOnlyAccount = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeAccount(aliceUid).first { it != null && it.trustedContactPhone == "+919811112222" }
    }!!
    assertTrue(savedPhoneOnlyAccount.hasTrustedContact())
    assertTrue(savedPhoneOnlyAccount.trustedContactConsent)
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        aliceRepo.updateTrustedContactSettings("Priya Sister", "+919811112222", true)
      }.isSuccess
    )
    signInTestUser(bobEmail)
    assertTrue(withTimeout(TIMEOUT_MS) { bobRepo.recordLevel1GmailVerified("bob@gmail.com") }.isSuccess)
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        bobRepo.submitLevel2LiveSelfie("data:image/jpeg;base64,/9j/4AAQSkZJRg==")
      }.isSuccess
    )

    // 3d. Admin approves & grants Blue Tick to both Alice and Bob
    signInVerifiedAdmin()
    val adminRepo = FriendsHubRepository(firestore)
    assertTrue(withTimeout(TIMEOUT_MS) { adminRepo.adminToggleVerified(aliceUid, true) }.isSuccess)
    assertTrue(withTimeout(TIMEOUT_MS) { adminRepo.adminToggleVerified(bobUid, true) }.isSuccess)

    // Bob switches live activity from Tea/Coffee to Eat something and back -> reflected live
    signInTestUser(bobEmail)
    assertTrue(withTimeout(TIMEOUT_MS) { bobRepo.updateLiveActivity("Eat something") }.isSuccess)
    val bobAfterUpdate = withTimeout(TIMEOUT_MS) { bobRepo.getProfileById(bobUid).getOrThrow()!! }
    assertEquals("Eat something", bobAfterUpdate.preferredActivity)
    assertTrue(bobAfterUpdate.isVerified)
    assertTrue(bobAfterUpdate.selfieVerified)
    assertTrue(withTimeout(TIMEOUT_MS) { bobRepo.updateLiveActivity("Have tea or coffee") }.isSuccess)

    signInTestUser(aliceEmail)
    val aliceProfile = withTimeout(TIMEOUT_MS) { aliceRepo.getProfileById(aliceUid).getOrThrow()!! }
    val bobProfile = withTimeout(TIMEOUT_MS) { aliceRepo.getProfileById(bobUid).getOrThrow()!! }
    assertTrue(aliceProfile.isVerified)
    assertTrue(aliceProfile.gmailVerified)
    assertTrue(aliceProfile.isLevel1Verified())
    assertEquals("APPROVED", aliceProfile.selfieStatus)

    // 4. Verified Alice sends a connection request to verified Bob
    val sendReq = withTimeout(TIMEOUT_MS) {
      aliceRepo.sendConnectionRequest(
        fromProfile = aliceProfile,
        toProfile = bobProfile,
        activity = "Tea",
        message = "Let's grab chai near Sector 29!",
      )
    }
    assertTrue("Verified users should connect cleanly: ${sendReq.exceptionOrNull()?.message}", sendReq.isSuccess)

    // Duplicate pending request is blocked
    val dupReq = withTimeout(TIMEOUT_MS) {
      aliceRepo.sendConnectionRequest(aliceProfile, bobProfile, "Tea", "Again")
    }
    assertTrue(dupReq.isFailure)

    // 5. Bob sees and accepts request -> creates active connection
    signInTestUser(bobEmail)
    val bobReqs = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeConnectionRequests(bobUid).first { list ->
        list.any { it.fromUid == aliceUid && it.status == "PENDING" }
      }
    }
    val pendingReq = bobReqs.first { it.fromUid == aliceUid && it.status == "PENDING" }
    val acceptResult = withTimeout(TIMEOUT_MS) {
      bobRepo.respondToConnectionRequest(pendingReq, accept = true, responderCity = "DLF CyberHub")
    }
    assertTrue(acceptResult.isSuccess)

    val connections = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeConnections(bobUid).first { list ->
        list.any { it.participantUids.contains(aliceUid) && it.status == "ACTIVE" }
      }
    }
    val activeConn = connections.first { it.participantUids.contains(aliceUid) }

    // 6. Calling Alice while Alice has availability OFF must fail with an offline error message
    val blockedCallResult = withTimeout(TIMEOUT_MS) {
      bobRepo.initiateCall(activeConn, "Bob", "v=0\no=- 1 1 IN IP4 127.0.0.1\n")
    }
    assertTrue("Calling an unavailable user must fail", blockedCallResult.isFailure)
    assertTrue(
      "Expected 'not online' message, got: ${blockedCallResult.exceptionOrNull()?.message}",
      blockedCallResult.exceptionOrNull()?.message?.contains("not online", ignoreCase = true) == true,
    )

    // Once Alice switches availability ON, Bob's call succeeds
    signInTestUser(aliceEmail)
    assertTrue(withTimeout(TIMEOUT_MS) { aliceRepo.startAvailability("Have tea or coffee", 60, 28.46, 77.03) }.isSuccess)
    signInTestUser(bobEmail)

    val callResult = withTimeout(TIMEOUT_MS) {
      bobRepo.initiateCall(activeConn, "Bob", "v=0\no=- 1 1 IN IP4 127.0.0.1\n")
    }
    assertTrue(callResult.isSuccess)
    val call = callResult.getOrThrow()

    signInTestUser(aliceEmail)
    val acceptCall = withTimeout(TIMEOUT_MS) { aliceRepo.acceptCall(call.id, "v=0\no=- 2 2 IN IP4 127.0.0.1\n") }
    assertTrue(acceptCall.isSuccess)
    val endCall = withTimeout(TIMEOUT_MS) { aliceRepo.updateCallStatus(call.id, "ENDED", 42L) }
    assertTrue(endCall.isSuccess)

    // 6b. Private residential meetup locations are rejected; public cafe with trusted contact succeeds
    val privateMeetupResult = withTimeout(TIMEOUT_MS) {
      aliceRepo.proposeMeetup(
        connection = activeConn,
        myName = "Alice",
        activity = "Tea",
        scheduledDateText = "Today",
        scheduledTimeText = "6:30 PM",
        publicLocationName = "My Home Apartment",
        publicArea = "Sector 29, Gurugram",
        notes = "",
      )
    }
    assertTrue("Private locations must be rejected", privateMeetupResult.isFailure)

    val meetupId = withTimeout(TIMEOUT_MS) {
      aliceRepo.proposeMeetup(
        connection = activeConn,
        myName = "Alice",
        activity = "Tea",
        scheduledDateText = "Today",
        scheduledTimeText = "6:30 PM",
        publicLocationName = "Blue Tokai Cafe",
        publicArea = "Sector 29, Gurugram",
        notes = "Main entrance table",
        scheduledAtMillis = System.currentTimeMillis() + 1_800_000L,
        sharedWithTrustedContact = true,
      ).getOrThrow()
    }
    assertTrue(meetupId.isNotBlank())

    signInTestUser(bobEmail)
    val meetup = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeMeetups(bobUid).first { list -> list.any { it.id == meetupId } }
    }.first { it.id == meetupId }
    assertTrue(withTimeout(TIMEOUT_MS) { bobRepo.confirmMeetup(meetup, "Bob") }.isSuccess)

    // Alice checks in on arrival, records reminder/trusted alert, and marks I'm Safe
    signInTestUser(aliceEmail)
    val meetupForAlice = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeMeetups(aliceUid).first { list -> list.any { it.id == meetupId } }
    }.first { it.id == meetupId }
    assertTrue(withTimeout(TIMEOUT_MS) { aliceRepo.checkInMeetup(meetupForAlice) }.isSuccess)
    assertTrue(withTimeout(TIMEOUT_MS) { aliceRepo.recordMissedCheckInReminder(meetupForAlice, "Alice") }.isSuccess)
    assertTrue(withTimeout(TIMEOUT_MS) { aliceRepo.recordTrustedContactAlert(meetupForAlice, "Alice", "Priya") }.isSuccess)
    assertTrue(withTimeout(TIMEOUT_MS) { aliceRepo.markMeetupSafe(meetupForAlice) }.isSuccess)

    val completedMeetup = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeMeetups(aliceUid).first { list ->
        list.any { it.id == meetupId && it.isMarkedSafeBy(aliceUid) && it.status == "COMPLETED" }
      }
    }.first { it.id == meetupId }
    assertTrue(completedMeetup.isCheckedInBy(aliceUid))
    assertTrue(completedMeetup.isSharedWithTrustedBy(aliceUid))

    // 7. Bob reports and blocks Alice -> active connection ends and blocked calls/meetups fail
    signInTestUser(bobEmail)
    val reportRes = withTimeout(TIMEOUT_MS) {
      bobRepo.reportUser(aliceUid, "Alice", "Harassment", "Test safety report")
    }
    assertTrue(reportRes.isSuccess)

    val blockRes = withTimeout(TIMEOUT_MS) {
      bobRepo.blockUser(aliceUid, "Alice", "Safety boundary")
    }
    assertTrue(blockRes.isSuccess)

    val blockedFollowUpCall = withTimeout(TIMEOUT_MS) {
      bobRepo.initiateCall(activeConn, "Bob", "v=0\no=- 1 1 IN IP4 127.0.0.1\n")
    }
    assertTrue("Blocked users cannot call each other", blockedFollowUpCall.isFailure)

    val endedConn = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeConnections(bobUid).first { list ->
        list.any { it.id == activeConn.id && it.status == "ENDED" }
      }
    }
    assertTrue(endedConn.any { it.id == activeConn.id && it.status == "ENDED" })
  }

  @Test
  fun meetup_5mProximityAutoStartsMeetup_andAdminSeesFullAuditRecords() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceEmail = "prox_alice_$suffix@test.com"
    val bobEmail = "prox_bob_$suffix@test.com"

    val aliceUid = signInTestUser(aliceEmail)
    val aliceRepo = FriendsHubRepository(firestore)
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        aliceRepo.completeOnboarding(
          displayName = "Alice",
          email = aliceEmail,
          phoneNote = "",
          dateOfBirthMillis = adultDobMillis(),
          gender = "Female",
          bio = "Tea meetup tester",
          cityArea = "Sector 29, Gurugram",
          photoData = "",
          interests = listOf("Have tea or coffee"),
        )
      }.isSuccess
    )

    val bobUid = signInTestUser(bobEmail)
    val bobRepo = FriendsHubRepository(firestore)
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        bobRepo.completeOnboarding(
          displayName = "Bob",
          email = bobEmail,
          phoneNote = "",
          dateOfBirthMillis = adultDobMillis(),
          gender = "Male",
          bio = "Coffee meetup tester",
          cityArea = "Sector 29, Gurugram",
          photoData = "",
          interests = listOf("Have tea or coffee"),
        )
      }.isSuccess
    )

    // Verify both users via Admin
    signInVerifiedAdmin()
    val adminRepo = FriendsHubRepository(firestore)
    assertTrue(withTimeout(TIMEOUT_MS) { adminRepo.adminToggleVerified(aliceUid, true) }.isSuccess)
    assertTrue(withTimeout(TIMEOUT_MS) { adminRepo.adminToggleVerified(bobUid, true) }.isSuccess)

    // Connect Alice & Bob
    signInTestUser(aliceEmail)
    val aliceProfile = withTimeout(TIMEOUT_MS) { aliceRepo.getProfileById(aliceUid).getOrThrow()!! }
    val bobProfile = withTimeout(TIMEOUT_MS) { aliceRepo.getProfileById(bobUid).getOrThrow()!! }
    assertTrue(
      withTimeout(TIMEOUT_MS) {
        aliceRepo.sendConnectionRequest(aliceProfile, bobProfile, "Tea", "Chai at Blue Tokai?")
      }.isSuccess
    )

    signInTestUser(bobEmail)
    val req = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeConnectionRequests(bobUid).first { list -> list.any { it.fromUid == aliceUid } }
    }.first { it.fromUid == aliceUid }
    assertTrue(withTimeout(TIMEOUT_MS) { bobRepo.respondToConnectionRequest(req, true, "Sector 29") }.isSuccess)

    val conn = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeConnections(bobUid).first { list -> list.any { it.participantUids.contains(aliceUid) } }
    }.first { it.participantUids.contains(aliceUid) }

    // Alice creates Tea meetup with 15-minute countdown
    signInTestUser(aliceEmail)
    val scheduledMillis = System.currentTimeMillis() + 15 * 60_000L
    val meetupId = withTimeout(TIMEOUT_MS) {
      aliceRepo.proposeMeetup(
        connection = conn,
        myName = "Alice",
        activity = "Tea",
        scheduledDateText = "Today",
        scheduledTimeText = "6:00 PM",
        publicLocationName = "Blue Tokai Coffee Roasters",
        publicArea = "Sector 29, Gurugram",
        notes = "Table near entrance",
        scheduledAtMillis = scheduledMillis,
        sharedWithTrustedContact = false,
      ).getOrThrow()
    }

    // Bob confirms meetup
    signInTestUser(bobEmail)
    val proposedMeetup = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeMeetups(bobUid).first { list -> list.any { it.id == meetupId } }
    }.first { it.id == meetupId }
    assertTrue(withTimeout(TIMEOUT_MS) { bobRepo.confirmMeetup(proposedMeetup, "Bob") }.isSuccess)

    // Bob shares location at (28.459500, 77.026600)
    val bobStarted = withTimeout(TIMEOUT_MS) {
      bobRepo.updateMeetupProximityAndAutoStart(meetupId, 28.459500, 77.026600, "Bob").getOrThrow()
    }
    assertFalse("Only one participant location present; must not start yet", bobStarted)

    // Alice is ~22m away (28.459700, 77.026600) -> remains CONFIRMED
    signInTestUser(aliceEmail)
    val aliceFarStarted = withTimeout(TIMEOUT_MS) {
      aliceRepo.updateMeetupProximityAndAutoStart(meetupId, 28.459700, 77.026600, "Alice").getOrThrow()
    }
    assertFalse("22m away must NOT trigger STARTED", aliceFarStarted)

    // Alice walks within ~2.2m of Bob (28.459520, 77.026600) -> auto-starts meetup!
    val aliceCloseStarted = withTimeout(TIMEOUT_MS) {
      aliceRepo.updateMeetupProximityAndAutoStart(meetupId, 28.459520, 77.026600, "Alice").getOrThrow()
    }
    assertTrue("<= 5m proximity must trigger STARTED", aliceCloseStarted)

    val startedMeetup = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeMeetups(aliceUid).first { list ->
        list.any { it.id == meetupId && it.status == "STARTED" }
      }
    }.first { it.id == meetupId }
    assertEquals("STARTED", startedMeetup.status)
    assertTrue(startedMeetup.startedAtMillis > 0L)
    assertTrue(startedMeetup.proximityDistanceMeters in 0.0..5.0)
    assertTrue(startedMeetup.isCheckedInBy(aliceUid))
    assertTrue(startedMeetup.isCheckedInBy(bobUid))

    // Bob receives MEETUP_STARTED notification
    signInTestUser(bobEmail)
    val bobNotifs = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeNotifications(bobUid).first { list -> list.any { it.type == "MEETUP_STARTED" && it.referenceId == meetupId } }
    }
    assertTrue(bobNotifs.any { it.type == "MEETUP_STARTED" && it.referenceId == meetupId })

    // Mark safe -> transitions STARTED -> COMPLETED with completedAtMillis
    assertTrue(withTimeout(TIMEOUT_MS) { bobRepo.markMeetupSafe(startedMeetup) }.isSuccess)
    val completedMeetup = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeMeetups(bobUid).first { list ->
        list.any { it.id == meetupId && it.status == "COMPLETED" }
      }
    }.first { it.id == meetupId }
    assertTrue(completedMeetup.completedAtMillis > 0L)

    // Admin sees full investigation audit trail in snapshot.meetups
    signInVerifiedAdmin()
    val adminSnapshot = withTimeout(TIMEOUT_MS) { adminRepo.fetchAdminDashboardData().getOrThrow() }
    val auditRecord = adminSnapshot.meetups.firstOrNull { it.id == meetupId }
    assertTrue("Admin dashboard must include the meetup audit record", auditRecord != null)
    assertEquals("COMPLETED", auditRecord!!.status)
    assertTrue(auditRecord.startedAtMillis > 0L)
    assertTrue(auditRecord.completedAtMillis > 0L)
    assertTrue(auditRecord.proximityDistanceMeters in 0.0..5.0)
  }

  @Test
  fun unauthenticated_and_cross_user_access_fail_with_permission_denied() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceEmail = "priv_alice_$suffix@test.com"
    val bobEmail = "priv_bob_$suffix@test.com"

    val aliceUid = signInTestUser(aliceEmail)
    val repo = FriendsHubRepository(firestore)
    withTimeout(TIMEOUT_MS) {
      repo.completeOnboarding(
        displayName = "Alice Private",
        email = aliceEmail,
        phoneNote = "9999999999",
        dateOfBirthMillis = adultDobMillis(),
        gender = "",
        bio = "Bio",
        cityArea = "Gurugram",
        photoData = "",
        interests = listOf("Have tea or coffee"),
      ).getOrThrow()
    }

    // Bob cannot read Alice's private user account doc (which holds email/phoneNote/DOB)
    signInTestUser(bobEmail)
    var crossUserDenied = false
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAccount(aliceUid).first()
      }
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
      crossUserDenied = true
    }
    assertTrue("Expected PERMISSION_DENIED on cross-user private account read", crossUserDenied)

    // Unauthenticated user cannot list discovery profiles
    auth.signOut()
    var unauthDenied = false
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeAvailableProfiles(aliceUid).first()
      }
    } catch (e: Exception) {
      val fsEx = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
      unauthDenied = true
    }
    assertTrue("Expected PERMISSION_DENIED for unauthenticated discovery query", unauthDenied)
  }

  @Test
  fun underage_onboarding_is_rejected_by_client_and_rules() = runBlocking {
    val suffix = UUID.randomUUID().toString().take(8)
    signInTestUser("minor_$suffix@test.com")
    val repo = FriendsHubRepository(firestore)
    val recentMillis = System.currentTimeMillis() - (15L * 365 * 24 * 60 * 60 * 1000)
    val result = withTimeout(TIMEOUT_MS) {
      repo.completeOnboarding(
        displayName = "Minor User",
        email = "minor@test.com",
        phoneNote = "",
        dateOfBirthMillis = recentMillis,
        gender = "",
        bio = "Bio",
        cityArea = "Gurugram",
        photoData = "",
        interests = listOf("Have tea or coffee"),
      )
    }
    assertFalse(result.isSuccess)
  }

  private companion object {
    const val TIMEOUT_MS = 12000L
    const val FLOW_TIMEOUT_MS = 8000L
  }
}
