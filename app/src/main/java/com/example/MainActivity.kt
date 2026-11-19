package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.FriendsHubApp
import com.example.ui.theme.FriendsHubTheme
import com.example.util.SafetyNotificationHelper

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val initialMeetupId = intent?.getStringExtra(SafetyNotificationHelper.EXTRA_MEETUP_ID)
    setContent {
      FriendsHubTheme {
        FriendsHubApp(initialMeetupId = initialMeetupId)
      }
    }
  }
}
