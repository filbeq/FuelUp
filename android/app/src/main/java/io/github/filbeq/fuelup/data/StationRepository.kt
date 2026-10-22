package io.github.filbeq.fuelup.data

import io.github.filbeq.fuelup.core.FormatError
import io.github.filbeq.fuelup.core.PipelineError
import io.github.filbeq.fuelup.core.Thresholds
import io.github.filbeq.fuelup.core.build
import io.github.filbeq.fuelup.core.parsePrices
import io.github.filbeq.fuelup.core.parseStations
import io.github.filbeq.fuelup.core.useMimitLines
import io.github.filbeq.fuelup.core.writeOutput
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** Cached data ready to show: the file plus the meta.json that describes it. */
class Snapshot(val meta: Meta, val stations: StationsFile, val origin: DataOrigin = DataOrigin.PAGES) {
    val dataDate: LocalDate get() = LocalDate.parse(meta.dataDate)
}

/** Outcome of [StationRepository.refresh]. */
sealed interface RefreshResult {
    /** Nothing new (or not time to check yet): keep what is shown. */
    data object UpToDate : RefreshResult
    data class Updated(val snapshot: Snapshot) : RefreshResult
    /** No connection or connection lost (including timeouts). */
    data object Offline : RefreshResult
    /** Server error, or a downloaded file that failed validation. */
    data object Failed : RefreshResult
    /** The published data uses a schema this app version cannot read. */
    data object UpdateRequired : RefreshResult
}

/**
 * Downloads, validates and caches the station data in [dir], from one of two
 * sources ([DataOrigin]):
 * - the copy published on GitHub Pages, when it already holds the newest data
 *   MIMIT has (1.5 MB compressed);
 * - otherwise MIMIT's two CSV files (7.5 MB, not compressed), cleaned on the
 *   phone by the same code that builds the published copy (`:core`), with the
 *   same safety checks. If that fails (no answer from outside Italy, a format
 *   change, data that fails the checks), the published copy is used when it
 *   is newer than the cache.
 *
 * Files: `meta.json` + `stations.json` (the cache, always consistent with each
 * other), `origin` (where they came from) and `last_meta_check` (when a server
 * was last asked for its data date). New data is written to temporary files
 * and checked (size, SHA-256, schema, date) and parsed before it replaces the
 * cache, so a bad download never breaks what the app already has.
 *
 * Blocking: call it from a background thread.
 */
class StationRepository(
    private val dir: File,
    private val fetcher: Fetcher,
    private val clock: () -> Instant = Instant::now,
    private val baseUrl: String = DataSource.BASE_URL,
    private val mimitPricesUrl: String = DataSource.MIMIT_PRICES_URL,
    private val mimitStationsUrl: String = DataSource.MIMIT_STATIONS_URL,
    /** The pipeline's safety checks; smaller in tests. */
    private val thresholds: Thresholds = Thresholds(),
    /** Receives timings (label, milliseconds) for performance logging. */
    private val trace: (String, Long) -> Unit = { _, _ -> },
) {
    private val metaFile = File(dir, DataSource.META_FILE)
    private val stationsFile = File(dir, DataSource.STATIONS_FILE)
    private val originFile = File(dir, "origin")
    private val lastCheckFile = File(dir, "last_meta_check")

    /**
     * Whether the last attempt to reach MIMIT got no answer at all (as from
     * outside Italy), or null if MIMIT wasn't tried in this session.
     */
    @Volatile
    var mimitUnreachable: Boolean? = null
        private set

    /** The cached data, or null if there is none or it can't be read. */
    fun loadCached(): Snapshot? {
        if (!metaFile.exists() || !stationsFile.exists()) return null
        return try {
            val meta = metaFile.inputStream().use(StationDataJson::parseMeta)
            if (meta.schemaVersion != SUPPORTED_SCHEMA_VERSION) return null
            val stations = timed("parse cached stations.json") {
                stationsFile.inputStream().buffered().use(StationDataJson::parseStations)
            }
            if (stations.schemaVersion != SUPPORTED_SCHEMA_VERSION || stations.dataDate != meta.dataDate) {
                null
            } else {
                Snapshot(meta, stations, cachedOrigin())
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) { // includes SerializationException
            null
        }
    }

    /**
     * Brings the cache up to date if needed. [current] is what is cached/shown now.
     * [force] ignores the hourly limit (Retry); [manual] ("Update data now")
     * always checks the server, even when the cache already holds the newest
     * date, so a file republished for the same date is picked up too.
     */
    fun refresh(current: Snapshot?, force: Boolean = false, manual: Boolean = false): RefreshResult {
        val now = clock()
        if (!manual && !RefreshPolicy.shouldCheckMeta(current?.dataDate, lastMetaCheck(), now, force)) {
            return RefreshResult.UpToDate
        }
        dir.mkdirs()

        // 1. The published copy, if it already has the newest possible date.
        val pages = checkPages(now)
        val published = (pages as? PagesCheck.Found)?.meta
        val publishedDate = published?.let { LocalDate.parse(it.dataDate) }
        if (published != null && !publishedDate!!.isBefore(RefreshPolicy.newestPossibleDataDate(now))) {
            return if (RefreshPolicy.needsDownload(current?.meta, published)) {
                downloadPages(published, (pages as PagesCheck.Found).bytes)
            } else {
                RefreshResult.UpToDate
            }
        }

        // 2. MIMIT directly, when it has something newer than the cache and the published copy.
        val direct = refreshFromMimit(current, publishedDate, now)
        if (direct is DirectResult.Done) return direct.result

        // 3. Otherwise the published copy, if newer than the cache.
        if (published != null && (current == null || publishedDate!!.isAfter(current.dataDate))) {
            return downloadPages(published, (pages as PagesCheck.Found).bytes)
        }
        return when {
            pages == PagesCheck.UpdateRequired -> RefreshResult.UpdateRequired
            direct == DirectResult.NothingNewer -> RefreshResult.UpToDate
            direct == DirectResult.Failed -> RefreshResult.Failed
            // MIMIT unreachable (e.g. from abroad): offline only if GitHub didn't answer either.
            pages == PagesCheck.Offline -> RefreshResult.Offline
            pages == PagesCheck.Failed -> RefreshResult.Failed
            else -> RefreshResult.UpToDate
        }
    }

    private sealed interface PagesCheck {
        class Found(val meta: Meta, val bytes: ByteArray) : PagesCheck
        data object UpdateRequired : PagesCheck
        data object Offline : PagesCheck
        data object Failed : PagesCheck
    }

    /** The published meta.json. */
    private fun checkPages(now: Instant): PagesCheck = try {
        val metaBytes = timed("download meta.json") {
            fetcher.open(baseUrl + DataSource.META_FILE).use { it.readBytes() }
        }
        val published = metaBytes.inputStream().use(StationDataJson::parseMeta)
        lastCheckFile.writeText(now.toEpochMilli().toString())
        if (published.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            PagesCheck.UpdateRequired
        } else {
            PagesCheck.Found(published, metaBytes)
        }
    } catch (e: ServerException) {
        PagesCheck.Failed
    } catch (e: IllegalArgumentException) { // malformed JSON (SerializationException)
        PagesCheck.Failed
    } catch (e: IOException) {
        PagesCheck.Offline
    }

    private sealed interface DirectResult {
        class Done(val result: RefreshResult) : DirectResult
        /** MIMIT has nothing newer than the cache or the published copy. */
        data object NothingNewer : DirectResult
        /** No answer (timeout, no connection). */
        data object Unreachable : DirectResult
        /** An answer, but not usable data (HTTP error, format change, failed checks). */
        data object Failed : DirectResult
    }

    private fun refreshFromMimit(current: Snapshot?, publishedDate: LocalDate?, now: Instant): DirectResult {
        val pricesTmp = File(dir, "prices.csv.tmp")
        val stationsTmp = File(dir, "stations.csv.tmp")
        val outTmp = File(dir, "mimit.tmp")
        var answered = false
        try {
            val mimitDate = timed("check MIMIT date") { mimitDate() }
            answered = true
            mimitUnreachable = false
            lastCheckFile.writeText(now.toEpochMilli().toString())
            val newest = listOfNotNull(current?.dataDate, publishedDate).maxOrNull()
            // Same date as the published copy: that one is smaller to download.
            if (newest != null && !mimitDate.isAfter(newest)) return DirectResult.NothingNewer

            val pricesBytes = timed("download MIMIT prices") { download(mimitPricesUrl, pricesTmp) }
            val stationsBytes = timed("download MIMIT stations") { download(mimitStationsUrl, stationsTmp) }
            trace("MIMIT files: $pricesBytes + $stationsBytes bytes", 0)
            val meta = timed("clean MIMIT data") {
                val stations = timed("  parse stations CSV") { stationsTmp.inputStream().useMimitLines(::parseStations) }
                val prices = timed("  parse prices CSV") { pricesTmp.inputStream().useMimitLines(::parsePrices) }
                val document = timed("  join") { build(stations, prices, thresholds).document }
                trace("heap after cleaning: ${usedHeapMb()} MB", 0)
                outTmp.deleteRecursively()
                timed("  write JSON") { writeOutput(document, outTmp) }
            }
            pricesTmp.delete()
            stationsTmp.delete()

            val stations = timed("parse cleaned stations.json") {
                File(outTmp, DataSource.STATIONS_FILE).inputStream().buffered().use(StationDataJson::parseStations)
            }
            val metaBytes = File(outTmp, DataSource.META_FILE).readBytes()
            val parsedMeta = metaBytes.inputStream().use(StationDataJson::parseMeta)
            if (current != null && !meta.dataDate.isAfter(current.dataDate)) return DirectResult.NothingNewer

            replace(File(outTmp, DataSource.STATIONS_FILE), stationsFile)
            writeOrigin(DataOrigin.MIMIT)
            replace(File(outTmp, DataSource.META_FILE), metaFile)
            return DirectResult.Done(RefreshResult.Updated(Snapshot(parsedMeta, stations, DataOrigin.MIMIT)))
        } catch (e: ServerException) {
            mimitUnreachable = false // it answered
            trace("MIMIT: $e", 0)
            return DirectResult.Failed
        } catch (e: FormatError) {
            trace("MIMIT format: ${e.message}", 0)
            return DirectResult.Failed
        } catch (e: PipelineError) {
            trace("MIMIT data rejected: ${e.message}", 0)
            return DirectResult.Failed
        } catch (e: IllegalArgumentException) { // our own output unreadable: never expected
            trace("MIMIT output unreadable: ${e.message}", 0)
            return DirectResult.Failed
        } catch (e: IOException) {
            if (!answered) mimitUnreachable = true
            trace("MIMIT unreachable: $e", 0)
            return DirectResult.Unreachable
        } finally {
            pricesTmp.delete()
            stationsTmp.delete()
            outTmp.deleteRecursively()
        }
    }

    /** MIMIT's data date, from the first line of the prices file ("Estrazione del 2026-10-21"). */
    private fun mimitDate(): LocalDate {
        val head = fetcher.openStart(mimitPricesUrl, MIMIT_HEAD_BYTES).use { input ->
            val buffer = ByteArray(MIMIT_HEAD_BYTES)
            var n = 0
            while (n < buffer.size) {
                val read = input.read(buffer, n, buffer.size - n)
                if (read < 0) break
                n += read
            }
            String(buffer, 0, n, Charsets.UTF_8)
        }
        val match = MIMIT_DATE.find(head.lineSequence().first()) ?: throw FormatError("unexpected first line")
        return LocalDate.parse(match.groupValues[1])
    }

    /** Saves [url] to [file]; returns its size. */
    private fun download(url: String, file: File): Long = fetcher.open(url).use { input ->
        file.outputStream().use { input.copyTo(it, 64 * 1024) }
    }

    private fun downloadPages(published: Meta, metaBytes: ByteArray): RefreshResult = try {
        downloadStations(published, metaBytes)
    } catch (e: ServerException) {
        RefreshResult.Failed
    } catch (e: IOException) {
        RefreshResult.Offline
    }

    private fun downloadStations(published: Meta, metaBytes: ByteArray): RefreshResult {
        val tmp = File(dir, DataSource.STATIONS_FILE + ".tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val size = timed("download stations.json") {
                fetcher.open(baseUrl + DataSource.STATIONS_FILE).use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            total += read
                        }
                        total
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (size != published.bytes || sha != published.sha256) return RefreshResult.Failed

            val stations = try {
                timed("parse downloaded stations.json") {
                    tmp.inputStream().buffered().use(StationDataJson::parseStations)
                }
            } catch (e: IllegalArgumentException) {
                return RefreshResult.Failed
            }
            if (stations.schemaVersion != SUPPORTED_SCHEMA_VERSION) return RefreshResult.UpdateRequired
            if (stations.dataDate != published.dataDate) return RefreshResult.Failed

            // Replace the cache: stations first, then the meta.json that vouches for it.
            // loadCached() rejects a mismatched pair if the app dies in between.
            replace(tmp, stationsFile)
            writeOrigin(DataOrigin.PAGES)
            val metaTmp = File(dir, DataSource.META_FILE + ".tmp")
            metaTmp.writeBytes(metaBytes)
            replace(metaTmp, metaFile)
            return RefreshResult.Updated(Snapshot(published, stations, DataOrigin.PAGES))
        } finally {
            tmp.delete()
        }
    }

    private fun replace(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun writeOrigin(origin: DataOrigin) {
        val tmp = File(dir, "origin.tmp")
        tmp.writeText(origin.name)
        replace(tmp, originFile)
    }

    /** Where the cache came from; caches older than the file came from GitHub Pages. */
    private fun cachedOrigin(): DataOrigin = try {
        DataOrigin.valueOf(originFile.readText().trim())
    } catch (e: IOException) {
        DataOrigin.PAGES
    } catch (e: IllegalArgumentException) {
        DataOrigin.PAGES
    }

    private fun usedHeapMb(): Long = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / (1024 * 1024) }

    /** When a server last told us its data date, or null if never. */
    fun lastMetaCheck(): Instant? = try {
        Instant.ofEpochMilli(lastCheckFile.readText().trim().toLong())
    } catch (e: IOException) {
        null
    } catch (e: NumberFormatException) {
        null
    }

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        trace(label, (System.nanoTime() - start) / 1_000_000)
        return result
    }

    private companion object {
        /** Enough for "Estrazione del YYYY-MM-DD" and the line break. */
        const val MIMIT_HEAD_BYTES = 64
        val MIMIT_DATE = Regex("""^\uFEFF?Estrazione del ([0-9]{4}-[0-9]{2}-[0-9]{2})\s*$""")
    }
}
