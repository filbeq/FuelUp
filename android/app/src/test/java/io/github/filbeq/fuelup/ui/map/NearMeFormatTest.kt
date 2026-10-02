package io.github.filbeq.fuelup.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NearMeFormatTest {
    @Test
    fun accuracy() {
        assertEquals("800 m", formatAccuracy(812f, Locale.ITALIAN))
        assertEquals("50 m", formatAccuracy(10f, Locale.ITALIAN))
        assertEquals("1,5 km", formatAccuracy(1520f, Locale.ITALIAN))
        assertEquals("1.5 km", formatAccuracy(1520f, Locale.ENGLISH))
        assertEquals("2 km", formatAccuracy(2000f, Locale.ENGLISH))
    }

    @Test
    fun distanceApproximateOrPrecise() {
        assertEquals("< 1 km", formatDistance(0.4, approximate = true, Locale.ITALIAN))
        assertEquals("≈ 3 km", formatDistance(2.6, approximate = true, Locale.ITALIAN))
        assertEquals("850 m", formatDistance(0.86, approximate = false, Locale.ITALIAN))
        assertEquals("2,6 km", formatDistance(2.6, approximate = false, Locale.ITALIAN))
        assertEquals("2.6 km", formatDistance(2.6, approximate = false, Locale.ENGLISH))
    }
}
