package io.github.filbeq.fuelup.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.cos

enum class NearbySort { PRICE, DISTANCE }

/** A station in the "near me" or area list: its price for the chosen fuel and how far it is. */
class NearbyStation(val station: Station, val price: RankedPrice, val distanceKm: Double)

/** The "cheapest nearby" list. Distances are as the crow flies (no roads). */
object Nearby {
    /** Radius choices. 10 km: ~40 stations at the median place, 3+ in 99% of places (measured). */
    val RADII_KM = listOf(5, 10, 20)
    const val DEFAULT_RADIUS_KM = 10

    /** Radius of the view the app opens on when it may use the location (the saved radius is kept for the button). */
    const val LAUNCH_RADIUS_KM = 5

    /** A fresh fix this far from the one shown re-frames the map (if the user hasn't moved it). */
    const val REFRAME_KM = 0.5

    /** True if [new] is far enough from [old] to re-frame the map ([REFRAME_KM]). */
    fun movedEnough(old: UserPosition, new: UserPosition): Boolean =
        Geo.distanceKm(old.lat, old.lon, new.lat, new.lon) > REFRAME_KM

    /** Stations within [radiusKm] of a point that sell the chosen fuel (those in [ranking]), sorted by [sort]. */
    fun find(
        stations: List<Station>,
        ranking: Map<Int, RankedPrice>,
        lat: Double,
        lon: Double,
        radiusKm: Int,
        sort: NearbySort,
    ): List<NearbyStation> {
        // Cheap box test first, so the exact distance is computed only near the point.
        val maxDLat = radiusKm / KM_PER_DEG_LAT
        val maxDLon = radiusKm / (KM_PER_DEG_LAT * cos(Math.toRadians(lat)).coerceAtLeast(0.1))
        val found = stations.mapNotNull { station ->
            if (Math.abs(station.lat - lat) > maxDLat || Math.abs(station.lon - lon) > maxDLon) return@mapNotNull null
            val price = ranking[station.id] ?: return@mapNotNull null
            val distance = Geo.distanceKm(lat, lon, station.lat, station.lon)
            if (distance > radiusKm) null else NearbyStation(station, price, distance)
        }
        return sorted(found, sort)
    }

    /**
     * Stations inside [area] that sell the chosen fuel (those in [ranking]),
     * with their distance from a point: the user when the position is known,
     * else the middle of the area. Sorted as [find].
     */
    fun inArea(
        stations: List<Station>,
        ranking: Map<Int, RankedPrice>,
        area: MapArea,
        fromLat: Double,
        fromLon: Double,
        sort: NearbySort,
    ): List<NearbyStation> {
        val found = stations.mapNotNull { station ->
            val price = ranking[station.id] ?: return@mapNotNull null
            if (!area.contains(station.lat, station.lon)) return@mapNotNull null
            NearbyStation(station, price, Geo.distanceKm(fromLat, fromLon, station.lat, station.lon))
        }
        return sorted(found, sort)
    }

    /**
     * By price: cheapest first, ties by distance, prices "to verify" last (a
     * suspect price is never on top). By distance: nearest first, ties by price.
     */
    private fun sorted(found: List<NearbyStation>, sort: NearbySort): List<NearbyStation> = when (sort) {
        NearbySort.PRICE -> found.sortedWith(
            compareBy<NearbyStation> { it.price.priceClass == PriceClass.TO_VERIFY }
                .thenBy { it.price.priceMilli }
                .thenBy { it.distanceKm },
        )
        NearbySort.DISTANCE -> found.sortedWith(compareBy<NearbyStation> { it.distanceKm }.thenBy { it.price.priceMilli })
    }

    private const val KM_PER_DEG_LAT = 111.0
}

data class NearbySettings(val radiusKm: Int = Nearby.DEFAULT_RADIUS_KM, val sort: NearbySort = NearbySort.PRICE)

/** Saves [NearbySettings] in the app's settings file; anything unknown falls back to the default. */
class NearbySettingsStore(private val prefs: SharedPreferences) {
    fun load() = NearbySettings(
        radiusKm = prefs.getInt(KEY_RADIUS, Nearby.DEFAULT_RADIUS_KM).takeIf { it in Nearby.RADII_KM }
            ?: Nearby.DEFAULT_RADIUS_KM,
        sort = NearbySort.entries.firstOrNull { it.name == prefs.getString(KEY_SORT, null) } ?: NearbySort.PRICE,
    )

    fun save(settings: NearbySettings) {
        prefs.edit {
            putInt(KEY_RADIUS, settings.radiusKm)
            putString(KEY_SORT, settings.sort.name)
        }
    }

    private companion object {
        const val KEY_RADIUS = "nearbyRadiusKm"
        const val KEY_SORT = "nearbySort"
    }
}
