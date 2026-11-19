package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class ActivityCategory(val label: String, val shortLabel: String) {
  TEA_COFFEE("Have tea or coffee", "Tea / Coffee"),
  FOOD("Eat something", "Food"),
  WALK("Go for a walk", "Walking"),
  EXPLORE("Explore nearby places", "Exploring"),
  CONVERSATION("Have a conversation", "Conversation"),
  MOVIE("Watch a movie", "Movie"),
  OTHER("Other activities", "Other");

  companion object {
    val allLabels: List<String> = entries.map { it.label }
    val quickActions: List<ActivityCategory> = listOf(TEA_COFFEE, FOOD, WALK, CONVERSATION, EXPLORE)
  }
}

enum class AvailabilityDuration(val minutes: Int, val label: String) {
  MIN_30(30, "30 min"),
  HOUR_1(60, "1 hour"),
  HOUR_2(120, "2 hours"),
  HOUR_4(240, "4 hours")
}

enum class RadiusFilter(val km: Int, val label: String) {
  KM_1(1, "1 KM"),
  KM_3(3, "3 KM"),
  KM_5(5, "5 KM"),
  KM_10(10, "10 KM")
}

@IgnoreExtraProperties
data class UserAccount(
  @DocumentId val id: String = "",
  val uid: String = "",
  val displayName: String = "",
  val email: String = "",
  val phoneNote: String = "",
  val verifiedPhoneNumber: String = "",
  val phoneVerified: Boolean = false,
  val trustedContactName: String = "",
  val trustedContactPhone: String = "",
  val trustedContactConsent: Boolean = false,
  val dateOfBirthMillis: Long = 0L,
  @get:PropertyName("isAdultVerified")
  @set:PropertyName("isAdultVerified")
  var isAdultVerified: Boolean = false,
  val accountStatus: String = "ACTIVE",
  val role: String = "USER",
  val pushNotificationsEnabled: Boolean = true,
  val callAlertsEnabled: Boolean = true,
  val meetupRemindersEnabled: Boolean = true,
  val onboardingCompleted: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun hasTrustedContact(): Boolean = trustedContactPhone.isNotBlank()
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val firstName: String = "",
  val age: Int = 18,
  val gender: String = "",
  val bio: String = "",
  val cityArea: String = "",
  val photoData: String = "",
  val interests: List<String> = emptyList(),
  @get:PropertyName("isVerified")
  @set:PropertyName("isVerified")
  var isVerified: Boolean = false,
  val phoneVerified: Boolean = false,
  val selfieVerified: Boolean = false,
  val selfieStatus: String = "NONE",
  val selfiePhotoData: String = "",
  @get:PropertyName("isAvailable")
  @set:PropertyName("isAvailable")
  var isAvailable: Boolean = false,
  val preferredActivity: String = "",
  val availabilityDurationMinutes: Int = 0,
  val availabilityExpiresAtMillis: Long = 0L,
  val approxLat: Double = 0.0,
  val approxLng: Double = 0.0,
  val locationUpdatedAtMillis: Long = 0L,
  val discoveryOptIn: Boolean = true,
  val accountStatus: String = "ACTIVE",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isCurrentlyAvailable(nowMillis: Long = System.currentTimeMillis()): Boolean {
    return isAvailable &&
      discoveryOptIn &&
      accountStatus == "ACTIVE" &&
      availabilityExpiresAtMillis > nowMillis
  }

  fun verificationLevel(): Int {
    return when {
      isVerified || selfieVerified -> 2
      phoneVerified -> 1
      else -> 0
    }
  }

  fun verificationLevelLabel(): String {
    return when {
      isVerified -> "Verified (Blue Tick)"
      selfieVerified -> "Level 2 Selfie Verified"
      phoneVerified && selfieStatus == "PENDING" -> "Level 1 Verified • Selfie Pending"
      phoneVerified -> "Level 1 Phone Verified"
      selfieStatus == "PENDING" -> "Selfie Pending • Needs Phone OTP"
      else -> "Unverified (Level 0)"
    }
  }
}

@IgnoreExtraProperties
data class AvailabilitySession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val activity: String = "",
  val durationMinutes: Int = 60,
  val status: String = "ACTIVE",
  val startedAtMillis: Long = 0L,
  val expiresAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ConnectionRequest(
  @DocumentId val id: String = "",
  val fromUid: String = "",
  val toUid: String = "",
  val fromName: String = "",
  val toName: String = "",
  val fromCityArea: String = "",
  val activity: String = "",
  val message: String = "",
  val status: String = "PENDING",
  val participantUids: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Connection(
  @DocumentId val id: String = "",
  val participantUids: List<String> = emptyList(),
  val userAUid: String = "",
  val userBUid: String = "",
  val userAName: String = "",
  val userBName: String = "",
  val userACity: String = "",
  val userBCity: String = "",
  val activity: String = "",
  val requestId: String = "",
  val status: String = "ACTIVE",
  val endedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun otherUid(myUid: String): String = if (userAUid == myUid) userBUid else userAUid
  fun otherName(myUid: String): String = if (userAUid == myUid) userBName else userAName
  fun otherCity(myUid: String): String = if (userAUid == myUid) userBCity else userACity
}

@IgnoreExtraProperties
data class Meetup(
  @DocumentId val id: String = "",
  val connectionId: String = "",
  val participantUids: List<String> = emptyList(),
  val proposerUid: String = "",
  val proposerName: String = "",
  val otherUid: String = "",
  val otherName: String = "",
  val activity: String = "",
  val scheduledDateText: String = "",
  val scheduledTimeText: String = "",
  val scheduledAtMillis: Long = 0L,
  val publicLocationName: String = "",
  val publicArea: String = "",
  val notes: String = "",
  val status: String = "PROPOSED",
  val confirmedByUid: String = "",
  val cancelledByUid: String = "",
  val sharedWithTrustedContactUids: List<String> = emptyList(),
  val checkedInUids: List<String> = emptyList(),
  val checkedInAtMillis: Long = 0L,
  val safeUids: List<String> = emptyList(),
  val safeMarkedAtMillis: Long = 0L,
  val reminderSentUids: List<String> = emptyList(),
  val reminderSentAtMillis: Long = 0L,
  val trustedAlertUids: List<String> = emptyList(),
  val trustedAlertSentAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun peerNameFor(myUid: String): String = if (proposerUid == myUid) otherName else proposerName
  fun isCheckedInBy(uid: String): Boolean = uid in checkedInUids
  fun isMarkedSafeBy(uid: String): Boolean = uid in safeUids
  fun isSharedWithTrustedBy(uid: String): Boolean = uid in sharedWithTrustedContactUids
  fun hasReminderSentTo(uid: String): Boolean = uid in reminderSentUids
  fun hasTrustedAlertTriggeredFor(uid: String): Boolean = uid in trustedAlertUids
}

@IgnoreExtraProperties
data class VoiceCall(
  @DocumentId val id: String = "",
  val connectionId: String = "",
  val callerUid: String = "",
  val calleeUid: String = "",
  val callerName: String = "",
  val calleeName: String = "",
  val participantUids: List<String> = emptyList(),
  val status: String = "RINGING",
  val offerSdp: String = "",
  val answerSdp: String = "",
  val durationSeconds: Long = 0L,
  val startedAtMillis: Long = 0L,
  val endedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun peerUid(myUid: String): String = if (callerUid == myUid) calleeUid else callerUid
  fun peerName(myUid: String): String = if (callerUid == myUid) calleeName else callerName
}

@IgnoreExtraProperties
data class IceCandidateRecord(
  @DocumentId val id: String = "",
  val senderUid: String = "",
  val participantUids: List<String> = emptyList(),
  val sdpMid: String = "",
  val sdpMLineIndex: Int = 0,
  val candidate: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class BlockedUser(
  @DocumentId val id: String = "",
  val blockerUid: String = "",
  val blockedUid: String = "",
  val blockedName: String = "",
  val reason: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SafetyReport(
  @DocumentId val id: String = "",
  val reporterUid: String = "",
  val reportedUid: String = "",
  val reportedName: String = "",
  val category: String = "",
  val details: String = "",
  val status: String = "OPEN",
  val adminNotes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppNotification(
  @DocumentId val id: String = "",
  val recipientUid: String = "",
  val actorUid: String = "",
  val actorName: String = "",
  val type: String = "",
  val title: String = "",
  val body: String = "",
  val referenceId: String = "",
  @get:PropertyName("isRead")
  @set:PropertyName("isRead")
  var isRead: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
)

data class NearbyUserCardModel(
  val profile: UserProfile,
  val distanceKm: Double,
  val distanceLabel: String,
  val commonInterests: List<String>,
)

data class PublicMeetupSpot(
  val name: String,
  val area: String,
  val categoryHint: String,
)
