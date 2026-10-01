package io.github.filbeq.fuelup.ui.station

import io.github.filbeq.fuelup.data.Fuel
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.Snapshot
import io.github.filbeq.fuelup.data.Station
import io.github.filbeq.fuelup.data.StationDataJson
import io.github.filbeq.fuelup.data.StationsFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StationDetailsTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/fixtures/$name"))

    private val snapshot = Snapshot(
        fixture("meta.json").use(StationDataJson::parseMeta),
        fixture("stations.json").use(StationDataJson::parseStations),
    )

    @Test
    fun motorwayStationWithSelfAndServed() {
        val details = snapshot.stationDetails(3464)!!
        assertEquals("PO EST", details.displayName)
        assertEquals("Api-Ip", details.brand)
        assertTrue(details.motorway)
        assertEquals("FERRARA", details.municipality)
        assertEquals("FE", details.province)
        assertEquals(listOf("Benzina", "Gasolio", "GPL", "Metano"), details.rows.map { it.name })

        val petrol = details.rows[0]
        assertEquals(FuelKind.PETROL, petrol.kind)
        assertEquals(2049L, petrol.self!!.priceMilli)
        assertEquals(2409L, petrol.served!!.priceMilli)
        assertEquals(Instant.parse("2026-09-29T18:30:08Z"), petrol.self!!.updated)

        val methane = details.rows[3]
        assertTrue(methane.perKg)
        assertNull(methane.self) // served only
        assertEquals(1794L, methane.served!!.priceMilli)

        assertEquals(listOf("Benzina", "Gasolio"), details.mainRows.map { it.name })
        assertEquals(2049L, details.mainRows[0].preferred!!.priceMilli) // self preferred
    }

    @Test
    fun specialFuelKeepsItsName() {
        val details = snapshot.stationDetails(54386)!!
        val ethanol = details.rows.single { it.name == "Etanolo E85" }
        assertFalse(ethanol.std)
        assertEquals(FuelKind.OTHER, ethanol.kind)
        assertEquals("Etanolo E85" to "Altro", fuelTitle(ethanol, "Altro"))
        assertEquals(1500L, ethanol.preferred!!.priceMilli) // served only → served
    }

    @Test
    fun mainPricesFallBackToFirstStandardFuel() {
        // Station 40820 only has LPG left after the pipeline's cleaning.
        val details = snapshot.stationDetails(40820)!!
        assertEquals(listOf("GPL"), details.mainRows.map { it.name })
    }

    @Test
    fun unknownStation() {
        assertNull(snapshot.stationDetails(1))
        assertNull(snapshot.stationDetails(99999))
    }

    @Test
    fun fuelTitles() {
        fun row(name: String, kind: FuelKind, std: Boolean) = FuelRow(name, kind, std, false, null, null)
        assertEquals("Benzina" to null, fuelTitle(row("Benzina", FuelKind.PETROL, true), "Benzina"))
        assertEquals("Petrol" to null, fuelTitle(row("Benzina", FuelKind.PETROL, true), "Petrol"))
        assertEquals("Metano (L-GNC)" to null, fuelTitle(row("L-GNC", FuelKind.CNG, true), "Metano"))
        assertEquals("Blue Diesel" to "Diesel", fuelTitle(row("Blue Diesel", FuelKind.DIESEL, false), "Diesel"))
    }

    @Test
    fun emptyNameFallsBackToBrandAndUnknownTypeIsOther() {
        val file = StationsFile(
            schemaVersion = 1,
            dataDate = "2026-09-30",
            brands = listOf("Q8"),
            fuels = listOf(Fuel("Idrogeno", "HYDROGEN", "KG", std = false)),
            stations = listOf(Station(7, "", 0, 0, "VIA X", "ROMA", "RM", 41.9, 12.5, listOf(longArrayOf(0, 1500, 1, 0)))),
        )
        val details = Snapshot(snapshot.meta, file).stationDetails(7)!!
        assertEquals("Q8", details.displayName)
        assertEquals(FuelKind.OTHER, details.rows[0].kind)
        assertEquals(listOf("Idrogeno"), details.mainRows.map { it.name }) // no standard fuel at all
    }
}
