package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.AvailabilityDuration
import com.example.data.model.AvailabilitySession
import com.example.data.model.BlockedUser
import com.example.data.model.Connection
import com.example.data.model.ConnectionRequest
import com.example.data.model.Meetup
import com.example.data.model.NearbyUserCardModel
import com.example.data.model.RadiusFilter
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCall
import com.example.data.remote.unwrapFirestoreException
import com.example.data.repository.AdminDashboardSnapshot
import com.example.data.repository.FriendsHubRepository
import com.example.util.LocationPrivacy
import com.example.util.SafetyNotificationHelper
import com.example.voice.VoiceCallEngine
import com.example.voice.WebRtcVoiceCallEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FriendsHubViewModel(
  private val repository: FriendsHubRepository,
  val currentUserId: String,
  private val callEngineFactory: (Context) -> VoiceCallEngine = { ctx -> WebRtcVoiceCallEngine(ctx) },
) : ViewModel() {

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _radiusFilter = MutableStateFlow(RadiusFilter.KM_5)
  val radiusFilter: StateFlow<RadiusFilter> = _radiusFilter.asStateFlow()

  private val _activityFilter = MutableStateFlow<String?>(null)
  val activityFilter: StateFlow<String?> = _activityFilter.asStateFlow()

  private val _isEnablingAvailability = MutableStateFlow(false)
  val isEnablingAvailability: StateFlow<Boolean> = _isEnablingAvailability.asStateFlow()

  private val _callBlockedNotice = MutableStateFlow<String?>(null)
  val callBlockedNotice: StateFlow<String?> = _callBlockedNotice.asStateFlow()

  private val _missedCheckInAlertMeetup = MutableStateFlow<Meetup?>(null)
  val missedCheckInAlertMeetup: StateFlow<Meetup?> = _missedCheckInAlertMeetup.asStateFlow()

  private val _viewerLocation = MutableStateFlow<Pair<Double, Double>?>(null)
  val viewerLocation: StateFlow<Pair<Double, Double>?> = _viewerLocation.asStateFlow()

  private val _viewerCityArea = MutableStateFlow<String?>(null)
  val viewerCityArea: StateFlow<String?> = _viewerCityArea.asStateFlow()

  private var liveLocationJob: Job? = null
  private var lastSyncedCoords: Pair<Double, Double>? = null
  private var lastSyncedCityArea: String = ""

  private val _cachedPeerProfiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

  // Live Account & Profile
  val accountState: StateFlow<UiState<UserAccount?>> =
    repository.observeAccount(currentUserId)
      .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
      .catch { error ->
        val root = error.unwrapFirestoreException() ?: if (error is CancellationException) throw error else error
        Log.w(TAG, "observeAccount error", root)
        emit(UiState.Error(root.message ?: "Unable to load account"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val myProfileState: StateFlow<UiState<UserProfile?>> =
    repository.observeMyProfile(currentUserId)
      .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
      .catch { error ->
        val root = error.unwrapFirestoreException() ?: if (error is CancellationException) throw error else error
        Log.w(TAG, "observeMyProfile error", root)
        emit(UiState.Error(root.message ?: "Unable to load profile"))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

  val availableProfiles: StateFlow<List<UserProfile>> =
    repository.observeAvailableProfiles(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val blocks: StateFlow<List<BlockedUser>> =
    repository.observeBlocks(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val blockedByOthers: StateFlow<List<BlockedUser>> =
    repository.observeBlockedByOthers(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  private val allBlockedUids: StateFlow<Set<String>> =
    combine(blocks, blockedByOthers) { myBlocks, blockedMe ->
      (myBlocks.map { it.blockedUid } + blockedMe.map { it.blockerUid }).toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

  // Filtered against blockedUids so blocked users cannot see requests, contact, or call each other
  val requests: StateFlow<List<ConnectionRequest>> =
    combine(
      repository.observeConnectionRequests(currentUserId).catch { emit(emptyList()) },
      allBlockedUids,
    ) { list, blocked ->
      list.filter { req ->
        val other = if (req.fromUid == currentUserId) req.toUid else req.fromUid
        other !in blocked
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val connections: StateFlow<List<Connection>> =
    combine(
      repository.observeConnections(currentUserId).catch { emit(emptyList()) },
      allBlockedUids,
    ) { list, blocked ->
      list.filter { conn -> conn.otherUid(currentUserId) !in blocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val peerProfiles: StateFlow<Map<String, UserProfile>> =
    combine(availableProfiles, _cachedPeerProfiles) { liveProfiles, cached ->
      buildMap {
        putAll(cached)
        liveProfiles.forEach { put(it.uid, it) }
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

  val meetups: StateFlow<List<Meetup>> =
    combine(
      repository.observeMeetups(currentUserId).catch { emit(emptyList()) },
      allBlockedUids,
    ) { list, blocked ->
      list.filter { meetup ->
        val other = if (meetup.proposerUid == currentUserId) meetup.otherUid else meetup.proposerUid
        other !in blocked
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val calls: StateFlow<List<VoiceCall>> =
    repository.observeCalls(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val notifications: StateFlow<List<AppNotification>> =
    repository.observeNotifications(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val sessions: StateFlow<List<AvailabilitySession>> =
    repository.observeAvailabilitySessions(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  // Filtered Nearby Discovery cards (strictly local to user's GPS radius or matching city/area, excluding blocked users)
  val nearbyUsers: StateFlow<List<NearbyUserCardModel>> =
    combine(
      myProfileState,
      availableProfiles,
      allBlockedUids,
      _radiusFilter,
      _activityFilter,
      _viewerLocation,
      _viewerCityArea,
    ) { values ->
      @Suppress("UNCHECKED_CAST")
      val myProfileStateVal = values[0] as UiState<UserProfile?>
      @Suppress("UNCHECKED_CAST")
      val candidates = values[1] as List<UserProfile>
      @Suppress("UNCHECKED_CAST")
      val blockedUids = values[2] as Set<String>
      val radius = values[3] as RadiusFilter
      val activity = values[4] as String?
      @Suppress("UNCHECKED_CAST")
      val viewerCoords = values[5] as Pair<Double, Double>?
      val liveCityArea = values[6] as String?

      val me = (myProfileStateVal as? UiState.Success)?.data

      LocationPrivacy.buildNearbyUserCards(
        myProfile = me,
        viewerLat = viewerCoords?.first ?: 0.0,
        viewerLng = viewerCoords?.second ?: 0.0,
        candidates = candidates,
        blockedUids = blockedUids,
        radius = radius,
        activityFilter = activity,
        viewerCityArea = liveCityArea ?: me?.cityArea.orEmpty(),
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  // Active / Ringing Call state (never surface ringing calls from blocked users)
  private val _activeCallId = MutableStateFlow<String?>(null)
  val activeCall: StateFlow<VoiceCall?> =
    combine(calls, myProfileState, allBlockedUids, _activeCallId) { callList, profileState, blocked, activeId ->
      val myProfile = (profileState as? UiState.Success)?.data
      val amAvailable = myProfile?.isCurrentlyAvailable() == true
      if (activeId != null) {
        callList.firstOrNull { it.id == activeId }
      } else {
        callList.firstOrNull { call ->
          call.peerUid(currentUserId) !in blocked &&
            (call.status == "ACTIVE" ||
              (call.status == "RINGING" && (call.callerUid == currentUserId || amAvailable)))
        }
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  private val _isMuted = MutableStateFlow(false)
  val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

  private val _isSpeakerOn = MutableStateFlow(false)
  val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

  private val _callDurationSeconds = MutableStateFlow(0L)
  val callDurationSeconds: StateFlow<Long> = _callDurationSeconds.asStateFlow()

  private val _callConnectionNote = MutableStateFlow("")
  val callConnectionNote: StateFlow<String> = _callConnectionNote.asStateFlow()

  private var callEngine: VoiceCallEngine? = null
  private var callObserverJob: Job? = null
  private var candidateObserverJob: Job? = null
  private var callTimerJob: Job? = null
  private val processedCandidateIds = mutableSetOf<String>()
  private var remoteAnswerApplied = false

  // Admin Console State
  private val _adminSnapshot = MutableStateFlow<AdminDashboardSnapshot?>(null)
  val adminSnapshot: StateFlow<AdminDashboardSnapshot?> = _adminSnapshot.asStateFlow()
  private val _adminLoading = MutableStateFlow(false)
  val adminLoading: StateFlow<Boolean> = _adminLoading.asStateFlow()

  init {
    // Periodic check for automatic availability expiration
    viewModelScope.launch {
      while (true) {
        delay(30_000L)
        val profile = (myProfileState.value as? UiState.Success)?.data
        if (profile != null && profile.isAvailable && profile.availabilityExpiresAtMillis in 1..System.currentTimeMillis()) {
          repository.stopAvailability(markExpired = true)
          _statusMessage.value = "Your availability session has expired."
        }
      }
    }

    // Automatically reject incoming ringing calls if availability is OFF or caller is blocked
    viewModelScope.launch {
      combine(calls, myProfileState, allBlockedUids) { callList, profileState, blocked ->
        Triple(callList, (profileState as? UiState.Success)?.data, blocked)
      }.collect { (callList, myProfile, blocked) ->
        callList
          .filter { it.calleeUid == currentUserId && it.status == "RINGING" }
          .forEach { incoming ->
            val shouldRejectForOffline = myProfile != null && !myProfile.isCurrentlyAvailable()
            val shouldRejectForBlock = incoming.callerUid in blocked
            if (shouldRejectForOffline || shouldRejectForBlock) {
              repository.updateCallStatus(incoming.id, "DECLINED", 0L)
            }
          }
      }
    }

    // Load profiles for connected/request peers so verification status & avatars stay visible
    viewModelScope.launch {
      combine(connections, requests) { connList, reqList ->
        buildSet {
          connList.forEach { conn ->
            val other = conn.otherUid(currentUserId)
            if (other.isNotBlank()) add(other)
          }
          reqList.forEach { req ->
            val other = if (req.fromUid == currentUserId) req.toUid else req.fromUid
            if (other.isNotBlank()) add(other)
          }
        }
      }.collect { peerUids ->
        val missing = peerUids.filter { it !in _cachedPeerProfiles.value }
        if (missing.isNotEmpty()) {
          val fetched =
            missing.mapNotNull { uid ->
              repository.getProfileById(uid).getOrNull()?.let { uid to it }
            }.toMap()
          if (fetched.isNotEmpty()) {
            _cachedPeerProfiles.value = _cachedPeerProfiles.value + fetched
          }
        }
      }
    }
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun dismissCallBlockedNotice() {
    _callBlockedNotice.value = null
  }

  fun dismissMissedCheckInAlert() {
    _missedCheckInAlertMeetup.value = null
  }

  fun showMessage(msg: String) {
    _statusMessage.value = msg
  }

  fun setRadiusFilter(filter: RadiusFilter) {
    _radiusFilter.value = filter
  }

  fun setActivityFilter(activity: String?) {
    _activityFilter.value = activity
  }

  // ---------------------------------------------------------------------------
  // Onboarding, Trusted Contacts, Identity Verification & Profile
  // ---------------------------------------------------------------------------
  fun completeOnboarding(
    displayName: String,
    email: String,
    phoneNote: String,
    dateOfBirthMillis: Long,
    gender: String,
    bio: String,
    cityArea: String,
    photoData: String,
    interests: List<String>,
    approxLat: Double = 0.0,
    approxLng: Double = 0.0,
    onDone: () -> Unit,
  ) {
    viewModelScope.launch {
      repository.completeOnboarding(
        displayName = displayName,
        email = email,
        phoneNote = phoneNote,
        dateOfBirthMillis = dateOfBirthMillis,
        gender = gender,
        bio = bio,
        cityArea = cityArea,
        photoData = photoData,
        interests = interests,
        approxLat = approxLat,
        approxLng = approxLng,
      ).onSuccess {
        _statusMessage.value = "Welcome to FriendsHub!"
        onDone()
      }.onFailure { err ->
        _statusMessage.value = err.localizedMessage ?: "Failed to save profile"
      }
    }
  }

  fun updateProfile(
    firstName: String,
    bio: String,
    cityArea: String,
    gender: String,
    photoData: String,
    interests: List<String>,
    discoveryOptIn: Boolean,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      repository.updateProfile(firstName, bio, cityArea, gender, photoData, interests, discoveryOptIn)
        .onSuccess {
          _statusMessage.value = "Profile updated"
          onDone()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Update failed" }
    }
  }

  fun updateNotificationPreferences(push: Boolean, calls: Boolean, meetups: Boolean) {
    viewModelScope.launch {
      repository.updateNotificationPreferences(push, calls, meetups)
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not save preferences" }
    }
  }

  fun updateTrustedContact(name: String, phone: String, consent: Boolean, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.updateTrustedContactSettings(name, phone, consent)
        .onSuccess {
          _statusMessage.value = "Trusted contact safety settings saved."
          onDone()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not save trusted contact" }
    }
  }

  fun recordLevel1PhoneVerified(verifiedPhoneNumber: String) {
    viewModelScope.launch {
      repository.recordLevel1PhoneVerified(verifiedPhoneNumber)
        .onSuccess {
          _statusMessage.value = "Level 1 verified! Phone number verified with OTP."
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not save phone verification" }
    }
  }

  fun submitLevel2LiveSelfie(selfiePhotoData: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.submitLevel2LiveSelfie(selfiePhotoData)
        .onSuccess {
          _statusMessage.value = "Live selfie submitted for Level 2 admin review."
          onDone()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not submit live selfie" }
    }
  }

  // ---------------------------------------------------------------------------
  // Availability & Coarse Location
  // ---------------------------------------------------------------------------
  private suspend fun applyDetectedLocation(detected: LocationPrivacy.DetectedLocationAddress) {
    _viewerLocation.value = detected.rawLat to detected.rawLng
    if (detected.cityArea.isNotBlank()) {
      _viewerCityArea.value = detected.cityArea
    }
    val profile = (myProfileState.value as? UiState.Success)?.data ?: return
    val isLive = profile.isCurrentlyAvailable()
    val newArea = detected.cityArea.trim()
    val areaChanged =
      newArea.isNotBlank() &&
        !newArea.equals(profile.cityArea.trim(), ignoreCase = true) &&
        !newArea.equals(lastSyncedCityArea, ignoreCase = true)
    val coordsMoved =
      isLive &&
        (lastSyncedCoords == null ||
          LocationPrivacy.distanceKm(
            lastSyncedCoords!!.first,
            lastSyncedCoords!!.second,
            detected.approxLat,
            detected.approxLng,
          ) >= 0.3)

    if (areaChanged || coordsMoved) {
      if (areaChanged) lastSyncedCityArea = newArea
      if (coordsMoved) lastSyncedCoords = detected.approxLat to detected.approxLng
      repository.syncCurrentLocation(
        cityArea = newArea.takeIf { areaChanged },
        approxLat = detected.approxLat,
        approxLng = detected.approxLng,
        updateCoordinates = isLive,
      )
    }
  }

  fun startLiveLocationUpdates(context: Context) {
    val appContext = context.applicationContext
    if (!LocationPrivacy.hasLocationPermission(appContext)) return
    if (liveLocationJob?.isActive == true) {
      refreshViewerLocation(appContext)
      return
    }
    liveLocationJob =
      viewModelScope.launch {
        LocationPrivacy.observeLiveLocations(appContext)
          .catch { /* keep last known state if provider temporarily unavailable */ }
          .collect { detected ->
            applyDetectedLocation(detected)
          }
      }
  }

  fun stopLiveLocationUpdates() {
    liveLocationJob?.cancel()
    liveLocationJob = null
  }

  fun refreshViewerLocation(context: Context, showFeedback: Boolean = false) {
    viewModelScope.launch {
      val detected = withTimeoutOrNull(6000L) {
        LocationPrivacy.detectCityAreaFromDeviceLocation(context)
      }
      if (detected != null) {
        applyDetectedLocation(detected)
        if (showFeedback) {
          val areaSuffix = detected.cityArea.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
          _statusMessage.value = "Location updated$areaSuffix."
        }
      } else if (showFeedback) {
        _statusMessage.value = "Enable location permission in settings to refresh your live area."
      }
    }
  }

  fun turnOnAvailability(
    context: Context,
    activity: String,
    duration: AvailabilityDuration,
  ) {
    viewModelScope.launch {
      _isEnablingAvailability.value = true
      try {
        val detected = withTimeoutOrNull(6000L) {
          LocationPrivacy.detectCityAreaFromDeviceLocation(context)
        }
        if (detected != null) {
          _viewerLocation.value = detected.rawLat to detected.rawLng
          if (detected.cityArea.isNotBlank()) {
            _viewerCityArea.value = detected.cityArea
            lastSyncedCityArea = detected.cityArea
          }
          lastSyncedCoords = detected.approxLat to detected.approxLng
        }
        val currentProfile = (myProfileState.value as? UiState.Success)?.data
        val fallbackLat = _viewerLocation.value?.first ?: currentProfile?.approxLat ?: 0.0
        val fallbackLng = _viewerLocation.value?.second ?: currentProfile?.approxLng ?: 0.0
        val coords =
          if (detected != null) (detected.approxLat to detected.approxLng)
          else (fallbackLat to fallbackLng)
        repository.startAvailability(
          activity = activity,
          durationMinutes = duration.minutes,
          approxLat = coords.first,
          approxLng = coords.second,
          cityAreaOverride = detected?.cityArea?.takeIf { it.isNotBlank() },
        )
          .onSuccess {
            _statusMessage.value =
              if (detected != null) {
                "You're now visible nearby for ${duration.label} ($activity)."
              } else {
                "You're now live for ${duration.label} ($activity) using your city area."
              }
          }
          .onFailure {
            _statusMessage.value = it.localizedMessage ?: "Could not enable availability"
          }
      } finally {
        _isEnablingAvailability.value = false
      }
    }
  }

  fun updateLiveActivity(activity: String) {
    viewModelScope.launch {
      repository.updateLiveActivity(activity)
        .onSuccess { _statusMessage.value = "Updated live activity to $activity." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not update activity" }
    }
  }

  fun turnOffAvailability() {
    viewModelScope.launch {
      repository.stopAvailability(markExpired = false)
        .onSuccess { _statusMessage.value = "Availability turned off. Location sharing stopped." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to turn off availability" }
    }
  }

  // ---------------------------------------------------------------------------
  // Connections & Requests
  // ---------------------------------------------------------------------------
  fun sendConnectionRequest(
    toProfile: UserProfile,
    activity: String,
    message: String,
    onSent: () -> Unit = {},
  ) {
    val myProfile = (myProfileState.value as? UiState.Success)?.data
    if (myProfile == null) {
      _statusMessage.value = "Complete your profile first."
      return
    }
    viewModelScope.launch {
      repository.sendConnectionRequest(myProfile, toProfile, activity, message)
        .onSuccess {
          _statusMessage.value = "Connection request sent to ${toProfile.firstName}!"
          onSent()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not send request" }
    }
  }

  fun respondToRequest(request: ConnectionRequest, accept: Boolean) {
    val myCity = (myProfileState.value as? UiState.Success)?.data?.cityArea.orEmpty()
    viewModelScope.launch {
      repository.respondToConnectionRequest(request, accept, myCity)
        .onSuccess {
          _statusMessage.value =
            if (accept) "Connected with ${request.fromName}!" else "Request declined."
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Action failed" }
    }
  }

  fun endConnection(connection: Connection) {
    viewModelScope.launch {
      repository.endConnection(connection)
        .onSuccess { _statusMessage.value = "Connection ended." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not end connection" }
    }
  }

  // ---------------------------------------------------------------------------
  // Meetups & Safety Check-In Policy
  // ---------------------------------------------------------------------------
  fun proposeMeetup(
    connection: Connection,
    activity: String,
    dateText: String,
    timeText: String,
    locationName: String,
    area: String,
    notes: String,
    scheduledAtMillis: Long = System.currentTimeMillis() + 60 * 60_000L,
    shareWithTrustedContact: Boolean = false,
    onDone: () -> Unit = {},
  ) {
    val myName = (myProfileState.value as? UiState.Success)?.data?.firstName ?: "Friend"
    viewModelScope.launch {
      repository.proposeMeetup(
        connection = connection,
        myName = myName,
        activity = activity,
        scheduledDateText = dateText,
        scheduledTimeText = timeText,
        publicLocationName = locationName,
        publicArea = area,
        notes = notes,
        scheduledAtMillis = scheduledAtMillis,
        sharedWithTrustedContact = shareWithTrustedContact,
      )
        .onSuccess {
          _statusMessage.value = "Meetup invitation sent!"
          onDone()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not propose meetup" }
    }
  }

  fun confirmMeetup(meetup: Meetup) {
    val myName = (myProfileState.value as? UiState.Success)?.data?.firstName ?: "Friend"
    viewModelScope.launch {
      repository.confirmMeetup(meetup, myName)
        .onSuccess { _statusMessage.value = "Meetup confirmed!" }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not confirm meetup" }
    }
  }

  fun markMeetupSharedWithTrustedContact(meetup: Meetup) {
    viewModelScope.launch {
      repository.markMeetupSharedWithTrustedContact(meetup)
        .onSuccess { _statusMessage.value = "Meetup plan marked as shared with your trusted contact." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not update meetup sharing" }
    }
  }

  fun checkInMeetup(meetup: Meetup) {
    viewModelScope.launch {
      repository.checkInMeetup(meetup)
        .onSuccess { _statusMessage.value = "Checked in at ${meetup.publicLocationName}. Stay safe!" }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Check-in failed" }
    }
  }

  fun markMeetupSafe(meetup: Meetup) {
    viewModelScope.launch {
      repository.markMeetupSafe(meetup)
        .onSuccess {
          if (_missedCheckInAlertMeetup.value?.id == meetup.id) {
            _missedCheckInAlertMeetup.value = null
          }
          _statusMessage.value = "Glad you're safe! Meetup marked complete."
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not mark safe" }
    }
  }

  fun cancelMeetup(meetup: Meetup) {
    val myName = (myProfileState.value as? UiState.Success)?.data?.firstName ?: "Friend"
    viewModelScope.launch {
      repository.cancelMeetup(meetup, myName)
        .onSuccess { _statusMessage.value = "Meetup cancelled." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not cancel meetup" }
    }
  }

  fun completeMeetup(meetup: Meetup) {
    viewModelScope.launch {
      repository.completeMeetup(meetup)
        .onSuccess { _statusMessage.value = "Meetup marked completed!" }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not update meetup" }
    }
  }

  /**
   * Evaluates confirmed/proposed meetups against their scheduled start time:
   * - After 5 minutes past scheduled time without Check In or "I'm Safe": sends a safety reminder.
   * - After 15 minutes past scheduled time without Check In or "I'm Safe" (if user consent is ON):
   *   triggers the missed check-in alert for their selected Trusted Contact.
   */
  fun evaluateMissedMeetupCheckIns(context: Context, nowMillis: Long = System.currentTimeMillis()) {
    val account = (accountState.value as? UiState.Success)?.data ?: return
    val profile = (myProfileState.value as? UiState.Success)?.data ?: return
    val activeMeetups = meetups.value.filter { it.status == "CONFIRMED" && it.scheduledAtMillis > 0L }

    activeMeetups.forEach { meetup ->
      if (meetup.isCheckedInBy(currentUserId) || meetup.isMarkedSafeBy(currentUserId)) return@forEach

      val elapsedSinceStart = nowMillis - meetup.scheduledAtMillis
      if (elapsedSinceStart >= REMINDER_GRACE_MILLIS && !meetup.hasReminderSentTo(currentUserId)) {
        viewModelScope.launch {
          repository.recordMissedCheckInReminder(meetup, profile.firstName)
          SafetyNotificationHelper.showCheckInReminderNotification(context, meetup)
          _statusMessage.value = "Safety check-in reminder: Tap Check In or I'm Safe for ${meetup.publicLocationName}."
        }
      }

      val hasConsent = account.trustedContactConsent || meetup.isSharedWithTrustedBy(currentUserId)
      if (
        hasConsent &&
        account.hasTrustedContact() &&
        elapsedSinceStart >= ESCALATION_GRACE_MILLIS &&
        !meetup.hasTrustedAlertTriggeredFor(currentUserId)
      ) {
        viewModelScope.launch {
          repository.recordTrustedContactAlert(
            meetup = meetup,
            myName = profile.firstName,
            trustedContactName = account.trustedContactName,
          )
          SafetyNotificationHelper.showTrustedContactAlertNotification(
            context = context,
            meetup = meetup,
            myName = profile.firstName,
            trustedContactName = account.trustedContactName,
            trustedContactPhone = account.trustedContactPhone,
            liveCityArea = _viewerCityArea.value ?: profile.cityArea,
          )
          _missedCheckInAlertMeetup.value = meetup
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Voice Calling (WebRTC + Firestore Signaling)
  // ---------------------------------------------------------------------------
  fun startOutgoingCall(context: Context, connection: Connection) {
    val myName = (myProfileState.value as? UiState.Success)?.data?.firstName ?: "Friend"
    val calleeUid = connection.otherUid(currentUserId)
    val calleeName = connection.otherName(currentUserId)
    val participants = listOf(currentUserId, calleeUid)

    if (calleeUid in allBlockedUids.value) {
      _statusMessage.value = "Blocked users cannot call each other."
      return
    }

    viewModelScope.launch {
      val calleeProfile = repository.getProfileById(calleeUid).getOrNull()
      if (calleeProfile == null || !calleeProfile.isCurrentlyAvailable()) {
        val msg = "$calleeName is not online right now. Calls are only allowed when their availability is switched ON."
        _callBlockedNotice.value = msg
        _statusMessage.value = "$calleeName is not online right now."
        return@launch
      }

      cleanupVoiceSession()
      _isMuted.value = false
      _isSpeakerOn.value = false
      _callDurationSeconds.value = 0L
      _callConnectionNote.value = "Calling $calleeName..."

      val engine = callEngineFactory(context.applicationContext)
      callEngine = engine

      val bufferedCandidates = mutableListOf<Triple<String, Int, String>>()
      var createdCallId: String? = null

      engine.startAsCaller(
        onOfferCreated = { offerSdp ->
          viewModelScope.launch {
            repository.initiateCall(connection, myName, offerSdp)
              .onSuccess { call ->
                createdCallId = call.id
                _activeCallId.value = call.id
                bufferedCandidates.forEach { (mid, idx, cand) ->
                  launch { repository.addIceCandidate(call.id, participants, mid, idx, cand) }
                }
                bufferedCandidates.clear()
                observeActiveCallSignaling(call.id, isCaller = true)
              }
              .onFailure { err ->
                val rawMsg = err.localizedMessage ?: "Unable to start call"
                val friendlyMsg =
                  if (rawMsg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                    rawMsg.contains("not online", ignoreCase = true)
                  ) {
                    "$calleeName is not online right now. Calls are only allowed when their availability is switched ON."
                  } else {
                    rawMsg
                  }
                _callBlockedNotice.value = friendlyMsg
                _statusMessage.value = friendlyMsg
                cleanupVoiceSession()
              }
          }
        },
        onIceCandidate = { sdpMid, sdpMLineIndex, candidate ->
          val cid = createdCallId
          if (cid == null) {
            bufferedCandidates.add(Triple(sdpMid, sdpMLineIndex, candidate))
          } else {
            viewModelScope.launch {
              repository.addIceCandidate(cid, participants, sdpMid, sdpMLineIndex, candidate)
            }
          }
        },
        onConnected = {
          _callConnectionNote.value = "Connected • Encrypted P2P Audio"
          startCallTimer()
        },
        onDisconnected = { reason ->
          _callConnectionNote.value = reason
        },
      )
    }
  }

  fun acceptIncomingCall(context: Context, call: VoiceCall) {
    cleanupVoiceSession()
    _isMuted.value = false
    _isSpeakerOn.value = false
    _callDurationSeconds.value = 0L
    _activeCallId.value = call.id
    _callConnectionNote.value = "Connecting audio..."

    val engine = callEngineFactory(context.applicationContext)
    callEngine = engine

    engine.startAsCallee(
      remoteOfferSdp = call.offerSdp,
      onAnswerCreated = { answerSdp ->
        viewModelScope.launch {
          repository.acceptCall(call.id, answerSdp)
            .onSuccess {
              observeActiveCallSignaling(call.id, isCaller = false)
            }
            .onFailure { err ->
              _statusMessage.value = err.localizedMessage ?: "Could not answer call"
              cleanupVoiceSession()
            }
        }
      },
      onIceCandidate = { sdpMid, sdpMLineIndex, candidate ->
        viewModelScope.launch {
          repository.addIceCandidate(call.id, call.participantUids, sdpMid, sdpMLineIndex, candidate)
        }
      },
      onConnected = {
        _callConnectionNote.value = "Connected • Encrypted P2P Audio"
        startCallTimer()
      },
      onDisconnected = { reason ->
        _callConnectionNote.value = reason
      },
    )
  }

  fun declineIncomingCall(call: VoiceCall) {
    viewModelScope.launch {
      repository.updateCallStatus(call.id, "DECLINED", 0L)
      cleanupVoiceSession()
    }
  }

  fun endActiveCall(call: VoiceCall) {
    val duration = _callDurationSeconds.value
    val targetStatus = if (call.status == "RINGING") "MISSED" else "ENDED"
    viewModelScope.launch {
      repository.updateCallStatus(call.id, targetStatus, duration)
      cleanupVoiceSession()
    }
  }

  fun toggleMute() {
    val next = !_isMuted.value
    _isMuted.value = next
    callEngine?.setMuted(next)
  }

  fun toggleSpeaker() {
    val next = !_isSpeakerOn.value
    _isSpeakerOn.value = next
    callEngine?.setSpeakerEnabled(next)
  }

  private fun observeActiveCallSignaling(callId: String, isCaller: Boolean) {
    callObserverJob?.cancel()
    candidateObserverJob?.cancel()
    processedCandidateIds.clear()
    remoteAnswerApplied = false

    callObserverJob =
      viewModelScope.launch {
        repository.observeCall(callId, currentUserId).collect { call ->
          if (call == null) return@collect
          if (isCaller && !remoteAnswerApplied && call.answerSdp.isNotBlank()) {
            remoteAnswerApplied = true
            callEngine?.setRemoteAnswer(call.answerSdp)
            _callConnectionNote.value = "Connecting audio..."
          }
          if (call.status in listOf("DECLINED", "MISSED", "ENDED", "FAILED")) {
            _statusMessage.value =
              when (call.status) {
                "DECLINED" -> "Call declined."
                "MISSED" -> "Call ended."
                "FAILED" -> "Call connection failed."
                else -> "Call ended."
              }
            cleanupVoiceSession()
          }
        }
      }

    candidateObserverJob =
      viewModelScope.launch {
        repository.observeIceCandidates(callId, currentUserId).collect { list ->
          list.filter { it.senderUid != currentUserId && it.id !in processedCandidateIds }
            .forEach { record ->
              processedCandidateIds.add(record.id)
              callEngine?.addRemoteIceCandidate(record)
            }
        }
      }
  }

  private fun startCallTimer() {
    if (callTimerJob?.isActive == true) return
    callTimerJob =
      viewModelScope.launch {
        while (true) {
          delay(1000L)
          _callDurationSeconds.value += 1L
        }
      }
  }

  private fun cleanupVoiceSession() {
    callTimerJob?.cancel()
    callTimerJob = null
    callObserverJob?.cancel()
    callObserverJob = null
    candidateObserverJob?.cancel()
    candidateObserverJob = null
    processedCandidateIds.clear()
    remoteAnswerApplied = false
    callEngine?.stop()
    callEngine = null
    _activeCallId.value = null
    _isMuted.value = false
    _isSpeakerOn.value = false
    _callDurationSeconds.value = 0L
    _callConnectionNote.value = ""
  }

  // ---------------------------------------------------------------------------
  // Safety, Notifications & Admin
  // ---------------------------------------------------------------------------
  fun blockUser(blockedUid: String, blockedName: String, reason: String = "", onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.blockUser(blockedUid, blockedName, reason)
        .onSuccess {
          _statusMessage.value = "$blockedName has been blocked."
          onDone()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Block failed" }
    }
  }

  fun unblockUser(blockedUid: String) {
    viewModelScope.launch {
      repository.unblockUser(blockedUid)
        .onSuccess { _statusMessage.value = "User unblocked." }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Unblock failed" }
    }
  }

  fun reportUser(
    reportedUid: String,
    reportedName: String,
    category: String,
    details: String,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      repository.reportUser(reportedUid, reportedName, category, details)
        .onSuccess {
          _statusMessage.value = "Safety report submitted. Thank you for keeping FriendsHub safe."
          onDone()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not submit report" }
    }
  }

  fun markNotificationRead(notificationId: String) {
    viewModelScope.launch {
      repository.markNotificationRead(notificationId)
    }
  }

  fun deleteMyAccountData(onDeleted: () -> Unit) {
    viewModelScope.launch {
      repository.deleteMyAccountData()
        .onSuccess {
          _statusMessage.value = "Account data deleted."
          onDeleted()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Could not delete account" }
    }
  }

  fun loadAdminDashboard() {
    viewModelScope.launch {
      _adminLoading.value = true
      repository.fetchAdminDashboardData()
        .onSuccess { _adminSnapshot.value = it }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Admin access required" }
      _adminLoading.value = false
    }
  }

  fun adminToggleSuspend(targetUid: String, suspend: Boolean) {
    viewModelScope.launch {
      repository.adminSetAccountStatus(targetUid, suspend)
        .onSuccess {
          _statusMessage.value = if (suspend) "Account suspended." else "Account reactivated."
          loadAdminDashboard()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Action failed" }
    }
  }

  fun adminToggleVerified(targetUid: String, verified: Boolean) {
    viewModelScope.launch {
      repository.adminToggleVerified(targetUid, verified)
        .onSuccess {
          _statusMessage.value = if (verified) "Profile marked verified." else "Verification removed."
          loadAdminDashboard()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Action failed" }
    }
  }

  fun adminUpdateReport(reportId: String, status: String, notes: String) {
    viewModelScope.launch {
      repository.adminUpdateReport(reportId, status, notes)
        .onSuccess {
          _statusMessage.value = "Report updated."
          loadAdminDashboard()
        }
        .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to update report" }
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopLiveLocationUpdates()
    cleanupVoiceSession()
  }

  private companion object {
    const val TAG = "FriendsHubVM"
    const val STOP_TIMEOUT_MS = 5000L
    const val REMINDER_GRACE_MILLIS = 5 * 60_000L
    const val ESCALATION_GRACE_MILLIS = 15 * 60_000L
  }
}
