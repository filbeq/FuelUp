package io.github.filbeq.fuelup.ui.station

import io.github.filbeq.fuelup.data.RefreshPolicy
import java.text.NumberFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "1,849" (Italian) or "1.849" (English): always 3 decimals, as published. */
fun formatPriceNumber(priceMilli: Long, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 3
        maximumFractionDigits = 3
    }.format(priceMilli / 1000.0)

/**
 * Calendar days between the report and [now], in Italian time: 0 = today,
 * 1 = yesterday. A report "in the future" (clock differences) counts as today.
 */
fun daysSinceReported(updated: Instant, now: Instant): Long {
    val reportedDay = updated.atZone(RefreshPolicy.ITALY).toLocalDate()
    val today = now.atZone(RefreshPolicy.ITALY).toLocalDate()
    return ChronoUnit.DAYS.between(reportedDay, today).coerceAtLeast(0)
}
