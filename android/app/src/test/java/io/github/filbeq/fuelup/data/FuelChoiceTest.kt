package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelChoiceTest {
    private val file = checkNotNull(javaClass.getResourceAsStream("/fixtures/stations.json"))
        .use(StationDataJson::parseStations)

    private fun station(id: Int) = file.stations.single { it.id == id }

    private fun price(id: Int, choice: FuelChoice) =
        station(id).priceFor(choice, file.standardFuelIndices(choice.fuel))?.priceMilli

    @Test
    fun defaultsAndDecoding() {
        assertEquals(FuelChoice(FuelKind.PETROL, ServiceMode.SELF), FuelChoice.Default)
        assertEquals(FuelChoice.Default, FuelChoice.decode(null, null))
        assertEquals(FuelChoice(FuelKind.LPG, ServiceMode.SERVED), FuelChoice.decode("LPG", "SERVED"))
        assertEquals(FuelChoice.Default, FuelChoice.decode("OTHER", "SOMETIMES")) // not selectable / unknown
    }

    @Test
    fun modeAppliesOnlyToPetrolAndDiesel() {
        assertTrue(FuelChoice(FuelKind.PETROL, ServiceMode.SELF).modeApplies)
        assertTrue(FuelChoice(FuelKind.DIESEL, ServiceMode.SERVED).modeApplies)
        assertFalse(FuelChoice(FuelKind.LPG, ServiceMode.SELF).modeApplies)
        assertFalse(FuelChoice(FuelKind.CNG, ServiceMode.SELF).modeApplies)
    }

    @Test
    fun pricesForChoices() {
        // 3464: Benzina 2.409 served / 2.049 self, GPL 0.849 served, Metano 1.794 served.
        assertEquals(2049L, price(3464, FuelChoice(FuelKind.PETROL, ServiceMode.SELF)))
        assertEquals(2409L, price(3464, FuelChoice(FuelKind.PETROL, ServiceMode.SERVED)))
        // GPL is served-only here, but the mode doesn't apply to GPL: still found.
        assertEquals(849L, price(3464, FuelChoice(FuelKind.LPG, ServiceMode.SELF)))
        assertEquals(1794L, price(3464, FuelChoice(FuelKind.CNG, ServiceMode.SELF)))
        assertNull(price(3464, FuelChoice(FuelKind.LNG, ServiceMode.SELF)))
        // 54386: self only for Benzina → nothing served.
        assertNull(price(54386, FuelChoice(FuelKind.PETROL, ServiceMode.SERVED)))
    }

    @Test
    fun specialProductsDoNotCount() {
        // 54386 sells "Etanolo E85" (OTHER, not standard): no fuel choice picks it up.
        FuelChoice.SELECTABLE.forEach { kind ->
            assertTrue(file.standardFuelIndices(kind).none { file.fuels[it].name == "Etanolo E85" })
        }
    }
}
