package io.github.filbeq.fuelup.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * How the data date reads in the app, in Italian time. MIMIT publishes the
 * prices of day D on the morning of D+1, so "yesterday" is normally the newest
 * there is. Between midnight and the morning publication the day before
 * yesterday is still the newest: that is not late either. Late means older
 * than what should exist by now, with the same deadline as the pipeline's
 * staleness alarm (`pipeline/fuel_pipeline/freshness.py`): by 14:00 Italian
 * time yesterday's prices should be published.
 */
object DataDateLabel {
    /** The day as a word ("yesterday", "today"), or as a weekday and date. */
    enum class Day { TODAY, YESTERDAY, OTHER }

    data class Label(val day: Day, val late: Boolean)

    /** By this time (Italian) yesterday's prices should be published. */
    val PUBLISH_DEADLINE: LocalTime = LocalTime.of(14, 0)

    fun of(dataDate: LocalDate, now: Instant): Label {
        val local = now.atZone(RefreshPolicy.ITALY)
        val today = local.toLocalDate()
        val day = when (dataDate) {
            today -> Day.TODAY
            today.minusDays(1) -> Day.YESTERDAY
            else -> Day.OTHER
        }
        return Label(day, late = dataDate.isBefore(expectedDate(now)))
    }

    /** The data date that should be published by [now]: yesterday's from 14:00, else the day before. */
    fun expectedDate(now: Instant): LocalDate {
        val local = now.atZone(RefreshPolicy.ITALY)
        val yesterday = local.toLocalDate().minusDays(1)
        return if (local.toLocalTime() >= PUBLISH_DEADLINE) yesterday else yesterday.minusDays(1)
    }

    /** When the label may next change by itself: the next midnight or 14:00 in Italy. */
    fun nextChange(now: Instant): Instant {
        val local = now.atZone(RefreshPolicy.ITALY)
        val deadline = local.toLocalDate().atTime(PUBLISH_DEADLINE).atZone(RefreshPolicy.ITALY)
        if (deadline.toInstant().isAfter(now)) return deadline.toInstant()
        return local.toLocalDate().plusDays(1).atStartOfDay(RefreshPolicy.ITALY).toInstant()
    }
}
