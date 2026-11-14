package com.example.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPrivacyTest {

  @Test
  fun quantizeCoordinate_roundsToApprox550mGrid() {
    val rawLat = 28.459496
    val quantized = LocationPrivacy.quantizeCoordinate(rawLat)
    // 28.459496 * 200 = 5691.8992 -> rounds to 5692 -> / 200 = 28.46
    assertEquals(28.46, quantized, 0.0001)
  }

  @Test
  fun isAdult_enforcesStrict18PlusBoundary() {
    val now = Calendar.getInstance().apply {
      set(2026, Calendar.MAY, 15, 12, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val exact18 = Calendar.getInstance().apply {
      set(2008, Calendar.MAY, 15, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val oneDayShortOf18 = Calendar.getInstance().apply {
      set(2008, Calendar.MAY, 16, 0, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }
    assertTrue(LocationPrivacy.isAdult(exact18.timeInMillis, now.timeInMillis))
    assertFalse(LocationPrivacy.isAdult(oneDayShortOf18.timeInMillis, now.timeInMillis))
  }

  @Test
  fun distanceAndFormatting_producePrivacyFriendlyText() {
    assertEquals("Within 0.5 KM", LocationPrivacy.formatApproxDistance(0.2))
    assertEquals("3.4 KM away", LocationPrivacy.formatApproxDistance(3.42))
    assertEquals("Nearby city", LocationPrivacy.formatApproxDistance(150.0))

    val dist = LocationPrivacy.distanceKm(28.46, 77.03, 28.495, 77.085)
    assertTrue(dist in 5.0..9.0)
  }

  @Test
  fun suggestPublicMeetupSpots_prioritizesMatchingActivityAndUsesCityArea() {
    val cafeSpots = LocationPrivacy.suggestPublicMeetupSpots("Sector 29, Gurugram", "Have tea or coffee")
    assertTrue(cafeSpots.isNotEmpty())
    assertTrue(cafeSpots.first().categoryHint.contains("cafe", ignoreCase = true))
    assertEquals("Sector 29, Gurugram", cafeSpots.first().area)

    val walkSpots = LocationPrivacy.suggestPublicMeetupSpots("", "Go for a walk")
    assertTrue(walkSpots.first().categoryHint.contains("walking", ignoreCase = true))
    assertEquals("City Center", walkSpots.first().area)
  }

  @Test
  fun isSameCityOrArea_matchesSameCityAndRejectsDistantCities() {
    assertTrue(LocationPrivacy.isSameCityOrArea("Sector 22, Gurugram", "Sector 29, Gurugram"))
    assertTrue(LocationPrivacy.isSameCityOrArea("Sector 22, Gurgaon", "Sector 23, Gurugram"))
    assertFalse(LocationPrivacy.isSameCityOrArea("Sector 22, Gurugram", "Sector 22, Noida"))
    assertFalse(LocationPrivacy.isSameCityOrArea("Sector 22, Gurugram", "Andheri, Mumbai"))
    assertFalse(LocationPrivacy.isSameCityOrArea("Sector 22, Gurugram", ""))
  }

  @Test
  fun buildNearbyUserCards_showsOnlyNearbyUsersWithinRadiusOrMatchingCity() {
    val sector22User =
      com.example.data.model.UserProfile(
        uid = "me",
        firstName = "Nikhil",
        cityArea = "Sector 22, Gurugram",
        approxLat = 0.0,
        approxLng = 0.0,
      )
    val nearbyGurugramPeer =
      com.example.data.model.UserProfile(
        uid = "peer_ggn_near",
        firstName = "Aarav",
        cityArea = "Sector 23, Gurugram",
        approxLat = 28.510,
        approxLng = 77.055,
        isAvailable = true,
      )
    val farNoidaPeer =
      com.example.data.model.UserProfile(
        uid = "peer_noida_far",
        firstName = "Riya",
        cityArea = "Sector 62, Noida",
        approxLat = 28.628,
        approxLng = 77.365,
        isAvailable = true,
      )
    val mumbaiPeerNoCoords =
      com.example.data.model.UserProfile(
        uid = "peer_mumbai",
        firstName = "Kabir",
        cityArea = "Bandra, Mumbai",
        approxLat = 0.0,
        approxLng = 0.0,
        isAvailable = true,
      )
    val candidates = listOf(nearbyGurugramPeer, farNoidaPeer, mumbaiPeerNoCoords)

    // 1. Viewer in Gurugram Sector 22 WITH live GPS (even with availability OFF on profile)
    val withGpsCards =
      LocationPrivacy.buildNearbyUserCards(
        myProfile = sector22User,
        viewerLat = 28.505,
        viewerLng = 77.065,
        candidates = candidates,
        blockedUids = emptySet(),
        radius = com.example.data.model.RadiusFilter.KM_5,
        activityFilter = null,
      )
    assertEquals(listOf("peer_ggn_near"), withGpsCards.map { it.profile.uid })

    // 2. Viewer in Gurugram Sector 22 WITHOUT GPS falls back strictly to matching city/area (never Mumbai/Noida)
    val withoutGpsCards =
      LocationPrivacy.buildNearbyUserCards(
        myProfile = sector22User,
        viewerLat = 0.0,
        viewerLng = 0.0,
        candidates = candidates,
        blockedUids = emptySet(),
        radius = com.example.data.model.RadiusFilter.KM_5,
        activityFilter = null,
      )
    assertEquals(listOf("peer_ggn_near"), withoutGpsCards.map { it.profile.uid })
  }
}
