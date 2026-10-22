package io.github.filbeq.fuelup.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/* Parse the daily price file (prezzo_alle_8.csv). */

val PRICE_COLUMNS = listOf("idImpianto", "descCarburante", "prezzo", "isSelf", "dtComu")
val ITALY_TZ: ZoneId = ZoneId.of("Europe/Rome")

/** Prices are those in force at 08:00 (Italian time) on the extraction date. */
private val PRICES_AT = LocalTime.of(8, 0)
private val MAX_AGE = Duration.ofDays(8)
/** A few minutes past 08:00 is normal (extraction lag); a day ahead is a clock error. */
private val MAX_AHEAD = Duration.ofDays(1)

/**
 * Plausible range per fuel type, in thousandths of a euro per litre (or per kg).
 * Wide on purpose: they only catch typos such as 0.100 or 8.888.
 */
val PRICE_BOUNDS: Map<FuelType, LongRange> = mapOf(
    FuelType.PETROL to 800L..4000L,
    FuelType.DIESEL to 800L..4000L,
    FuelType.LPG to 300L..2000L,
    FuelType.CNG to 500L..4000L,
    FuelType.LNG to 500L..4000L,
    FuelType.OTHER to 300L..4000L,
)

data class Price(
    val stationId: Long,
    val fuelName: String,
    val fuel: FuelInfo,
    /** 2409 means 2.409 €. */
    val priceMilli: Long,
    val isSelf: Boolean,
    val updated: Instant,
)

data class PriceFile(
    val extractionDate: LocalDate,
    val prices: List<Price>,
    val report: DropReport,
)

/** The moment the published prices refer to. */
fun pricesAt(extractionDate: LocalDate): ZonedDateTime = ZonedDateTime.of(extractionDate, PRICES_AT, ITALY_TZ)

fun parsePrices(lines: Sequence<String>): PriceFile {
    val iterator = lines.iterator()
    val extractionDate = parseHeader(iterator, PRICE_COLUMNS)
    val reference = pricesAt(extractionDate).toInstant()
    val report = DropReport("prices")
    // One price per (station, fuel name, self/served): the most recently updated.
    val latest = LinkedHashMap<Triple<Long, String, Boolean>, Price>()

    for (line in iterator) {
        if (line.stripSpace().isEmpty()) continue
        report.rowsRead++
        val fields = line.split(SEPARATOR).map(::cleanText)
        val ident = fields[0]
        if (fields.size != PRICE_COLUMNS.size) {
            report.drop("wrong number of fields", ident)
            continue
        }

        val price = parseRow(fields, report) ?: continue

        if (price.updated < reference - MAX_AGE) {
            report.drop("stale price (older than 8 days)", ident)
            continue
        }
        if (price.updated > reference + MAX_AHEAD) {
            report.drop("update date in the future", ident)
            continue
        }
        if (price.priceMilli !in PRICE_BOUNDS.getValue(price.fuel.type)) {
            report.drop("price out of plausible range", "$ident ${price.fuelName} ${fields[2]}")
            continue
        }
        if (!price.fuel.known) {
            report.note("unknown fuel name ${pyRepr(price.fuelName)} -> ${price.fuel.type}", ident)
        }

        val key = Triple(price.stationId, price.fuelName, price.isSelf)
        val previous = latest[key]
        if (previous != null) {
            report.drop("duplicate price, older one dropped", ident)
            if (previous.updated >= price.updated) continue
        }
        latest[key] = price
    }

    return PriceFile(extractionDate, latest.values.toList(), report)
}

private fun parseRow(fields: List<String>, report: DropReport): Price? {
    val (ident, fuelName, priceText, selfText, dateText) = fields
    val stationId = parsePyInt(ident) ?: run {
        report.drop("invalid station id", ident)
        return null
    }
    if (fuelName.isEmpty()) {
        report.drop("missing fuel name", ident)
        return null
    }
    val priceMilli = parsePriceMilli(priceText) ?: run {
        report.drop("invalid price", ident)
        return null
    }
    if (selfText != "0" && selfText != "1") {
        report.drop("invalid self/served flag", ident)
        return null
    }
    val updated = parseUpdateTime(dateText) ?: run {
        report.drop("invalid update date", ident)
        return null
    }
    return Price(
        stationId = stationId,
        fuelName = fuelName,
        fuel = classifyFuel(fuelName),
        priceMilli = priceMilli,
        isSelf = selfText == "1",
        updated = updated,
    )
}

private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)

/**
 * Price text ("2.049") to thousandths of a euro, rounded half to even as Python's
 * Decimal does; null when it isn't a number. Values beyond Long are clamped: they
 * are out of every plausible range anyway.
 */
internal fun parsePriceMilli(text: String): Long? {
    if (!PY_NUMBER.matches(text)) return null
    val milli = try {
        BigDecimal(text.replace("_", "")).movePointRight(3).setScale(0, RoundingMode.HALF_EVEN).toBigInteger()
    } catch (e: ArithmeticException) {
        return null
    }
    return milli.max(LONG_MIN).min(LONG_MAX).toLong()
}

// "%d/%m/%Y %H:%M:%S" as Python's strptime reads it (single digits allowed).
private val UPDATE_TIME = Regex(
    """(3[01]|[12][0-9]|0[1-9]|[1-9])/(1[0-2]|0[1-9]|[1-9])/([0-9]{4}) """ +
        """(2[0-3]|[01][0-9]|[0-9]):([0-5][0-9]|[0-9]):(6[01]|[0-5][0-9]|[0-9])"""
)

/**
 * Italian local time of a report ("29/09/2026 20:30:08") as an instant. A time
 * skipped or repeated by a DST change resolves like Python (fold=0) and Java's
 * default: the offset in force before the change.
 */
internal fun parseUpdateTime(text: String): Instant? {
    val m = UPDATE_TIME.matchEntire(text) ?: return null
    val (d, mo, y, h, mi, s) = m.destructured
    if (y.toInt() == 0) return null // Python has no year 0
    return try {
        val local = LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
        ZonedDateTime.ofLocal(local, ITALY_TZ, null).toInstant()
    } catch (e: DateTimeException) {
        null // e.g. 31/02, second 60 or 61
    }
}

/** Python's `repr()` of a string, for log messages. */
internal fun pyRepr(s: String): String {
    val quote = if ('\'' in s && '"' !in s) '"' else '\''
    val body = buildString {
        for (c in s) {
            when {
                c == '\\' -> append("\\\\")
                c == quote -> append('\\').append(c)
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' || c == '\u007f' -> append("\\x%02x".format(c.code))
                else -> append(c)
            }
        }
    }
    return "$quote$body$quote"
}
