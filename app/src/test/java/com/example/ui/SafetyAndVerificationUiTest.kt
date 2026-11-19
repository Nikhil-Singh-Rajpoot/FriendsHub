package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.Connection
import com.example.data.model.Meetup
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.ui.screens.EmergencyHelpScreen
import com.example.ui.screens.IdentityVerificationScreen
import com.example.ui.screens.MeetupDetailsScreen
import com.example.ui.screens.MeetupPlanningScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FriendsHubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafetyAndVerificationUiTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun emergencyHelpScreen_displaysHelplinesTrustedContactAndImSafeAction() {
    var safeClicked = false
    val meetup =
      Meetup(
        id = "m_1",
        connectionId = "c_1",
        proposerUid = "u_me",
        proposerName = "Aarav",
        otherUid = "u_peer",
        otherName = "Riya",
        activity = "Coffee",
        scheduledDateText = "Today",
        scheduledTimeText = "6:30 PM",
        publicLocationName = "Blue Tokai Cafe",
        publicArea = "Sector 29, Gurugram",
        status = "CONFIRMED",
      )

    composeTestRule.setContent {
      FriendsHubTheme {
        EmergencyHelpScreen(
          account =
            UserAccount(
              uid = "u_me",
              displayName = "Aarav",
              trustedContactName = "Priya Sister",
              trustedContactPhone = "+919876543210",
              trustedContactConsent = true,
            ),
          profile = UserProfile(uid = "u_me", firstName = "Aarav", age = 26, cityArea = "Sector 29, Gurugram"),
          liveCityArea = "Sector 29, Gurugram",
          liveCoordinates = 28.46 to 77.06,
          activeMeetup = meetup,
          onBack = {},
          onMarkMeetupSafe = { safeClicked = true },
          onOpenSettingsForTrustedContact = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("dial_112_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("dial_100_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("dial_181_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("alert_trusted_sms_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("emergency_im_safe_button").performScrollTo().performClick()
    assertTrue(safeClicked)
  }

  @Test
  fun identityVerificationScreen_displaysLevel1PhoneOtpAndLevel2RealTimeSelfie() {
    composeTestRule.setContent {
      FriendsHubTheme {
        IdentityVerificationScreen(
          account = UserAccount(uid = "u_me", displayName = "Aarav", phoneVerified = false),
          profile = UserProfile(uid = "u_me", firstName = "Aarav", age = 26, phoneVerified = false, isVerified = false),
          onBack = {},
          onPhoneVerified = {},
          onSubmitLiveSelfie = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("level1_phone_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Level 1: Phone Number OTP").assertIsDisplayed()
    composeTestRule.onNodeWithTag("send_otp_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("level2_selfie_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Level 2: Real-Time Live Selfie").assertIsDisplayed()
    composeTestRule.onNodeWithTag("open_live_selfie_camera_button").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun meetupPlanningScreen_blocksPrivateLocationsAndAllowsPublicCafes() {
    val conn =
      Connection(
        id = "c_1",
        participantUids = listOf("u_me", "u_peer"),
        userAUid = "u_me",
        userBUid = "u_peer",
        userAName = "Aarav",
        userBName = "Riya",
        activity = "Coffee",
      )

    composeTestRule.setContent {
      FriendsHubTheme {
        MeetupPlanningScreen(
          connection = conn,
          currentUserId = "u_me",
          myCityArea = "Sector 29, Gurugram",
          myName = "Aarav",
          account =
            UserAccount(
              uid = "u_me",
              displayName = "Aarav",
              trustedContactName = "Priya",
              trustedContactPhone = "+919876543210",
            ),
          onBack = {},
          onOpenTrustedContactSetup = {},
          onPropose = { _, _, _, _, _, _, _, _ -> },
        )
      }
    }

    // Default public cafe is valid
    composeTestRule.onNodeWithTag("propose_meetup_button").performScrollTo().assertIsEnabled()

    // Entering a private apartment disables propose button and shows safety error
    composeTestRule.onNodeWithTag("meetup_location_input").performScrollTo().performTextReplacement("My Home Apartment")
    composeTestRule.onNodeWithTag("propose_meetup_button").performScrollTo().assertIsNotEnabled()
    composeTestRule.onNodeWithText("not a private or residential location", substring = true)
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun meetupDetailsScreen_triggersCheckInAndImSafeCallbacks() {
    var checkedIn = false
    var markedSafe = false
    val meetup =
      Meetup(
        id = "m_1",
        connectionId = "c_1",
        proposerUid = "u_me",
        proposerName = "Aarav",
        otherUid = "u_peer",
        otherName = "Riya",
        activity = "Tea",
        scheduledDateText = "Today",
        scheduledTimeText = "5:30 PM",
        publicLocationName = "Blue Tokai Cafe",
        publicArea = "Sector 29, Gurugram",
        status = "CONFIRMED",
      )

    composeTestRule.setContent {
      FriendsHubTheme {
        MeetupDetailsScreen(
          meetup = meetup,
          currentUserId = "u_me",
          account = UserAccount(uid = "u_me", displayName = "Aarav"),
          myName = "Aarav",
          onBack = {},
          onConfirm = {},
          onCancel = {},
          onComplete = {},
          onCheckIn = { checkedIn = true },
          onMarkSafe = { markedSafe = true },
          onShareWithTrustedContact = {},
          onOpenEmergencyHelp = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("checkin_arrival_button").performScrollTo().performClick()
    assertTrue(checkedIn)
    composeTestRule.onNodeWithTag("im_safe_button").performScrollTo().performClick()
    assertTrue(markedSafe)
  }

  @Test
  fun settingsScreen_savesTrustedContactWithPhoneOnlyAndValidatesInvalidPhone() {
    var savedPhone = ""
    var savedName = "unset"
    var savedConsent = false

    composeTestRule.setContent {
      FriendsHubTheme {
        SettingsScreen(
          account = UserAccount(uid = "u_me", displayName = "Aarav"),
          onBack = {},
          onSavePrefs = { _, _, _ -> },
          onSaveTrustedContact = { name, phone, consent ->
            savedName = name
            savedPhone = phone
            savedConsent = consent
          },
        )
      }
    }

    // Invalid short phone shows inline validation error and does not invoke save
    composeTestRule.onNodeWithTag("trusted_contact_phone_input").performScrollTo().performTextReplacement("123")
    composeTestRule.onNodeWithTag("save_trusted_contact_button").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Enter a valid phone number", substring = true).performScrollTo().assertIsDisplayed()
    assertEquals("", savedPhone)
    assertFalse(savedConsent)

    // Valid phone number with blank name saves immediately
    composeTestRule.onNodeWithTag("trusted_contact_phone_input").performScrollTo().performTextReplacement("+919876543210")
    composeTestRule.onNodeWithTag("trusted_contact_consent_switch").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("save_trusted_contact_button").performScrollTo().performClick()

    assertEquals("", savedName)
    assertEquals("+919876543210", savedPhone)
    assertTrue(savedConsent)
  }

  @Test
  fun settingsScreen_displaysSavedBannerWhenOnlyPhoneIsSaved() {
    val account =
      UserAccount(
        uid = "u_me",
        displayName = "Aarav",
        trustedContactName = "",
        trustedContactPhone = "+919876543210",
        trustedContactConsent = true,
      )
    assertTrue(account.hasTrustedContact())

    composeTestRule.setContent {
      FriendsHubTheme {
        SettingsScreen(
          account = account,
          onBack = {},
          onSavePrefs = { _, _, _ -> },
          onSaveTrustedContact = { _, _, _ -> },
        )
      }
    }

    composeTestRule.onNodeWithTag("saved_trusted_contact_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Saved").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("clear_trusted_contact_button").performScrollTo().assertIsDisplayed()
  }
}
