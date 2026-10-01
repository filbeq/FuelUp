package io.github.filbeq.fuelup.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PricesAtFormatTest {
    @Test
    fun italian() {
        assertEquals(
            "30/09" to "8:00",
            formatPricesAt("2026-09-30T08:00:00+02:00", "dd/MM", "H:mm", Locale.ITALIAN),
        )
    }

    @Test
    fun english() {
        val (date, time) = formatPricesAt("2026-09-30T08:00:00+02:00", "MMM d", "h:mm a", Locale.ENGLISH)
        assertEquals("Sep 30", date)
        assertEquals("8:00 AM", time.replace(' ', ' ')) // some JDKs use a narrow space before AM
    }

    @Test
    fun alwaysShownInItalianTime() {
        // Same instant written in UTC: still 8:00 in Italy.
        assertEquals(
            "30/09" to "8:00",
            formatPricesAt("2026-09-30T06:00:00Z", "dd/MM", "H:mm", Locale.ITALIAN),
        )
    }
}
