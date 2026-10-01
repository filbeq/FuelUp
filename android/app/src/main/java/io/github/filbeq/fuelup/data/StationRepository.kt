package io.github.filbeq.fuelup.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/** Cached data ready to show: the file plus the meta.json that describes it. */
class Snapshot(val meta: Meta, val stations: StationsFile) {
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
 * Downloads, validates and caches the station data in [dir].
 *
 * Files: `meta.json` + `stations.json` (the cache, always consistent with each
 * other) and `last_meta_check` (when meta.json was last fetched). A new
 * stations.json is downloaded to a temporary file, checked against meta.json
 * (size, SHA-256, schema, date) and parsed before it replaces the cache, so a
 * bad download never breaks what the app already has.
 *
 * Blocking: call it from a background thread.
 */
class StationRepository(
    private val dir: File,
    private val fetcher: Fetcher,
    private val clock: () -> Instant = Instant::now,
    private val baseUrl: String = DataSource.BASE_URL,
    /** Receives timings (label, milliseconds) for performance logging. */
    private val trace: (String, Long) -> Unit = { _, _ -> },
) {
    private val metaFile = File(dir, DataSource.META_FILE)
    private val stationsFile = File(dir, DataSource.STATIONS_FILE)
    private val lastCheckFile = File(dir, "last_meta_check")

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
                Snapshot(meta, stations)
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) { // includes SerializationException
            null
        }
    }

    /** Brings the cache up to date if needed. [current] is what is cached/shown now. */
    fun refresh(current: Snapshot?, force: Boolean = false): RefreshResult {
        val now = clock()
        if (!RefreshPolicy.shouldCheckMeta(current?.dataDate, lastMetaCheck(), now, force)) {
            return RefreshResult.UpToDate
        }
        dir.mkdirs()
        return try {
            val metaBytes = timed("download meta.json") {
                fetcher.open(baseUrl + DataSource.META_FILE).use { it.readBytes() }
            }
            val published = metaBytes.inputStream().use(StationDataJson::parseMeta)
            lastCheckFile.writeText(now.toEpochMilli().toString())
            when {
                published.schemaVersion != SUPPORTED_SCHEMA_VERSION -> RefreshResult.UpdateRequired
                !RefreshPolicy.needsDownload(current?.meta, published) -> RefreshResult.UpToDate
                else -> downloadStations(published, metaBytes)
            }
        } catch (e: ServerException) {
            RefreshResult.Failed
        } catch (e: IllegalArgumentException) { // malformed JSON (SerializationException)
            RefreshResult.Failed
        } catch (e: IOException) {
            RefreshResult.Offline
        }
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
            val metaTmp = File(dir, DataSource.META_FILE + ".tmp")
            metaTmp.writeBytes(metaBytes)
            replace(metaTmp, metaFile)
            return RefreshResult.Updated(Snapshot(published, stations))
        } finally {
            tmp.delete()
        }
    }

    private fun replace(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun lastMetaCheck(): Instant? = try {
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
}
