package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.Connection
import com.example.data.model.Meetup
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.FriendsHubRepository
import com.example.ui.auth.GoogleSignInHelper
import com.example.ui.screens.ActivityHistoryScreen
import com.example.ui.screens.AdminModerationScreen
import com.example.ui.screens.AuthWelcomeScreen
import com.example.ui.screens.BlockedUsersScreen
import com.example.ui.screens.ConnectionsScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LegalInfoScreen
import com.example.ui.screens.MeetupDetailsScreen
import com.example.ui.screens.MeetupPlanningScreen
import com.example.ui.screens.NearbyDiscoveryScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingFlowScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SendConnectionRequestDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UserProfileDetailsScreen
import com.example.ui.screens.VoiceCallScreen
import com.example.ui.viewmodel.FriendsHubViewModel
import com.example.util.LocationPrivacy
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class MainTab(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home),
  DISCOVER("Discover", Icons.Rounded.Explore, Icons.Outlined.Explore),
  CONNECTIONS("Connections", Icons.Rounded.Group, Icons.Outlined.Group),
  ACTIVITY("Activity", Icons.Rounded.History, Icons.Outlined.History),
  PROFILE("Profile", Icons.Rounded.Person, Icons.Outlined.Person),
}

sealed interface SubScreen {
  data class ProfileDetails(val profile: UserProfile) : SubScreen
  data class PlanMeetup(val connection: Connection) : SubScreen
  data class MeetupDetails(val meetupId: String) : SubScreen
  data object Notifications : SubScreen
  data object Settings : SubScreen
  data object BlockedUsers : SubScreen
  data object PrivacyPolicy : SubScreen
  data object Terms : SubScreen
  data object AdminModeration : SubScreen
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun FriendsHubApp() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  var showSplash by remember { mutableStateOf(true) }
  val currentUser by Firebase.auth.authStateFlow()
    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
  var unauthLegalScreen by remember { mutableStateOf<SubScreen?>(null) }

  LaunchedEffect(Unit) {
    GoogleSignInHelper.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {},
      onUnauthenticated = {},
      scope = scope,
    )
    delay(750L)
    showSplash = false
  }

  if (showSplash) {
    SplashBrandingScreen()
    return
  }

  val user = currentUser
  if (user == null) {
    when (unauthLegalScreen) {
      SubScreen.PrivacyPolicy -> {
        BackHandler { unauthLegalScreen = null }
        LegalInfoScreen("Privacy Policy", isPrivacyPolicy = true) { unauthLegalScreen = null }
      }
      SubScreen.Terms -> {
        BackHandler { unauthLegalScreen = null }
        LegalInfoScreen("Terms & Safety", isPrivacyPolicy = false) { unauthLegalScreen = null }
      }
      else -> {
        AuthWelcomeScreen(
          onAuthSuccess = {},
          onOpenPrivacy = { unauthLegalScreen = SubScreen.PrivacyPolicy },
          onOpenTerms = { unauthLegalScreen = SubScreen.Terms },
        )
      }
    }
  } else {
    key(user.uid) {
      val viewModel: FriendsHubViewModel =
        viewModel(
          key = user.uid,
          factory =
            viewModelFactory {
              initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FriendsHubViewModel(FriendsHubRepository(db), user.uid)
              }
            },
        )
      AuthenticatedAppShell(
        viewModel = viewModel,
        userDisplayName = user.displayName.orEmpty(),
        userEmail = user.email.orEmpty(),
        onSignOut = {
          GoogleSignInHelper.signOut(credentialManager, scope)
        },
      )
    }
  }
}

@Composable
private fun SplashBrandingScreen() {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.primary)
        .statusBarsPadding()
        .navigationBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Image(
        painter = painterResource(id = R.drawable.ic_friendshub_logo),
        contentDescription = "FriendsHub",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(92.dp).clip(MaterialTheme.shapes.large),
      )
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "FriendsHub",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onPrimary,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Find a friend. Share a moment.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
      )
    }
  }
}

@Composable
private fun AuthenticatedAppShell(
  viewModel: FriendsHubViewModel,
  userDisplayName: String,
  userEmail: String,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val accountState by viewModel.accountState.collectAsStateWithLifecycle()
  val myProfileState by viewModel.myProfileState.collectAsStateWithLifecycle()
  val nearbyUsers by viewModel.nearbyUsers.collectAsStateWithLifecycle()
  val requests by viewModel.requests.collectAsStateWithLifecycle()
  val connections by viewModel.connections.collectAsStateWithLifecycle()
  val meetups by viewModel.meetups.collectAsStateWithLifecycle()
  val calls by viewModel.calls.collectAsStateWithLifecycle()
  val blocks by viewModel.blocks.collectAsStateWithLifecycle()
  val notifications by viewModel.notifications.collectAsStateWithLifecycle()
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val radiusFilter by viewModel.radiusFilter.collectAsStateWithLifecycle()
  val activityFilter by viewModel.activityFilter.collectAsStateWithLifecycle()
  val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
  val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
  val isSpeakerOn by viewModel.isSpeakerOn.collectAsStateWithLifecycle()
  val callDuration by viewModel.callDurationSeconds.collectAsStateWithLifecycle()
  val callNote by viewModel.callConnectionNote.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
  val adminSnapshot by viewModel.adminSnapshot.collectAsStateWithLifecycle()
  val adminLoading by viewModel.adminLoading.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(statusMessage) {
    val msg = statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  // Loading gate while account/profile resolves
  if (accountState is UiState.Loading || myProfileState is UiState.Loading) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val account = (accountState as? UiState.Success)?.data
  val myProfile = (myProfileState as? UiState.Success)?.data

  // Suspended check
  if (account?.accountStatus == "SUSPENDED") {
    Box(
      modifier = Modifier.fillMaxSize().padding(28.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account Suspended", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Text(
          "Your FriendsHub account has been suspended by moderation due to a safety policy violation.",
          textAlign = TextAlign.Center,
        )
        Button(onClick = onSignOut) { Text("Sign Out") }
      }
    }
    return
  }

  // Onboarding gate for first-time users
  if (account == null || !account.onboardingCompleted || myProfile == null) {
    OnboardingFlowScreen(
      defaultName = userDisplayName,
      userEmail = userEmail,
      onCompleteOnboarding = { displayName, phoneNote, dobMillis, gender, bio, cityArea, photoData, interests ->
        viewModel.completeOnboarding(
          displayName = displayName,
          email = userEmail,
          phoneNote = phoneNote,
          dateOfBirthMillis = dobMillis,
          gender = gender,
          bio = bio,
          cityArea = cityArea,
          photoData = photoData,
          interests = interests,
          onDone = {},
        )
      },
      onSignOut = onSignOut,
    )
    return
  }

  // Voice Call full-screen overlay whenever a call is RINGING or ACTIVE
  val currentCall = activeCall
  if (currentCall != null) {
    VoiceCallScreen(
      call = currentCall,
      currentUserId = viewModel.currentUserId,
      isMuted = isMuted,
      isSpeakerOn = isSpeakerOn,
      durationSeconds = callDuration,
      connectionNote = callNote,
      onAcceptIncoming = { viewModel.acceptIncomingCall(context, currentCall) },
      onDeclineIncoming = { viewModel.declineIncomingCall(currentCall) },
      onToggleMute = { viewModel.toggleMute() },
      onToggleSpeaker = { viewModel.toggleSpeaker() },
      onEndCall = { viewModel.endActiveCall(currentCall) },
    )
    return
  }

  var currentTab by remember { mutableStateOf(MainTab.HOME) }
  var activeSubScreen by remember { mutableStateOf<SubScreen?>(null) }
  var quickConnectTarget by remember { mutableStateOf<UserProfile?>(null) }

  if (activeSubScreen != null) {
    BackHandler { activeSubScreen = null }
  }

  val pendingIncomingCount = requests.count { it.toUid == viewModel.currentUserId && it.status == "PENDING" }
  val activeConnectionsCount = connections.count { it.status == "ACTIVE" }
  val upcomingMeetups = meetups.filter { it.status == "PROPOSED" || it.status == "CONFIRMED" }
  val unreadNotifications = notifications.count { !it.isRead }

  // Sub-screens take full screen cleanly with BackHandler
  when (val sub = activeSubScreen) {
    is SubScreen.ProfileDetails -> {
      val distKm = LocationPrivacy.distanceKm(
        myProfile.approxLat,
        myProfile.approxLng,
        sub.profile.approxLat,
        sub.profile.approxLng,
      )
      val distLabel =
        if (myProfile.approxLat == 0.0) sub.profile.cityArea
        else LocationPrivacy.formatApproxDistance(distKm)
      val common = sub.profile.interests.filter { it in myProfile.interests }

      UserProfileDetailsScreen(
        profile = sub.profile,
        distanceLabel = distLabel,
        commonInterests = common,
        onBack = { activeSubScreen = null },
        onSendRequest = { act, msg ->
          viewModel.sendConnectionRequest(sub.profile, act, msg) {
            activeSubScreen = null
          }
        },
        onBlockUser = {
          viewModel.blockUser(sub.profile.uid, sub.profile.firstName) {
            activeSubScreen = null
          }
        },
        onReportUser = { cat, details ->
          viewModel.reportUser(sub.profile.uid, sub.profile.firstName, cat, details) {
            activeSubScreen = null
          }
        },
      )
      return
    }

    is SubScreen.PlanMeetup -> {
      MeetupPlanningScreen(
        connection = sub.connection,
        currentUserId = viewModel.currentUserId,
        myCityArea = myProfile.cityArea,
        onBack = { activeSubScreen = null },
        onPropose = { act, date, time, loc, area, notes ->
          viewModel.proposeMeetup(sub.connection, act, date, time, loc, area, notes) {
            activeSubScreen = null
          }
        },
      )
      return
    }

    is SubScreen.MeetupDetails -> {
      val meetup = meetups.firstOrNull { it.id == sub.meetupId }
      if (meetup == null) {
        activeSubScreen = null
      } else {
        MeetupDetailsScreen(
          meetup = meetup,
          currentUserId = viewModel.currentUserId,
          onBack = { activeSubScreen = null },
          onConfirm = { viewModel.confirmMeetup(meetup) },
          onCancel = { viewModel.cancelMeetup(meetup) },
          onComplete = { viewModel.completeMeetup(meetup) },
        )
      }
      return
    }

    SubScreen.Notifications -> {
      NotificationsScreen(
        notifications = notifications,
        onBack = { activeSubScreen = null },
        onMarkRead = { viewModel.markNotificationRead(it) },
      )
      return
    }

    SubScreen.Settings -> {
      SettingsScreen(
        account = account,
        onBack = { activeSubScreen = null },
        onSavePrefs = { p, c, m -> viewModel.updateNotificationPreferences(p, c, m) },
      )
      return
    }

    SubScreen.BlockedUsers -> {
      BlockedUsersScreen(
        blocks = blocks,
        onBack = { activeSubScreen = null },
        onUnblock = { viewModel.unblockUser(it) },
      )
      return
    }

    SubScreen.PrivacyPolicy -> {
      LegalInfoScreen("Privacy Policy", isPrivacyPolicy = true) { activeSubScreen = null }
      return
    }

    SubScreen.Terms -> {
      LegalInfoScreen("Terms & Safety Guidelines", isPrivacyPolicy = false) { activeSubScreen = null }
      return
    }

    SubScreen.AdminModeration -> {
      AdminModerationScreen(
        snapshot = adminSnapshot,
        isLoading = adminLoading,
        onLoad = { viewModel.loadAdminDashboard() },
        onBack = { activeSubScreen = null },
        onToggleSuspend = { uid, suspend -> viewModel.adminToggleSuspend(uid, suspend) },
        onToggleVerified = { uid, verified -> viewModel.adminToggleVerified(uid, verified) },
        onResolveReport = { id, status -> viewModel.adminUpdateReport(id, status, "") },
      )
      return
    }

    null -> {}
  }

  // Adaptive Navigation Shell (Bottom NavigationBar on phones, NavigationRail on tablets)
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      bottomBar = {
        if (!useRail) {
          NavigationBar(modifier = Modifier.navigationBarsPadding()) {
            MainTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  BadgedBox(
                    badge = {
                      if (tab == MainTab.CONNECTIONS && pendingIncomingCount > 0) {
                        Badge { Text(pendingIncomingCount.toString()) }
                      }
                    }
                  ) {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = tab.label,
                    )
                  }
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .statusBarsPadding(),
      ) {
        if (useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            MainTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
              )
            }
          }
        }

        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(modifier = Modifier.fillMaxSize().widthIn(max = 720.dp)) {
            when (currentTab) {
              MainTab.HOME -> {
                HomeDashboardScreen(
                  myProfile = myProfile,
                  nearbyUsers = nearbyUsers,
                  pendingIncomingCount = pendingIncomingCount,
                  activeConnectionsCount = activeConnectionsCount,
                  upcomingMeetups = upcomingMeetups,
                  unreadNotifications = unreadNotifications,
                  onEnableAvailability = { act, dur -> viewModel.turnOnAvailability(context, act, dur) },
                  onDisableAvailability = { viewModel.turnOffAvailability() },
                  onActivityShortcutClick = { act ->
                    viewModel.setActivityFilter(act)
                    currentTab = MainTab.DISCOVER
                  },
                  onOpenNotifications = { activeSubScreen = SubScreen.Notifications },
                  onOpenDiscovery = { currentTab = MainTab.DISCOVER },
                  onOpenConnections = { currentTab = MainTab.CONNECTIONS },
                  onOpenProfileDetails = { activeSubScreen = SubScreen.ProfileDetails(it) },
                  onQuickConnect = { quickConnectTarget = it },
                  onOpenMeetup = { activeSubScreen = SubScreen.MeetupDetails(it.id) },
                )
              }

              MainTab.DISCOVER -> {
                NearbyDiscoveryScreen(
                  nearbyUsers = nearbyUsers,
                  radiusFilter = radiusFilter,
                  activityFilter = activityFilter,
                  myIsAvailable = myProfile.isCurrentlyAvailable(),
                  onSelectRadius = { viewModel.setRadiusFilter(it) },
                  onSelectActivity = { viewModel.setActivityFilter(it) },
                  onViewProfile = { activeSubScreen = SubScreen.ProfileDetails(it) },
                  onConnectClick = { quickConnectTarget = it },
                )
              }

              MainTab.CONNECTIONS -> {
                ConnectionsScreen(
                  currentUserId = viewModel.currentUserId,
                  requests = requests,
                  connections = connections,
                  meetups = meetups,
                  onAcceptRequest = { viewModel.respondToRequest(it, accept = true) },
                  onRejectRequest = { viewModel.respondToRequest(it, accept = false) },
                  onCallConnection = { viewModel.startOutgoingCall(context, it) },
                  onPlanMeetup = { activeSubScreen = SubScreen.PlanMeetup(it) },
                  onOpenMeetupDetails = { activeSubScreen = SubScreen.MeetupDetails(it.id) },
                  onEndConnection = { viewModel.endConnection(it) },
                  onBlockPeer = { uid, name -> viewModel.blockUser(uid, name) },
                  onReportPeer = { uid, name, cat, details ->
                    viewModel.reportUser(uid, name, cat, details)
                  },
                )
              }

              MainTab.ACTIVITY -> {
                ActivityHistoryScreen(
                  currentUserId = viewModel.currentUserId,
                  meetups = meetups,
                  calls = calls,
                  sessions = sessions,
                  onOpenMeetup = { activeSubScreen = SubScreen.MeetupDetails(it.id) },
                )
              }

              MainTab.PROFILE -> {
                ProfileScreen(
                  account = account,
                  profile = myProfile,
                  userEmail = userEmail,
                  onSaveProfile = { name, bio, city, gender, photo, interests, optIn ->
                    viewModel.updateProfile(name, bio, city, gender, photo, interests, optIn)
                  },
                  onOpenNotifications = { activeSubScreen = SubScreen.Notifications },
                  onOpenSettings = { activeSubScreen = SubScreen.Settings },
                  onOpenBlockedUsers = { activeSubScreen = SubScreen.BlockedUsers },
                  onOpenPrivacy = { activeSubScreen = SubScreen.PrivacyPolicy },
                  onOpenTerms = { activeSubScreen = SubScreen.Terms },
                  onOpenAdmin = { activeSubScreen = SubScreen.AdminModeration },
                  onSignOut = onSignOut,
                  onDeleteAccount = {
                    viewModel.deleteMyAccountData { onSignOut() }
                  },
                )
              }
            }
          }
        }
      }
    }
  }

  quickConnectTarget?.let { target ->
    SendConnectionRequestDialog(
      targetName = target.firstName,
      defaultActivity = target.preferredActivity.ifBlank { "Tea" },
      onDismiss = { quickConnectTarget = null },
      onSend = { activity, msg ->
        quickConnectTarget = null
        viewModel.sendConnectionRequest(target, activity, msg)
      },
    )
  }
}
