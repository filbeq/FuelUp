package io.github.filbeq.fuelup.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the files produced by the data pipeline from its own test fixtures
 * (regenerate with pipeline/scripts/make_android_fixture.py), so a format change
 * on either side breaks this test.
 */
class StationDataTest {
    private fun fixture(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }

    @Test
    fun parsesMeta() {
        val meta = fixture("meta.json").use(StationDataJson::parseMeta)
        assertEquals(SUPPORTED_SCHEMA_VERSION, meta.schemaVersion)
        assertEquals("2026-09-30", meta.dataDate)
        assertEquals("2026-09-30T08:00:00+02:00", meta.pricesAt)
        assertEquals(3, meta.stations)
        assertEquals(10, meta.prices)
        assertEquals(64, meta.sha256.length)
    }

    @Test
    fun parsesStations() {
        val file = fixture("stations.json").use(StationDataJson::parseStations)
        assertEquals(1, file.schemaVersion)
        assertEquals("2026-09-30", file.dataDate)
        assertEquals(listOf("Api-Ip", "Pompe Bianche"), file.brands)
        assertEquals(Fuel("Benzina", "PETROL", "L", std = true), file.fuels[0])
        assertEquals(Fuel("Metano", "CNG", "KG", std = true), file.fuels[3])
        assertFalse(file.fuels[4].std)
        assertEquals(listOf(3464, 40820, 54386), file.stations.map { it.id })

        val station = file.stations[0]
        assertEquals("PO EST", station.name)
        assertEquals("Api-Ip", file.brands[station.brand])
        assertEquals(1, station.motorway)
        assertEquals("FERRARA", station.municipality)
        assertEquals("FE", station.province)
        assertEquals(44.88012, station.lat, 0.0)
        assertEquals(11.57083, station.lon, 0.0)
        assertEquals(6, station.prices.size)
        // Benzina, 2.409 €, served, 29/09/2026 20:30:07 Italian time
        assertArrayEquals(longArrayOf(0, 2409, 0, 1790706607), station.prices[0])
        assertEquals(10, file.stations.sumOf { it.prices.size })
    }

    @Test
    fun ignoresUnknownKeys() {
        val json = """{"schemaVersion":1,"dataDate":"d","pricesAt":"p","stations":0,"prices":0,
            "file":"stations.json","bytes":0,"sha256":"x","newField":{"a":[1,2]}}"""
        val meta = json.byteInputStream().use(StationDataJson::parseMeta)
        assertEquals(1, meta.schemaVersion)
    }

    @Test
    fun priceEntryPositions() {
        val entry = longArrayOf(4, 1500, 1, 1790676000)
        assertEquals(4L, entry[PriceEntry.FUEL])
        assertEquals(1500L, entry[PriceEntry.PRICE_MILLI])
        assertTrue(entry[PriceEntry.SELF] == 1L)
        assertEquals(1790676000L, entry[PriceEntry.UPDATED])
    }
}
