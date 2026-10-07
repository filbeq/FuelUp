package io.github.filbeq.fuelup.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PricesAtFormatTest {
    @Test
    fun weekdayAndDate() {
        // Short weekday: the pill must fit beside the widest fuel button ("Gasolio · Servito").
        assertEquals("mar 06/10", formatPricesDate("2026-10-06T08:00:00+02:00", "EEE dd/MM", Locale.ITALIAN))
        assertEquals("Tue, Oct 6", formatPricesDate("2026-10-06T08:00:00+02:00", "EEE, MMM d", Locale.ENGLISH))
    }

    @Test
    fun alwaysTheItalianDay() {
        // 23:30 UTC on the 5th is already the 6th in Italy.
        assertEquals("mar 06/10", formatPricesDate("2026-10-05T23:30:00Z", "EEE dd/MM", Locale.ITALIAN))
        assertEquals(LocalDate.of(2026, 10, 6), pricesAtDate("2026-10-05T23:30:00Z"))
        assertEquals(LocalDate.of(2026, 10, 6), pricesAtDate("2026-10-06T08:00:00+02:00"))
    }
}
