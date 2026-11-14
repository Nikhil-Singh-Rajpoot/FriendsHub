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

  @SuppressLint("MissingPermission")
  suspend fun fetchCoarseDeviceLocation(context: Context): Pair<Double, Double>? {
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
    if (!hasCoarse && !hasFine) return null

    val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    return suspendCancellableCoroutine { cont ->
      val cts = CancellationTokenSource()
      cont.invokeOnCancellation { cts.cancel() }
      client
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
        .addOnSuccessListener { location ->
          if (location != null) {
            cont.resume(quantizeCoordinate(location.latitude) to quantizeCoordinate(location.longitude))
          } else {
            client.lastLocation
              .addOnSuccessListener { last ->
                if (last != null) {
                  cont.resume(quantizeCoordinate(last.latitude) to quantizeCoordinate(last.longitude))
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
}
