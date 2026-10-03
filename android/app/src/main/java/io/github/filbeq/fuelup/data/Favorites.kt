package io.github.filbeq.fuelup.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A station the user starred, keyed by MIMIT's station id. The labels and
 * position are the last ones seen in the data, so a station missing from
 * today's data can still be listed by name (it is never removed by itself).
 */
@Serializable
data class FavoriteStation(
    val id: Int,
    val name: String,
    val brand: String,
    /** As MIMIT writes it (capitals); shown through [PlaceNames.municipality]. */
    val municipality: String,
    val province: String,
    val lat: Double,
    val lon: Double,
    /** `dataDate` of the newest data that contained the station, e.g. "2026-10-02". */
    val lastSeen: String,
) {
    val displayName: String get() = name.ifEmpty { brand }
}

/**
 * Favourite stations: pure list operations, newest first. MIMIT gives a
 * station a new id when it is registered again (e.g. a new operator), at the
 * same spot: [successor] finds it there, so the user can move the star over.
 */
object Favorites {
    fun of(station: Station, file: StationsFile): FavoriteStation = FavoriteStation(
        id = station.id,
        name = station.name,
        brand = file.brands.getOrElse(station.brand) { "" },
        municipality = station.municipality,
        province = station.province,
        lat = station.lat,
        lon = station.lon,
        lastSeen = file.dataDate,
    )

    /** Adds [station] at the top, or removes it if it is already a favourite. */
    fun toggle(list: List<FavoriteStation>, station: Station, file: StationsFile): List<FavoriteStation> =
        if (list.any { it.id == station.id }) list.filter { it.id != station.id } else listOf(of(station, file)) + list

    fun remove(list: List<FavoriteStation>, id: Int): List<FavoriteStation> = list.filter { it.id != id }

    /** Updates labels, position and [FavoriteStation.lastSeen] of the favourites present in [file]; the others stay as they were. */
    fun refresh(list: List<FavoriteStation>, file: StationsFile): List<FavoriteStation> =
        list.map { favorite -> file.station(favorite.id)?.let { of(it, file) } ?: favorite }

    fun isMissing(favorite: FavoriteStation, file: StationsFile): Boolean = file.station(favorite.id) == null

    /**
     * For a missing favourite: a station now at exactly its coordinates (one
     * with the same name first, if the spot has two registrations), or null.
     */
    fun successor(favorite: FavoriteStation, file: StationsFile): Station? {
        val here = file.stationsAt(favorite.lat, favorite.lon).filter { it.id != favorite.id }
        return here.firstOrNull { it.name.isNotEmpty() && it.name == favorite.name } ?: here.firstOrNull()
    }

    /** Replaces [old] with [station], in the same place in the list (no duplicate if it is already a favourite). */
    fun replace(list: List<FavoriteStation>, old: FavoriteStation, station: Station, file: StationsFile): List<FavoriteStation> =
        list.filter { it.id != station.id }.map { if (it.id == old.id) of(station, file) else it }
}

/** Saves the favourites as one JSON text in their own SharedPreferences file (the only one Android backs up). */
class FavoritesStore(private val prefs: SharedPreferences) {
    fun load(): List<FavoriteStation> = decode(prefs.getString(KEY_LIST, null))

    fun save(list: List<FavoriteStation>) {
        prefs.edit { putString(KEY_LIST, encode(list)) }
    }

    companion object {
        /** SharedPreferences file name; `res/xml/backup_rules.xml` and `data_extraction_rules.xml` include it. */
        const val PREFS_NAME = "favorites"
        private const val KEY_LIST = "stations"
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(list: List<FavoriteStation>): String = json.encodeToString(list)

        /** Unreadable text (should never happen) gives an empty list rather than a crash. */
        fun decode(text: String?): List<FavoriteStation> {
            if (text == null) return emptyList()
            return try {
                json.decodeFromString<List<FavoriteStation>>(text)
            } catch (e: IllegalArgumentException) { // includes SerializationException
                emptyList()
            }
        }
    }
}
