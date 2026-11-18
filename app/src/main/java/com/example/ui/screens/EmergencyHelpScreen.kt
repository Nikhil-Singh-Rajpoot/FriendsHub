package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.ShareLocation
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Meetup
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.ui.components.SafetyNoticeCard
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldOnline
import com.example.util.SafetyNotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyHelpScreen(
  account: UserAccount?,
  profile: UserProfile,
  liveCityArea: String,
  liveCoordinates: Pair<Double, Double>?,
  activeMeetup: Meetup?,
  onBack: () -> Unit,
  onMarkMeetupSafe: (Meetup) -> Unit,
  onOpenSettingsForTrustedContact: () -> Unit,
) {
  val context = LocalContext.current
  val hasTrusted = account?.hasTrustedContact() == true
  val effectiveArea = liveCityArea.ifBlank { profile.cityArea }
  val sosMessage =
    SafetyNotificationHelper.buildEmergencyHelpSmsText(
      myName = profile.firstName,
      liveCityArea = effectiveArea,
      latLng = liveCoordinates,
      activeMeetup = activeMeetup,
    )

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Emergency Help & SOS") },
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
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = CoralContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("emergency_sos_banner"),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Sos, contentDescription = null, tint = CoralAlert)
            Text("Immediate Safety Assistance", style = MaterialTheme.typography.titleLarge, color = CoralAlert)
          }
          Text(
            text = "If you ever feel uncomfortable or unsafe during a meetup, leave immediately to a crowded public counter and use the emergency tools below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      // Direct Helpline Calling (zero-permission ACTION_DIAL)
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Emergency Helplines", style = MaterialTheme.typography.titleMedium)
          Button(
            onClick = { SafetyNotificationHelper.launchDialer(context, "112") },
            colors = ButtonDefaults.buttonColors(containerColor = CoralAlert),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("dial_112_button"),
          ) {
            Icon(Icons.Rounded.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Call 112 (National Emergency)")
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = { SafetyNotificationHelper.launchDialer(context, "100") },
              modifier = Modifier.weight(1f).height(48.dp).testTag("dial_100_button"),
            ) {
              Icon(Icons.Rounded.LocalPolice, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Police 100")
            }
            OutlinedButton(
              onClick = { SafetyNotificationHelper.launchDialer(context, "181") },
              modifier = Modifier.weight(1f).height(48.dp).testTag("dial_181_button"),
            ) {
              Icon(Icons.Rounded.SupportAgent, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Helpline 181")
            }
          }
        }
      }

      // Trusted Contact Quick Alert
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("emergency_trusted_contact_card"),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ContactPhone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Trusted Contact Alert", style = MaterialTheme.typography.titleMedium)
          }

          if (hasTrusted && account != null) {
            Text(
              text = "${account.trustedContactName} • ${account.trustedContactPhone}",
              style = MaterialTheme.typography.bodyLarge,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = {
                  SafetyNotificationHelper.launchTrustedContactSmsOrShare(
                    context = context,
                    phone = account.trustedContactPhone,
                    title = "Alert Trusted Contact",
                    body = sosMessage,
                  )
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("alert_trusted_sms_button"),
              ) {
                Icon(Icons.Rounded.Sms, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("SMS Alert")
              }
              OutlinedButton(
                onClick = { SafetyNotificationHelper.launchDialer(context, account.trustedContactPhone) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("call_trusted_contact_button"),
              ) {
                Icon(Icons.Rounded.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Call")
              }
            }
          } else {
            Text(
              text = "No trusted contact saved yet. Add a family member or close friend so you can alert them in one tap.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = onOpenSettingsForTrustedContact,
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("setup_trusted_contact_button"),
            ) {
              Text("Set Up Trusted Contact")
            }
          }

          OutlinedButton(
            onClick = {
              SafetyNotificationHelper.launchShareSheet(
                context = context,
                title = "Share Live Safety Update",
                body = sosMessage,
              )
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("share_live_location_sos_button"),
          ) {
            Icon(Icons.Rounded.ShareLocation, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Live Area & Details")
          }
        }
      }

      if (activeMeetup != null && !activeMeetup.isMarkedSafeBy(profile.uid)) {
        Card(
          colors = CardDefaults.cardColors(containerColor = EmeraldContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Active Meetup: ${activeMeetup.publicLocationName}",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "With ${activeMeetup.peerNameFor(profile.uid)} (${activeMeetup.scheduledDateText}, ${activeMeetup.scheduledTimeText})",
              style = MaterialTheme.typography.bodyMedium,
            )
            Button(
              onClick = { onMarkMeetupSafe(activeMeetup) },
              colors = ButtonDefaults.buttonColors(containerColor = EmeraldOnline),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("emergency_im_safe_button"),
            ) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("I'm Safe Now")
            }
          }
        }
      }

      SafetyNoticeCard(
        title = "First-Meetup Safety Checklist",
        message = "1. Stay in busy, well-lit public spaces.\n2. Arrange your own transportation.\n3. Check in on arrival and tap \"I'm Safe\" after leaving.\n4. Block and report anyone who crosses boundaries.",
      )
    }
  }
}
