package io.github.filbeq.fuelup.core

import io.github.filbeq.fuelup.core.FuelType.CNG
import io.github.filbeq.fuelup.core.FuelType.DIESEL
import io.github.filbeq.fuelup.core.FuelType.LNG
import io.github.filbeq.fuelup.core.FuelType.LPG
import io.github.filbeq.fuelup.core.FuelType.OTHER
import io.github.filbeq.fuelup.core.FuelType.PETROL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelsTest {
    // Every descCarburante value seen in the 2026-09-30 MIMIT file, spelled as published.
    private val realNames = mapOf(
        "Benzina" to PETROL, "Gasolio" to DIESEL, "GPL" to LPG, "Metano" to CNG, "GNL" to LNG, "L-GNC" to CNG,
        "Blue Diesel" to DIESEL, "HVOlution" to DIESEL, "Supreme Diesel" to DIESEL, "Blue Super" to PETROL,
        "HVO" to DIESEL, "Hi-Q Diesel" to DIESEL, "Gasolio speciale" to DIESEL, "HiQ Perform+" to PETROL,
        "Gasolio Premium" to DIESEL, "Benzina speciale" to PETROL, "Benzina WR 100" to PETROL,
        "Diesel Shell V Power" to DIESEL, "DieselMax" to DIESEL, "HVO100" to DIESEL, "S-Diesel" to DIESEL,
        "Gasolio artico" to DIESEL, "Benzina Shell V Power" to PETROL, "Excellium Diesel" to DIESEL,
        "Gasolio Oro Diesel" to DIESEL, "REHVO" to DIESEL, "F101" to OTHER, "Diesel HVO" to DIESEL,
        "Gasolio Energy D" to DIESEL, "E-DIESEL" to DIESEL, "Gasolio Ecoplus" to DIESEL, "GP DIESEL" to DIESEL,
        "Gasolio Artico" to DIESEL, "Benzina Energy 98 ottani" to PETROL, "BCHVO" to DIESEL,
        "Gasolio Bio HVO" to DIESEL, "Benzina Plus 98" to PETROL, "Gasolio HVO" to DIESEL,
        "Gasolio Gelo" to DIESEL, "Gasolio Alpino" to DIESEL, "V-Power" to PETROL, "Excellium diesel" to DIESEL,
        "HVO Future" to DIESEL, "HVO eco diesel" to DIESEL, "Blu Diesel Alpino" to DIESEL,
        "HiQ Perform B100 Ottani" to PETROL, "Gasolio Prestazionale" to DIESEL, "Gasolio Plus" to DIESEL,
        "Diesel HVO Energy" to DIESEL, "Verde speciale" to PETROL, "HVOvolution" to DIESEL,
        "HVO Energy Diesel" to DIESEL, "Benzina Speciale 98 Ottani" to PETROL, "F-101" to OTHER,
        "Gasolio Artico Igloo" to DIESEL, "V-Power Diesel" to DIESEL, "Benzina 100 ottani" to PETROL,
        "GASOLIO HVO" to DIESEL, "Benzina 102 Ottani" to PETROL,
    )

    @Test
    fun allRealNamesAreKnownAndTyped() {
        for ((name, expected) in realNames) {
            val info = classifyFuel(name)
            assertEquals(name, expected, info.type)
            assertTrue(name, info.known)
        }
    }

    @Test
    fun standardFuels() {
        for (name in listOf("Benzina", "Gasolio", "GPL", "Metano", "GNL", "L-GNC")) {
            assertTrue(name, classifyFuel(name).std)
        }
        assertFalse(classifyFuel("Blue Diesel").std)
        assertFalse(classifyFuel("HVO").std)
    }

    @Test
    fun units() {
        assertEquals(FuelUnit.LITRE, classifyFuel("Benzina").unit)
        assertEquals(FuelUnit.LITRE, classifyFuel("GPL").unit)
        assertEquals(FuelUnit.KILOGRAM, classifyFuel("Metano").unit)
        assertEquals(FuelUnit.KILOGRAM, classifyFuel("L-GNC").unit)
        assertEquals(FuelUnit.KILOGRAM, classifyFuel("GNL").unit)
    }

    @Test
    fun caseAndWhitespaceAreIgnored() {
        assertEquals(DIESEL, classifyFuel("  gasolio   ARTICO ").type)
        assertTrue(classifyFuel("BENZINA").std)
    }

    @Test
    fun unknownNameUsesKeywordsAndIsFlagged() {
        val info = classifyFuel("Super Diesel Xtra")
        assertEquals(DIESEL, info.type) // diesel rule wins over "super"
        assertFalse(info.known)
        assertFalse(info.std)
        assertEquals(CNG, classifyFuel("Metano Bio").type)
        assertEquals(FuelUnit.KILOGRAM, classifyFuel("Metano Bio").unit)
    }

    @Test
    fun unconfirmedKnownNameIsOther() {
        val info = classifyFuel("F101")
        assertEquals(OTHER, info.type)
        assertEquals(FuelUnit.LITRE, info.unit)
        assertTrue(info.known) // deliberate, so not logged as unknown
        assertFalse(info.std)
    }

    @Test
    fun unrecognizableNameIsOther() {
        val info = classifyFuel("AdBlue")
        assertEquals(OTHER, info.type)
        assertFalse(info.known)
    }
}
