package io.github.filbeq.fuelup.ui.station

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale

class PriceFormatTest {
    @Test
    fun priceNumbers() {
        assertEquals("1,849", formatPriceNumber(1849, Locale.ITALIAN))
        assertEquals("1.849", formatPriceNumber(1849, Locale.ENGLISH))
        assertEquals("0,750", formatPriceNumber(750, Locale.ITALIAN)) // always 3 decimals
        assertEquals("2.000", formatPriceNumber(2000, Locale.ENGLISH))
    }

    @Test
    fun daysCountInItalianCalendarDays() {
        val now = Instant.parse("2026-10-01T08:00:00Z") // 10:00 on 01/10 in Italy
        assertEquals(0, daysSinceReported(Instant.parse("2026-09-30T22:30:00Z"), now)) // 00:30 on 01/10
        assertEquals(1, daysSinceReported(Instant.parse("2026-09-30T21:30:00Z"), now)) // 23:30 on 30/09
        assertEquals(6, daysSinceReported(Instant.parse("2026-09-25T10:00:00Z"), now))
        assertEquals(0, daysSinceReported(now.plusSeconds(600), now)) // slightly in the future
    }

    @Test
    fun daysAcrossDaylightSavingChange() {
        // Clocks went back on 25/10/2026: still exactly 2 calendar days.
        assertEquals(
            2,
            daysSinceReported(Instant.parse("2026-10-24T22:30:00Z"), Instant.parse("2026-10-26T23:30:00Z")),
        )
    }
}
