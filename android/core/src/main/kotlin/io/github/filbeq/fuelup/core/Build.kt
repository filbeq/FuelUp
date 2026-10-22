package io.github.filbeq.fuelup.core

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/* Join stations and prices and write the published files (stations.json, meta.json). */

/**
 * Bump only for breaking changes; the app rejects unknown major versions.
 * Adding new keys is not a breaking change (the app must ignore unknown keys).
 */
const val SCHEMA_VERSION = 1
const val STATIONS_FILE = "stations.json"
const val META_FILE = "meta.json"
const val SOURCE = "Ministero delle Imprese e del Made in Italy — Osservaprezzi Carburanti"
const val LICENSE = "IODL-2.0"

/** The data looks wrong: stop instead of publishing it. */
class PipelineError(message: String) : Exception(message)

/** Run-level sanity checks. Defaults fit the real files (~21k stations, ~92k prices). */
data class Thresholds(
    val minStations: Int = 15_000,
    val minPrices: Int = 50_000,
    /** Per input file. */
    val maxDropRatio: Double = 0.10,
)

/** One entry of the fuel table. */
data class FuelEntry(val name: String, val info: FuelInfo)

/** One price of a station: `[fuelIndex, priceMilli, self, updatedEpochSeconds]`. */
typealias PriceEntry = LongArray

/** The content of stations.json. */
class Document(
    val dataDate: LocalDate,
    val brands: List<String>,
    val fuels: List<FuelEntry>,
    val stations: List<Pair<Station, List<PriceEntry>>>,
) {
    val priceCount: Int get() = stations.sumOf { it.second.size }
}

class BuildResult(val document: Document, val joinReport: DropReport)

fun build(stations: StationFile, prices: PriceFile, thresholds: Thresholds = Thresholds()): BuildResult {
    if (stations.extractionDate != prices.extractionDate) {
        throw PipelineError(
            "extraction dates differ: stations ${stations.extractionDate}, prices ${prices.extractionDate}"
        )
    }
    for (report in listOf(stations.report, prices.report)) {
        if (report.rowsRead > 0 && report.droppedTotal.toDouble() / report.rowsRead > thresholds.maxDropRatio) {
            throw PipelineError("${report.name}: ${report.droppedTotal} of ${report.rowsRead} rows dropped")
        }
    }

    val joinReport = DropReport("join")
    // In order of each station's first price, as the fuel table's tie-breaks depend on it.
    val byStation = LinkedHashMap<Long, MutableList<Price>>()
    for (price in prices.prices) {
        if (price.stationId in stations.stations) {
            byStation.getOrPut(price.stationId) { mutableListOf() }.add(price)
        } else {
            joinReport.drop("price for unknown station", price.stationId)
        }
    }
    for (stationId in stations.stations.keys) {
        if (stationId !in byStation) joinReport.drop("station without valid prices", stationId)
    }

    val kept = byStation.keys.sorted().map { stations.stations.getValue(it) }
    val priceCount = byStation.values.sumOf { it.size }
    if (kept.size < thresholds.minStations || priceCount < thresholds.minPrices) {
        throw PipelineError("too little data: ${kept.size} stations, $priceCount prices")
    }

    return BuildResult(document(stations.extractionDate, kept, byStation), joinReport)
}

private fun document(dataDate: LocalDate, kept: List<Station>, byStation: Map<Long, List<Price>>): Document {
    // Ties (same name in another case) in code point order, so the output never
    // depends on hashing; the Python pipeline left them to set order.
    val brands = kept.map { it.brand }.toSortedSet(CODE_POINT_ORDER).sortedWith(
        compareBy(CODE_POINT_ORDER) { casefold(it) }
    )

    // Fuel table: grouped by type (enum order), standard fuel first, then by name.
    // Same-key ties keep first appearance (stable sort).
    val fuelInfos = LinkedHashMap<String, FuelInfo>()
    for (list in byStation.values) for (p in list) fuelInfos[p.fuelName] = p.fuel
    val fuelNames = fuelInfos.keys.sortedWith(
        compareBy<String> { fuelInfos.getValue(it).type.ordinal }
            .thenBy { !fuelInfos.getValue(it).std }
            .thenBy(CODE_POINT_ORDER) { casefold(it) }
    )
    val fuelIndex = fuelNames.withIndex().associate { (i, n) -> n to i }

    val stations = kept.map { s ->
        val entries = byStation.getValue(s.id)
            .map { p ->
                longArrayOf(
                    fuelIndex.getValue(p.fuelName).toLong(),
                    p.priceMilli,
                    if (p.isSelf) 1 else 0,
                    p.updated.epochSecond,
                )
            }
            // by fuel, then served before self-service
            .sortedWith(compareBy<LongArray> { it[0] }.thenBy { it[2] })
        s to entries
    }
    return Document(
        dataDate = dataDate,
        brands = brands.toList(),
        fuels = fuelNames.map { FuelEntry(it, fuelInfos.getValue(it)) },
        stations = stations,
    )
}

/** Compact JSON of stations.json, exactly as the Python pipeline wrote it. */
fun writeStationsJson(document: Document, out: OutputStream) {
    val w = out.bufferedWriter(Charsets.UTF_8)
    w.write("{\"schemaVersion\":$SCHEMA_VERSION,\"dataDate\":")
    w.write(jsonString(document.dataDate.toString()))
    w.write(",\"brands\":[")
    document.brands.forEachIndexed { i, b ->
        if (i > 0) w.write(",")
        w.write(jsonString(b))
    }
    w.write("],\"fuels\":[")
    document.fuels.forEachIndexed { i, f ->
        if (i > 0) w.write(",")
        w.write("{\"name\":${jsonString(f.name)},\"type\":\"${f.info.type}\",\"unit\":\"${f.info.unit.code}\",\"std\":${f.info.std}}")
    }
    w.write("],\"stations\":[")
    val brandIndex = document.brands.withIndex().associate { (i, b) -> b to i }
    document.stations.forEachIndexed { i, (s, entries) ->
        if (i > 0) w.write(",")
        w.write("{\"id\":${s.id},\"n\":${jsonString(s.name)},\"b\":${brandIndex.getValue(s.brand)}")
        w.write(",\"hw\":${if (s.motorway) 1 else 0},\"a\":${jsonString(s.address)}")
        w.write(",\"c\":${jsonString(s.municipality)},\"pr\":${jsonString(s.province)}")
        w.write(",\"lat\":${coordJson(s.lat)},\"lon\":${coordJson(s.lon)},\"f\":[")
        entries.forEachIndexed { j, e ->
            if (j > 0) w.write(",")
            w.write("[${e[0]},${e[1]},${e[2]},${e[3]}]")
        }
        w.write("]}")
    }
    w.write("]}")
    w.flush()
}

/** The content of meta.json (written with [writeMetaJson]). */
data class Meta(
    val dataDate: LocalDate,
    val generatedAt: Instant,
    val stations: Int,
    val prices: Int,
    val bytes: Long,
    val sha256: String,
)

/** meta.json as Python's `json.dumps(meta, ensure_ascii=False, indent=2)` wrote it. */
fun metaJson(meta: Meta): String {
    val generated = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .format(meta.generatedAt.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC))
    val fields = listOf(
        "schemaVersion" to "$SCHEMA_VERSION",
        "dataDate" to jsonString(meta.dataDate.toString()),
        "pricesAt" to jsonString(pricesAt(meta.dataDate).toOffsetDateTime().toString().let(::withSeconds)),
        "generatedAt" to jsonString(generated),
        "stations" to "${meta.stations}",
        "prices" to "${meta.prices}",
        "file" to jsonString(STATIONS_FILE),
        "bytes" to "${meta.bytes}",
        "sha256" to jsonString(meta.sha256),
        "source" to jsonString(SOURCE),
        "license" to jsonString(LICENSE),
    )
    return fields.joinToString(",\n", prefix = "{\n", postfix = "\n}") { (k, v) -> "  \"$k\": $v" }
}

/** Java leaves out ":00" seconds ("2026-09-30T08:00+02:00"); Python's isoformat keeps them. */
private fun withSeconds(iso: String): String =
    if (iso.length == "2026-09-30T08:00+02:00".length) iso.substring(0, 16) + ":00" + iso.substring(16) else iso

/**
 * Write stations.json, then meta.json (last, so it never points to a missing
 * file), each through a temporary file and an atomic rename.
 */
fun writeOutput(document: Document, outDir: File, generatedAt: Instant = Instant.now()): Meta {
    outDir.mkdirs()
    val digest = MessageDigest.getInstance("SHA-256")
    val stationsTmp = File(outDir, "$STATIONS_FILE.tmp")
    var bytes = 0L
    stationsTmp.outputStream().use { file ->
        val counting = object : OutputStream() {
            override fun write(b: Int) {
                file.write(b); digest.update(b.toByte()); bytes++
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                file.write(b, off, len); digest.update(b, off, len); bytes += len
            }
        }
        writeStationsJson(document, counting)
    }
    moveAtomic(stationsTmp, File(outDir, STATIONS_FILE))

    val meta = Meta(
        dataDate = document.dataDate,
        generatedAt = generatedAt,
        stations = document.stations.size,
        prices = document.priceCount,
        bytes = bytes,
        sha256 = digest.digest().joinToString("") { "%02x".format(it) },
    )
    val metaTmp = File(outDir, "$META_FILE.tmp")
    metaTmp.writeText(metaJson(meta), Charsets.UTF_8)
    moveAtomic(metaTmp, File(outDir, META_FILE))
    return meta
}

private fun moveAtomic(source: File, target: File) {
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/** A JSON string as Python's json module writes it with ensure_ascii=False. */
internal fun jsonString(s: String): String = buildString(s.length + 2) {
    append('"')
    for (c in s) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

/**
 * A coordinate rounded to 5 decimals, as Python's repr writes it ("44.88012",
 * "13.0"): the shortest text that reads back as the same number. Independent of
 * the JVM's own Double.toString, which differs between versions and Android.
 */
internal fun coordJson(x: Double): String {
    // x is the double nearest to units / 100000, so x * 1e5 is within far less
    // than 0.5 of that integer.
    val units = Math.rint(x * 1e5).toLong()
    val sign = if (units < 0) "-" else ""
    val abs = kotlin.math.abs(units)
    val fraction = (abs % 100_000).toString().padStart(5, '0').trimEnd('0').ifEmpty { "0" }
    return "$sign${abs / 100_000}.$fraction"
}
