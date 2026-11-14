package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import com.example.data.model.PublicMeetupSpot
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.suspendCancellableCoroutine

object LocationPrivacy {

  /**
   * Quantizes GPS coordinates to ~550m grid cells (0.005 degrees) before storing in Firestore
   * so exact user coordinates or home addresses are never stored or exposed.
   */
  fun quantizeCoordinate(value: Double): Double {
    return round(value * 200.0) / 200.0
  }

  /** Computes Haversine distance in kilometers between two approximate coordinates. */
  fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    if (lat1 == 0.0 && lng1 == 0.0) return 999.0
    if (lat2 == 0.0 && lng2 == 0.0) return 999.0
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
  }

  fun formatApproxDistance(km: Double): String {
    return when {
      km < 0.5 -> "Within 0.5 KM"
      km >= 100.0 -> "Nearby city"
      else -> String.format(Locale.US, "%.1f KM away", km)
    }
  }

  /** Calculates completed age in years from UTC epoch millis. */
  fun calculateAge(birthDateMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int {
    if (birthDateMillis <= 0L || birthDateMillis >= nowMillis) return 0
    val dob = Calendar.getInstance().apply { timeInMillis = birthDateMillis }
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH)
    val dobMonth = dob.get(Calendar.MONTH)
    if (
      todayMonth < dobMonth ||
        (todayMonth == dobMonth && today.get(Calendar.DAY_OF_MONTH) < dob.get(Calendar.DAY_OF_MONTH))
    ) {
      age--
    }
    return age
  }

  fun isAdult(birthDateMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
    return calculateAge(birthDateMillis, nowMillis) >= 18
  }

  fun suggestPublicMeetupSpots(cityArea: String, activity: String): List<PublicMeetupSpot> {
    val cleanArea = cityArea.ifBlank { "City Center" }
    return listOf(
      PublicMeetupSpot(
        name = "Blue Tokai Coffee Roasters",
        area = cleanArea,
        categoryHint = "Well-lit public cafe",
      ),
      PublicMeetupSpot(
        name = "Third Wave Coffee",
        area = cleanArea,
        categoryHint = "Popular cafe seating",
      ),
      PublicMeetupSpot(
        name = "Central Courtyard / Galleria Plaza",
        area = cleanArea,
        categoryHint = "Open public promenade",
      ),
      PublicMeetupSpot(
        name = "Crossword / Public Book Cafe",
        area = cleanArea,
        categoryHint = "Quiet conversation space",
      ),
      PublicMeetupSpot(
        name = "Main City Park Walking Track",
        area = cleanArea,
        categoryHint = "Public daytime walking trail",
      ),
    ).sortedByDescending { spot ->
      when {
        activity.contains("tea", ignoreCase = true) || activity.contains("coffee", ignoreCase = true) ->
          if (spot.categoryHint.contains("cafe", ignoreCase = true)) 1 else 0
        activity.contains("walk", ignoreCase = true) ->
          if (spot.categoryHint.contains("walking", ignoreCase = true)) 1 else 0
        else -> 0
      }
    }
  }

  private val cityAliases =
    mapOf(
      "gurgaon" to "gurugram",
      "bangalore" to "bengaluru",
      "bombay" to "mumbai",
      "calcutta" to "kolkata",
      "madras" to "chennai",
      "poona" to "pune",
      "trivandrum" to "thiruvananthapuram",
      "cochin" to "kochi",
      "new delhi" to "delhi",
    )

  private val genericAreaWords =
    setOf(
      "sector",
      "phase",
      "block",
      "pocket",
      "road",
      "marg",
      "street",
      "lane",
      "nagar",
      "colony",
      "enclave",
      "vihar",
      "extension",
      "ext",
      "area",
      "near",
      "city",
      "district",
      "india",
      "sec",
    )

  private fun normalizeCityAlias(value: String): String {
    val trimmed = value.trim().lowercase(Locale.US)
    return cityAliases[trimmed] ?: trimmed
  }

  /**
   * Checks whether two "Neighborhood, City" strings refer to the same local city/area.
   * Ensures e.g. "Sector 22, Gurugram" matches "Sector 29, Gurugram" / "Sector 22, Gurgaon",
   * while rejecting "Sector 22, Noida" or "Andheri, Mumbai".
   */
  fun isSameCityOrArea(myCityArea: String, otherCityArea: String): Boolean {
    val aParts = myCityArea.split(",").map { normalizeCityAlias(it) }.filter { it.isNotBlank() }
    val bParts = otherCityArea.split(",").map { normalizeCityAlias(it) }.filter { it.isNotBlank() }
    if (aParts.isEmpty() || bParts.isEmpty()) return false

    fun tokensFrom(parts: List<String>): Set<String> {
      return parts
        .flatMap { part -> part.split(Regex("[^a-z0-9]+")) }
        .map { normalizeCityAlias(it) }
        .filter { token ->
          token.length >= 3 &&
            token !in genericAreaWords &&
            !token.all { it.isDigit() }
        }
        .toSet()
    }

    val aEffectiveParts = if (aParts.size >= 3) aParts.take(2) else aParts
    val bEffectiveParts = if (bParts.size >= 3) bParts.take(2) else bParts

    if (aEffectiveParts.size >= 2 && bEffectiveParts.size >= 2) {
      if (aEffectiveParts[1] == bEffectiveParts[1]) return true
    }

    val aTokens = tokensFrom(aEffectiveParts)
    val bTokens = tokensFrom(bEffectiveParts)
    if (aTokens.isNotEmpty() && bTokens.isNotEmpty()) {
      return (aTokens intersect bTokens).isNotEmpty()
    }

    return aEffectiveParts.first() == bEffectiveParts.first()
  }

  /**
   * Filters available candidates to ONLY users genuinely nearby the current user:
   * - If both users have coordinates, enforces Haversine distance <= radiusFilter.km.
   * - If either user lacks coordinates (e.g. viewer has availability OFF and no GPS permission),
   *   requires matching city/area instead of showing distant users.
   */
  fun buildNearbyUserCards(
    myProfile: com.example.data.model.UserProfile?,
    viewerLat: Double,
    viewerLng: Double,
    candidates: List<com.example.data.model.UserProfile>,
    blockedUids: Set<String>,
    radius: com.example.data.model.RadiusFilter,
    activityFilter: String?,
  ): List<com.example.data.model.NearbyUserCardModel> {
    val effectiveLat = if (viewerLat != 0.0 || viewerLng != 0.0) viewerLat else (myProfile?.approxLat ?: 0.0)
    val effectiveLng = if (viewerLat != 0.0 || viewerLng != 0.0) viewerLng else (myProfile?.approxLng ?: 0.0)
    val viewerHasCoords = effectiveLat != 0.0 || effectiveLng != 0.0
    val myCityArea = myProfile?.cityArea.orEmpty()
    val myInterests = myProfile?.interests?.toSet().orEmpty()

    return candidates
      .asSequence()
      .filter { it.uid !in blockedUids }
      .filter { profile ->
        activityFilter.isNullOrBlank() ||
          profile.preferredActivity.contains(activityFilter, ignoreCase = true) ||
          (profile.preferredActivity.isBlank() &&
            profile.interests.any { it.contains(activityFilter, ignoreCase = true) })
      }
      .mapNotNull { profile ->
        val peerHasCoords = profile.approxLat != 0.0 || profile.approxLng != 0.0
        val sameCity = isSameCityOrArea(myCityArea, profile.cityArea)
        val distKm =
          if (viewerHasCoords && peerHasCoords) {
            distanceKm(effectiveLat, effectiveLng, profile.approxLat, profile.approxLng)
          } else {
            999.0
          }

        val isNearby =
          if (viewerHasCoords && peerHasCoords) {
            distKm <= radius.km.toDouble()
          } else {
            sameCity
          }

        if (!isNearby) return@mapNotNull null

        val common = profile.interests.filter { it in myInterests }
        val label =
          if (viewerHasCoords && peerHasCoords) {
            formatApproxDistance(distKm)
          } else {
            profile.cityArea.ifBlank { "Same area" }
          }

        com.example.data.model.NearbyUserCardModel(
          profile = profile,
          distanceKm = if (viewerHasCoords && peerHasCoords) distKm else 0.5,
          distanceLabel = label,
          commonInterests = common,
        )
      }
      .sortedBy { it.distanceKm }
      .toList()
  }

  data class DetectedLocationAddress(
    val cityArea: String,
    val approxLat: Double,
    val approxLng: Double,
  )

  private fun hasLocationPermission(context: Context): Boolean {
    val hasCoarse =
      androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasFine =
      androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    return hasCoarse || hasFine
  }

  @SuppressLint("MissingPermission")
  private suspend fun fetchRawDeviceLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null

    val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    return suspendCancellableCoroutine { cont ->
      val cts = CancellationTokenSource()
      cont.invokeOnCancellation { cts.cancel() }
      client
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
        .addOnSuccessListener { location ->
          if (location != null) {
            cont.resume(location.latitude to location.longitude)
          } else {
            client.lastLocation
              .addOnSuccessListener { last ->
                if (last != null) {
                  cont.resume(last.latitude to last.longitude)
                } else {
                  cont.resume(null)
                }
              }
              .addOnFailureListener { cont.resume(null) }
          }
        }
        .addOnFailureListener { cont.resume(null) }
    }
  }

  suspend fun fetchCoarseDeviceLocation(context: Context): Pair<Double, Double>? {
    val raw = fetchRawDeviceLocation(context) ?: return null
    return quantizeCoordinate(raw.first) to quantizeCoordinate(raw.second)
  }

  suspend fun detectCityAreaFromDeviceLocation(context: Context): DetectedLocationAddress? {
    val raw = fetchRawDeviceLocation(context) ?: return null
    val qLat = quantizeCoordinate(raw.first)
    val qLng = quantizeCoordinate(raw.second)
    val cityArea = reverseGeocodeCityArea(context, raw.first, raw.second).orEmpty()
    return DetectedLocationAddress(
      cityArea = cityArea,
      approxLat = qLat,
      approxLng = qLng,
    )
  }

  private fun formatAddressToCityArea(address: android.location.Address?): String {
    if (address == null) return ""
    val neighborhood = address.subLocality?.trim().orEmpty()
    val city = address.locality?.trim().orEmpty()
    val district = address.subAdminArea?.trim().orEmpty()
    val state = address.adminArea?.trim().orEmpty()

    val parts =
      linkedSetOf<String>().apply {
        if (neighborhood.isNotBlank()) add(neighborhood)
        if (city.isNotBlank()) {
          add(city)
        } else if (district.isNotBlank()) {
          add(district)
        }
        if (size < 2 && state.isNotBlank()) add(state)
      }
    return parts.joinToString(", ").take(80)
  }

  private suspend fun reverseGeocodeCityArea(
    context: Context,
    latitude: Double,
    longitude: Double,
  ): String? {
    if (!android.location.Geocoder.isPresent()) return null
    val geocoder = android.location.Geocoder(context.applicationContext, Locale.getDefault())
    return runCatching {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine<String?> { cont ->
          geocoder.getFromLocation(
            latitude,
            longitude,
            1,
            object : android.location.Geocoder.GeocodeListener {
              override fun onGeocode(addresses: MutableList<android.location.Address>) {
                if (cont.isActive) {
                  cont.resume(formatAddressToCityArea(addresses.firstOrNull()))
                }
              }

              override fun onError(errorMessage: String?) {
                if (cont.isActive) {
                  cont.resume(null)
                }
              }
            },
          )
        }
      } else {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          @Suppress("DEPRECATION")
          val results = geocoder.getFromLocation(latitude, longitude, 1)
          formatAddressToCityArea(results?.firstOrNull())
        }
      }
    }.getOrNull()
  }
}
