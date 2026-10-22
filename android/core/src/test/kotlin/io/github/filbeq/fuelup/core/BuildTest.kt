package io.github.filbeq.fuelup.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** End-to-end tests on the fixture files: join, document and written files. */
class BuildTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val stations = Fixtures.stations()
    private val prices = Fixtures.prices()
    private val result = build(stations, prices, Fixtures.NO_LIMITS)
    private val doc = result.document

    private fun epoch(iso: String) = Instant.parse(iso).epochSecond

    @Test
    fun header() = assertEquals(LocalDate.of(2026, 9, 30), doc.dataDate)

    @Test
    fun onlyStationsWithPricesAreKept() =
        assertEquals(listOf<Long>(3464, 40820, 54386), doc.stations.map { it.first.id })

    @Test
    fun lookupTables() {
        assertEquals(listOf("Api-Ip", "Pompe Bianche"), doc.brands)
        assertEquals(
            listOf(
                "Benzina PETROL L true", "Gasolio DIESEL L true", "GPL LPG L true",
                "Metano CNG KG true", "Etanolo E85 OTHER L false",
            ),
            doc.fuels.map { "${it.name} ${it.info.type} ${it.info.unit.code} ${it.info.std}" },
        )
    }

    @Test
    fun stationRecord() {
        val (station, entries) = doc.stations[0]
        assertEquals("PO EST", station.name)
        val t0 = epoch("2026-09-29T18:30:06Z")
        val t1 = epoch("2026-09-29T18:30:07Z")
        val t2 = epoch("2026-09-29T18:30:08Z")
        assertArrayEquals(
            arrayOf(
                longArrayOf(0, 2409, 0, t1),
                longArrayOf(0, 2049, 1, t2),
                longArrayOf(1, 2609, 0, t1),
                longArrayOf(1, 2249, 1, t2),
                longArrayOf(2, 849, 0, t0),
                longArrayOf(3, 1794, 0, t0),
            ),
            entries.toTypedArray(),
        )
    }

    @Test
    fun recoveredStationAndSpecialFuel() {
        val (s, entries) = doc.stations[2]
        assertEquals("PRADELLI - MONTEOMBRARO", s.name)
        assertEquals("Pompe Bianche", s.brand)
        assertEquals(listOf(0L, 1L, 4L), entries.map { it[0] })
    }

    @Test
    fun joinReport() {
        assertEquals(8, result.joinReport.dropped["price for unknown station"])
        assertEquals(9, result.joinReport.dropped["station without valid prices"])
    }

    @Test
    fun extractionDatesMustMatch() {
        val e = assertThrows(PipelineError::class.java) {
            build(stations, prices.copy(extractionDate = LocalDate.of(2026, 9, 29)), Fixtures.NO_LIMITS)
        }
        assertTrue(e.message!!.startsWith("extraction dates differ"))
    }

    @Test
    fun tooFewStations() {
        val e = assertThrows(PipelineError::class.java) {
            build(stations, prices, Fixtures.NO_LIMITS.copy(minStations = 4))
        }
        assertTrue(e.message!!.startsWith("too little data"))
    }

    @Test
    fun tooFewPrices() {
        val e = assertThrows(PipelineError::class.java) {
            build(stations, prices, Fixtures.NO_LIMITS.copy(minPrices = 100))
        }
        assertTrue(e.message!!.startsWith("too little data"))
    }

    @Test
    fun tooManyDroppedRows() {
        // 8 of 20 station rows are dropped in the fixture.
        val e = assertThrows(PipelineError::class.java) {
            build(stations, prices, Fixtures.NO_LIMITS.copy(maxDropRatio = 0.3))
        }
        assertEquals("stations: 8 of 20 rows dropped", e.message)
    }

    /**
     * The files match, byte for byte, the app's test fixture, which the Python
     * pipeline generated: the published format is unchanged.
     */
    @Test
    fun outputMatchesTheAppFixture() {
        val out = tmp.newFolder()
        val meta = writeOutput(doc, out, generatedAt = Instant.parse("2026-10-01T07:12:03Z"))
        val golden = File("../app/src/test/resources/fixtures")
        assertArrayEquals(File(golden, "stations.json").readBytes(), File(out, "stations.json").readBytes())
        assertEquals(File(golden, "meta.json").readText(), File(out, "meta.json").readText())
        assertEquals(listOf("meta.json", "stations.json"), out.list()!!.sorted())
        assertEquals(3, meta.stations)
        assertEquals(10, meta.prices)
    }

    @Test
    fun coordinatesLikePythonRepr() {
        assertEquals("44.88012", coordJson(44.88012))
        assertEquals("13.0", coordJson(13.0))
        assertEquals("7.62133", coordJson(roundCoord(7.6213346)))
    }

    /** The fast rounding and formatting against exact decimal arithmetic. */
    @Test
    fun coordinatesMatchExactArithmetic() {
        val random = java.util.Random(42)
        val values = List(1_000_000) { 6.0 + random.nextDouble() * 42.0 } +
            // Halfway in decimal (but not in binary): the slow path.
            List(10_000) { (600_000 + random.nextInt(4_200_000)) / 100_000.0 + 0.000005 }
        for (x in values) {
            val exact = java.math.BigDecimal(x).setScale(5, java.math.RoundingMode.HALF_EVEN)
            val rounded = roundCoord(x)
            assertEquals("$x", exact.toDouble(), rounded, 0.0)
            val text = exact.stripTrailingZeros().toPlainString().let { if ('.' in it) it else "$it.0" }
            assertEquals("$x", text, coordJson(rounded))
        }
    }

    @Test
    fun jsonStringsLikePython() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001é–\"", jsonString("a\"b\\c\n\t\u0001é–"))
        val bytes = ByteArrayOutputStream().also { writeStationsJson(doc, it) }.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }
}
