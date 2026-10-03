package io.github.filbeq.fuelup.ui.station

import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.PlaceNames
import io.github.filbeq.fuelup.data.PriceEntry
import io.github.filbeq.fuelup.data.Snapshot
import io.github.filbeq.fuelup.data.Station
import io.github.filbeq.fuelup.data.StationsFile
import io.github.filbeq.fuelup.data.station
import io.github.filbeq.fuelup.data.stationsAt
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/** One price at a station. */
data class PriceInfo(val priceMilli: Long, val updated: Instant)

/** One fuel at a station, with its self-service and served prices (either may be missing). */
data class FuelRow(
    /** Name as published by MIMIT, e.g. "Benzina", "L-GNC", "Blue Diesel". */
    val name: String,
    val kind: FuelKind,
    /** Standard fuel (true) or branded/special product (false). */
    val std: Boolean,
    /** Priced per kilogram (methane, LNG) rather than per litre. */
    val perKg: Boolean,
    val self: PriceInfo?,
    val served: PriceInfo?,
) {
    /** The price to show when there's room for only one: self-service if offered. */
    val preferred: PriceInfo? get() = self ?: served
}

/** Everything the station sheet shows, computed from the cached data. */
data class StationDetails(
    val id: Int,
    /** MIMIT station name; may be empty (show [brand] instead). */
    val name: String,
    val brand: String,
    val motorway: Boolean,
    val address: String,
    val municipality: String,
    val province: String,
    val lat: Double,
    val lon: Double,
    /** One row per fuel, in the data's order (by type, standard fuel first). */
    val rows: List<FuelRow>,
    /**
     * Other stations at exactly the same coordinates (two registrations at one
     * site): their markers are drawn on top of each other, so the sheet links them.
     */
    val sameLocation: List<OtherStation> = emptyList(),
) {
    val displayName: String get() = name.ifEmpty { brand }
}

/** A station the sheet links to, by id and display name. */
data class OtherStation(val id: Int, val displayName: String)

/** Standard fuel names that are fully described by their localized type label. */
private val PLAIN_STANDARD_NAMES = setOf("benzina", "gasolio", "gpl", "metano", "gnl")

/**
 * Title and optional subtitle for a fuel row, given the localized label of its type.
 * - standard fuel: "Benzina"; with a non-plain name: "Metano (L-GNC)"
 * - special fuel: its own name, with the type underneath ("Blue Diesel" / "Gasolio")
 */
fun fuelTitle(row: FuelRow, kindLabel: String): Pair<String, String?> = when {
    !row.std -> row.name to kindLabel
    row.name.lowercase() in PLAIN_STANDARD_NAMES -> kindLabel to null
    else -> "$kindLabel (${row.name})" to null
}

/** Details of station [id], or null if it isn't in the data. */
fun Snapshot.stationDetails(id: Int): StationDetails? {
    val station = stations.station(id) ?: return null
    val sameLocation = stations.stationsAt(station.lat, station.lon)
        .filter { it.id != id }
        .map { OtherStation(it.id, it.name.ifEmpty { this.stations.brands.getOrElse(it.brand) { "" } }) }
    return station.toDetails(this.stations).copy(sameLocation = sameLocation)
}

private fun Station.toDetails(file: StationsFile): StationDetails {
    val rows = prices
        .groupBy { it[PriceEntry.FUEL].toInt() } // keeps the data's order
        .map { (fuelIndex, entries) ->
            val fuel = file.fuels[fuelIndex]
            fun price(self: Boolean) = entries.firstOrNull { (it[PriceEntry.SELF] == 1L) == self }?.let {
                PriceInfo(it[PriceEntry.PRICE_MILLI], Instant.ofEpochSecond(it[PriceEntry.UPDATED]))
            }
            FuelRow(
                name = fuel.name,
                kind = FuelKind.of(fuel.type),
                std = fuel.std,
                perKg = fuel.unit == "KG",
                self = price(self = true),
                served = price(self = false),
            )
        }
    return StationDetails(
        id = id,
        name = name,
        brand = file.brands.getOrElse(brand) { "" },
        motorway = motorway == 1,
        address = address,
        municipality = PlaceNames.municipality(municipality),
        province = province,
        lat = lat,
        lon = lon,
        rows = rows,
    )
}

/** True if [row] is the product a [FuelChoice] refers to (the standard one of its type). */
fun FuelRow.isChosen(choice: FuelChoice): Boolean = std && kind == choice.fuel

/** "3,5" / "3.5" / "20": cents (from thousandths of a euro), at most one decimal. */
fun formatCents(diffMilli: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(abs(diffMilli) / 10)
