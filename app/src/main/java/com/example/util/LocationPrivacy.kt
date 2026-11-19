package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.example.data.model.PublicMeetupSpot
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

object LocationPrivacy {

  /**
   * Quantizes GPS coordinates to ~220m grid cells (0.002 degrees) before storing in Firestore
   * so exact street/home coordinates are never stored while avoiding large boundary drift.
   */
  fun quantizeCoordinate(value: Double): Double {
    return round(value * 500.0) / 500.0
  }

  const val MEETUP_PROXIMITY_START_METERS = 5.0

  /** Computes Haversine distance in meters between two exact coordinates (used for <= 5m meetup start). */
  fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    if ((lat1 == 0.0 && lng1 == 0.0) || (lat2 == 0.0 && lng2 == 0.0)) return Double.MAX_VALUE
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
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

  fun formatCountdownDuration(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
      String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
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
        categoryHint = "Busy well-lit public cafe • High foot traffic",
      ),
      PublicMeetupSpot(
        name = "Third Wave Coffee",
        area = cleanArea,
        categoryHint = "Busy public cafe • Open storefront",
      ),
      PublicMeetupSpot(
        name = "Starbucks / Central Mall Atrium",
        area = cleanArea,
        categoryHint = "Busy mall ground-floor cafe • Security on site",
      ),
      PublicMeetupSpot(
        name = "Main Mall Food Court",
        area = cleanArea,
        categoryHint = "Crowded public dining plaza • CCTV & security",
      ),
      PublicMeetupSpot(
        name = "Central Courtyard / Galleria Plaza",
        area = cleanArea,
        categoryHint = "Busy open public promenade",
      ),
      PublicMeetupSpot(
        name = "Crossword / Public Book Cafe",
        area = cleanArea,
        categoryHint = "Well-lit public bookstore cafe",
      ),
      PublicMeetupSpot(
        name = "Main City Park Walking Track",
        area = cleanArea,
        categoryHint = "Busy daytime public walking trail",
      ),
    ).sortedByDescending { spot ->
      when {
        activity.contains("tea", ignoreCase = true) || activity.contains("coffee", ignoreCase = true) ->
          if (spot.categoryHint.contains("cafe", ignoreCase = true)) 2 else 0
        activity.contains("food", ignoreCase = true) || activity.contains("eat", ignoreCase = true) ->
          if (spot.categoryHint.contains("dining", ignoreCase = true) || spot.categoryHint.contains("mall", ignoreCase = true)) 2 else 0
        activity.contains("walk", ignoreCase = true) ->
          when {
            spot.categoryHint.contains("walking", ignoreCase = true) -> 3
            spot.categoryHint.contains("promenade", ignoreCase = true) -> 1
            else -> 0
          }
        else -> 0
      }
    }
  }

  private val privateLocationTerms =
    listOf(
      "my home",
      "home",
      "my house",
      "house no",
      "flat ",
      "apartment",
      "apt ",
      "hostel room",
      "bedroom",
      "my room",
      "hotel room",
      "my place",
      "private villa",
      "basement",
      "pg room",
    )

  /**
   * Ensures users choose a public, busy meeting place rather than a residential or private venue.
   * Returns a human-readable error message if invalid, or null if acceptable.
   */
  fun validatePublicMeetupLocation(locationName: String): String? {
    val trimmed = locationName.trim()
    if (trimmed.length < 3) {
      return "Enter the name of a busy public place (cafe, mall, or public park)."
    }
    val lower = trimmed.lowercase(Locale.US)
    val matchedPrivate = privateLocationTerms.firstOrNull { term -> lower.contains(term) }
    if (matchedPrivate != null) {
      return "For safety, meetups must be at a busy public venue (cafe, mall, or plaza) — not a private or residential location."
    }
    return null
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
    viewerCityArea: String = myProfile?.cityArea.orEmpty(),
  ): List<com.example.data.model.NearbyUserCardModel> {
    val effectiveLat = if (viewerLat != 0.0 || viewerLng != 0.0) viewerLat else (myProfile?.approxLat ?: 0.0)
    val effectiveLng = if (viewerLat != 0.0 || viewerLng != 0.0) viewerLng else (myProfile?.approxLng ?: 0.0)
    val viewerHasCoords = effectiveLat != 0.0 || effectiveLng != 0.0
    val myCityArea = viewerCityArea.ifBlank { if (viewerHasCoords) "" else myProfile?.cityArea.orEmpty() }
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
    val rawLat: Double = approxLat,
    val rawLng: Double = approxLng,
  )

  fun hasLocationPermission(context: Context): Boolean {
    return hasFineLocationPermission(context) || hasCoarseLocationPermission(context)
  }

  private fun hasFineLocationPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
  }

  private fun hasCoarseLocationPermission(context: Context): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
  }

  private fun preferredLocationPriority(context: Context): Int {
    return if (hasFineLocationPermission(context)) {
      Priority.PRIORITY_HIGH_ACCURACY
    } else {
      Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }
  }

  @SuppressLint("MissingPermission")
  suspend fun fetchRawDeviceLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null

    val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    val priority = preferredLocationPriority(context)
    return suspendCancellableCoroutine { cont ->
      val cts = CancellationTokenSource()
      cont.invokeOnCancellation { cts.cancel() }
      client
        .getCurrentLocation(priority, cts.token)
        .addOnSuccessListener { location ->
          if (location != null) {
            cont.resume(location.latitude to location.longitude)
          } else {
            client.lastLocation
              .addOnSuccessListener { last ->
                val maxAgeMillis = 3 * 60_000L
                val isFresh = last != null && (System.currentTimeMillis() - last.time) <= maxAgeMillis
                if (isFresh && last != null) {
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
      rawLat = raw.first,
      rawLng = raw.second,
    )
  }

  @SuppressLint("MissingPermission")
  fun observeLiveLocations(context: Context): Flow<DetectedLocationAddress> = callbackFlow {
    val appContext = context.applicationContext
    if (!hasLocationPermission(appContext)) {
      close()
      return@callbackFlow
    }

    val client = LocationServices.getFusedLocationProviderClient(appContext)
    var lastGeocodedCoords: Pair<Double, Double>? = null
    var lastGeocodedCityArea = ""

    suspend fun emitAddress(lat: Double, lng: Double) {
      val movedFarEnough =
        lastGeocodedCoords == null ||
          distanceKm(lastGeocodedCoords!!.first, lastGeocodedCoords!!.second, lat, lng) >= 0.4
      if (movedFarEnough || lastGeocodedCityArea.isBlank()) {
        val resolved = reverseGeocodeCityArea(appContext, lat, lng).orEmpty()
        if (resolved.isNotBlank()) {
          lastGeocodedCityArea = resolved
          lastGeocodedCoords = lat to lng
        }
      }
      trySend(
        DetectedLocationAddress(
          cityArea = lastGeocodedCityArea,
          approxLat = quantizeCoordinate(lat),
          approxLng = quantizeCoordinate(lng),
          rawLat = lat,
          rawLng = lng,
        )
      )
    }

    launch {
      fetchRawDeviceLocation(appContext)?.let { (lat, lng) ->
        emitAddress(lat, lng)
      }
    }

    val request =
      LocationRequest.Builder(preferredLocationPriority(appContext), 20_000L)
        .setMinUpdateIntervalMillis(10_000L)
        .setMinUpdateDistanceMeters(150f)
        .build()

    val callback =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          val loc = result.lastLocation ?: return
          launch { emitAddress(loc.latitude, loc.longitude) }
        }
      }

    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    awaitClose { client.removeLocationUpdates(callback) }
  }

  /**
   * Streams raw high-accuracy GPS coordinates strictly for active confirmed meetup 5-meter
   * proximity verification between two participants.
   */
  @SuppressLint("MissingPermission")
  fun observeLiveExactLocation(context: Context): Flow<Pair<Double, Double>> = callbackFlow {
    val appContext = context.applicationContext
    if (!hasLocationPermission(appContext)) {
      close()
      return@callbackFlow
    }
    val client = LocationServices.getFusedLocationProviderClient(appContext)
    launch {
      fetchRawDeviceLocation(appContext)?.let { coords ->
        trySend(coords)
      }
    }
    val request =
      LocationRequest.Builder(preferredLocationPriority(appContext), 3_000L)
        .setMinUpdateIntervalMillis(1_500L)
        .setMinUpdateDistanceMeters(1.5f)
        .build()

    val callback =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          val loc = result.lastLocation ?: return
          trySend(loc.latitude to loc.longitude)
        }
      }

    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    awaitClose { client.removeLocationUpdates(callback) }
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
