package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AvailabilitySession
import com.example.data.model.Connection
import com.example.data.model.ConnectionRequest
import com.example.data.model.Meetup
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCall
import com.example.ui.components.EmptyIllustrationCard
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SafetyNoticeCard
import com.example.ui.components.StatusPill
import com.example.ui.components.VerificationBadge
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueContainer
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnline
import com.example.util.LocationPrivacy
import com.example.util.SafetyNotificationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ConnectionsScreen(
  currentUserId: String,
  requests: List<ConnectionRequest>,
  connections: List<Connection>,
  meetups: List<Meetup>,
  availableUserIds: Set<String> = emptySet(),
  peerProfiles: Map<String, UserProfile> = emptyMap(),
  onAcceptRequest: (ConnectionRequest) -> Unit,
  onRejectRequest: (ConnectionRequest) -> Unit,
  onCallConnection: (Connection) -> Unit,
  onPlanMeetup: (Connection) -> Unit,
  onOpenMeetupDetails: (Meetup) -> Unit,
  onEndConnection: (Connection) -> Unit,
  onBlockPeer: (uid: String, name: String) -> Unit,
  onReportPeer: (uid: String, name: String, category: String, details: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val incomingPending = requests.filter { it.toUid == currentUserId && it.status == "PENDING" }
  val sentPending = requests.filter { it.fromUid == currentUserId && it.status == "PENDING" }
  val activeConnections = connections.filter { it.status == "ACTIVE" }
  val pastConnections = connections.filter { it.status != "ACTIVE" }

  var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

  Column(modifier = modifier.fillMaxSize()) {
    Text(
      text = "Connections",
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
    )

    TabRow(selectedTabIndex = selectedTab) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Active (${activeConnections.size})") },
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Requests (${incomingPending.size})") },
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = { Text("Past (${pastConnections.size})") },
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      when (selectedTab) {
        0 -> {
          if (activeConnections.isEmpty()) {
            item {
              EmptyIllustrationCard(
                title = "No active connections yet",
                subtitle = "Verified 18+ users can connect nearby to unlock private voice calling and public meetup planning.",
              )
            }
          } else {
            items(activeConnections, key = { it.id }) { conn ->
              val peerUid = conn.otherUid(currentUserId)
              val peerName = conn.otherName(currentUserId)
              val peerCity = conn.otherCity(currentUserId)
              val peerProfile = peerProfiles[peerUid]
              val isPeerOnline = peerUid in availableUserIds
              val latestMeetup = meetups.firstOrNull { it.connectionId == conn.id }

              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("connection_card_${conn.id}"),
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(
                      photoData = peerProfile?.photoData.orEmpty(),
                      name = peerName,
                      size = 54.dp,
                      isVerified = peerProfile?.isVerified == true,
                      isOnline = isPeerOnline,
                      showVerificationStatus = peerProfile != null,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Text(peerName, style = MaterialTheme.typography.titleLarge)
                        if (peerProfile != null) {
                          VerificationBadge(
                            isVerified = peerProfile.isVerified,
                            showLabel = !peerProfile.isVerified,
                          )
                        }
                        StatusPill(
                          text = if (isPeerOnline) "Online" else "Offline",
                          containerColor = if (isPeerOnline) EmeraldContainer else MaterialTheme.colorScheme.surfaceVariant,
                          contentColor = if (isPeerOnline) EmeraldOnline else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = listOfNotNull(conn.activity.takeIf { it.isNotBlank() }, peerCity.takeIf { it.isNotBlank() }).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    IconButton(onClick = { reportTarget = peerUid to peerName }) {
                      Icon(Icons.Rounded.Flag, contentDescription = "Report $peerName")
                    }
                    IconButton(onClick = { onBlockPeer(peerUid, peerName) }) {
                      Icon(Icons.Rounded.Block, contentDescription = "Block $peerName")
                    }
                  }

                  if (latestMeetup != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                      onClick = { onOpenMeetupDetails(latestMeetup) },
                      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                      shape = MaterialTheme.shapes.small,
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                      ) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text(
                            text = "Meetup: ${latestMeetup.publicLocationName}",
                            style = MaterialTheme.typography.labelLarge,
                          )
                          Text(
                            text = "${latestMeetup.scheduledDateText}, ${latestMeetup.scheduledTimeText} • ${latestMeetup.status}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                          )
                        }
                        TextButton(onClick = { onOpenMeetupDetails(latestMeetup) }) { Text("View") }
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(12.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Button(
                      onClick = { onCallConnection(conn) },
                      modifier = Modifier.weight(1f).height(48.dp).testTag("call_conn_${conn.id}"),
                    ) {
                      Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Voice Call")
                    }
                    OutlinedButton(
                      onClick = { onPlanMeetup(conn) },
                      modifier = Modifier.weight(1f).height(48.dp).testTag("meetup_conn_${conn.id}"),
                    ) {
                      Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Meetup")
                    }
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  TextButton(
                    onClick = { onEndConnection(conn) },
                    modifier = Modifier.align(Alignment.End),
                  ) {
                    Text("End Connection", color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }
        }

        1 -> {
          if (incomingPending.isEmpty() && sentPending.isEmpty()) {
            item {
              EmptyIllustrationCard(
                title = "No pending requests",
                subtitle = "Incoming and sent connection requests appear here.",
                showIllustration = false,
              )
            }
          }
          if (incomingPending.isNotEmpty()) {
            item { Text("Incoming Requests", style = MaterialTheme.typography.titleMedium) }
            items(incomingPending, key = { it.id }) { req ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                  val senderProfile = peerProfiles[req.fromUid]
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(
                      photoData = senderProfile?.photoData.orEmpty(),
                      name = req.fromName,
                      size = 50.dp,
                      isVerified = senderProfile?.isVerified == true,
                      isOnline = senderProfile?.isCurrentlyAvailable() == true,
                      showVerificationStatus = senderProfile != null,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Text(req.fromName, style = MaterialTheme.typography.titleMedium)
                        if (senderProfile != null) {
                          VerificationBadge(
                            isVerified = senderProfile.isVerified,
                            showLabel = !senderProfile.isVerified,
                          )
                        }
                      }
                      Text(
                        "${req.activity} • ${req.fromCityArea.ifBlank { "Nearby" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                  if (req.message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("\"${req.message}\"", style = MaterialTheme.typography.bodyMedium)
                  }
                  Spacer(modifier = Modifier.height(12.dp))
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                      onClick = { onRejectRequest(req) },
                      modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                      Icon(Icons.Rounded.Close, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Decline")
                    }
                    Button(
                      onClick = { onAcceptRequest(req) },
                      modifier = Modifier.weight(1f).height(48.dp).testTag("accept_req_${req.id}"),
                    ) {
                      Icon(Icons.Rounded.Check, contentDescription = null)
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Accept")
                    }
                  }
                }
              }
            }
          }
          if (sentPending.isNotEmpty()) {
            item { Text("Sent Requests", style = MaterialTheme.typography.titleMedium) }
            items(sentPending, key = { it.id }) { req ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
              ) {
                val targetProfile = peerProfiles[req.toUid]
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Text("To ${req.toName}", style = MaterialTheme.typography.titleMedium)
                      if (targetProfile != null) {
                        VerificationBadge(
                          isVerified = targetProfile.isVerified,
                          showLabel = !targetProfile.isVerified,
                        )
                      }
                    }
                    Text("Activity: ${req.activity}", style = MaterialTheme.typography.bodyMedium)
                  }
                  StatusPill(text = "Pending", containerColor = AmberContainer, contentColor = AmberWarm)
                }
              }
            }
          }
        }

        2 -> {
          if (pastConnections.isEmpty()) {
            item {
              EmptyIllustrationCard(
                title = "No past connections",
                subtitle = "Ended connections are recorded here for your history.",
                showIllustration = false,
              )
            }
          } else {
            items(pastConnections, key = { it.id }) { conn ->
              val peerName = conn.otherName(currentUserId)
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column {
                    Text(peerName, style = MaterialTheme.typography.titleMedium)
                    Text(conn.activity, style = MaterialTheme.typography.bodyMedium)
                  }
                  StatusPill(text = "Ended", containerColor = CoralContainer, contentColor = CoralAlert)
                }
              }
            }
          }
        }
      }
    }
  }

  reportTarget?.let { (uid, name) ->
    ReportUserDialog(
      targetName = name,
      onDismiss = { reportTarget = null },
      onSubmit = { cat, details ->
        reportTarget = null
        onReportPeer(uid, name, cat, details)
      },
    )
  }
}

private data class MeetupTimePreset(
  val label: String,
  val dateText: String,
  val timeText: String,
  val offsetMillis: Long,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetupPlanningScreen(
  connection: Connection,
  currentUserId: String,
  myCityArea: String,
  account: UserAccount? = null,
  myName: String = "Me",
  onBack: () -> Unit,
  onOpenTrustedContactSetup: () -> Unit = {},
  onPropose: (
    activity: String,
    dateText: String,
    timeText: String,
    locationName: String,
    area: String,
    notes: String,
    scheduledAtMillis: Long,
    shareWithTrustedContact: Boolean,
  ) -> Unit,
) {
  val context = LocalContext.current
  val peerName = connection.otherName(currentUserId)
  var activity by remember { mutableStateOf(connection.activity.ifBlank { "Tea" }) }

  val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.US) }
  val now = remember { System.currentTimeMillis() }
  val presets = remember(now) {
    val eveningCal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 18)
      set(Calendar.MINUTE, 30)
      set(Calendar.SECOND, 0)
      if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
    }
    listOf(
      MeetupTimePreset("In 15 min", "Today", timeFmt.format(now + 15 * 60_000L), now + 15 * 60_000L),
      MeetupTimePreset("In 30 min", "Today", timeFmt.format(now + 30 * 60_000L), now + 30 * 60_000L),
      MeetupTimePreset("In 1 hour", "Today", timeFmt.format(now + 60 * 60_000L), now + 60 * 60_000L),
      MeetupTimePreset(
        label = "6:30 PM",
        dateText = if (eveningCal.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) "Today" else "Tomorrow",
        timeText = "6:30 PM",
        offsetMillis = eveningCal.timeInMillis,
      ),
    )
  }

  var selectedPresetIndex by remember { mutableIntStateOf(2) }
  var dateText by remember { mutableStateOf(presets[2].dateText) }
  var timeText by remember { mutableStateOf(presets[2].timeText) }
  var scheduledAtMillis by remember { mutableLongStateOf(presets[2].offsetMillis) }

  var locationName by remember { mutableStateOf("Blue Tokai Coffee Roasters") }
  var publicArea by remember { mutableStateOf(myCityArea.ifBlank { "Sector 29, Gurugram" }) }
  var notes by remember { mutableStateOf("") }
  var shareWithTrustedContact by remember(account?.trustedContactConsent) {
    mutableStateOf(account?.hasTrustedContact() == true && account.trustedContactConsent)
  }

  val suggestedSpots = remember(publicArea, activity) {
    LocationPrivacy.suggestPublicMeetupSpots(publicArea, activity)
  }
  val selectedSpotHint = suggestedSpots.firstOrNull { it.name == locationName }?.categoryHint
  val publicLocationError = remember(locationName) { LocationPrivacy.validatePublicMeetupLocation(locationName) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Plan Public Meetup with $peerName") },
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      SafetyNoticeCard(
        title = "Public, busy meeting places only",
        message = "Between-Us recommends well-lit, crowded cafes, malls, and public plazas. Never agree to meet at private residences.",
      )

      Text("Activity", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Tea", "Coffee", "Food", "Walking", "Conversation", "Movie").forEach { act ->
          FilterChip(
            selected = activity == act,
            onClick = { activity = act },
            label = { Text(act) },
          )
        }
      }

      Text("Meeting Time (Check-In Schedule)", style = MaterialTheme.typography.labelLarge)
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        presets.forEachIndexed { idx, preset ->
          FilterChip(
            selected = selectedPresetIndex == idx,
            onClick = {
              selectedPresetIndex = idx
              dateText = preset.dateText
              timeText = preset.timeText
              scheduledAtMillis = preset.offsetMillis
            },
            label = { Text(preset.label) },
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = dateText,
          onValueChange = { dateText = it },
          label = { Text("Date") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = timeText,
          onValueChange = { timeText = it },
          label = { Text("Time") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Text("Recommended Busy Public Venues", style = MaterialTheme.typography.labelLarge)
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        suggestedSpots.forEach { spot ->
          FilterChip(
            selected = locationName == spot.name,
            onClick = { locationName = spot.name },
            label = { Text(spot.name) },
          )
        }
      }
      if (!selectedSpotHint.isNullOrBlank()) {
        StatusPill(
          text = selectedSpotHint,
          containerColor = EmeraldContainer,
          contentColor = EmeraldOnline,
        )
      }

      OutlinedTextField(
        value = locationName,
        onValueChange = { locationName = it },
        label = { Text("Public Cafe / Mall / Park Name") },
        isError = publicLocationError != null,
        supportingText = publicLocationError?.let { err -> { Text(err) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("meetup_location_input"),
      )

      OutlinedTextField(
        value = publicArea,
        onValueChange = { publicArea = it },
        label = { Text("Public Area / Neighborhood") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = notes,
        onValueChange = { if (it.length <= 250) notes = it },
        label = { Text("Optional Note (e.g. Ground-floor cafe near main entrance)") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )

      // Share plan with Trusted Contact card
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Share plan with Trusted Contact", style = MaterialTheme.typography.titleSmall)
              val trustedText =
                if (account?.hasTrustedContact() == true) {
                  "Trusted Contact: ${account.trustedContactName.ifBlank { "Saved Number" }} (${account.trustedContactPhone})"
                } else {
                  "Opens SMS / Share Sheet so a family member or friend knows your plan."
                }
              Text(
                text = trustedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = shareWithTrustedContact,
              onCheckedChange = { shareWithTrustedContact = it },
              modifier = Modifier.testTag("share_trusted_contact_switch"),
            )
          }
          if (account?.hasTrustedContact() != true) {
            TextButton(onClick = onOpenTrustedContactSetup) {
              Text("Save a default Trusted Contact in Settings")
            }
          }
        }
      }

      Button(
        onClick = {
          if (publicLocationError == null && publicArea.trim().length >= 2) {
            if (shareWithTrustedContact) {
              val shareBody =
                SafetyNotificationHelper.buildMeetupShareMessage(
                  myName = myName,
                  peerName = peerName,
                  activity = activity,
                  dateText = dateText,
                  timeText = timeText,
                  publicLocationName = locationName,
                  publicArea = publicArea,
                )
              SafetyNotificationHelper.launchTrustedContactSmsOrShare(
                context = context,
                phone = account?.trustedContactPhone.orEmpty(),
                title = "Share Meetup Plan",
                body = shareBody,
              )
            }
            onPropose(
              activity,
              dateText,
              timeText,
              locationName,
              publicArea,
              notes,
              scheduledAtMillis,
              shareWithTrustedContact,
            )
          }
        },
        enabled = publicLocationError == null && publicArea.trim().length >= 2,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("propose_meetup_button"),
      ) {
        Text("Send Meetup Proposal")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetupDetailsScreen(
  meetup: Meetup,
  currentUserId: String,
  account: UserAccount? = null,
  myName: String = "Me",
  onBack: () -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  onComplete: () -> Unit,
  onCheckIn: () -> Unit = {},
  onMarkSafe: () -> Unit = onComplete,
  onShareWithTrustedContact: () -> Unit = {},
  onOpenEmergencyHelp: () -> Unit = {},
) {
  val context = LocalContext.current
  val isProposer = meetup.proposerUid == currentUserId
  val isCheckedIn = meetup.isCheckedInBy(currentUserId)
  val isSafe = meetup.isMarkedSafeBy(currentUserId)
  val isShared = meetup.isSharedWithTrustedBy(currentUserId)
  val reminderSent = meetup.hasReminderSentTo(currentUserId)
  val alertTriggered = meetup.hasTrustedAlertTriggeredFor(currentUserId)
  val peerName = meetup.peerNameFor(currentUserId)

  val statusColors =
    when (meetup.status) {
      "CONFIRMED" -> EmeraldContainer to EmeraldOnline
      "CANCELLED" -> CoralContainer to CoralAlert
      "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
      else -> AmberContainer to AmberWarm
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Meetup & Safety Check-In") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = onOpenEmergencyHelp,
            modifier = Modifier.testTag("meetup_emergency_help_button"),
          ) {
            Icon(Icons.Rounded.Sos, contentDescription = "Emergency Help", tint = CoralAlert)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(meetup.activity, style = MaterialTheme.typography.headlineSmall)
            StatusPill(
              text = meetup.status,
              containerColor = statusColors.first,
              contentColor = statusColors.second,
            )
          }
          Text(
            text = "Between ${meetup.proposerName} & ${meetup.otherName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(meetup.publicLocationName, style = MaterialTheme.typography.titleMedium)
              Text(meetup.publicArea, style = MaterialTheme.typography.bodyMedium)
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("${meetup.scheduledDateText} at ${meetup.scheduledTimeText}", style = MaterialTheme.typography.bodyLarge)
          }
          if (meetup.notes.isNotBlank()) {
            Text("Note: \"${meetup.notes}\"", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      // Meeting Safety Check-In Policy Card
      if (meetup.status != "CANCELLED") {
        Card(
          colors = CardDefaults.cardColors(containerColor = CobaltBlueContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth().testTag("meetup_safety_checkin_card"),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.Shield, contentDescription = null, tint = CobaltBlue)
              Text("Meeting Safety Check-In", style = MaterialTheme.typography.titleMedium)
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              StatusPill(
                text = if (isShared) "Plan Shared" else "Not Shared Yet",
                containerColor = if (isShared) EmeraldContainer else AmberContainer,
                contentColor = if (isShared) EmeraldOnline else AmberWarm,
              )
              StatusPill(
                text = if (isCheckedIn) "Arrived & Checked In" else "Arrival Pending",
                containerColor = if (isCheckedIn) EmeraldContainer else AmberContainer,
                contentColor = if (isCheckedIn) EmeraldOnline else AmberWarm,
              )
              StatusPill(
                text = if (isSafe) "Marked Safe" else "Post-Meetup Pending",
                containerColor = if (isSafe) EmeraldContainer else AmberContainer,
                contentColor = if (isSafe) EmeraldOnline else AmberWarm,
              )
            }

            if (alertTriggered && !isCheckedIn && !isSafe) {
              Card(
                colors = CardDefaults.cardColors(containerColor = CoralContainer),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                    text = "Missed Check-In Alert Triggered",
                    style = MaterialTheme.typography.labelLarge,
                    color = CoralAlert,
                  )
                  Text(
                    text = "You missed your arrival check-in for this meetup. Confirm you are safe or notify ${account?.trustedContactName?.ifBlank { "your trusted contact" } ?: "your trusted contact"}.",
                    style = MaterialTheme.typography.bodySmall,
                  )
                  Button(
                    onClick = {
                      val alertBody =
                        SafetyNotificationHelper.buildMissedCheckInAlertText(
                          myName = myName,
                          trustedContactName = account?.trustedContactName.orEmpty(),
                          meetup = meetup,
                          liveCityArea = meetup.publicArea,
                        )
                      SafetyNotificationHelper.launchTrustedContactSmsOrShare(
                        context = context,
                        phone = account?.trustedContactPhone.orEmpty(),
                        title = "Send Missed Check-In Alert",
                        body = alertBody,
                      )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAlert),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                  ) {
                    Text("Send Alert to Trusted Contact")
                  }
                }
              }
            } else if (reminderSent && !isCheckedIn && !isSafe) {
              Text(
                text = "Reminder: Please tap Check In on arrival or I'm Safe when leaving.",
                style = MaterialTheme.typography.bodyMedium,
                color = CoralAlert,
              )
            }

            if (!isSafe) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OutlinedButton(
                  enabled = !isCheckedIn,
                  onClick = onCheckIn,
                  modifier = Modifier.weight(1f).height(48.dp).testTag("checkin_arrival_button"),
                ) {
                  Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(if (isCheckedIn) "Checked In" else "Check In")
                }
                Button(
                  onClick = onMarkSafe,
                  colors = ButtonDefaults.buttonColors(containerColor = EmeraldOnline),
                  modifier = Modifier.weight(1f).height(48.dp).testTag("im_safe_button"),
                ) {
                  Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("I'm Safe")
                }
              }
            }

            OutlinedButton(
              onClick = {
                val shareBody =
                  SafetyNotificationHelper.buildMeetupShareMessage(
                    myName = myName,
                    peerName = peerName,
                    activity = meetup.activity,
                    dateText = meetup.scheduledDateText,
                    timeText = meetup.scheduledTimeText,
                    publicLocationName = meetup.publicLocationName,
                    publicArea = meetup.publicArea,
                  )
                SafetyNotificationHelper.launchTrustedContactSmsOrShare(
                  context = context,
                  phone = account?.trustedContactPhone.orEmpty(),
                  title = "Share Meetup Plan",
                  body = shareBody,
                )
                onShareWithTrustedContact()
              },
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("share_meetup_plan_button"),
            ) {
              Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (isShared) "Share Plan Again with Trusted Contact" else "Share Plan with Trusted Contact")
            }
          }
        }
      }

      if (meetup.status == "PROPOSED" && !isProposer) {
        Button(
          onClick = onConfirm,
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_meetup_button"),
        ) {
          Text("Confirm Meetup")
        }
      } else if (meetup.status == "PROPOSED" && isProposer) {
        SafetyNoticeCard(
          title = "Awaiting confirmation",
          message = "Waiting for ${meetup.otherName} to confirm this meetup.",
        )
      }

      if (meetup.status == "CONFIRMED" && !isSafe) {
        OutlinedButton(
          onClick = onComplete,
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Text("Mark Meetup Completed")
        }
      }

      if (meetup.status == "PROPOSED" || meetup.status == "CONFIRMED") {
        OutlinedButton(
          onClick = onCancel,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralAlert),
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
          Text("Cancel Meetup")
        }
      }

      OutlinedButton(
        onClick = onOpenEmergencyHelp,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralAlert),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("meetup_sos_bottom_button"),
      ) {
        Icon(Icons.Rounded.Sos, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Emergency Help & SOS")
      }
    }
  }
}

@Composable
fun ActivityHistoryScreen(
  currentUserId: String,
  meetups: List<Meetup>,
  calls: List<VoiceCall>,
  sessions: List<AvailabilitySession>,
  onOpenMeetup: (Meetup) -> Unit,
  modifier: Modifier = Modifier,
) {
  val fmt = remember { SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(18.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column {
        Text("Activity History", style = MaterialTheme.typography.headlineMedium)
        Text(
          "Your meetups, private voice calls, and availability sessions.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (meetups.isEmpty() && calls.isEmpty() && sessions.isEmpty()) {
      item {
        EmptyIllustrationCard(
          title = "No activity recorded yet",
          subtitle = "Go available, make a voice call, or plan a public meetup to see your timeline.",
          showIllustration = false,
        )
      }
    }

    if (meetups.isNotEmpty()) {
      item { Text("Meetups", style = MaterialTheme.typography.titleMedium) }
      items(meetups, key = { "m_${it.id}" }) { m ->
        Card(
          onClick = { onOpenMeetup(m) },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${m.activity} at ${m.publicLocationName}", style = MaterialTheme.typography.titleMedium)
              Text("${m.scheduledDateText}, ${m.scheduledTimeText} • ${m.publicArea}", style = MaterialTheme.typography.bodyMedium)
            }
            StatusPill(text = m.status)
          }
        }
      }
    }

    if (calls.isNotEmpty()) {
      item { Text("Voice Call History", style = MaterialTheme.typography.titleMedium) }
      items(calls, key = { "c_${it.id}" }) { call ->
        val peerName = call.peerName(currentUserId)
        val direction = if (call.callerUid == currentUserId) "Outgoing" else "Incoming"
        val durText = if (call.durationSeconds > 0L) " • ${call.durationSeconds}s" else ""
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("$direction call with $peerName", style = MaterialTheme.typography.titleMedium)
              Text("${call.status}$durText", style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }

    if (sessions.isNotEmpty()) {
      item { Text("Availability Sessions", style = MaterialTheme.typography.titleMedium) }
      items(sessions.take(10), key = { "s_${it.id}" }) { s ->
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
            Column {
              Text("${s.activity} (${s.durationMinutes} min)", style = MaterialTheme.typography.titleMedium)
              Text(fmt.format(s.startedAtMillis), style = MaterialTheme.typography.bodyMedium)
            }
            StatusPill(text = s.status)
          }
        }
      }
    }
  }
}
