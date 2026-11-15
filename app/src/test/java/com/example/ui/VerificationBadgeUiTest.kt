package com.example.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.NearbyUserCardModel
import com.example.data.model.UserAccount
import com.example.data.model.UserProfile
import com.example.ui.screens.NearbyMemberCard
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.UserProfileDetailsScreen
import com.example.ui.theme.FriendsHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VerificationBadgeUiTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun nearbyMemberCard_showsUnverifiedMarkForNewUserAndBlueTickForVerifiedUser() {
    val unverifiedPeer =
      UserProfile(
        uid = "u_unverified",
        firstName = "Rohan",
        age = 25,
        cityArea = "Sector 22, Gurugram",
        isVerified = false,
        isAvailable = true,
        availabilityExpiresAtMillis = System.currentTimeMillis() + 3_600_000L,
      )
    val verifiedPeer =
      UserProfile(
        uid = "u_verified",
        firstName = "Priya",
        age = 26,
        cityArea = "Sector 29, Gurugram",
        isVerified = true,
        isAvailable = true,
        availabilityExpiresAtMillis = System.currentTimeMillis() + 3_600_000L,
      )

    composeTestRule.setContent {
      FriendsHubTheme {
        androidx.compose.foundation.layout.Column(
          modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())
        ) {
          NearbyMemberCard(
            model = NearbyUserCardModel(unverifiedPeer, 1.2, "1.2 KM away", emptyList()),
            onViewProfile = {},
            onConnect = {},
          )
          NearbyMemberCard(
            model = NearbyUserCardModel(verifiedPeer, 2.1, "2.1 KM away", emptyList()),
            onViewProfile = {},
            onConnect = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("unverified_badge_u_unverified", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithText("Unverified", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("verified_badge_u_verified", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithContentDescription("Verified", useUnmergedTree = true).assertExists()
  }

  @Test
  fun userProfileDetailsScreen_showsUnverifiedAccountMarkUntilAdminVerifies() {
    val unverifiedPeer =
      UserProfile(
        uid = "u_new",
        firstName = "Aman",
        age = 24,
        cityArea = "Sector 22, Gurugram",
        isVerified = false,
        isAvailable = false,
      )

    composeTestRule.setContent {
      FriendsHubTheme {
        UserProfileDetailsScreen(
          profile = unverifiedPeer,
          distanceLabel = "1.0 KM away",
          commonInterests = emptyList(),
          onBack = {},
          onSendRequest = { _, _ -> },
          onBlockUser = {},
          onReportUser = { _, _ -> },
        )
      }
    }

    composeTestRule.onNodeWithTag("profile_details_verification_badge").assertIsDisplayed()
    composeTestRule.onNodeWithText("Unverified Account").assertIsDisplayed()
    composeTestRule.onNodeWithText("Not yet verified by admin").assertIsDisplayed()
  }

  @Test
  fun profileScreen_showsUnverifiedAccountPillForNewUser() {
    val myUnverifiedProfile =
      UserProfile(
        uid = "u_me",
        firstName = "Neha",
        age = 23,
        cityArea = "Sector 22, Gurugram",
        isVerified = false,
      )

    composeTestRule.setContent {
      FriendsHubTheme {
        ProfileScreen(
          account = UserAccount(uid = "u_me", displayName = "Neha", isAdultVerified = true),
          profile = myUnverifiedProfile,
          userEmail = "neha@example.com",
          onSaveProfile = { _, _, _, _, _, _, _ -> },
          onOpenNotifications = {},
          onOpenSettings = {},
          onOpenBlockedUsers = {},
          onOpenPrivacy = {},
          onOpenTerms = {},
          onSignOut = {},
          onDeleteAccount = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("my_profile_verification_badge").assertIsDisplayed()
    composeTestRule.onNodeWithText("Unverified Account").assertIsDisplayed()
  }
}
