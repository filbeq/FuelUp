package io.github.filbeq.fuelup.ui.map

import io.github.filbeq.fuelup.data.RefreshPolicy
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats the day of meta.json's `pricesAt` (e.g. "2026-09-30T08:00:00+02:00")
 * in Italian time with a pattern from the string resources, e.g. "mer 30/09".
 */
fun formatPricesDate(pricesAt: String, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(OffsetDateTime.parse(pricesAt).atZoneSameInstant(RefreshPolicy.ITALY))

/** The data date of meta.json's `pricesAt`: the day in Italy at that instant. */
fun pricesAtDate(pricesAt: String): LocalDate = OffsetDateTime.parse(pricesAt).atZoneSameInstant(RefreshPolicy.ITALY).toLocalDate()
