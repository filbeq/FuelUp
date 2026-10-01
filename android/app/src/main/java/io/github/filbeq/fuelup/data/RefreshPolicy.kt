package io.github.filbeq.fuelup.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When to contact the server. MIMIT publishes the prices of day D on the
 * morning of D+1, so once the cache holds yesterday's prices nothing newer can
 * exist and the app stays offline.
 */
object RefreshPolicy {
    val ITALY: ZoneId = ZoneId.of("Europe/Rome")

    /** Opening the app repeatedly before the morning publication checks at most this often. */
    val META_CHECK_INTERVAL: Duration = Duration.ofHours(1)

    /** The most recent data date that can have been published at [now]. */
    fun newestPossibleDataDate(now: Instant): LocalDate = now.atZone(ITALY).toLocalDate().minusDays(1)

    /**
     * Whether to download meta.json now.
     * - no cache: always (there is nothing to show otherwise);
     * - cache already holds the newest possible date: never;
     * - otherwise at most once per [META_CHECK_INTERVAL], unless the user asked ([force]).
     */
    fun shouldCheckMeta(
        cachedDataDate: LocalDate?,
        lastMetaCheck: Instant?,
        now: Instant,
        force: Boolean,
    ): Boolean {
        if (cachedDataDate == null) return true
        if (!cachedDataDate.isBefore(newestPossibleDataDate(now))) return false
        if (force || lastMetaCheck == null || lastMetaCheck.isAfter(now)) return true
        return Duration.between(lastMetaCheck, now) >= META_CHECK_INTERVAL
    }

    /**
     * Whether the published stations.json differs from the cached one. A file
     * republished for the same date with a fix is picked up only if meta.json is
     * checked, which [shouldCheckMeta] skips once the date is current (see
     * DEVELOPMENT.md).
     */
    fun needsDownload(cached: Meta?, published: Meta): Boolean =
        cached == null || cached.dataDate != published.dataDate || cached.sha256 != published.sha256
}
