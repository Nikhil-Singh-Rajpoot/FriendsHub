package com.example.util

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPrivacyTest {

  @Test
  fun quantizeCoordinate_roundsToApprox220mGrid() {
    val rawLat = 28.4581
    val quantized = LocationPrivacy.quantizeCoordinate(rawLat)
    // 28.4581 * 500 = 14229.05 -> rounds to 14229 -> / 500 = 28.458
    assertEquals(28.458, quantized, 0.0001)
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

  @Test
  fun buildNearbyUserCards_updatesWhenUserTravelsFromGurugramToNoida() {
    // User registered in Gurugram (profile still says Gurugram), now physically in Noida
    val registeredInGurugram =
      com.example.data.model.UserProfile(
        uid = "me",
        firstName = "Nikhil",
        cityArea = "Sector 22, Gurugram",
        approxLat = 0.0,
        approxLng = 0.0,
      )
    val gurugramPeerWithCoords =
      com.example.data.model.UserProfile(
        uid = "peer_ggn_gps",
        firstName = "Aarav",
        cityArea = "Sector 29, Gurugram",
        approxLat = 28.468,
        approxLng = 77.062,
        isAvailable = true,
      )
    val gurugramPeerNoCoords =
      com.example.data.model.UserProfile(
        uid = "peer_ggn_nogps",
        firstName = "Sanya",
        cityArea = "Sector 22, Gurugram",
        approxLat = 0.0,
        approxLng = 0.0,
        isAvailable = true,
      )
    val noidaPeerNear =
      com.example.data.model.UserProfile(
        uid = "peer_noida_near",
        firstName = "Riya",
        cityArea = "Sector 62, Noida",
        approxLat = 28.628,
        approxLng = 77.366,
        isAvailable = true,
      )

    val cardsInNoida =
      LocationPrivacy.buildNearbyUserCards(
        myProfile = registeredInGurugram,
        viewerLat = 28.627,
        viewerLng = 77.365,
        candidates = listOf(gurugramPeerWithCoords, gurugramPeerNoCoords, noidaPeerNear),
        blockedUids = emptySet(),
        radius = com.example.data.model.RadiusFilter.KM_5,
        activityFilter = null,
        viewerCityArea = "Sector 62, Noida",
      )

    assertEquals(listOf("peer_noida_near"), cardsInNoida.map { it.profile.uid })
  }

  @Test
  fun buildNearbyUserCards_strictlyExcludes8KmPeerUnder5KmFilter() {
    val personB =
      com.example.data.model.UserProfile(
        uid = "person_b",
        firstName = "PersonB",
        cityArea = "Sector 22, Gurugram",
      )
    // ~8.0 km north-east of (28.500, 77.050): dLat = 0.072 deg ≈ 8.0 km
    val personA8KmAway =
      com.example.data.model.UserProfile(
        uid = "person_a_8km",
        firstName = "PersonA",
        cityArea = "Gurugram",
        approxLat = 28.572,
        approxLng = 77.050,
        isAvailable = true,
      )

    val dist = LocationPrivacy.distanceKm(28.500, 77.050, personA8KmAway.approxLat, personA8KmAway.approxLng)
    assertTrue("Expected ~8 km distance, got $dist", dist in 7.8..8.2)

    listOf(
      com.example.data.model.RadiusFilter.KM_1,
      com.example.data.model.RadiusFilter.KM_3,
      com.example.data.model.RadiusFilter.KM_5,
    ).forEach { filter ->
      val cards =
        LocationPrivacy.buildNearbyUserCards(
          myProfile = personB,
          viewerLat = 28.500,
          viewerLng = 77.050,
          candidates = listOf(personA8KmAway),
          blockedUids = emptySet(),
          radius = filter,
          activityFilter = null,
        )
      assertTrue("8km peer must NOT appear under ${filter.label}", cards.isEmpty())
    }

    val cards10Km =
      LocationPrivacy.buildNearbyUserCards(
        myProfile = personB,
        viewerLat = 28.500,
        viewerLng = 77.050,
        candidates = listOf(personA8KmAway),
        blockedUids = emptySet(),
        radius = com.example.data.model.RadiusFilter.KM_10,
        activityFilter = null,
      )
    assertEquals(listOf("person_a_8km"), cards10Km.map { it.profile.uid })
  }
}
