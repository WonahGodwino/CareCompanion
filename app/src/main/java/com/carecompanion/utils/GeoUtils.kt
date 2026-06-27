package com.carecompanion.utils

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight geospatial helpers for the on-device risk engine.
 *
 * Patient coordinates are stored as strings ([com.carecompanion.data.database.entities.Patient]
 * latitude/longitude). Facility coordinates are not yet synced — once the Facility entity
 * carries lat/lng, [haversineKm] gives the straight-line distance a client must travel,
 * which feeds the access/distance risk dimension.
 */
object GeoUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    /** Parse a stored coordinate string to a Double, or null if blank/invalid. */
    fun parseCoord(value: String?): Double? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /**
     * Great-circle distance in kilometres between two lat/lng points, or null if any
     * coordinate is missing. Straight-line distance is a reasonable travel-burden proxy.
     */
    fun haversineKm(lat1: Double?, lng1: Double?, lat2: Double?, lng2: Double?): Double? {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return null
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
