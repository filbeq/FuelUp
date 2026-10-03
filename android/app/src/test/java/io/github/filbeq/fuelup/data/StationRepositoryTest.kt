package io.github.filbeq.fuelup.data

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

    private fun repository() = StationRepository(dir, server, clock = { now }, baseUrl = BASE)

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

        // Not published yet: only meta.json is fetched.
        assertEquals(RefreshResult.UpToDate, repo.refresh(repo.loadCached()))
        assertEquals(listOf("meta.json"), server.requests)

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

        assertEquals(RefreshResult.Offline, repo.refresh(repo.loadCached()))
        assertEquals("2026-09-30", repo.loadCached()!!.meta.dataDate)
    }

    @Test
    fun serverErrorFails() {
        server.failure = ServerException(404)
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
        assertEquals(listOf("meta.json"), server.requests) // stations.json not downloaded
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

    private class FakeServer : Fetcher {
        val requests = mutableListOf<String>()
        var failure: IOException? = null
        private var files = emptyMap<String, ByteArray>()

        fun publish(dataset: Dataset) {
            files = mapOf("meta.json" to dataset.meta, "stations.json" to dataset.stations)
        }

        fun sha256(name: String) =
            MessageDigest.getInstance("SHA-256").digest(files.getValue(name)).joinToString("") { "%02x".format(it) }

        override fun open(url: String): InputStream {
            val name = url.removePrefix(BASE)
            requests += name
            failure?.let { throw it }
            return (files[name] ?: throw ServerException(404)).inputStream()
        }
    }

    private companion object {
        const val BASE = "https://example.test/"
    }
}
