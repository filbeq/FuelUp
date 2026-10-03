package io.github.filbeq.fuelup.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream

// Kotlin mirror of the files published by the data pipeline. The format is
// documented in pipeline/README.md ("Output format").

/** The only major schema version this app can read. */
const val SUPPORTED_SCHEMA_VERSION = 1

/** meta.json: small file describing the current stations.json. */
@Serializable
data class Meta(
    val schemaVersion: Int,
    /** Date the prices refer to, e.g. "2026-09-30". */
    val dataDate: String,
    /** Moment the prices refer to, e.g. "2026-09-30T08:00:00+02:00". */
    val pricesAt: String,
    val stations: Int,
    val prices: Int,
    val file: String,
    /** Size of stations.json in bytes (uncompressed). */
    val bytes: Long,
    val sha256: String,
)

/** stations.json: every station with its prices. */
@Serializable
data class StationsFile(
    val schemaVersion: Int,
    val dataDate: String,
    val brands: List<String>,
    val fuels: List<Fuel>,
    val stations: List<Station>,
)

@Serializable
data class Fuel(
    /** Name as published by MIMIT, e.g. "Benzina" or "Blue Diesel". */
    val name: String,
    /** PETROL, DIESEL, LPG, CNG, LNG or OTHER (kept as text: new values must not break parsing). */
    val type: String,
    /** "L" or "KG". */
    val unit: String,
    /** True for the standard fuels, false for branded/special products. */
    val std: Boolean,
)

@Serializable
class Station(
    val id: Int,
    @SerialName("n") val name: String,
    /** Index into [StationsFile.brands]. */
    @SerialName("b") val brand: Int,
    @SerialName("hw") val motorway: Int,
    @SerialName("a") val address: String,
    @SerialName("c") val municipality: String,
    @SerialName("pr") val province: String,
    val lat: Double,
    val lon: Double,
    /**
     * Prices, each `[fuelIndex, priceInMilliEuro, isSelf, updatedEpochSeconds]`.
     * Kept as primitive arrays: ~92k entries without boxing every number.
     */
    @SerialName("f") val prices: List<LongArray>,
)

/** Station [id], or null if it isn't in the data. */
fun StationsFile.station(id: Int): Station? {
    // Stations are sorted by id (the pipeline writes them that way).
    val index = stations.binarySearch { it.id.compareTo(id) }
    return if (index < 0) null else stations[index]
}

/**
 * Stations at exactly these coordinates (two registrations at one site, or a
 * station registered again under a new id). A plain scan: ~20k comparisons.
 */
fun StationsFile.stationsAt(lat: Double, lon: Double): List<Station> = stations.filter { it.lat == lat && it.lon == lon }

/** Positions inside each [Station.prices] entry. */
object PriceEntry {
    const val FUEL = 0
    const val PRICE_MILLI = 1
    const val SELF = 2
    const val UPDATED = 3
}

@OptIn(ExperimentalSerializationApi::class)
object StationDataJson {
    // Unknown keys are ignored: the pipeline may add fields without a major version bump.
    private val json = Json { ignoreUnknownKeys = true }

    fun parseMeta(input: InputStream): Meta = json.decodeFromStream(input)

    /** Streams from [input]: the ~5 MB file is never loaded as one string. */
    fun parseStations(input: InputStream): StationsFile = json.decodeFromStream(input)
}
