package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityCategory
import com.example.data.model.AppNotification
import com.example.data.model.BlockedUser
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.data.repository.AdminDashboardSnapshot
import com.example.ui.components.EmptyIllustrationCard
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.StatusPill
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnline
import com.example.util.ImageUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
  account: UserAccount?,
  profile: UserProfile,
  userEmail: String,
  onSaveProfile: (
    firstName: String,
    bio: String,
    cityArea: String,
    gender: String,
    photoData: String,
    interests: List<String>,
    discoveryOptIn: Boolean,
  ) -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenBlockedUsers: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenTerms: () -> Unit,
  onSignOut: () -> Unit,
  onDeleteAccount: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var editing by remember { mutableStateOf(false) }
  var firstName by remember(profile.firstName) { mutableStateOf(profile.firstName) }
  var bio by remember(profile.bio) { mutableStateOf(profile.bio) }
  var cityArea by remember(profile.cityArea) { mutableStateOf(profile.cityArea) }
  var gender by remember(profile.gender) { mutableStateOf(profile.gender) }
  var photoData by remember(profile.photoData) { mutableStateOf(profile.photoData) }
  var interests by remember(profile.interests) { mutableStateOf(profile.interests.toSet()) }
  var discoveryOptIn by remember(profile.discoveryOptIn) { mutableStateOf(profile.discoveryOptIn) }
  var showDeleteDialog by remember { mutableStateOf(false) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          ImageUtils.encodePickerUriToDataString(context, uri)?.let { photoData = it }
        }
      }
    }

  Column(
    modifier =
      modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Profile", style = MaterialTheme.typography.headlineMedium)
      IconButton(onClick = { editing = !editing }) {
        Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile")
      }
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = MaterialTheme.shapes.large,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        ProfileAvatar(
          photoData = photoData,
          name = firstName,
          size = 90.dp,
          isVerified = profile.isVerified,
          isOnline = profile.isCurrentlyAvailable(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("${profile.firstName}, ${profile.age}", style = MaterialTheme.typography.headlineSmall)
        Text(
          profile.cityArea,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        StatusPill(
          text = if (profile.isVerified) "Verified Member" else "18+ Adult Verified",
          containerColor = EmeraldContainer,
          contentColor = EmeraldOnline,
        )
        if (editing) {
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
          ) {
            Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Change Photo")
          }
        }
      }
    }

    if (editing) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = cityArea,
            onValueChange = { cityArea = it },
            label = { Text("City / Area") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 280) bio = it },
            label = { Text("Bio") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
          )
          Text("Interests", style = MaterialTheme.typography.labelLarge)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ActivityCategory.allLabels.forEach { label ->
              FilterChip(
                selected = label in interests,
                onClick = {
                  interests =
                    if (label in interests && interests.size > 1) interests - label
                    else interests + label
                },
                label = { Text(label) },
              )
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Show me in Nearby Discovery", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = discoveryOptIn, onCheckedChange = { discoveryOptIn = it })
          }
          Button(
            onClick = {
              onSaveProfile(firstName, bio, cityArea, gender, photoData, interests.toList(), discoveryOptIn)
              editing = false
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text("Save Changes")
          }
        }
      }
    }

    ProfileMenuItem(Icons.Rounded.Notifications, "Notifications", onClick = onOpenNotifications)
    ProfileMenuItem(Icons.Rounded.Settings, "Notification & Privacy Settings", onClick = onOpenSettings)
    ProfileMenuItem(Icons.Rounded.Block, "Blocked Users", onClick = onOpenBlockedUsers)
    ProfileMenuItem(Icons.Rounded.Policy, "Privacy Policy", onClick = onOpenPrivacy)
    ProfileMenuItem(Icons.Rounded.Policy, "Terms & Community Guidelines", onClick = onOpenTerms)

    OutlinedButton(
      onClick = onSignOut,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
    ) {
      Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Sign Out")
    }

    TextButton(
      onClick = { showDeleteDialog = true },
      modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
      Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = CoralAlert)
      Spacer(modifier = Modifier.width(4.dp))
      Text("Delete Account Data", color = CoralAlert)
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Delete Account Data?") },
      text = { Text("Your profile and private account details will be permanently deleted from FriendsHub.") },
      confirmButton = {
        Button(
          onClick = {
            showDeleteDialog = false
            onDeleteAccount()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CoralAlert),
        ) {
          Text("Delete Forever")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun ProfileMenuItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Spacer(modifier = Modifier.width(14.dp))
      Text(label, style = MaterialTheme.typography.titleMedium)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
  notifications: List<AppNotification>,
  onBack: () -> Unit,
  onMarkRead: (String) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Notifications") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (notifications.isEmpty()) {
        item {
          EmptyIllustrationCard(
            title = "All caught up",
            subtitle = "Connection requests, call alerts, and meetup confirmations appear here.",
            showIllustration = false,
          )
        }
      } else {
        items(notifications, key = { it.id }) { notif ->
          Card(
            onClick = { if (!notif.isRead) onMarkRead(notif.id) },
            colors =
              CardDefaults.cardColors(
                containerColor =
                  if (notif.isRead) MaterialTheme.colorScheme.surface
                  else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
              ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(notif.title, style = MaterialTheme.typography.titleMedium)
                if (!notif.isRead) StatusPill(text = "New")
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(notif.body, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  account: UserAccount?,
  onBack: () -> Unit,
  onSavePrefs: (push: Boolean, calls: Boolean, meetups: Boolean) -> Unit,
  onOpenAdminGate: () -> Unit = {},
) {
  var push by remember(account?.pushNotificationsEnabled) { mutableStateOf(account?.pushNotificationsEnabled ?: true) }
  var calls by remember(account?.callAlertsEnabled) { mutableStateOf(account?.callAlertsEnabled ?: true) }
  var meetups by remember(account?.meetupRemindersEnabled) { mutableStateOf(account?.meetupRemindersEnabled ?: true) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Notification Preferences", style = MaterialTheme.typography.titleMedium)
          Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Connection Request Alerts")
            Switch(checked = push, onCheckedChange = { push = it; onSavePrefs(it, calls, meetups) })
          }
          Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Incoming Call Alerts")
            Switch(checked = calls, onCheckedChange = { calls = it; onSavePrefs(push, it, meetups) })
          }
          Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Meetup Reminders")
            Switch(checked = meetups, onCheckedChange = { meetups = it; onSavePrefs(push, calls, it) })
          }
        }
      }

      Spacer(modifier = Modifier.weight(1f))
      TextButton(
        onClick = onOpenAdminGate,
        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("admin_gate_button"),
      ) {
        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Admin Console (Restricted)")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
  blocks: List<BlockedUser>,
  onBack: () -> Unit,
  onUnblock: (String) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Blocked Users") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(18.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (blocks.isEmpty()) {
        item {
          EmptyIllustrationCard(
            title = "No blocked users",
            subtitle = "If you block someone, they will appear here and can be unblocked anytime.",
            showIllustration = false,
          )
        }
      } else {
        items(blocks, key = { it.id }) { block ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(block.blockedName, style = MaterialTheme.typography.titleMedium)
              OutlinedButton(onClick = { onUnblock(block.blockedUid) }) { Text("Unblock") }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportUserDialog(
  targetName: String,
  onDismiss: () -> Unit,
  onSubmit: (category: String, details: String) -> Unit,
) {
  val categories = listOf("Fake profile", "Harassment", "Inappropriate behavior", "Spam", "Suspicious activity")
  var selectedCategory by remember { mutableStateOf(categories.first()) }
  var details by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Report $targetName") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          categories.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat) },
            )
          }
        }
        OutlinedTextField(
          value = details,
          onValueChange = { if (it.length <= 400) details = it },
          label = { Text("Details (optional)") },
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmit(selectedCategory, details) },
        colors = ButtonDefaults.buttonColors(containerColor = CoralAlert),
      ) {
        Text("Submit Report")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalInfoScreen(
  title: String,
  isPrivacyPolicy: Boolean,
  onBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (isPrivacyPolicy) {
        Text("FriendsHub Privacy Policy", style = MaterialTheme.typography.headlineSmall)
        Text(
          "1. Purpose & Consent: FriendsHub collects only the minimum information required for short-term friendship discovery among verified adults 18+ in India (DPDP Act aligned).\n\n" +
            "2. Location Privacy: GPS coordinates are quantized on-device to approximate ~500m cells and stored only while your availability toggle is ON. Exact GPS coordinates and residential addresses are never shared.\n\n" +
            "3. Private Communications: Personal phone numbers and emails are strictly isolated and never exposed to other members. In-app voice calls use peer-to-peer WebRTC signaling gated to active connections.\n\n" +
            "4. Account Deletion: You can permanently delete your profile and account data at any time from the Profile tab.",
          style = MaterialTheme.typography.bodyLarge,
        )
      } else {
        Text("FriendsHub Terms & Safety Guidelines", style = MaterialTheme.typography.headlineSmall)
        Text(
          "1. Adults 18+ Only: You must be at least 18 years old to register and use FriendsHub.\n\n" +
            "2. Friendship Only: FriendsHub is a platonic social companionship platform for tea, coffee, meals, walks, and conversation — not a dating or solicitation service.\n\n" +
            "3. Public Meetups: Always meet new connections in well-lit public venues such as cafes, malls, or parks.\n\n" +
            "4. Zero Tolerance: Harassment, impersonation, spam, or unsafe behavior results in immediate suspension.",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminModerationScreen(
  currentSignedInEmail: String,
  isEmailVerified: Boolean,
  snapshot: AdminDashboardSnapshot?,
  isLoading: Boolean,
  onLoad: () -> Unit,
  onBack: () -> Unit,
  onToggleSuspend: (uid: String, suspend: Boolean) -> Unit,
  onToggleVerified: (uid: String, verified: Boolean) -> Unit,
  onResolveReport: (reportId: String, status: String) -> Unit,
) {
  val authorizedAdminEmail = "nikhilkumarsingh2000@gmail.com"
  val requiredAccessKey = com.example.BuildConfig.ADMIN_ACCESS_KEY
  var unlocked by remember { mutableStateOf(false) }
  var enteredEmail by remember { mutableStateOf("") }
  var enteredKey by remember { mutableStateOf("") }
  var gateError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(unlocked) {
    if (unlocked) onLoad()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Admin Moderation") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    if (!unlocked) {
      Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Admin Credential Verification", style = MaterialTheme.typography.titleLarge)
            Text(
              "Only the single authorized FriendsHub administrator credential can unlock moderation tools.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = enteredEmail,
              onValueChange = { enteredEmail = it; gateError = null },
              label = { Text("Authorized Admin Email") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("admin_email_input"),
            )
            if (requiredAccessKey.isNotBlank()) {
              OutlinedTextField(
                value = enteredKey,
                onValueChange = { enteredKey = it; gateError = null },
                label = { Text("Admin Access Key") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("admin_key_input"),
              )
            }
            if (gateError != null) {
              Text(
                text = gateError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
              )
            }
            Button(
              onClick = {
                val signedInMatches =
                  currentSignedInEmail.equals(authorizedAdminEmail, ignoreCase = true) && isEmailVerified
                val inputMatches =
                  enteredEmail.trim().equals(authorizedAdminEmail, ignoreCase = true)
                val keyMatches =
                  requiredAccessKey.isBlank() || enteredKey.trim() == requiredAccessKey
                when {
                  !signedInMatches ->
                    gateError = "Access denied: you must be signed in with the single authorized admin Google account."
                  !inputMatches || !keyMatches ->
                    gateError = "Invalid admin credential."
                  else -> {
                    gateError = null
                    unlocked = true
                  }
                }
              },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_unlock_button"),
            ) {
              Text("Verify & Open Admin Console")
            }
          }
        }
      }
    } else if (isLoading && snapshot == null) {
      Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CircularProgressIndicator()
      }
    } else if (snapshot == null) {
      Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("Admin authorization required to view moderation tools.")
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminStat("Users", snapshot.users.size, Modifier.weight(1f))
            AdminStat("Connections", snapshot.totalConnections, Modifier.weight(1f))
            AdminStat("Meetups", snapshot.totalMeetups, Modifier.weight(1f))
            AdminStat("Reports", snapshot.reports.count { it.status == "OPEN" }, Modifier.weight(1f))
          }
        }

        item { Text("Safety Reports", style = MaterialTheme.typography.titleLarge) }
        if (snapshot.reports.isEmpty()) {
          item { Text("No reports filed.", style = MaterialTheme.typography.bodyMedium) }
        } else {
          items(snapshot.reports, key = { it.id }) { rep ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                  Text("${rep.reportedName} • ${rep.category}", style = MaterialTheme.typography.titleMedium)
                  StatusPill(text = rep.status)
                }
                if (rep.details.isNotBlank()) {
                  Text(rep.details, style = MaterialTheme.typography.bodyMedium)
                }
                if (rep.status == "OPEN") {
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onResolveReport(rep.id, "DISMISSED") }) { Text("Dismiss") }
                    Button(onClick = { onResolveReport(rep.id, "ACTIONED") }) { Text("Mark Actioned") }
                  }
                }
              }
            }
          }
        }

        item { Text("Registered Members", style = MaterialTheme.typography.titleLarge) }
        items(snapshot.profiles, key = { it.uid }) { prof ->
          val suspended = prof.accountStatus == "SUSPENDED"
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                  Text("${prof.firstName}, ${prof.age}", style = MaterialTheme.typography.titleMedium)
                  Text(prof.cityArea, style = MaterialTheme.typography.bodyMedium)
                }
                StatusPill(
                  text = prof.accountStatus,
                  containerColor = if (suspended) CoralContainer else EmeraldContainer,
                  contentColor = if (suspended) CoralAlert else EmeraldOnline,
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onToggleVerified(prof.uid, !prof.isVerified) }) {
                  Icon(Icons.Rounded.Verified, contentDescription = null)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(if (prof.isVerified) "Unverify" else "Verify")
                }
                Button(
                  onClick = { onToggleSuspend(prof.uid, !suspended) },
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = if (suspended) EmeraldOnline else CoralAlert,
                    ),
                ) {
                  Text(if (suspended) "Activate" else "Suspend")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AdminStat(label: String, count: Int, modifier: Modifier = Modifier) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.medium,
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
      Text(label, style = MaterialTheme.typography.labelSmall)
    }
  }
}
