package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ActivityCategory
import com.example.data.model.AvailabilityDuration
import com.example.data.model.Connection
import com.example.data.model.ConnectionRequest
import com.example.data.model.Meetup
import com.example.data.model.NearbyUserCardModel
import com.example.data.model.RadiusFilter
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyIllustrationCard
import com.example.ui.components.LiveBadge
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SafetyNoticeCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueContainer
import com.example.ui.theme.DeepPurple
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnline

@Composable
fun HomeDashboardScreen(
  myProfile: UserProfile,
  nearbyUsers: List<NearbyUserCardModel>,
  pendingIncomingCount: Int,
  activeConnectionsCount: Int,
  upcomingMeetups: List<Meetup>,
  unreadNotifications: Int,
  isEnablingAvailability: Boolean = false,
  onEnableAvailability: (String, AvailabilityDuration) -> Unit,
  onUpdateLiveActivity: (String) -> Unit = {},
  onDisableAvailability: () -> Unit,
  onActivityShortcutClick: (String) -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenDiscovery: () -> Unit,
  onOpenConnections: () -> Unit,
  onOpenProfileDetails: (UserProfile) -> Unit,
  onQuickConnect: (UserProfile) -> Unit,
  onOpenMeetup: (Meetup) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showAvailabilitySetup by remember { mutableStateOf(false) }
  var selectedDuration by remember { mutableStateOf(AvailabilityDuration.HOUR_1) }
  var selectedActivity by remember(myProfile.preferredActivity) {
    mutableStateOf(myProfile.preferredActivity.ifBlank { ActivityCategory.TEA_COFFEE.label })
  }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      // Even if user chooses not to share GPS, go live using their profile city area
      onEnableAvailability(selectedActivity, selectedDuration)
      showAvailabilitySetup = false
    }

  val isAvailableNow = myProfile.isCurrentlyAvailable()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Hey, ${myProfile.firstName.ifBlank { "Friend" }}!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "Looking for some company today?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onOpenNotifications,
          modifier = Modifier.testTag("home_notifications_button"),
        ) {
          BadgedBox(
            badge = {
              if (unreadNotifications > 0) {
                Badge { Text(unreadNotifications.coerceAtMost(99).toString()) }
              }
            }
          ) {
            Icon(Icons.Rounded.Notifications, contentDescription = "Notifications")
          }
        }
      }
    }

    // Prominent Availability Toggle Card
    item {
      Card(
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (isAvailableNow) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surface,
          ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Available to meet people",
                  style = MaterialTheme.typography.titleLarge,
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              if (isAvailableNow) {
                val minsLeft =
                  ((myProfile.availabilityExpiresAtMillis - System.currentTimeMillis()) / 60_000L)
                    .coerceAtLeast(1L)
                LiveBadge()
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "${myProfile.preferredActivity} • ~$minsLeft min left",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              } else if (isEnablingAvailability) {
                Text(
                  text = "Going live for $selectedActivity...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              } else {
                Text(
                  text = "Switch ON to appear in nearby discovery and share your approximate area.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
              checked = isAvailableNow || showAvailabilitySetup || isEnablingAvailability,
              enabled = !isEnablingAvailability,
              onCheckedChange = { wantOn ->
                if (wantOn) {
                  showAvailabilitySetup = true
                } else {
                  showAvailabilitySetup = false
                  if (isAvailableNow) {
                    onDisableAvailability()
                  }
                }
              },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = DeepPurple,
                ),
              modifier = Modifier.testTag("availability_toggle"),
            )
          }

          if (isAvailableNow) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Interested now (tap to switch):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              ActivityCategory.quickActions.forEach { act ->
                FilterChip(
                  selected = myProfile.preferredActivity == act.label,
                  onClick = {
                    if (myProfile.preferredActivity != act.label) {
                      onUpdateLiveActivity(act.label)
                    }
                  },
                  label = { Text(act.shortLabel) },
                )
              }
            }
          }

          AnimatedVisibility(visible = showAvailabilitySetup && !isAvailableNow) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
              Text("How long are you available?", style = MaterialTheme.typography.labelLarge)
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                AvailabilityDuration.entries.forEach { dur ->
                  FilterChip(
                    selected = selectedDuration == dur,
                    onClick = { selectedDuration = dur },
                    label = { Text(dur.label) },
                  )
                }
              }
              Spacer(modifier = Modifier.height(10.dp))
              Text("What would you like to do?", style = MaterialTheme.typography.labelLarge)
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ActivityCategory.quickActions.forEach { act ->
                  FilterChip(
                    selected = selectedActivity == act.label,
                    onClick = { selectedActivity = act.label },
                    label = { Text(act.shortLabel) },
                  )
                }
              }
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                onClick = {
                  val hasCoarse =
                    ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                  val hasFine =
                    ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                  if (hasCoarse || hasFine) {
                    onEnableAvailability(selectedActivity, selectedDuration)
                    showAvailabilitySetup = false
                  } else {
                    locationPermissionLauncher.launch(
                      arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                      )
                    )
                  }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_availability_button"),
              ) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Go Live for ${selectedDuration.label}")
              }
            }
          }
        }
      }
    }

    // Activity Shortcuts
    item {
      Column {
        Text("Quick activity shortcuts", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ActivityShortcutTile("Tea / Coffee", Icons.Rounded.Coffee) {
            onActivityShortcutClick("Tea")
          }
          ActivityShortcutTile("Food", Icons.Rounded.Restaurant) {
            onActivityShortcutClick("Eat")
          }
          ActivityShortcutTile("Walking", Icons.Rounded.DirectionsWalk) {
            onActivityShortcutClick("walk")
          }
          ActivityShortcutTile("Conversation", Icons.Rounded.Forum) {
            onActivityShortcutClick("conversation")
          }
          ActivityShortcutTile("Exploring", Icons.Rounded.Explore) {
            onActivityShortcutClick("Explore")
          }
        }
      }
    }

    // Status Summary Strip
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        HomeStatCard(
          label = "Pending\nRequests",
          count = pendingIncomingCount,
          onClick = onOpenConnections,
          modifier = Modifier.weight(1f),
        )
        HomeStatCard(
          label = "Active\nConnections",
          count = activeConnectionsCount,
          onClick = onOpenConnections,
          modifier = Modifier.weight(1f),
        )
        HomeStatCard(
          label = "Upcoming\nMeetups",
          count = upcomingMeetups.size,
          onClick = onOpenConnections,
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (upcomingMeetups.isNotEmpty()) {
      item {
        val next = upcomingMeetups.first()
        Card(
          colors = CardDefaults.cardColors(containerColor = CobaltBlueContainer),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().clickable { onOpenMeetup(next) },
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = CobaltBlue)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Next Meetup: ${next.activity} (${next.status})",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "${next.publicLocationName} • ${next.scheduledDateText}, ${next.scheduledTimeText}",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
      }
    }

    // Nearby People Preview
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Available nearby", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onOpenDiscovery) { Text("See all") }
      }
    }

    if (nearbyUsers.isEmpty()) {
      item {
        EmptyIllustrationCard(
          title = "No one live nearby right now",
          subtitle = "Switch on your availability or check Discover with a wider radius to find company.",
          action = {
            OutlinedButton(onClick = onOpenDiscovery) { Text("Open Discovery") }
          },
        )
      }
    } else {
      items(nearbyUsers.take(3), key = { it.profile.uid }) { card ->
        NearbyMemberCard(
          model = card,
          onViewProfile = { onOpenProfileDetails(card.profile) },
          onConnect = { onQuickConnect(card.profile) },
        )
      }
    }
  }
}

@Composable
private fun ActivityShortcutTile(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.medium,
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier.width(96.dp).padding(vertical = 14.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
      ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun HomeStatCard(
  label: String,
  count: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.medium,
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun NearbyDiscoveryScreen(
  nearbyUsers: List<NearbyUserCardModel>,
  radiusFilter: RadiusFilter,
  activityFilter: String?,
  myIsAvailable: Boolean,
  onSelectRadius: (RadiusFilter) -> Unit,
  onSelectActivity: (String?) -> Unit,
  onViewProfile: (UserProfile) -> Unit,
  onConnectClick: (UserProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column {
        Text("Nearby Discovery", style = MaterialTheme.typography.headlineMedium)
        Text(
          text = "Verified 18+ people currently available near your approximate area.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          RadiusFilter.entries.forEach { radius ->
            FilterChip(
              selected = radiusFilter == radius,
              onClick = { onSelectRadius(radius) },
              label = { Text(radius.label) },
              modifier = Modifier.testTag("radius_chip_${radius.km}"),
            )
          }
        }
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = activityFilter == null,
            onClick = { onSelectActivity(null) },
            label = { Text("All Activities") },
          )
          ActivityCategory.quickActions.forEach { cat ->
            val key = cat.shortLabel.substringBefore(" /")
            FilterChip(
              selected = activityFilter == key,
              onClick = { onSelectActivity(if (activityFilter == key) null else key) },
              label = { Text(cat.shortLabel) },
            )
          }
        }
      }
    }

    if (!myIsAvailable) {
      item {
        SafetyNoticeCard(
          title = "Availability is currently OFF",
          message = "Turn ON availability on Home to share your approximate distance and let nearby friends connect with you too.",
        )
      }
    }

    if (nearbyUsers.isEmpty()) {
      item {
        EmptyIllustrationCard(
          title = "No available friends within ${radiusFilter.label}",
          subtitle = "Try expanding your radius to 10 KM or clearing the activity filter.",
          action = {
            if (radiusFilter != RadiusFilter.KM_10) {
              Button(onClick = { onSelectRadius(RadiusFilter.KM_10) }) {
                Text("Expand to 10 KM")
              }
            }
          },
        )
      }
    } else {
      items(nearbyUsers, key = { it.profile.uid }) { model ->
        NearbyMemberCard(
          model = model,
          onViewProfile = { onViewProfile(model.profile) },
          onConnect = { onConnectClick(model.profile) },
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyMemberCard(
  model: NearbyUserCardModel,
  onViewProfile: () -> Unit,
  onConnect: () -> Unit,
) {
  val p = model.profile
  Card(
    onClick = onViewProfile,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = MaterialTheme.shapes.large,
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier.fillMaxWidth().testTag("nearby_card_${p.uid}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(
          photoData = p.photoData,
          name = p.firstName,
          size = 62.dp,
          isVerified = p.isVerified,
          isOnline = true,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "${p.firstName}, ${p.age}",
              style = MaterialTheme.typography.titleLarge,
            )
            if (p.isVerified) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                Icons.Rounded.Verified,
                contentDescription = "Verified",
                tint = CobaltBlue,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Text(
            text = "${model.distanceLabel} • ${p.cityArea}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          if (p.preferredActivity.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            StatusPill(text = p.preferredActivity)
          }
        }
      }

      if (p.bio.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "\"${p.bio}\"",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      if (model.commonInterests.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          model.commonInterests.take(3).forEach { interest ->
            StatusPill(
              text = "Shared: $interest",
              containerColor = EmeraldContainer,
              contentColor = EmeraldOnline,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onViewProfile,
          modifier = Modifier.weight(1f).height(48.dp),
        ) {
          Text("View Profile")
        }
        Button(
          onClick = onConnect,
          modifier = Modifier.weight(1f).height(48.dp).testTag("connect_btn_${p.uid}"),
        ) {
          Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Connect")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileDetailsScreen(
  profile: UserProfile,
  distanceLabel: String,
  commonInterests: List<String>,
  onBack: () -> Unit,
  onSendRequest: (activity: String, message: String) -> Unit,
  onBlockUser: () -> Unit,
  onReportUser: (category: String, details: String) -> Unit,
) {
  var showConnectDialog by remember { mutableStateOf(false) }
  var showReportDialog by remember { mutableStateOf(false) }
  var showBlockConfirm by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("${profile.firstName}'s Profile") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = { showReportDialog = true }) {
            Icon(Icons.Rounded.Flag, contentDescription = "Report user")
          }
          IconButton(onClick = { showBlockConfirm = true }) {
            Icon(Icons.Rounded.Block, contentDescription = "Block user")
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
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          ProfileAvatar(
            photoData = profile.photoData,
            name = profile.firstName,
            size = 96.dp,
            isVerified = profile.isVerified,
            isOnline = profile.isCurrentlyAvailable(),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${profile.firstName}, ${profile.age}", style = MaterialTheme.typography.headlineSmall)
            if (profile.isVerified) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = CobaltBlue)
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "$distanceLabel • ${profile.cityArea}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          if (profile.gender.isNotBlank()) {
            Text(
              text = profile.gender,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (profile.preferredActivity.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            StatusPill(text = "Interested now: ${profile.preferredActivity}")
          }
        }
      }

      if (profile.bio.isNotBlank()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(profile.bio, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("Interests", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(10.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            profile.interests.forEach { interest ->
              val isShared = interest in commonInterests
              StatusPill(
                text = if (isShared) "★ $interest" else interest,
                containerColor = if (isShared) EmeraldContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isShared) EmeraldOnline else MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }

      SafetyNoticeCard(
        title = "Safety reminder",
        message = "Meet only at well-lit public places like cafes or parks. Phone numbers and exact locations are never shared.",
      )

      Button(
        onClick = { showConnectDialog = true },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("profile_connect_button"),
      ) {
        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Send Connection Request")
      }
    }
  }

  if (showConnectDialog) {
    SendConnectionRequestDialog(
      targetName = profile.firstName,
      defaultActivity = profile.preferredActivity.ifBlank { "Tea" },
      onDismiss = { showConnectDialog = false },
      onSend = { activity, msg ->
        showConnectDialog = false
        onSendRequest(activity, msg)
      },
    )
  }

  if (showReportDialog) {
    ReportUserDialog(
      targetName = profile.firstName,
      onDismiss = { showReportDialog = false },
      onSubmit = { cat, details ->
        showReportDialog = false
        onReportUser(cat, details)
      },
    )
  }

  if (showBlockConfirm) {
    AlertDialog(
      onDismissRequest = { showBlockConfirm = false },
      title = { Text("Block ${profile.firstName}?") },
      text = { Text("They won't appear in discovery or be able to send you connection requests or calls.") },
      confirmButton = {
        Button(
          onClick = {
            showBlockConfirm = false
            onBlockUser()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Block User")
        }
      },
      dismissButton = {
        TextButton(onClick = { showBlockConfirm = false }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendConnectionRequestDialog(
  targetName: String,
  defaultActivity: String,
  onDismiss: () -> Unit,
  onSend: (activity: String, message: String) -> Unit,
) {
  val activities = listOf("Tea", "Coffee", "Food", "Walking", "Conversation", "Other")
  var selectedActivity by remember {
    mutableStateOf(activities.firstOrNull { defaultActivity.contains(it, ignoreCase = true) } ?: "Tea")
  }
  var message by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Connect with $targetName") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pick an activity to suggest:", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          activities.forEach { act ->
            FilterChip(
              selected = selectedActivity == act,
              onClick = { selectedActivity = act },
              label = { Text(act) },
            )
          }
        }
        OutlinedTextField(
          value = message,
          onValueChange = { if (it.length <= 200) message = it },
          label = { Text("Short note (optional)") },
          placeholder = { Text("Hey! Would love to grab $selectedActivity nearby.") },
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSend(selectedActivity, message) },
        modifier = Modifier.testTag("send_request_confirm"),
      ) {
        Text("Send Request")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
