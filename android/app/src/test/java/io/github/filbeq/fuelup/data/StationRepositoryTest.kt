package io.github.filbeq.fuelup.data

import io.github.filbeq.fuelup.core.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Instant

class StationRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var server: FakeServer
    private var now = Instant.parse("2026-10-01T08:00:00Z") // 10:00 in Italy

    @Before
    fun setUp() {
        dir = File(tmp.root, "data")
        server = FakeServer()
        server.publish(dataset("2026-09-30"))
    }

    private fun repository(thresholds: Thresholds = NO_LIMITS) = StationRepository(
        dir, server, clock = { now }, baseUrl = BASE,
        mimitPricesUrl = BASE + MIMIT_PRICES, mimitStationsUrl = BASE + MIMIT_STATIONS, thresholds = thresholds,
    )

    @Test
    fun firstLaunchDownloadsAndCaches() {
        val repo = repository()
        assertNull(repo.loadCached())

        val result = repo.refresh(null)

        assertTrue(result is RefreshResult.Updated)
        assertEquals("2026-09-30", (result as RefreshResult.Updated).snapshot.meta.dataDate)
        assertEquals(listOf("meta.json", "stations.json"), server.requests)
        val cached = repo.loadCached()
        assertNotNull(cached)
        assertEquals(3, cached!!.stations.stations.size)
        assertFalse(File(dir, "stations.json.tmp").exists())
    }

    @Test
    fun currentCacheMakesNoRequests() {
        val repo = repository()
        repo.refresh(null)
        server.requests.clear()

        assertEquals(RefreshResult.UpToDate, repo.refresh(repo.loadCached()))
        assertEquals(emptyList<String>(), server.requests)
    }

    @Test
    fun oldCacheChecksMetaHourlyAndDownloadsOnlyNewData() {
        val repo = repository()
        repo.refresh(null)
        server.requests.clear()
        now = Instant.parse("2026-10-02T08:00:00Z") // the cached 30/09 is now old

        // Not published yet: only meta.json is fetched (and MIMIT's date, unreachable here).
        assertEquals(RefreshResult.UpToDate, repo.refresh(repo.loadCached()))
        assertEquals(listOf("meta.json", "$MIMIT_PRICES (start)"), server.requests)

        // Within the hour: no request at all.
        server.requests.clear()
        now = now.plusSeconds(30 * 60)
        assertEquals(RefreshResult.UpToDate, repo.refresh(repo.loadCached()))
        assertEquals(emptyList<String>(), server.requests)

        // Published, and an hour has passed: downloaded and cached.
        server.publish(dataset("2026-10-01"))
        now = now.plusSeconds(31 * 60)
        val result = repo.refresh(repo.loadCached())
        assertTrue(result is RefreshResult.Updated)
        assertEquals(DataOrigin.PAGES, (result as RefreshResult.Updated).snapshot.origin)
        assertEquals(listOf("meta.json", "stations.json"), server.requests)
        assertEquals("2026-10-01", repo.loadCached()!!.meta.dataDate)
    }

    @Test
    fun manualCheckIgnoresSameDateRuleAndHourlyLimit() {
        val repo = repository()
        repo.refresh(null)
        server.requests.clear()

        // The cache already holds the newest date and was checked just now,
        // yet "Update data now" still asks the server; same file: nothing downloaded.
        assertEquals(RefreshResult.UpToDate, repo.refresh(repo.loadCached(), manual = true))
        assertEquals(listOf("meta.json"), server.requests)
    }

    @Test
    fun manualCheckDownloadsFileRepublishedForSameDate() {
        val repo = repository()
        repo.refresh(null)
        server.publish(republished(dataset("2026-09-30")))
        server.requests.clear()

        // The automatic check never sees it (same date)...
        assertEquals(RefreshResult.UpToDate, repo.refresh(repo.loadCached()))
        assertEquals(emptyList<String>(), server.requests)
        // ...the manual one does, and replaces the cache.
        val result = repo.refresh(repo.loadCached(), manual = true)
        assertTrue(result is RefreshResult.Updated)
        assertEquals(listOf("meta.json", "stations.json"), server.requests)
        assertEquals(server.sha256("stations.json"), repo.loadCached()!!.meta.sha256)
    }

    @Test
    fun manualCheckOfflineKeepsCacheAndCheckTime() {
        val repo = repository()
        repo.refresh(null)
        val checked = repo.lastMetaCheck()
        now = now.plusSeconds(600)
        server.failure = IOException("no route to host")
        server.mimitFailure = IOException("no route to host")

        assertEquals(RefreshResult.Offline, repo.refresh(repo.loadCached(), manual = true))
        assertEquals("2026-09-30", repo.loadCached()!!.meta.dataDate)
        assertEquals(checked, repo.lastMetaCheck()) // a failed check doesn't count as checked
    }

    @Test
    fun lastMetaCheckRecordsEachServerCheck() {
        val repo = repository()
        assertNull(repo.lastMetaCheck())
        repo.refresh(null)
        assertEquals(now.toEpochMilli(), repo.lastMetaCheck()!!.toEpochMilli())
        now = now.plusSeconds(600)
        repo.refresh(repo.loadCached(), manual = true)
        assertEquals(now.toEpochMilli(), repo.lastMetaCheck()!!.toEpochMilli())
    }

    @Test
    fun offlineWithoutCache() {
        server.failure = IOException("no route to host")
        server.mimitFailure = IOException("no route to host")
        val repo = repository()
        assertEquals(RefreshResult.Offline, repo.refresh(null))
        assertNull(repo.loadCached())
    }

    @Test
    fun timeoutCountsAsOfflineAndKeepsCache() {
        val repo = repository()
        repo.refresh(null)
        now = Instant.parse("2026-10-03T08:00:00Z")
        server.failure = SocketTimeoutException("read timed out")
        server.mimitFailure = SocketTimeoutException("connect timed out")

        assertEquals(RefreshResult.Offline, repo.refresh(repo.loadCached()))
        assertEquals("2026-09-30", repo.loadCached()!!.meta.dataDate)
    }

    @Test
    fun serverErrorFails() {
        server.failure = ServerException(404)
        server.mimitFailure = ServerException(404)
        assertEquals(RefreshResult.Failed, repository().refresh(null))
    }

    @Test
    fun checksumMismatchIsRejectedAndCacheKept() {
        val repo = repository()
        repo.refresh(null)
        now = Instant.parse("2026-10-02T08:00:00Z")
        val next = dataset("2026-10-01")
        server.publish(next.copy(stations = next.stations.copyOf().also { it[10] = 'X'.code.toByte() }))

        assertEquals(RefreshResult.Failed, repo.refresh(repo.loadCached()))
        assertEquals("2026-09-30", repo.loadCached()!!.meta.dataDate)
        assertFalse(File(dir, "stations.json.tmp").exists())
    }

    @Test
    fun unknownSchemaKeepsCacheAndAsksForUpdate() {
        val repo = repository()
        repo.refresh(null)
        now = Instant.parse("2026-10-02T08:00:00Z")
        server.publish(dataset("2026-10-01", schemaVersion = 2))
        server.requests.clear()

        assertEquals(RefreshResult.UpdateRequired, repo.refresh(repo.loadCached()))
        // stations.json not downloaded (MIMIT, unreachable here, was asked).
        assertEquals(listOf("meta.json", "$MIMIT_PRICES (start)"), server.requests)
        assertEquals("2026-09-30", repo.loadCached()!!.meta.dataDate)
    }

    @Test
    fun corruptCacheIsIgnoredAndDownloadedAgain() {
        val repo = repository()
        repo.refresh(null)
        File(dir, "stations.json").writeText("{ broken")

        assertNull(repo.loadCached())
        assertTrue(repo.refresh(null) is RefreshResult.Updated)
        assertNotNull(repo.loadCached())
    }

    @Test
    fun mismatchedCachePairIsIgnored() {
        val repo = repository()
        repo.refresh(null)
        File(dir, "meta.json").writeBytes(dataset("2026-10-01").meta)
        assertNull(repo.loadCached())
    }

    // --- MIMIT directly ---

    @Test
    fun stalePublishedCopyAndNewerMimitDataDownloadsFromMimit() {
        val repo = repository()
        repo.refresh(null)
        server.requests.clear()
        now = Instant.parse("2026-10-02T08:00:00Z") // GitHub still has 30/09 (blocked)
        server.mimit = mimitFiles("2026-10-01")

        val result = repo.refresh(repo.loadCached())

        assertTrue(result is RefreshResult.Updated)
        val snapshot = (result as RefreshResult.Updated).snapshot
        assertEquals(DataOrigin.MIMIT, snapshot.origin)
        assertEquals("2026-10-01", snapshot.meta.dataDate)
        assertEquals(listOf(1, 2), snapshot.stations.stations.map { it.id })
        assertEquals(
            listOf("meta.json", "$MIMIT_PRICES (start)", MIMIT_PRICES, MIMIT_STATIONS),
            server.requests,
        )
        assertEquals(false, repo.mimitUnreachable)
        val cached = repo.loadCached()!!
        assertEquals(DataOrigin.MIMIT, cached.origin)
        assertEquals("2026-10-01", cached.meta.dataDate)
        assertEquals(listOf("meta.json", "origin", "last_meta_check", "stations.json").sorted(), dir.list()!!.sorted())
    }

    @Test
    fun firstLaunchWithStalePublishedCopyDownloadsFromMimit() {
        now = Instant.parse("2026-10-05T08:00:00Z")
        server.mimit = mimitFiles("2026-10-04")
        val result = repository().refresh(null)
        assertEquals(DataOrigin.MIMIT, (result as RefreshResult.Updated).snapshot.origin)
    }

    @Test
    fun serverIgnoringTheRangeStillWorks() {
        now = Instant.parse("2026-10-05T08:00:00Z")
        server.mimit = mimitFiles("2026-10-04")
        server.honourRange = false
        assertTrue(repository().refresh(null) is RefreshResult.Updated)
    }

    @Test
    fun mimitWithSameDateAsPublishedCopyUsesTheSmallerCopy() {
        now = Instant.parse("2026-10-03T08:00:00Z") // both stopped at 30/09
        server.mimit = mimitFiles("2026-09-30")

        val result = repository().refresh(null)

        assertEquals(DataOrigin.PAGES, (result as RefreshResult.Updated).snapshot.origin)
        assertEquals(listOf("meta.json", "$MIMIT_PRICES (start)", "stations.json"), server.requests)
    }

    @Test
    fun mimitUnreachableFallsBackToNewerPublishedCopy() {
        now = Instant.parse("2026-10-03T08:00:00Z")
        server.mimitFailure = SocketTimeoutException("connect timed out") // as from abroad
        val repo = repository()

        val result = repo.refresh(null)

        assertEquals(DataOrigin.PAGES, (result as RefreshResult.Updated).snapshot.origin)
        assertEquals(true, repo.mimitUnreachable)
    }

    @Test
    fun mimitUnreachableAndNothingNewerPublishedIsNotAnError() {
        val repo = repository()
        repo.refresh(null)
        now = Instant.parse("2026-10-03T08:00:00Z")
        server.mimitFailure = SocketTimeoutException("connect timed out")

        assertEquals(RefreshResult.UpToDate, repo.refresh(repo.loadCached()))
        assertEquals(true, repo.mimitUnreachable)
        assertEquals(now, repo.lastMetaCheck())
    }

    @Test
    fun mimitFormatChangeFallsBackToPublishedCopy() {
        now = Instant.parse("2026-10-03T08:00:00Z")
        val (prices, stations) = mimitFiles("2026-10-02")
        server.mimit = prices.replace("descCarburante", "carburante") to stations
        val repo = repository()

        val result = repo.refresh(null)

        assertEquals(DataOrigin.PAGES, (result as RefreshResult.Updated).snapshot.origin)
        assertEquals(false, repo.mimitUnreachable)
        assertEquals(listOf("last_meta_check", "meta.json", "origin", "stations.json"), dir.list()!!.sorted())
    }

    @Test
    fun mimitDataFailingTheChecksFallsBackToPublishedCopy() {
        now = Instant.parse("2026-10-03T08:00:00Z")
        server.mimit = mimitFiles("2026-10-02")
        val result = repository(thresholds = NO_LIMITS.copy(minStations = 100)).refresh(null)
        assertEquals(DataOrigin.PAGES, (result as RefreshResult.Updated).snapshot.origin)
    }

    @Test
    fun mimitFilesOfDifferentDaysAreRejected() {
        now = Instant.parse("2026-10-03T08:00:00Z")
        val (prices, _) = mimitFiles("2026-10-02")
        server.mimit = prices to mimitFiles("2026-10-01").second
        val result = repository().refresh(null)
        assertEquals(DataOrigin.PAGES, (result as RefreshResult.Updated).snapshot.origin)
    }

    @Test
    fun mimitFailingWithNothingNewerPublishedFails() {
        val repo = repository()
        repo.refresh(null)
        now = Instant.parse("2026-10-03T08:00:00Z")
        server.mimitFailure = ServerException(503)

        assertEquals(RefreshResult.Failed, repo.refresh(repo.loadCached()))
        assertEquals("2026-09-30", repo.loadCached()!!.meta.dataDate)
    }

    @Test
    fun githubDownMimitUp() {
        now = Instant.parse("2026-10-03T08:00:00Z")
        server.failure = IOException("no route to host")
        server.mimit = mimitFiles("2026-10-02")
        val result = repository().refresh(null)
        assertEquals(DataOrigin.MIMIT, (result as RefreshResult.Updated).snapshot.origin)
    }

    @Test
    fun publishedCopyNeedingNewerAppStillLetsMimitThrough() {
        now = Instant.parse("2026-10-03T08:00:00Z")
        server.publish(dataset("2026-10-02", schemaVersion = 2))
        server.mimit = mimitFiles("2026-10-02")
        val result = repository().refresh(null)
        assertEquals(DataOrigin.MIMIT, (result as RefreshResult.Updated).snapshot.origin)
    }

    @Test
    fun cacheWithoutOriginFileCameFromGithub() {
        val repo = repository()
        repo.refresh(null)
        File(dir, "origin").delete()
        assertEquals(DataOrigin.PAGES, repo.loadCached()!!.origin)
    }

    // --- helpers ---

    data class Dataset(val meta: ByteArray, val stations: ByteArray)

    /** The pipeline fixture, re-dated, with a matching meta.json. */
    private fun dataset(date: String, schemaVersion: Int = 1): Dataset {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/fixtures/stations.json")).use { it.readBytes() }
        val stations = String(fixture).replace("\"dataDate\":\"2026-09-30\"", "\"dataDate\":\"$date\"").toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(stations).joinToString("") { "%02x".format(it) }
        val meta = """{"schemaVersion":$schemaVersion,"dataDate":"$date","pricesAt":"${date}T08:00:00+02:00",
            "stations":3,"prices":10,"file":"stations.json","bytes":${stations.size},"sha256":"$sha"}"""
        return Dataset(meta.toByteArray(), stations)
    }

    /** The same data published again with a different file (e.g. after a pipeline fix). */
    private fun republished(dataset: Dataset): Dataset {
        val stations = dataset.stations + "\n".toByteArray()
        val meta = String(dataset.meta)
            .replace(Regex("\"bytes\":\\d+"), "\"bytes\":${stations.size}")
            .replace(Regex("\"sha256\":\"[0-9a-f]+\""), "\"sha256\":\"${sha256(stations)}\"")
        return Dataset(meta.toByteArray(), stations)
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * MIMIT's two files for [date]: (prices, stations). Two stations with
     * prices, as in the real files (quirks are tested in :core).
     */
    private fun mimitFiles(date: String): Pair<String, String> {
        val d = java.time.LocalDate.parse(date)
        val reported = "%02d/%02d/%d 07:00:00".format(d.dayOfMonth, d.monthValue, d.year)
        val prices = """
            Estrazione del $date
            idImpianto|descCarburante|prezzo|isSelf|dtComu
            1|Benzina|1.849|1|$reported
            1|Gasolio|1.749|1|$reported
            2|Benzina|1.899|0|$reported
        """.trimIndent() + "\n"
        val stations = """
            Estrazione del $date
            idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine
            1|X|Agip Eni|Stradale|UNO|VIA ROMA 1|PISA|PI|43.7|10.4
            2|Y|Q8|Autostradale|DUE|A12 KM 3|PISA|PI|43.71|10.41
        """.trimIndent() + "\n"
        return prices to stations
    }

    private class FakeServer : Fetcher {
        val requests = mutableListOf<String>()
        /** GitHub Pages failure. */
        var failure: IOException? = null
        /** MIMIT failure; by default MIMIT doesn't answer (as from GitHub's machines). */
        var mimitFailure: IOException? = SocketTimeoutException("connect timed out")
        var honourRange = true
        var mimit: Pair<String, String>? = null
            set(value) {
                field = value
                mimitFailure = null
            }
        private var files = emptyMap<String, ByteArray>()

        override fun openStart(url: String, bytes: Int): InputStream {
            val name = url.removePrefix(BASE)
            if (name != MIMIT_PRICES || !honourRange) return open(url)
            requests += "$name (start)"
            mimitFailure?.let { throw it }
            return mimitFile(name).copyOf(bytes).inputStream()
        }

        private fun mimitFile(name: String): ByteArray {
            val (prices, stations) = mimit ?: throw ServerException(404)
            return (if (name == MIMIT_PRICES) prices else stations).toByteArray()
        }

        fun publish(dataset: Dataset) {
            files = mapOf("meta.json" to dataset.meta, "stations.json" to dataset.stations)
        }

        fun sha256(name: String) =
            MessageDigest.getInstance("SHA-256").digest(files.getValue(name)).joinToString("") { "%02x".format(it) }

        override fun open(url: String): InputStream {
            val name = url.removePrefix(BASE)
            if (name.startsWith("mimit/")) {
                requests += if (name == MIMIT_PRICES && !honourRange && "$name (start)" !in requests) {
                    "$name (start)"
                } else {
                    name
                }
                mimitFailure?.let { throw it }
                return mimitFile(name).inputStream()
            }
            requests += name
            failure?.let { throw it }
            return (files[name] ?: throw ServerException(404)).inputStream()
        }
    }

    private companion object {
        const val BASE = "https://example.test/"
        const val MIMIT_PRICES = "mimit/prezzo_alle_8.csv"
        const val MIMIT_STATIONS = "mimit/anagrafica_impianti_attivi.csv"
        /** The test files are tiny: no size checks. */
        val NO_LIMITS = Thresholds(minStations = 0, minPrices = 0, maxDropRatio = 1.0)
    }
}
