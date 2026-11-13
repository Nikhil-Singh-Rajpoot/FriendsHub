package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.VoiceCall
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepPurpleDark
import com.example.ui.theme.EmeraldOnline
import java.util.Locale

@Composable
fun VoiceCallScreen(
  call: VoiceCall,
  currentUserId: String,
  isMuted: Boolean,
  isSpeakerOn: Boolean,
  durationSeconds: Long,
  connectionNote: String,
  onAcceptIncoming: () -> Unit,
  onDeclineIncoming: () -> Unit,
  onToggleMute: () -> Unit,
  onToggleSpeaker: () -> Unit,
  onEndCall: () -> Unit,
) {
  val context = LocalContext.current
  val isIncomingRinging = call.status == "RINGING" && call.calleeUid == currentUserId
  val peerName = call.peerName(currentUserId)

  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted && isIncomingRinging) {
        onAcceptIncoming()
      }
    }

  LaunchedEffect(Unit) {
    if (!hasMicPermission) {
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  val formattedTimer = remember(durationSeconds) {
    val mins = durationSeconds / 60L
    val secs = durationSeconds % 60L
    String.format(Locale.US, "%02d:%02d", mins, secs)
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(DeepPurpleDark, Color(0xFF1A1432))))
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(28.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
          color = Color.White.copy(alpha = 0.14f),
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            text = "FriendsHub Private Voice Call",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
        Spacer(modifier = Modifier.height(36.dp))
        ProfileAvatar(photoData = "", name = peerName, size = 120.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
          text = peerName,
          style = MaterialTheme.typography.displaySmall,
          color = Color.White,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            when {
              isIncomingRinging -> "Incoming in-app voice call..."
              call.status == "RINGING" -> "Ringing..."
              durationSeconds > 0L -> formattedTimer
              else -> connectionNote.ifBlank { "Connecting audio..." }
            },
          style = MaterialTheme.typography.titleLarge,
          color = Color.White.copy(alpha = 0.88f),
        )
        if (!hasMicPermission) {
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Microphone permission is required for voice calling.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFDE68A),
          )
        }
      }

      if (isIncomingRinging) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
              onClick = onDeclineIncoming,
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = CoralAlert),
              modifier = Modifier.size(68.dp).testTag("decline_call_button"),
            ) {
              Icon(Icons.Rounded.CallEnd, contentDescription = "Decline call", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Decline", color = Color.White)
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
              onClick = {
                if (hasMicPermission) {
                  onAcceptIncoming()
                } else {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              },
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldOnline),
              modifier = Modifier.size(68.dp).testTag("accept_call_button"),
            ) {
              Icon(Icons.Rounded.Call, contentDescription = "Accept call", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Accept", color = Color.White)
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
              onClick = onToggleMute,
              colors =
                IconButtonDefaults.filledIconButtonColors(
                  containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                ),
              modifier = Modifier.size(60.dp).testTag("mute_call_button"),
            ) {
              Icon(
                imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) DeepPurpleDark else Color.White,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (isMuted) "Muted" else "Mute", color = Color.White)
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
              onClick = onEndCall,
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = CoralAlert),
              modifier = Modifier.size(72.dp).testTag("end_call_button"),
            ) {
              Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("End", color = Color.White)
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
              onClick = onToggleSpeaker,
              colors =
                IconButtonDefaults.filledIconButtonColors(
                  containerColor = if (isSpeakerOn) CobaltBlue else Color.White.copy(alpha = 0.2f),
                ),
              modifier = Modifier.size(60.dp).testTag("speaker_call_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = "Speaker",
                tint = Color.White,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Speaker", color = Color.White)
          }
        }
      }
    }
  }
}
