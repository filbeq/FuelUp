package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyTest {
    // Around (45.0, 9.0). 0.01° of latitude ≈ 1.11 km.
    private fun station(id: Int, dLat: Double) =
        Station(id, "S$id", 0, 0, "VIA X", "PAVIA", "PV", 45.0 + dLat, 9.0, emptyList())

    private fun ranked(price: Long, priceClass: PriceClass = PriceClass.AVERAGE) =
        RankedPrice(price, 0, CompareGroup.ROAD, priceClass, 0.0)

    private val stations = listOf(
        station(1, 0.01), // 1.1 km
        station(2, 0.03), // 3.3 km
        station(3, 0.05), // 5.6 km
        station(4, 0.08), // 8.9 km
        station(5, 0.10), // 11.1 km: outside 10 km
        station(6, 0.02), // 2.2 km, doesn't sell the fuel
    )
    private val ranking = mapOf(
        1 to ranked(1900),
        2 to ranked(1850),
        3 to ranked(1300, PriceClass.TO_VERIFY),
        4 to ranked(1850),
        5 to ranked(1500),
    )

    private fun ids(radiusKm: Int, sort: NearbySort) =
        Nearby.find(stations, ranking, 45.0, 9.0, radiusKm, sort).map { it.station.id }

    @Test
    fun byPriceCheapestFirstTiesByDistanceToVerifyLast() {
        assertEquals(listOf(2, 4, 1, 3), ids(10, NearbySort.PRICE))
    }

    @Test
    fun byDistanceNearestFirst() {
        assertEquals(listOf(1, 2, 3, 4), ids(10, NearbySort.DISTANCE))
    }

    @Test
    fun radiusLimitsTheList() {
        assertEquals(listOf(1, 2), ids(5, NearbySort.DISTANCE))
        assertEquals(listOf(1, 2, 3, 4, 5), ids(20, NearbySort.DISTANCE))
    }

    @Test
    fun distancesAreAsTheCrowFlies() {
        val first = Nearby.find(stations, ranking, 45.0, 9.0, 10, NearbySort.DISTANCE).first()
        assertEquals(1.112, first.distanceKm, 0.01)
    }

    @Test
    fun aFreshFixReframesOnlyWhenItMovedMoreThan500m() {
        val old = UserPosition(45.0, 9.0, 1500f)
        // 0.004° of latitude ≈ 445 m, 0.005° ≈ 556 m.
        assertEquals(false, Nearby.movedEnough(old, UserPosition(45.004, 9.0, 20f)))
        assertEquals(true, Nearby.movedEnough(old, UserPosition(45.005, 9.0, 20f)))
    }
}
