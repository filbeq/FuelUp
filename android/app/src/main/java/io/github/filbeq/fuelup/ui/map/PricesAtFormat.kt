package io.github.filbeq.fuelup.ui.map

import io.github.filbeq.fuelup.data.RefreshPolicy
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats meta.json's `pricesAt` (e.g. "2026-09-30T08:00:00+02:00") in Italian
 * time with the patterns from the string resources, e.g. "30/09" and "8:00".
 */
fun formatPricesAt(pricesAt: String, datePattern: String, timePattern: String, locale: Locale): Pair<String, String> {
    val time = OffsetDateTime.parse(pricesAt).atZoneSameInstant(RefreshPolicy.ITALY)
    return DateTimeFormatter.ofPattern(datePattern, locale).format(time) to
        DateTimeFormatter.ofPattern(timePattern, locale).format(time)
}
