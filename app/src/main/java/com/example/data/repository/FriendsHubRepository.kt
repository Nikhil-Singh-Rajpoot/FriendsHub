package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.AvailabilitySession
import com.example.data.model.BlockedUser
import com.example.data.model.Connection
import com.example.data.model.ConnectionRequest
import com.example.data.model.IceCandidateRecord
import com.example.data.model.Meetup
import com.example.data.model.SafetyReport
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCall
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.util.LocationPrivacy
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FriendsHubRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return Firebase.auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  // ---------------------------------------------------------------------------
  // Account & Profile Observation
  // ---------------------------------------------------------------------------
  fun observeAccount(uid: String): Flow<UserAccount?> =
    db.collection("users")
      .document(uid)
      .snapshots()
      .map { snap -> snap.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.GET, "users/$uid")
        throw error
      }

  fun observeMyProfile(uid: String): Flow<UserProfile?> =
    db.collection("user_profiles")
      .document(uid)
      .snapshots()
      .map { snap -> snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.GET, "user_profiles/$uid")
        throw error
      }

  suspend fun getProfileById(uid: String): Result<UserProfile?> = runCatching {
    val path = "user_profiles/$uid"
    try {
      db.collection("user_profiles")
        .document(uid)
        .get()
        .await()
        .toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      throw e
    }
  }

  // ---------------------------------------------------------------------------
  // Onboarding, Account & Profile Management
  // ---------------------------------------------------------------------------
  suspend fun completeOnboarding(
    displayName: String,
    email: String,
    phoneNote: String,
    dateOfBirthMillis: Long,
    gender: String,
    bio: String,
    cityArea: String,
    photoData: String,
    interests: List<String>,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(LocationPrivacy.isAdult(dateOfBirthMillis)) {
      "You must be at least 18 years old to join FriendsHub."
    }
    val computedAge = LocationPrivacy.calculateAge(dateOfBirthMillis).coerceIn(18, 110)
    val firstName = displayName.trim().substringBefore(" ").ifBlank { displayName.trim() }.take(50)

    val userDoc = db.collection("users").document(uid)
    val profileDoc = db.collection("user_profiles").document(uid)

    val accountPayload =
      mapOf(
        "uid" to uid,
        "displayName" to displayName.trim().take(80),
        "email" to email.trim().take(160),
        "phoneNote" to phoneNote.trim().take(20),
        "dateOfBirthMillis" to dateOfBirthMillis,
        "isAdultVerified" to true,
        "accountStatus" to "ACTIVE",
        "role" to "USER",
        "pushNotificationsEnabled" to true,
        "callAlertsEnabled" to true,
        "meetupRemindersEnabled" to true,
        "onboardingCompleted" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    val profilePayload =
      mapOf(
        "uid" to uid,
        "firstName" to firstName,
        "age" to computedAge,
        "gender" to gender.trim().take(30),
        "bio" to bio.trim().take(300),
        "cityArea" to cityArea.trim().take(80),
        "photoData" to photoData,
        "interests" to interests.take(10),
        "isVerified" to false,
        "isAvailable" to false,
        "preferredActivity" to (interests.firstOrNull() ?: "Have tea or coffee"),
        "availabilityDurationMinutes" to 0,
        "availabilityExpiresAtMillis" to 0L,
        "approxLat" to 0.0,
        "approxLng" to 0.0,
        "locationUpdatedAtMillis" to 0L,
        "discoveryOptIn" to true,
        "accountStatus" to "ACTIVE",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    try {
      val batch = db.batch()
      batch.set(userDoc, accountPayload)
      batch.set(profileDoc, profilePayload)
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$uid")
      throw e
    }
  }

  suspend fun updateProfile(
    firstName: String,
    bio: String,
    cityArea: String,
    gender: String,
    photoData: String,
    interests: List<String>,
    discoveryOptIn: Boolean,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("user_profiles").document(uid)
    val cleanInterests = interests.take(10)
    val updates =
      mutableMapOf<String, Any>(
        "firstName" to firstName.trim().take(50),
        "bio" to bio.trim().take(300),
        "cityArea" to cityArea.trim().take(80),
        "gender" to gender.trim().take(30),
        "photoData" to photoData,
        "interests" to cleanInterests,
        "discoveryOptIn" to discoveryOptIn,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      val currentPreferred = docRef.get().await().getString("preferredActivity").orEmpty()
      if (cleanInterests.isNotEmpty() && currentPreferred !in cleanInterests) {
        updates["preferredActivity"] = cleanInterests.first()
      }
      docRef.set(updates, SetOptions.merge()).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun updateNotificationPreferences(
    pushEnabled: Boolean,
    callAlertsEnabled: Boolean,
    meetupRemindersEnabled: Boolean,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val updates =
      mapOf(
        "pushNotificationsEnabled" to pushEnabled,
        "callAlertsEnabled" to callAlertsEnabled,
        "meetupRemindersEnabled" to meetupRemindersEnabled,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(updates, SetOptions.merge()).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // ---------------------------------------------------------------------------
  // Availability & Coarse Location Management
  // ---------------------------------------------------------------------------
  suspend fun startAvailability(
    activity: String,
    durationMinutes: Int,
    approxLat: Double,
    approxLng: Double,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val now = System.currentTimeMillis()
    val expiresAt = now + durationMinutes * 60_000L
    val qLat = LocationPrivacy.quantizeCoordinate(approxLat)
    val qLng = LocationPrivacy.quantizeCoordinate(approxLng)
    val sessionId = UUID.randomUUID().toString()

    val profileRef = db.collection("user_profiles").document(uid)
    val sessionRef = db.collection("availability_sessions").document(sessionId)

    val profileUpdates =
      mapOf(
        "isAvailable" to true,
        "discoveryOptIn" to true,
        "preferredActivity" to activity.take(40),
        "availabilityDurationMinutes" to durationMinutes.coerceIn(15, 240),
        "availabilityExpiresAtMillis" to expiresAt,
        "approxLat" to qLat,
        "approxLng" to qLng,
        "locationUpdatedAtMillis" to now,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    val sessionPayload =
      mapOf(
        "userId" to uid,
        "activity" to activity.take(40),
        "durationMinutes" to durationMinutes.coerceIn(15, 240),
        "status" to "ACTIVE",
        "startedAtMillis" to now,
        "expiresAtMillis" to expiresAt,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      val batch = db.batch()
      batch.set(profileRef, profileUpdates, SetOptions.merge())
      batch.set(sessionRef, sessionPayload)
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, profileRef.path)
      throw e
    }
  }

  suspend fun updateLiveActivity(activity: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val profileRef = db.collection("user_profiles").document(uid)
    val cleanActivity = activity.ifBlank { "Have tea or coffee" }.take(40)
    try {
      profileRef
        .set(
          mapOf(
            "preferredActivity" to cleanActivity,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
          SetOptions.merge(),
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
      throw e
    }
  }

  suspend fun stopAvailability(markExpired: Boolean = false): Result<Unit> = runCatching {
    val uid = requireUserId()
    val profileRef = db.collection("user_profiles").document(uid)
    val updates =
      mapOf(
        "isAvailable" to false,
        "availabilityDurationMinutes" to 0,
        "availabilityExpiresAtMillis" to 0L,
        "approxLat" to 0.0,
        "approxLng" to 0.0,
        "locationUpdatedAtMillis" to 0L,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      profileRef.set(updates, SetOptions.merge()).await()
      val activeSessions =
        db.collection("availability_sessions")
          .whereEqualTo("userId", uid)
          .whereEqualTo("status", "ACTIVE")
          .get()
          .await()
      val newStatus = if (markExpired) "EXPIRED" else "ENDED"
      activeSessions.documents.forEach { doc ->
        doc.reference
          .set(mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
          .await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, profileRef.path)
      throw e
    }
  }

  fun observeAvailabilitySessions(uid: String): Flow<List<AvailabilitySession>> =
    db.collection("availability_sessions")
      .whereEqualTo("userId", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(AvailabilitySession::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.startedAtMillis }
      }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "availability_sessions")
        throw error
      }

  // ---------------------------------------------------------------------------
  // Nearby Discovery (Aligned with Firestore rules!)
  // ---------------------------------------------------------------------------
  fun observeAvailableProfiles(uid: String): Flow<List<UserProfile>> =
    db.collection("user_profiles")
      .whereEqualTo("isAvailable", true)
      .whereEqualTo("discoveryOptIn", true)
      .whereEqualTo("accountStatus", "ACTIVE")
      .snapshots()
      .map { snap ->
        val now = System.currentTimeMillis()
        snap.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
          .filter { profile ->
            profile.uid != uid &&
              profile.age >= 18 &&
              profile.availabilityExpiresAtMillis > now
          }
      }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "user_profiles")
        throw error
      }

  // ---------------------------------------------------------------------------
  // Connection Requests & Active Connections
  // ---------------------------------------------------------------------------
  fun observeConnectionRequests(uid: String): Flow<List<ConnectionRequest>> =
    db.collection("connection_requests")
      .whereArrayContains("participantUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(ConnectionRequest::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "connection_requests")
        throw error
      }

  suspend fun sendConnectionRequest(
    fromProfile: UserProfile,
    toProfile: UserProfile,
    activity: String,
    message: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    require(uid == fromProfile.uid)
    require(toProfile.uid != uid) { "You cannot connect with yourself." }

    // Prevent duplicate active requests or already-active connection
    val existingPending =
      db.collection("connection_requests")
        .whereArrayContains("participantUids", uid)
        .get()
        .await()
        .toObjects(ConnectionRequest::class.java, ServerTimestampBehavior.ESTIMATE)
        .any {
          it.status == "PENDING" &&
            ((it.fromUid == uid && it.toUid == toProfile.uid) ||
              (it.fromUid == toProfile.uid && it.toUid == uid))
        }
    require(!existingPending) { "A connection request is already pending between you two." }

    val connId = deterministicConnectionId(uid, toProfile.uid)
    val myConnections =
      db.collection("connections")
        .whereArrayContains("participantUids", uid)
        .get()
        .await()
        .toObjects(Connection::class.java, ServerTimestampBehavior.ESTIMATE)
    if (myConnections.any { it.id == connId && it.status == "ACTIVE" }) {
      throw IllegalStateException("You are already connected with ${toProfile.firstName}.")
    }

    val requestId = UUID.randomUUID().toString()
    val reqRef = db.collection("connection_requests").document(requestId)
    val payload =
      mapOf(
        "fromUid" to uid,
        "toUid" to toProfile.uid,
        "fromName" to fromProfile.firstName.ifBlank { "Friend" }.take(60),
        "toName" to toProfile.firstName.ifBlank { "Friend" }.take(60),
        "fromCityArea" to fromProfile.cityArea.take(80),
        "activity" to activity.ifBlank { "Tea" }.take(40),
        "message" to message.trim().take(240),
        "status" to "PENDING",
        "participantUids" to listOf(uid, toProfile.uid),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )

    try {
      reqRef.set(payload).await()
      createNotificationInternal(
        recipientUid = toProfile.uid,
        actorUid = uid,
        actorName = fromProfile.firstName.ifBlank { "Someone" },
        type = "CONNECTION_REQUEST",
        title = "New connection request",
        body = "${fromProfile.firstName} wants to connect for $activity.",
        referenceId = requestId,
      )
      requestId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, reqRef.path)
      throw e
    }
  }

  suspend fun respondToConnectionRequest(
    request: ConnectionRequest,
    accept: Boolean,
    responderCity: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(request.toUid == uid) { "Only the recipient can respond to this request." }
    require(request.status == "PENDING") { "Request has already been handled." }

    val reqRef = db.collection("connection_requests").document(request.id)
    val newStatus = if (accept) "ACCEPTED" else "REJECTED"

    try {
      val connId = deterministicConnectionId(request.fromUid, request.toUid)
      val existingConnection =
        if (accept) {
          db.collection("connections")
            .whereArrayContains("participantUids", uid)
            .get()
            .await()
            .documents
            .any { it.id == connId }
        } else {
          false
        }

      val batch = db.batch()
      batch.set(
        reqRef,
        mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp()),
        SetOptions.merge(),
      )
      if (accept) {
        val connRef = db.collection("connections").document(connId)
        if (existingConnection) {
          batch.set(
            connRef,
            mapOf(
              "status" to "ACTIVE",
              "activity" to request.activity,
              "requestId" to request.id,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
          )
        } else {
          val sortedUids = listOf(request.fromUid, request.toUid).sorted()
          val isFromA = sortedUids.first() == request.fromUid
          val connPayload =
            mapOf(
              "participantUids" to sortedUids,
              "userAUid" to sortedUids[0],
              "userBUid" to sortedUids[1],
              "userAName" to if (isFromA) request.fromName else request.toName,
              "userBName" to if (isFromA) request.toName else request.fromName,
              "userACity" to if (isFromA) request.fromCityArea else responderCity,
              "userBCity" to if (isFromA) responderCity else request.fromCityArea,
              "activity" to request.activity,
              "requestId" to request.id,
              "status" to "ACTIVE",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          batch.set(connRef, connPayload)
        }
      }
      batch.commit().await()

      if (accept) {
        createNotificationInternal(
          recipientUid = request.fromUid,
          actorUid = uid,
          actorName = request.toName,
          type = "REQUEST_ACCEPTED",
          title = "Connection accepted!",
          body = "${request.toName} accepted your ${request.activity} request.",
          referenceId = deterministicConnectionId(request.fromUid, request.toUid),
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
      throw e
    }
  }

  fun observeConnections(uid: String): Flow<List<Connection>> =
    db.collection("connections")
      .whereArrayContains("participantUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(Connection::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "connections")
        throw error
      }

  suspend fun endConnection(connection: Connection): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("connections").document(connection.id)
    try {
      docRef
        .set(
          mapOf(
            "status" to "ENDED",
            "endedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
          SetOptions.merge(),
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // ---------------------------------------------------------------------------
  // Meetups
  // ---------------------------------------------------------------------------
  fun observeMeetups(uid: String): Flow<List<Meetup>> =
    db.collection("meetups")
      .whereArrayContains("participantUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(Meetup::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "meetups")
        throw error
      }

  suspend fun proposeMeetup(
    connection: Connection,
    myName: String,
    activity: String,
    scheduledDateText: String,
    scheduledTimeText: String,
    publicLocationName: String,
    publicArea: String,
    notes: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    require(connection.status == "ACTIVE") { "Connection is no longer active." }
    val otherUid = connection.otherUid(uid)
    val otherName = connection.otherName(uid)
    val meetupId = UUID.randomUUID().toString()
    val docRef = db.collection("meetups").document(meetupId)

    val payload =
      mapOf(
        "connectionId" to connection.id,
        "participantUids" to listOf(uid, otherUid),
        "proposerUid" to uid,
        "proposerName" to myName.take(60),
        "otherUid" to otherUid,
        "otherName" to otherName.take(60),
        "activity" to activity.take(40),
        "scheduledDateText" to scheduledDateText.trim().take(40),
        "scheduledTimeText" to scheduledTimeText.trim().take(40),
        "publicLocationName" to publicLocationName.trim().take(120),
        "publicArea" to publicArea.trim().take(100),
        "notes" to notes.trim().take(300),
        "status" to "PROPOSED",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      createNotificationInternal(
        recipientUid = otherUid,
        actorUid = uid,
        actorName = myName,
        type = "MEETUP_PROPOSED",
        title = "Meetup proposed",
        body = "$myName suggested $activity at $publicLocationName ($scheduledTimeText).",
        referenceId = meetupId,
      )
      meetupId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun confirmMeetup(meetup: Meetup, myName: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(meetup.proposerUid != uid) { "Waiting for the other participant to confirm." }
    val docRef = db.collection("meetups").document(meetup.id)
    try {
      docRef
        .set(
          mapOf(
            "status" to "CONFIRMED",
            "confirmedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
          SetOptions.merge(),
        )
        .await()
      createNotificationInternal(
        recipientUid = meetup.proposerUid,
        actorUid = uid,
        actorName = myName,
        type = "MEETUP_CONFIRMED",
        title = "Meetup confirmed",
        body = "$myName confirmed your meetup at ${meetup.publicLocationName}.",
        referenceId = meetup.id,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun cancelMeetup(meetup: Meetup, myName: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("meetups").document(meetup.id)
    val otherUid = if (meetup.proposerUid == uid) meetup.otherUid else meetup.proposerUid
    try {
      docRef
        .set(
          mapOf(
            "status" to "CANCELLED",
            "cancelledByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
          SetOptions.merge(),
        )
        .await()
      createNotificationInternal(
        recipientUid = otherUid,
        actorUid = uid,
        actorName = myName,
        type = "MEETUP_CANCELLED",
        title = "Meetup cancelled",
        body = "$myName cancelled the meetup at ${meetup.publicLocationName}.",
        referenceId = meetup.id,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun completeMeetup(meetup: Meetup): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("meetups").document(meetup.id)
    try {
      docRef
        .set(
          mapOf("status" to "COMPLETED", "updatedAt" to FieldValue.serverTimestamp()),
          SetOptions.merge(),
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // ---------------------------------------------------------------------------
  // In-App Voice Calling & WebRTC Signaling
  // ---------------------------------------------------------------------------
  fun observeCalls(uid: String): Flow<List<VoiceCall>> =
    db.collection("calls")
      .whereArrayContains("participantUids", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(VoiceCall::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "calls")
        throw error
      }

  fun observeCall(callId: String, currentUid: String): Flow<VoiceCall?> =
    db.collection("calls")
      .document(callId)
      .snapshots()
      .map { snap -> snap.toObject(VoiceCall::class.java, ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        logFlowErrorIfSignedIn(currentUid, error, OperationType.GET, "calls/$callId")
        throw error
      }

  fun observeIceCandidates(callId: String, currentUid: String): Flow<List<IceCandidateRecord>> =
    db.collection("calls")
      .document(callId)
      .collection("candidates")
      .whereArrayContains("participantUids", currentUid)
      .snapshots()
      .map { snap -> snap.toObjects(IceCandidateRecord::class.java, ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        logFlowErrorIfSignedIn(currentUid, error, OperationType.LIST, "calls/$callId/candidates")
        throw error
      }

  suspend fun initiateCall(
    connection: Connection,
    callerName: String,
    offerSdp: String,
  ): Result<VoiceCall> = runCatching {
    val uid = requireUserId()
    require(connection.status == "ACTIVE") { "Only active connections can call each other." }
    val calleeUid = connection.otherUid(uid)
    val calleeName = connection.otherName(uid)

    // Prevent simultaneous active/ringing calls for caller
    val activeCalls =
      db.collection("calls")
        .whereArrayContains("participantUids", uid)
        .get()
        .await()
        .toObjects(VoiceCall::class.java, ServerTimestampBehavior.ESTIMATE)
        .any { it.status == "RINGING" || it.status == "ACTIVE" }
    require(!activeCalls) { "You already have an active or ringing call." }

    val callId = UUID.randomUUID().toString()
    val callRef = db.collection("calls").document(callId)
    val payload =
      mapOf(
        "connectionId" to connection.id,
        "callerUid" to uid,
        "calleeUid" to calleeUid,
        "callerName" to callerName.take(60),
        "calleeName" to calleeName.take(60),
        "participantUids" to listOf(uid, calleeUid),
        "status" to "RINGING",
        "offerSdp" to offerSdp.take(25000),
        "durationSeconds" to 0L,
        "startedAtMillis" to 0L,
        "endedAtMillis" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      callRef.set(payload).await()
      createNotificationInternal(
        recipientUid = calleeUid,
        actorUid = uid,
        actorName = callerName,
        type = "INCOMING_CALL",
        title = "Incoming voice call",
        body = "$callerName is calling you in FriendsHub.",
        referenceId = callId,
      )
      VoiceCall(
        id = callId,
        connectionId = connection.id,
        callerUid = uid,
        calleeUid = calleeUid,
        callerName = callerName,
        calleeName = calleeName,
        participantUids = listOf(uid, calleeUid),
        status = "RINGING",
        offerSdp = offerSdp,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, callRef.path)
      throw e
    }
  }

  suspend fun acceptCall(callId: String, answerSdp: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("calls").document(callId)
    try {
      docRef
        .set(
          mapOf(
            "status" to "ACTIVE",
            "answerSdp" to answerSdp.take(25000),
            "startedAtMillis" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
          SetOptions.merge(),
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun updateCallStatus(
    callId: String,
    status: String,
    durationSeconds: Long = 0L,
  ): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("calls").document(callId)
    try {
      docRef
        .set(
          mapOf(
            "status" to status,
            "durationSeconds" to durationSeconds.coerceAtLeast(0L),
            "endedAtMillis" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
          SetOptions.merge(),
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun addIceCandidate(
    callId: String,
    participantUids: List<String>,
    sdpMid: String,
    sdpMLineIndex: Int,
    candidate: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val candId = UUID.randomUUID().toString()
    val docRef = db.collection("calls").document(callId).collection("candidates").document(candId)
    val payload =
      mapOf(
        "senderUid" to uid,
        "participantUids" to participantUids,
        "sdpMid" to sdpMid.take(64),
        "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 16),
        "candidate" to candidate.take(1024),
        "createdAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  // ---------------------------------------------------------------------------
  // Safety: Block, Unblock, Report, Account Deletion
  // ---------------------------------------------------------------------------
  fun observeBlocks(uid: String): Flow<List<BlockedUser>> =
    db.collection("blocked_users")
      .whereEqualTo("blockerUid", uid)
      .snapshots()
      .map { snap -> snap.toObjects(BlockedUser::class.java, ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "blocked_users")
        throw error
      }

  fun observeBlockedByOthers(uid: String): Flow<List<BlockedUser>> =
    db.collection("blocked_users")
      .whereEqualTo("blockedUid", uid)
      .snapshots()
      .map { snap -> snap.toObjects(BlockedUser::class.java, ServerTimestampBehavior.ESTIMATE) }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "blocked_users")
        throw error
      }

  suspend fun blockUser(blockedUid: String, blockedName: String, reason: String): Result<Unit> =
    runCatching {
      val uid = requireUserId()
      require(uid != blockedUid)
      val blockId = "${uid}_${blockedUid}"
      val docRef = db.collection("blocked_users").document(blockId)
      val payload =
        mapOf(
          "blockerUid" to uid,
          "blockedUid" to blockedUid,
          "blockedName" to blockedName.ifBlank { "Member" }.take(60),
          "reason" to reason.trim().take(200),
          "createdAt" to FieldValue.serverTimestamp(),
        )
      try {
        docRef.set(payload).await()
        // End any active connection cleanly
        val connId = deterministicConnectionId(uid, blockedUid)
        val activeConn =
          db.collection("connections")
            .whereArrayContains("participantUids", uid)
            .get()
            .await()
            .documents
            .firstOrNull { it.id == connId && it.getString("status") == "ACTIVE" }
        if (activeConn != null) {
          activeConn.reference
            .set(
              mapOf(
                "status" to "ENDED",
                "endedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
              SetOptions.merge(),
            )
            .await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, docRef.path)
        throw e
      }
    }

  suspend fun unblockUser(blockedUid: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    val blockId = "${uid}_${blockedUid}"
    val docRef = db.collection("blocked_users").document(blockId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  suspend fun reportUser(
    reportedUid: String,
    reportedName: String,
    category: String,
    details: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    require(uid != reportedUid)
    val reportId = UUID.randomUUID().toString()
    val docRef = db.collection("reports").document(reportId)
    val payload =
      mapOf(
        "reporterUid" to uid,
        "reportedUid" to reportedUid,
        "reportedName" to reportedName.ifBlank { "Member" }.take(60),
        "category" to category.take(50),
        "details" to details.trim().take(500),
        "status" to "OPEN",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      reportId
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteMyAccountData(): Result<Unit> = runCatching {
    val uid = requireUserId()
    try {
      db.collection("user_profiles").document(uid).delete().await()
      db.collection("users").document(uid).delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, "users/$uid")
      throw e
    }
  }

  // ---------------------------------------------------------------------------
  // Notifications
  // ---------------------------------------------------------------------------
  fun observeNotifications(uid: String): Flow<List<AppNotification>> =
    db.collection("notifications")
      .whereEqualTo("recipientUid", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(AppNotification::class.java, ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
      }
      .catch { error ->
        logFlowErrorIfSignedIn(uid, error, OperationType.LIST, "notifications")
        throw error
      }

  suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("notifications").document(notificationId)
    try {
      docRef.set(mapOf("isRead" to true), SetOptions.merge()).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  private suspend fun createNotificationInternal(
    recipientUid: String,
    actorUid: String,
    actorName: String,
    type: String,
    title: String,
    body: String,
    referenceId: String,
  ) {
    val notifId = UUID.randomUUID().toString()
    val docRef = db.collection("notifications").document(notifId)
    val payload =
      mapOf(
        "recipientUid" to recipientUid,
        "actorUid" to actorUid,
        "actorName" to actorName.take(60),
        "type" to type.take(40),
        "title" to title.take(100),
        "body" to body.take(220),
        "referenceId" to referenceId.take(128),
        "isRead" to false,
        "createdAt" to FieldValue.serverTimestamp(),
      )
    runCatching { docRef.set(payload).await() }.onFailure {
      handleFirestoreError(it, OperationType.CREATE, docRef.path)
    }
  }

  // ---------------------------------------------------------------------------
  // Admin Moderation Console (Gated by Firestore rules isAdmin())
  // ---------------------------------------------------------------------------
  suspend fun fetchAdminDashboardData(): Result<AdminDashboardSnapshot> = runCatching {
    requireUserId()
    try {
      val users =
        db.collection("users").get().await()
          .toObjects(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
      val profiles =
        db.collection("user_profiles").get().await()
          .toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
      val reports =
        db.collection("reports").get().await()
          .toObjects(SafetyReport::class.java, ServerTimestampBehavior.ESTIMATE)
      val connectionsCount = db.collection("connections").get().await().size()
      val meetupsCount = db.collection("meetups").get().await().size()
      AdminDashboardSnapshot(
        users = users,
        profiles = profiles,
        reports = reports.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L },
        totalConnections = connectionsCount,
        totalMeetups = meetupsCount,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "admin_dashboard")
      throw e
    }
  }

  suspend fun adminSetAccountStatus(targetUid: String, suspend: Boolean): Result<Unit> = runCatching {
    requireUserId()
    val newStatus = if (suspend) "SUSPENDED" else "ACTIVE"
    try {
      val batch = db.batch()
      batch.set(
        db.collection("users").document(targetUid),
        mapOf("accountStatus" to newStatus, "updatedAt" to FieldValue.serverTimestamp()),
        SetOptions.merge(),
      )
      batch.set(
        db.collection("user_profiles").document(targetUid),
        mapOf("accountStatus" to newStatus, "updatedAt" to FieldValue.serverTimestamp()),
        SetOptions.merge(),
      )
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "users/$targetUid")
      throw e
    }
  }

  suspend fun adminToggleVerified(targetUid: String, verified: Boolean): Result<Unit> = runCatching {
    requireUserId()
    val docRef = db.collection("user_profiles").document(targetUid)
    try {
      docRef
        .set(
          mapOf("isVerified" to verified, "updatedAt" to FieldValue.serverTimestamp()),
          SetOptions.merge(),
        )
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun adminUpdateReport(reportId: String, status: String, notes: String): Result<Unit> =
    runCatching {
      requireUserId()
      val docRef = db.collection("reports").document(reportId)
      try {
        docRef
          .set(
            mapOf(
              "status" to status,
              "adminNotes" to notes.trim().take(300),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
          )
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        throw e
      }
    }

  private fun deterministicConnectionId(uidA: String, uidB: String): String {
    return if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
  }

  private fun logFlowErrorIfSignedIn(
    uid: String,
    error: Throwable,
    op: OperationType,
    path: String,
  ) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && Firebase.auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, op, path)
    }
  }
}

data class AdminDashboardSnapshot(
  val users: List<UserAccount> = emptyList(),
  val profiles: List<UserProfile> = emptyList(),
  val reports: List<SafetyReport> = emptyList(),
  val totalConnections: Int = 0,
  val totalMeetups: Int = 0,
)
