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
}
