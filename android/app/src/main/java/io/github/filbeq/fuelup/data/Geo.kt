package io.github.filbeq.fuelup.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Distances on the Earth's surface, as the crow flies (no roads). */
object Geo {
    /** Mean Earth radius. */
    const val EARTH_RADIUS_KM = 6371.0088

    /** Great-circle distance (haversine formula). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceAtMost(1.0)))
    }

    /**
     * A circle of [radiusKm] around a point, as a closed ring of [points] + 1
     * `[longitude, latitude]` pairs (GeoJSON order), first = last.
     */
    fun circle(lat: Double, lon: Double, radiusKm: Double, points: Int = 64): List<DoubleArray> {
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val d = radiusKm / EARTH_RADIUS_KM
        return (0..points).map { i ->
            val bearing = 2 * Math.PI * (i % points) / points
            val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearing))
            val lon2 = lon1 + atan2(sin(bearing) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
            doubleArrayOf(Math.toDegrees(lon2), Math.toDegrees(lat2))
        }
    }
}
