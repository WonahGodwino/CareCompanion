package com.carecompanion.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Captures a single GPS fix using the framework [LocationManager] — no Google Play
 * Services dependency required. Caller MUST hold ACCESS_FINE_LOCATION (or COARSE)
 * before invoking; a [SecurityException] is surfaced via [onError] otherwise.
 *
 * Used to record the facility's coordinates while a clinician is physically on-site,
 * which feeds the distance-to-facility signal in [PatientRiskEngine].
 */
object LocationCapture {

    @SuppressLint("MissingPermission")
    fun current(
        context: Context,
        timeoutMs: Long = 15_000,
        onResult: (latitude: Double, longitude: Double, accuracyMeters: Float?) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { onError("Location service unavailable on this device."); return }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            onError("Location is turned off. Enable GPS/Location and try again.")
            return
        }

        var delivered = false
        val handler = Handler(Looper.getMainLooper())

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (delivered) return
                delivered = true
                runCatching { lm.removeUpdates(this) }
                onResult(location.latitude, location.longitude, location.accuracyOrNull())
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            providers.forEach { provider ->
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            onError("Location permission not granted.")
            return
        }

        // Fall back to the freshest last-known fix if no live update arrives in time.
        handler.postDelayed({
            if (delivered) return@postDelayed
            delivered = true
            runCatching { lm.removeUpdates(listener) }
            val last = providers
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null) onResult(last.latitude, last.longitude, last.accuracyOrNull())
            else onError("Couldn't get a location fix. Move near a window or outdoors and try again.")
        }, timeoutMs)
    }

    private fun Location.accuracyOrNull(): Float? = if (hasAccuracy()) accuracy else null
}
