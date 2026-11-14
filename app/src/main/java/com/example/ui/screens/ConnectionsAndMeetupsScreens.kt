package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AvailabilitySession
import com.example.data.model.Connection
import com.example.data.model.ConnectionRequest
import com.example.data.model.Meetup
import com.example.data.model.VoiceCall
import com.example.ui.components.EmptyIllustrationCard
import com.example.ui.components.ProfileAvatar
import com.example.ui.components.SafetyNoticeCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnline
import com.example.util.LocationPrivacy
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ConnectionsScreen(
  currentUserId: String,
  requests: List<ConnectionRequest>,
  connections: List<Connection>,
  meetups: List<Meetup>,
  availableUserIds: Set<String> = emptySet(),
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
                subtitle = "Discover someone nearby or accept an incoming request to unlock private voice calling and meetup planning.",
              )
            }
          } else {
            items(activeConnections, key = { it.id }) { conn ->
              val peerUid = conn.otherUid(currentUserId)
              val peerName = conn.otherName(currentUserId)
              val peerCity = conn.otherCity(currentUserId)
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
                    ProfileAvatar(photoData = "", name = peerName, size = 54.dp, isOnline = isPeerOnline)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        Text(peerName, style = MaterialTheme.typography.titleLarge)
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
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(photoData = "", name = req.fromName, size = 50.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(req.fromName, style = MaterialTheme.typography.titleMedium)
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
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("To ${req.toName}", style = MaterialTheme.typography.titleMedium)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetupPlanningScreen(
  connection: Connection,
  currentUserId: String,
  myCityArea: String,
  onBack: () -> Unit,
  onPropose: (
    activity: String,
    dateText: String,
    timeText: String,
    locationName: String,
    area: String,
    notes: String,
  ) -> Unit,
) {
  val peerName = connection.otherName(currentUserId)
  var activity by remember { mutableStateOf(connection.activity.ifBlank { "Tea" }) }
  var dateText by remember { mutableStateOf("Today") }
  var timeText by remember { mutableStateOf("6:30 PM") }
  var locationName by remember { mutableStateOf("Blue Tokai Coffee Roasters") }
  var publicArea by remember { mutableStateOf(myCityArea.ifBlank { "Sector 29, Gurugram" }) }
  var notes by remember { mutableStateOf("") }
  val suggestedSpots = remember(publicArea, activity) {
    LocationPrivacy.suggestPublicMeetupSpots(publicArea, activity)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Plan Meetup with $peerName") },
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
        title = "Public meeting places only",
        message = "Choose a well-lit cafe, park, or public plaza. FriendsHub never shares residential addresses.",
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

      Text("Suggested Public Spots", style = MaterialTheme.typography.labelLarge)
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

      OutlinedTextField(
        value = locationName,
        onValueChange = { locationName = it },
        label = { Text("Public Cafe / Place Name") },
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
        label = { Text("Optional Note (e.g. Near the main entrance)") },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
      )

      Button(
        onClick = {
          if (locationName.trim().length >= 2 && publicArea.trim().length >= 2) {
            onPropose(activity, dateText, timeText, locationName, publicArea, notes)
          }
        },
        enabled = locationName.trim().length >= 2 && publicArea.trim().length >= 2,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("propose_meetup_button"),
      ) {
        Text("Send Meetup Proposal")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetupDetailsScreen(
  meetup: Meetup,
  currentUserId: String,
  onBack: () -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  onComplete: () -> Unit,
) {
  val isProposer = meetup.proposerUid == currentUserId
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
        title = { Text("Meetup Details") },
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

      if (meetup.status == "CONFIRMED") {
        Button(
          onClick = onComplete,
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
          Text("Mark as Completed")
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
