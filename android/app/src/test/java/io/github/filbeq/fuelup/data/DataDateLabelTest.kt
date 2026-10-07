package io.github.filbeq.fuelup.data

import io.github.filbeq.fuelup.data.DataDateLabel.Day
import io.github.filbeq.fuelup.data.DataDateLabel.Label
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DataDateLabelTest {
    private fun label(dataDate: String, nowUtc: String) = DataDateLabel.of(LocalDate.parse(dataDate), Instant.parse(nowUtc))

    // October 2026 is summer time (UTC+2) until the 25th, 03:00.

    @Test
    fun yesterdaysDataIsTheNewest() {
        // 7 Oct, 09:00 in Italy.
        assertEquals(Label(Day.YESTERDAY, late = false), label("2026-10-06", "2026-10-07T07:00:00Z"))
        // 7 Oct, 23:59.
        assertEquals(Label(Day.YESTERDAY, late = false), label("2026-10-06", "2026-10-07T21:59:00Z"))
    }

    @Test
    fun afterMidnightTheDayBeforeYesterdayIsStillTheNewest() {
        // 8 Oct, 00:00 in Italy: no longer "yesterday", but nothing newer exists yet.
        assertEquals(Label(Day.OTHER, late = false), label("2026-10-06", "2026-10-07T22:00:00Z"))
        // 8 Oct, 13:59: the morning publication may still come.
        assertEquals(Label(Day.OTHER, late = false), label("2026-10-06", "2026-10-08T11:59:00Z"))
    }

    @Test
    fun lateFromTheDeadline() {
        // 8 Oct, 14:00 in Italy: the 7 Oct prices should be out.
        assertEquals(Label(Day.OTHER, late = true), label("2026-10-06", "2026-10-08T12:00:00Z"))
        // Older still: late even before the deadline.
        assertEquals(Label(Day.OTHER, late = true), label("2026-10-05", "2026-10-07T22:00:00Z"))
    }

    @Test
    fun todayOrFutureDatesAreNeverLate() {
        // Only with a wrong clock or an early publication; shown as they are.
        assertEquals(Label(Day.TODAY, late = false), label("2026-10-08", "2026-10-08T08:00:00Z"))
        assertEquals(Label(Day.OTHER, late = false), label("2026-10-10", "2026-10-08T08:00:00Z"))
    }

    @Test
    fun italianMidnightAcrossTheAutumnClockChange() {
        // 25 Oct 2026 ends summer time: midnight of the 26th is 23:00 UTC, not 22:00.
        assertEquals(Label(Day.YESTERDAY, late = false), label("2026-10-24", "2026-10-25T22:30:00Z"))
        assertEquals(Label(Day.OTHER, late = false), label("2026-10-24", "2026-10-25T23:00:00Z"))
        // The deadline on the 26th is 13:00 UTC (14:00 winter time).
        assertEquals(Label(Day.OTHER, late = false), label("2026-10-24", "2026-10-26T12:59:00Z"))
        assertEquals(Label(Day.OTHER, late = true), label("2026-10-24", "2026-10-26T13:00:00Z"))
    }

    @Test
    fun italianMidnightAcrossTheSpringClockChange() {
        // 28 Mar 2027 starts summer time: midnight of the 29th is 22:00 UTC.
        assertEquals(Label(Day.YESTERDAY, late = false), label("2027-03-27", "2027-03-28T21:59:00Z"))
        assertEquals(Label(Day.OTHER, late = false), label("2027-03-27", "2027-03-28T22:00:00Z"))
        // The deadline on the 28th itself (the short day) is 12:00 UTC.
        assertEquals(Label(Day.OTHER, late = true), label("2027-03-26", "2027-03-28T12:00:00Z"))
        assertEquals(Label(Day.YESTERDAY, late = false), label("2027-03-27", "2027-03-28T12:00:00Z"))
    }

    @Test
    fun theLabelChangesAtTheDeadlineThenAtMidnight() {
        assertEquals(Instant.parse("2026-10-08T12:00:00Z"), DataDateLabel.nextChange(Instant.parse("2026-10-08T07:00:00Z")))
        assertEquals(Instant.parse("2026-10-08T22:00:00Z"), DataDateLabel.nextChange(Instant.parse("2026-10-08T12:00:00Z")))
        // Across the autumn change: midnight of the 26th is 23:00 UTC.
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), DataDateLabel.nextChange(Instant.parse("2026-10-25T20:00:00Z")))
    }
}
