package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceRankingTest {
    // Fuel table: 0 = Benzina (standard petrol), 1 = Blue Super (special petrol), 2 = GPL, 3 = Metano, 4 = L-GNC.
    private val fuels = listOf(
        Fuel("Benzina", "PETROL", "L", std = true),
        Fuel("Blue Super", "PETROL", "L", std = false),
        Fuel("GPL", "LPG", "L", std = true),
        Fuel("Metano", "CNG", "KG", std = true),
        Fuel("L-GNC", "CNG", "KG", std = true),
    )
    private var nextId = 1

    /** A station [eastKm] east of a point near Rome, selling [entries] = (fuel, price, self). */
    private fun station(
        eastKm: Double,
        vararg entries: Triple<Int, Long, Int>,
        motorway: Boolean = false,
        municipality: String = "ROMA",
    ) = Station(
        id = nextId++, name = "", brand = 0, motorway = if (motorway) 1 else 0,
        address = "", municipality = municipality, province = "RM",
        lat = 41.9, lon = 12.5 + eastKm / (111.2 * 0.7445), // 1 km east at this latitude
        prices = entries.map { (fuel, price, self) -> longArrayOf(fuel.toLong(), price, self.toLong(), 0) },
    )

    private fun petrolSelf(price: Long) = Triple(0, price, 1)

    /** [count] petrol-self stations at [price], spread 0.1 km apart starting [startKm] east. */
    private fun neighbours(count: Int, price: Long, startKm: Double = 1.0, motorway: Boolean = false, municipality: String = "ROMA") =
        List(count) { station(startKm + it * 0.1, petrolSelf(price), motorway = motorway, municipality = municipality) }

    private fun rank(stations: List<Station>, choice: FuelChoice = FuelChoice.Default): Map<Int, RankedPrice> {
        val file = StationsFile(1, "2026-09-30", listOf("X"), fuels, stations)
        return PriceRanking.rank(stations, choice, file.standardFuelIndices(choice.fuel))
    }

    @Test
    fun twoCentBand() {
        val cheap = station(0.0, petrolSelf(1980))      // exactly 2 c below
        val average = station(0.01, petrolSelf(1981))   // 1.9 c below
        val expensive = station(0.02, petrolSelf(2020)) // exactly 2 c above
        val result = rank(neighbours(25, 2000) + cheap + average + expensive)
        assertEquals(PriceClass.CHEAP, result.getValue(cheap.id).priceClass)
        assertEquals(PriceClass.AVERAGE, result.getValue(average.id).priceClass)
        assertEquals(PriceClass.EXPENSIVE, result.getValue(expensive.id).priceClass)
        assertEquals(-20.0, result.getValue(cheap.id).diffFromMedianMilli!!, 0.0)
    }

    @Test
    fun outliersAreToVerifyNeverCheap() {
        val flagged = station(0.0, petrolSelf(1650))  // exactly 35 c below
        val justCheap = station(0.01, petrolSelf(1651))
        val result = rank(neighbours(25, 2000) + flagged + justCheap)
        assertEquals(PriceClass.TO_VERIFY, result.getValue(flagged.id).priceClass)
        assertEquals(PriceClass.CHEAP, result.getValue(justCheap.id).priceClass)
    }

    @Test
    fun thresholdsArePerFuel() {
        val lpg = FuelChoice(FuelKind.LPG, ServiceMode.SELF)
        val others = List(25) { station(1.0 + it * 0.1, Triple(2, 750L, 0)) }
        val flagged = station(0.0, Triple(2, 550L, 0))  // 20 c below: LPG's limit
        val cheap = station(0.01, Triple(2, 551L, 0))
        val result = rank(others + flagged + cheap, lpg)
        assertEquals(PriceClass.TO_VERIFY, result.getValue(flagged.id).priceClass)
        assertEquals(PriceClass.CHEAP, result.getValue(cheap.id).priceClass)
        assertEquals(200, PriceRanking.THRESHOLDS.getValue(FuelKind.LPG).outlierMilli)
        assertEquals(500, PriceRanking.THRESHOLDS.getValue(FuelKind.CNG).outlierMilli)
    }

    @Test
    fun motorwayComparedOnlyWithMotorway() {
        val road = neighbours(25, 2000)
        val motorway = neighbours(8, 2200, startKm = 3.0, motorway = true)
        val result = rank(road + motorway)
        motorway.forEach {
            assertEquals(CompareGroup.MOTORWAY, result.getValue(it.id).group)
            assertEquals(PriceClass.AVERAGE, result.getValue(it.id).priceClass) // not "expensive"
        }
    }

    @Test
    fun dutyFreeComparedOnlyWithItself() {
        val valtellina = neighbours(25, 2100, municipality = "BORMIO")
        val livigno = neighbours(10, 1600, startKm = 5.0, municipality = "LIVIGNO")
        val result = rank(valtellina + livigno)
        livigno.forEach {
            assertEquals(CompareGroup.DUTY_FREE, result.getValue(it.id).group)
            assertEquals(PriceClass.AVERAGE, result.getValue(it.id).priceClass) // not "to verify"
        }
        valtellina.forEach { assertEquals(PriceClass.AVERAGE, result.getValue(it.id).priceClass) }
    }

    @Test
    fun tooFewNeighboursWithin50Km() {
        val lonely = station(0.0, petrolSelf(2000))
        val near = neighbours(4, 2000) // only 4 close by
        val far = List(10) { station(60.0 + it, petrolSelf(2000)) } // beyond 50 km
        val result = rank(listOf(lonely) + near + far)
        assertEquals(PriceClass.NOT_COMPARED, result.getValue(lonely.id).priceClass)
        assertNull(result.getValue(lonely.id).diffFromMedianMilli)
    }

    @Test
    fun usesTheNearest25AndExcludesItself() {
        val target = station(0.0, petrolSelf(1800))
        val near = neighbours(25, 2100) // the 25 nearest, at 2.100
        val farther = List(30) { station(10.0 + it * 0.1, petrolSelf(1900)) } // ignored: not among the 25 nearest
        val result = rank(listOf(target) + near + farther)
        // Median of the 25 nearest *others* is 2.100 → 30 c below → cheap (not to verify).
        assertEquals(-300.0, result.getValue(target.id).diffFromMedianMilli!!, 0.0)
        assertEquals(PriceClass.CHEAP, result.getValue(target.id).priceClass)
    }

    @Test
    fun modesSpecialProductsAndMethaneVariants() {
        val selfOnly = station(0.0, petrolSelf(2000))
        val servedOnly = station(0.1, Triple(0, 2200L, 0))
        val specialOnly = station(0.2, Triple(1, 2300L, 1)) // Blue Super: not standard
        val lgnc = station(0.3, Triple(4, 1500L, 0))
        val methane = station(0.4, Triple(3, 1600L, 0), Triple(4, 1550L, 0)) // cheapest standard CNG counts
        val all = listOf(selfOnly, servedOnly, specialOnly, lgnc, methane)

        val selfResult = rank(all)
        assertEquals(setOf(selfOnly.id), selfResult.keys)
        assertEquals(setOf(servedOnly.id), rank(all, FuelChoice(FuelKind.PETROL, ServiceMode.SERVED)).keys)

        val cng = rank(all, FuelChoice(FuelKind.CNG, ServiceMode.SELF)) // mode doesn't apply to methane
        assertEquals(setOf(lgnc.id, methane.id), cng.keys)
        assertEquals(1550L, cng.getValue(methane.id).priceMilli)
    }
}
