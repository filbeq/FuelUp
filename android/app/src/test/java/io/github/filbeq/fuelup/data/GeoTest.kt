package io.github.filbeq.fuelup.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun knownDistances() {
        // Milano Duomo → Roma Colosseo: ~477 km as the crow flies.
        assertEquals(477.0, Geo.distanceKm(45.4642, 9.1900, 41.8902, 12.4922), 2.0)
        // One degree of latitude: ~111.2 km.
        assertEquals(111.2, Geo.distanceKm(44.0, 11.0, 45.0, 11.0), 0.1)
        assertEquals(0.0, Geo.distanceKm(44.0, 11.0, 44.0, 11.0), 1e-9)
    }

    @Test
    fun circlePointsAreAtTheRadiusAndTheRingIsClosed() {
        val ring = Geo.circle(45.0, 9.0, 10.0, points = 32)
        assertEquals(33, ring.size)
        assertArrayEquals(ring.first(), ring.last(), 1e-12)
        ring.forEach { (lon, lat) -> assertEquals(10.0, Geo.distanceKm(45.0, 9.0, lat, lon), 1e-6) }
    }
}
