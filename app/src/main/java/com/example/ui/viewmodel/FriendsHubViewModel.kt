package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityCategory
import com.example.data.model.AppNotification
import com.example.data.model.AvailabilityDuration
import com.example.data.model.AvailabilitySession
import com.example.data.model.BlockedUser
import com.example.data.model.Connection
import com.example.data.model.ConnectionRequest
import com.example.data.model.IceCandidateRecord
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
import com.example.voice.VoiceCallEngine
import com.example.voice.WebRtcVoiceCallEngine
import java.util.UUID
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

  private val _viewerLocation = MutableStateFlow<Pair<Double, Double>?>(null)
  val viewerLocation: StateFlow<Pair<Double, Double>?> = _viewerLocation.asStateFlow()

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

  val requests: StateFlow<List<ConnectionRequest>> =
    repository.observeConnectionRequests(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val connections: StateFlow<List<Connection>> =
    repository.observeConnections(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val meetups: StateFlow<List<Meetup>> =
    repository.observeMeetups(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val calls: StateFlow<List<VoiceCall>> =
    repository.observeCalls(currentUserId)
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

  val notifications: StateFlow<List<AppNotification>> =
    repository.observeNotifications(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val sessions: StateFlow<List<AvailabilitySession>> =
    repository.observeAvailabilitySessions(currentUserId)
      .catch { emit(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  // Filtered Nearby Discovery cards (strictly local to user's GPS radius or matching city/area)
  val nearbyUsers: StateFlow<List<NearbyUserCardModel>> =
    combine(
      myProfileState,
      availableProfiles,
      blocks,
      blockedByOthers,
      _radiusFilter,
      _activityFilter,
      _viewerLocation,
    ) { values ->
      @Suppress("UNCHECKED_CAST")
      val myProfileStateVal = values[0] as UiState<UserProfile?>
      @Suppress("UNCHECKED_CAST")
      val candidates = values[1] as List<UserProfile>
      @Suppress("UNCHECKED_CAST")
      val myBlocks = values[2] as List<BlockedUser>
      @Suppress("UNCHECKED_CAST")
      val blockedMe = values[3] as List<BlockedUser>
      val radius = values[4] as RadiusFilter
      val activity = values[5] as String?
      @Suppress("UNCHECKED_CAST")
      val viewerCoords = values[6] as Pair<Double, Double>?

      val me = (myProfileStateVal as? UiState.Success)?.data
      val blockedUids = (myBlocks.map { it.blockedUid } + blockedMe.map { it.blockerUid }).toSet()

      LocationPrivacy.buildNearbyUserCards(
        myProfile = me,
        viewerLat = viewerCoords?.first ?: 0.0,
        viewerLng = viewerCoords?.second ?: 0.0,
        candidates = candidates,
        blockedUids = blockedUids,
        radius = radius,
        activityFilter = activity,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  // Active / Ringing Call state
  private val _activeCallId = MutableStateFlow<String?>(null)
  val activeCall: StateFlow<VoiceCall?> =
    combine(calls, myProfileState, _activeCallId) { callList, profileState, activeId ->
      val myProfile = (profileState as? UiState.Success)?.data
      val amAvailable = myProfile?.isCurrentlyAvailable() == true
      if (activeId != null) {
        callList.firstOrNull { it.id == activeId }
      } else {
        callList.firstOrNull { call ->
          call.status == "ACTIVE" ||
            (call.status == "RINGING" && (call.callerUid == currentUserId || amAvailable))
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

    // Automatically reject incoming ringing calls if the user has switched availability OFF
    viewModelScope.launch {
      combine(calls, myProfileState) { callList, profileState ->
        callList to (profileState as? UiState.Success)?.data
      }.collect { (callList, myProfile) ->
        if (myProfile != null && !myProfile.isCurrentlyAvailable()) {
          callList
            .filter { it.calleeUid == currentUserId && it.status == "RINGING" }
            .forEach { incoming ->
              repository.updateCallStatus(incoming.id, "DECLINED", 0L)
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
  // Onboarding & Profile
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

  // ---------------------------------------------------------------------------
  // Availability & Coarse Location
  // ---------------------------------------------------------------------------
  fun refreshViewerLocation(context: Context) {
    viewModelScope.launch {
      val fetched = withTimeoutOrNull(5000L) {
        LocationPrivacy.fetchCoarseDeviceLocation(context)
      }
      if (fetched != null) {
        _viewerLocation.value = fetched
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
        val fetchedCoords = withTimeoutOrNull(5000L) {
          LocationPrivacy.fetchCoarseDeviceLocation(context)
        }
        if (fetchedCoords != null) {
          _viewerLocation.value = fetchedCoords
        }
        val currentProfile = (myProfileState.value as? UiState.Success)?.data
        val fallbackLat = _viewerLocation.value?.first ?: currentProfile?.approxLat ?: 0.0
        val fallbackLng = _viewerLocation.value?.second ?: currentProfile?.approxLng ?: 0.0
        val coords = fetchedCoords ?: (fallbackLat to fallbackLng)
        repository.startAvailability(activity, duration.minutes, coords.first, coords.second)
          .onSuccess {
            _statusMessage.value =
              if (fetchedCoords != null) {
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
  // Meetups
  // ---------------------------------------------------------------------------
  fun proposeMeetup(
    connection: Connection,
    activity: String,
    dateText: String,
    timeText: String,
    locationName: String,
    area: String,
    notes: String,
    onDone: () -> Unit = {},
  ) {
    val myName = (myProfileState.value as? UiState.Success)?.data?.firstName ?: "Friend"
    viewModelScope.launch {
      repository.proposeMeetup(connection, myName, activity, dateText, timeText, locationName, area, notes)
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

  // ---------------------------------------------------------------------------
  // Voice Calling (WebRTC + Firestore Signaling)
  // ---------------------------------------------------------------------------
  fun startOutgoingCall(context: Context, connection: Connection) {
    val myName = (myProfileState.value as? UiState.Success)?.data?.firstName ?: "Friend"
    val calleeUid = connection.otherUid(currentUserId)
    val calleeName = connection.otherName(currentUserId)
    val participants = listOf(currentUserId, calleeUid)

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

      // Buffer local ICE candidates until call document ID exists
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
    cleanupVoiceSession()
  }

  private companion object {
    const val TAG = "FriendsHubVM"
    const val STOP_TIMEOUT_MS = 5000L
  }
}
