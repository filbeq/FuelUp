package io.github.filbeq.fuelup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * fixtures/prices.csv holds real rows from the 2026-09-30 MIMIT file (ids below
 * 900000) plus synthetic rows (ids 9000xx, X1, and the last three 54386 rows)
 * for quirks not present in that day's data.
 */
class PricesTest {
    private val result: PriceFile = Fixtures.prices()
    private val prices: Map<Triple<Long, String, Boolean>, Price> = result.prices.associateBy { Triple(it.stationId, it.fuelName, it.isSelf) }
    private val report = result.report

    private fun price(id: Long, fuel: String, self: Boolean) = prices.getValue(Triple(id, fuel, self))
    private fun has(id: Long, fuel: String, self: Boolean) = Triple(id, fuel, self) in prices

    @Test
    fun extractionDate() = assertEquals(LocalDate.of(2026, 9, 30), result.extractionDate)

    @Test
    fun normalRow() {
        val p = price(3464, "Benzina", true)
        assertEquals(2049L, p.priceMilli)
        assertEquals(FuelType.PETROL, p.fuel.type)
        assertTrue(p.fuel.std)
        // 20:30:08 Italian summer time is 18:30:08 UTC
        assertEquals(Instant.parse("2026-09-29T18:30:08Z"), p.updated)
        assertFalse(price(3464, "Benzina", false).isSelf)
    }

    @Test
    fun keptCount() = assertEquals(18, result.prices.size)

    @Test
    fun stalePricesDropped() {
        assertFalse(has(40820, "Metano", false)) // 12/09, 18 days old
        assertFalse(has(6422, "Hi-Q Diesel", true)) // 2013
        assertFalse(has(13829, "Benzina", false)) // 8.5 days old
        assertTrue(has(10061, "Benzina", false)) // 7.9 days old: kept
        assertTrue(has(40820, "GPL", false))
    }

    @Test
    fun minutesAfterEightAreKept() = assertTrue(has(5087, "Benzina", false))

    @Test
    fun suspiciousButPlausiblePriceIsKept() = assertEquals(1199L, price(31493, "Benzina", true).priceMilli)

    @Test
    fun specialFuelsKeepTheirName() {
        assertEquals(FuelType.DIESEL, price(7566, "Gasolio Artico", true).fuel.type)
        assertEquals(FuelType.DIESEL, price(4960, "Gasolio artico", true).fuel.type)
        assertFalse(price(3493, "HVOlution", false).fuel.std)
        assertEquals(FuelType.CNG, price(4485, "L-GNC", false).fuel.type)
        assertEquals(FuelType.LNG, price(4036, "GNL", true).fuel.type)
    }

    @Test
    fun unknownFuelIsKeptAsOtherAndNoted() {
        assertEquals(FuelType.OTHER, price(54386, "Etanolo E85", false).fuel.type)
        assertEquals(1, report.notes["unknown fuel name 'Etanolo E85' -> OTHER"])
    }

    @Test
    fun duplicatesKeepNewest() {
        assertEquals(2146L, price(54386, "Benzina", true).priceMilli)
        assertEquals(2299L, price(54386, "Gasolio", true).priceMilli)
    }

    @Test
    fun dropReasons() {
        assertEquals(
            mapOf(
                "stale price (older than 8 days)" to 4,
                "price out of plausible range" to 4,
                "update date in the future" to 1,
                "wrong number of fields" to 1,
                "invalid price" to 1,
                "invalid update date" to 1,
                "invalid self/served flag" to 1,
                "missing fuel name" to 1,
                "invalid station id" to 1,
                "duplicate price, older one dropped" to 2,
            ),
            report.dropped,
        )
        assertEquals(
            listOf("57122 Metano 0.100", "55136 Blue Super 0.100", "59612 HiQ Perform+ 0.113", "50753 Metano 4.999"),
            report.samples("price out of plausible range"),
        )
        assertEquals(35, report.rowsRead)
    }

    private fun parse(vararg lines: String) = parsePrices(lines.asSequence())

    @Test
    fun winterTimeConversion() {
        val result = parse("Estrazione del 2026-01-15", COLUMNS, "1|Benzina|1.800|1|15/01/2026 07:00:00")
        assertEquals(Instant.parse("2026-01-15T06:00:00Z"), result.prices[0].updated)
    }

    @Test
    fun dstChangeInsideAgeWindow() {
        // Clocks went back on 25/10/2026; the 8-day window spans the change.
        val result = parse(
            "Estrazione del 2026-10-30",
            COLUMNS,
            "1|Benzina|1.800|1|22/10/2026 09:30:00", // 7 days 23.5 h before: kept
            "2|Benzina|1.800|1|22/10/2026 08:30:00", // 8 days 0.5 h before: dropped
        )
        assertEquals(listOf(1L), result.prices.map { it.stationId })
    }

    @Test
    fun nanPriceIsInvalid() {
        val result = parse("Estrazione del 2026-09-30", COLUMNS, "1|Benzina|NaN|1|29/09/2026 07:00:00")
        assertEquals(1, result.report.dropped["invalid price"])
    }

    @Test(expected = FormatError::class)
    fun changedColumns() {
        parse("Estrazione del 2026-09-30", "idImpianto|carburante|prezzo|isSelf|dtComu")
    }

    // Python's number and date rules, which the Kotlin port mirrors.

    @Test
    fun priceTextLikePythonDecimal() {
        assertEquals(2049L, parsePriceMilli("2.049"))
        assertEquals(2050L, parsePriceMilli("2.0495")) // half to even
        assertEquals(2048L, parsePriceMilli("2.0485"))
        assertEquals(2000L, parsePriceMilli("2"))
        assertEquals(500L, parsePriceMilli(".5"))
        assertEquals(2000L, parsePriceMilli("2."))
        assertEquals(2000L, parsePriceMilli("2e0"))
        assertEquals(1000L, parsePriceMilli("1_0e-1"))
        assertNull(parsePriceMilli("2,049"))
        assertNull(parsePriceMilli("Infinity"))
        assertNull(parsePriceMilli(""))
        assertNull(parsePriceMilli("0x10"))
        assertNull(parsePriceMilli("2.0d"))
    }

    @Test
    fun updateTimeLikePythonStrptime() {
        assertEquals(Instant.parse("2026-09-29T07:05:09Z"), parseUpdateTime("29/9/2026 9:5:9"))
        assertNull(parseUpdateTime("31/02/2026 10:00:00"))
        assertNull(parseUpdateTime("29/09/2026 10:00:60"))
        assertNull(parseUpdateTime("29/09/0000 10:00:00"))
        assertNull(parseUpdateTime("2026-09-29 10:00:00"))
        // Skipped hour (29/03/2026 02:30 does not exist): offset before the change, like Python.
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), parseUpdateTime("29/03/2026 02:30:00"))
        // Repeated hour (25/10/2026 02:30 happens twice): the first one, like Python.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), parseUpdateTime("25/10/2026 02:30:00"))
    }

    @Test
    fun reprLikePython() {
        assertEquals("'Etanolo E85'", pyRepr("Etanolo E85"))
        assertEquals("\"Sant'Anna\"", pyRepr("Sant'Anna"))
        assertEquals("'a\\'b\"c'", pyRepr("a'b\"c"))
    }

    private companion object {
        const val COLUMNS = "idImpianto|descCarburante|prezzo|isSelf|dtComu"
    }
}
