package io.github.filbeq.fuelup.cli

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
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

/*
 * fuel-data [--prices FILE --stations FILE] [--data-dir DIR] [--out DIR]
 *
 * Without --prices/--stations the latest files are downloaded from MIMIT first.
 * Exit code 0 on success, 1 when the data could not be downloaded or looks
 * broken (then no output is written), 2 for wrong arguments.
 */

// Verified on the official dataset page on 2026-10-01:
// https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti
const val PRICES_URL = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
const val STATIONS_URL = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"

private const val USER_AGENT = "fuel-up-pipeline/0.2 (open data reuse; Java)"
private const val TIMEOUT_MS = 60_000
private const val ATTEMPTS = 3
private const val RETRY_DELAY_MS = 10_000L

private const val USAGE = "usage: fuel-data [--prices FILE --stations FILE] [--data-dir DIR] [--out DIR]"

fun main(args: Array<String>) {
    exitProcess(run(args.toList()))
}

fun run(args: List<String>, thresholds: Thresholds = Thresholds()): Int {
    val options = parseArgs(args) ?: run {
        System.err.println(USAGE)
        return 2
    }
    var prices = options["--prices"]?.let(::File)
    var stations = options["--stations"]?.let(::File)
    if ((prices == null) != (stations == null)) {
        System.err.println("--prices and --stations must be given together")
        return 2
    }
    val dataDir = File(options["--data-dir"] ?: "data")
    val outDir = File(options["--out"] ?: "out")

    return try {
        if (prices == null || stations == null) {
            prices = download(PRICES_URL, File(dataDir, "prezzo_alle_8.csv"))
            stations = download(STATIONS_URL, File(dataDir, "anagrafica_impianti_attivi.csv"))
        }
        val stationFile = stations.inputStream().useMimitLines(::parseStations)
        val priceFile = prices.inputStream().useMimitLines(::parsePrices)
        stationFile.report.lines().forEach(::info)
        priceFile.report.lines().forEach(::info)
        val result = build(stationFile, priceFile, thresholds)
        result.joinReport.lines().forEach(::info)
        val meta = writeOutput(result.document, outDir)
        info("wrote ${File(outDir, "stations.json")} (${meta.bytes} bytes, ${meta.stations} stations, ${meta.prices} prices)")
        0
    } catch (e: IOException) {
        error(e)
    } catch (e: FormatError) {
        error(e)
    } catch (e: PipelineError) {
        error(e)
    }
}

private fun error(e: Exception): Int {
    System.err.println("ERROR pipeline failed: ${e.message ?: e}")
    return 1
}

private fun info(line: String) = System.err.println("INFO $line")

private fun parseArgs(args: List<String>): Map<String, String>? {
    val known = setOf("--prices", "--stations", "--data-dir", "--out")
    if (args.size % 2 != 0) return null
    val options = args.chunked(2).associate { (k, v) -> k to v }
    return options.takeIf { it.keys.all(known::contains) }
}

/** Save [url] to [dest], retrying a few times on network errors. */
private fun download(url: String, dest: File): File {
    dest.parentFile?.mkdirs()
    var attempt = 1
    while (true) {
        try {
            val payload = fetch(url)
            val tmp = File(dest.path + ".tmp")
            tmp.writeBytes(payload)
            if (!tmp.renameTo(dest)) throw IOException("cannot rename $tmp")
            info("downloaded $url (${payload.size} bytes)")
            return dest
        } catch (e: IOException) {
            System.err.println("WARNING download of $url failed (attempt $attempt/$ATTEMPTS): $e")
            if (attempt == ATTEMPTS) throw e
            Thread.sleep(RETRY_DELAY_MS * attempt)
            attempt++
        }
    }
}

private fun fetch(url: String): ByteArray {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${connection.responseCode}")
        }
        return connection.inputStream.use { it.readBytes() }
    } finally {
        connection.disconnect()
    }
}
