package io.github.filbeq.fuelup.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/* Parse the station registry (anagrafica_impianti_attivi.csv). */

val STATION_COLUMNS = listOf(
    "idImpianto", "Gestore", "Bandiera", "Tipo Impianto", "Nome Impianto",
    "Indirizzo", "Comune", "Provincia", "Latitudine", "Longitudine",
)
private val STATION_TYPES = setOf("Stradale", "Autostradale")
private const val MOTORWAY = "Autostradale"

// Generous bounding box around Italy, San Marino and Vatican City.
private const val LAT_MIN = 35.0
private const val LAT_MAX = 47.2
private const val LON_MIN = 6.5
private const val LON_MAX = 18.6
private const val COORD_DECIMALS = 5 // ~1 m, plenty for a map pin

// Misplaced stations: coordinates are entered by operators and sometimes copied
// from another registration (e.g. a Sorrento station drawn on top of one in
// Ventimiglia). A station is dropped when no station of its own province is
// within MISPLACED_KM, but a station of another province is MISPLACED_RATIO
// times closer than the nearest one of its own. Real islands (Ustica, Linosa,
// ...) pass: nothing of another province is near them either.
private const val MISPLACED_KM = 25.0
private const val MISPLACED_RATIO = 3.0
private const val KM_PER_DEG_LAT = 111.2
// Grid cells at least MISPLACED_KM wide (in longitude, at Italy's northern tip),
// so the 3x3 cells around a station hold every station within MISPLACED_KM.
private const val CELL_DEG = 0.35

// Some station names carry a junk " | gestori.prezzibenzina.it" suffix, which
// also breaks the column count (the "|" is the separator).
private val JUNK_RE = Regex(
    """$WHITESPACE_CLASS*\|$WHITESPACE_CLASS*gestori\.prezzibenzina\.it""",
    setOf(RegexOption.IGNORE_CASE),
)

data class Station(
    val id: Long,
    val name: String,
    val brand: String,
    val motorway: Boolean,
    val address: String,
    val municipality: String,
    val province: String,
    /** Rounded to 5 decimals. */
    val lat: Double,
    val lon: Double,
)

data class StationFile(
    val extractionDate: LocalDate,
    /** By id, in file order. */
    val stations: Map<Long, Station>,
    val report: DropReport,
)

fun parseStations(lines: Sequence<String>): StationFile {
    val iterator = lines.iterator()
    val extractionDate = parseHeader(iterator, STATION_COLUMNS)
    val report = DropReport("stations")
    val stations = LinkedHashMap<Long, Station>()

    for (line in iterator) {
        if (line.isBlankSpace()) continue
        report.rowsRead++
        var fields = line.split(SEPARATOR)
        val ident = cleanText(fields[0])

        if (fields.size != STATION_COLUMNS.size) {
            fields = recoverFields(fields) ?: run {
                report.drop("wrong number of fields", ident)
                null
            } ?: continue
            report.note("extra separators recovered", ident)
        }

        val stationId = parsePyInt(ident) ?: run {
            report.drop("invalid station id", ident)
            null
        } ?: continue

        val coords = when (val parsed = parseCoords(fields[8], fields[9])) {
            is Coords.Ok -> parsed
            is Coords.Bad -> {
                report.drop(parsed.reason, stationId)
                continue
            }
        }

        if (stationId in stations) {
            report.drop("duplicate station id", stationId)
            continue
        }

        // The junk suffix always comes with a "|": skip the (slow) regex otherwise.
        val name = cleanText(if ('|' in fields[4]) fields[4].replace(JUNK_RE, "") else fields[4])
        if (name.isEmpty()) report.note("empty station name", stationId)
        stations[stationId] = Station(
            id = stationId,
            name = name,
            brand = cleanText(fields[2]),
            motorway = cleanText(fields[3]) == MOTORWAY,
            address = cleanText(fields[5]),
            municipality = cleanText(fields[6]),
            province = cleanText(fields[7]),
            lat = coords.lat,
            lon = coords.lon,
        )
    }

    dropMisplaced(stations, report)
    return StationFile(extractionDate, stations, report)
}

/** Drop stations drawn in another province (see MISPLACED_KM). */
private fun dropMisplaced(stations: LinkedHashMap<Long, Station>, report: DropReport) {
    val byProvince = HashMap<String, MutableList<Station>>()
    val cells = HashMap<Triple<String, Int, Int>, MutableList<Station>>()
    for (s in stations.values) {
        byProvince.getOrPut(s.province) { mutableListOf() }.add(s)
        cells.getOrPut(Triple(s.province, row(s), col(s))) { mutableListOf() }.add(s)
    }

    class Misplaced(val station: Station, val own: Double, val other: Double)
    val misplaced = mutableListOf<Misplaced>()
    for (s in stations.values) {
        val row = row(s)
        val col = col(s)
        // Most stations have one of their own province nearby: cheap check first.
        val hasNear = (row - 1..row + 1).any { r ->
            (col - 1..col + 1).any { c ->
                cells[Triple(s.province, r, c)].orEmpty().any { o ->
                    otherSpot(s, o) && latKm(s, o) <= MISPLACED_KM && km(s, o) <= MISPLACED_KM
                }
            }
        }
        if (hasNear) continue
        // The only station of its province: nothing to compare with.
        val own = nearestKm(s, byProvince.getValue(s.province)) { otherSpot(s, it) } ?: continue
        val other = nearestKm(s, stations.values) { it.province != s.province }
        if (other != null && other * MISPLACED_RATIO < own) misplaced.add(Misplaced(s, own, other))
    }

    for (m in misplaced) {
        val s = m.station
        stations.remove(s.id)
        report.drop(
            "coordinates in another province",
            "${s.id} ${s.municipality} (${s.province}): ${"%.0f".format(java.util.Locale.ROOT, m.own)} km " +
                "from own province, ${"%.1f".format(java.util.Locale.ROOT, m.other)} km from another",
        )
    }
}

/** A different station at different coordinates (a copy of s's own doesn't count). */
private fun otherSpot(s: Station, o: Station): Boolean = o.id != s.id && (o.lat != s.lat || o.lon != s.lon)

private fun row(s: Station): Int = floor(s.lat / CELL_DEG).toInt()
private fun col(s: Station): Int = floor(s.lon / CELL_DEG).toInt()

/**
 * Distance from [s] to the nearest of [candidates] that pass [filter], or null.
 * Skips a candidate whose latitude difference alone exceeds the best so far:
 * its distance can't be smaller ([km] is never below [latKm]), so the result is
 * exactly the minimum, with far fewer [km] calls (cos and hypot are slow on Android).
 */
private inline fun nearestKm(s: Station, candidates: Collection<Station>, filter: (Station) -> Boolean): Double? {
    var best = Double.POSITIVE_INFINITY
    var found = false
    for (o in candidates) {
        if (!filter(o)) continue
        found = true
        if (latKm(s, o) > best) continue
        val d = km(s, o)
        if (d < best) best = d
    }
    return if (found) best else null
}

/**
 * The north-south part of [km], computed the same way: [km] is never smaller,
 * since hypot(x, y) >= |y| holds exactly in floating point too.
 */
private fun latKm(a: Station, b: Station): Double = abs((a.lat - b.lat) * KM_PER_DEG_LAT)

/** Distance in km; a flat approximation, fine at these scales. */
private fun km(a: Station, b: Station): Double {
    val x = (a.lon - b.lon) * KM_PER_DEG_LAT * cos(Math.toRadians((a.lat + b.lat) / 2))
    val y = (a.lat - b.lat) * KM_PER_DEG_LAT
    return hypot(x, y)
}

/**
 * Rebuild a row that has extra "|" inside its text fields.
 *
 * The station type (Stradale/Autostradale) is used as an anchor: Gestore sits
 * between the id and Bandiera, which is right before the type. The last five
 * fields (Indirizzo, Comune, Provincia, lat, lon) never contain "|" in the data
 * seen so far, so whatever is left between the type and them is the name.
 */
private fun recoverFields(fields: List<String>): List<String>? {
    if (fields.size < STATION_COLUMNS.size) return null
    val anchors = fields.indices.filter { fields[it].stripSpace() in STATION_TYPES }
    if (anchors.size != 1) return null
    val t = anchors[0]
    if (t < 3 || t > fields.size - 7) return null
    return listOf(
        fields[0],
        fields.subList(1, t - 1).joinToString(SEPARATOR),
        fields[t - 1],
        fields[t],
        fields.subList(t + 1, fields.size - 5).joinToString(SEPARATOR),
    ) + fields.takeLast(5)
}

private sealed interface Coords {
    class Ok(val lat: Double, val lon: Double) : Coords
    class Bad(val reason: String) : Coords
}

private fun parseCoords(latField: String, lonField: String): Coords {
    val latText = latField.stripSpace()
    val lonText = lonField.stripSpace()
    if (latText.isEmpty() || lonText.isEmpty()) return Coords.Bad("missing coordinates")
    val lat: Double = parsePyFloat(latText) ?: return Coords.Bad("invalid coordinates")
    val lon: Double = parsePyFloat(lonText) ?: return Coords.Bad("invalid coordinates")
    if (!lat.isFinite() || !lon.isFinite()) return Coords.Bad("invalid coordinates")
    if (lat == 0.0 || lon == 0.0) return Coords.Bad("zero coordinates")
    if (lat !in LAT_MIN..LAT_MAX || lon !in LON_MIN..LON_MAX) return Coords.Bad("coordinates outside Italy")
    return Coords.Ok(roundCoord(lat), roundCoord(lon))
}

/** Python's `float()` on stripped text; inf/nan come back non-finite. */
private fun parsePyFloat(text: String): Double? {
    if (isPyNumber(text)) return text.replace("_", "").toDouble()
    return when (text.lowercase(java.util.Locale.ROOT).removePrefix("+").removePrefix("-")) {
        "inf", "infinity" -> Double.POSITIVE_INFINITY
        "nan" -> Double.NaN
        else -> null
    }
}

/**
 * Python's `round(x, 5)`: half to even on the exact binary value, giving the
 * double nearest to the rounded decimal. `x * 1e5` is off by far less than
 * 1e-6, so unless it lies that close to a half, its nearest integer is the
 * exact answer; only those rare cases need BigDecimal (slow on Android).
 */
internal fun roundCoord(x: Double): Double {
    val scaled = x * COORD_SCALE
    val floor = floor(scaled)
    val fraction = scaled - floor
    if (abs(fraction - 0.5) < 1e-6 || abs(scaled) > 1e15) {
        return BigDecimal(x).setScale(COORD_DECIMALS, RoundingMode.HALF_EVEN).toDouble()
    }
    val units = if (fraction < 0.5) floor else floor + 1
    return units / COORD_SCALE // exact operands: the double nearest to units / 100000
}

private const val COORD_SCALE = 1e5
