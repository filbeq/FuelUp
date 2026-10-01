package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RefreshPolicyTest {
    private val now = Instant.parse("2026-10-01T08:00:00Z") // 10:00 in Italy
    private val yesterday = LocalDate.parse("2026-09-30")
    private val twoDaysAgo = LocalDate.parse("2026-09-29")

    @Test
    fun newestPossibleDateUsesItalianTime() {
        // 22:30 UTC on 30/09 is already 00:30 on 01/10 in Italy.
        assertEquals(yesterday, RefreshPolicy.newestPossibleDataDate(Instant.parse("2026-09-30T22:30:00Z")))
        assertEquals(twoDaysAgo, RefreshPolicy.newestPossibleDataDate(Instant.parse("2026-09-30T21:30:00Z")))
    }

    @Test
    fun noCacheAlwaysChecks() {
        assertTrue(RefreshPolicy.shouldCheckMeta(null, now.minusSeconds(60), now, force = false))
    }

    @Test
    fun currentCacheNeverChecks() {
        assertFalse(RefreshPolicy.shouldCheckMeta(yesterday, null, now, force = false))
        assertFalse(RefreshPolicy.shouldCheckMeta(yesterday, null, now, force = true))
    }

    @Test
    fun oldCacheChecksAtMostHourly() {
        assertTrue(RefreshPolicy.shouldCheckMeta(twoDaysAgo, null, now, force = false))
        assertFalse(RefreshPolicy.shouldCheckMeta(twoDaysAgo, now.minusSeconds(59 * 60), now, force = false))
        assertTrue(RefreshPolicy.shouldCheckMeta(twoDaysAgo, now.minusSeconds(60 * 60), now, force = false))
    }

    @Test
    fun retryIgnoresHourlyLimit() {
        assertTrue(RefreshPolicy.shouldCheckMeta(twoDaysAgo, now.minusSeconds(60), now, force = true))
    }

    @Test
    fun lastCheckInTheFutureMeansTheClockChanged() {
        assertTrue(RefreshPolicy.shouldCheckMeta(twoDaysAgo, now.plusSeconds(3600), now, force = false))
    }

    @Test
    fun downloadOnlyWhenPublishedFileDiffers() {
        val meta = Meta(1, "2026-09-30", "p", 1, 1, "stations.json", 10, "aaa")
        assertTrue(RefreshPolicy.needsDownload(null, meta))
        assertFalse(RefreshPolicy.needsDownload(meta, meta.copy()))
        assertTrue(RefreshPolicy.needsDownload(meta, meta.copy(dataDate = "2026-10-01")))
        assertTrue(RefreshPolicy.needsDownload(meta, meta.copy(sha256 = "bbb")))
    }
}
