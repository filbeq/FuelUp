package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAreaTest {
    // A north-up area around (45.0, 9.0): 0.1° of latitude ≈ 11.1 km, 0.1° of longitude ≈ 7.9 km here.
    private val northUp = MapArea(
        listOf(
            doubleArrayOf(45.1, 8.9), // top left
            doubleArrayOf(45.1, 9.1), // top right
            doubleArrayOf(44.9, 9.1), // bottom right
            doubleArrayOf(44.9, 8.9), // bottom left
        ),
    )

    // The same place turned 45°: a diamond.
    private val turned = MapArea(
        listOf(
            doubleArrayOf(45.1, 9.0),
            doubleArrayOf(45.0, 9.1),
            doubleArrayOf(44.9, 9.0),
            doubleArrayOf(45.0, 8.9),
        ),
    )

    @Test
    fun containsPointsInsideAndOnTheEdge() {
        assertTrue(northUp.contains(45.0, 9.0))
        assertTrue(northUp.contains(45.1, 9.0))
        assertFalse(northUp.contains(45.11, 9.0))
        assertFalse(northUp.contains(45.0, 8.89))
    }

    @Test
    fun aTurnedAreaLeavesOutItsBoundingBoxCorners() {
        assertTrue(turned.contains(45.0, 9.0))
        assertTrue(turned.contains(45.04, 9.04))
        assertFalse(turned.contains(45.08, 9.08))
    }

    @Test
    fun longerSideAndCentre() {
        assertEquals(22.2, northUp.longerSideKm, 0.1)
        assertEquals(45.0, northUp.centreLat, 1e-9)
        assertEquals(9.0, northUp.centreLon, 1e-9)
        assertFalse(northUp.tooLarge)
    }

    @Test
    fun aRegionIsTooLarge() {
        // About 111 × 79 km.
        val region = MapArea(
            listOf(doubleArrayOf(45.5, 8.5), doubleArrayOf(45.5, 9.5), doubleArrayOf(44.5, 9.5), doubleArrayOf(44.5, 8.5)),
        )
        assertTrue(region.tooLarge)
    }

    @Test
    fun savedAndRestored() {
        val restored = MapArea.fromArray(turned.toArray())
        assertEquals(turned.corners.map { it.toList() }, restored.corners.map { it.toList() })
    }
}
