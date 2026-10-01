package io.github.filbeq.fuelup.data

import java.util.stream.IntStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/** How a station's price compares with nearby stations selling the same fuel. */
enum class PriceClass {
    CHEAP,
    AVERAGE,
    EXPENSIVE,
    /** Too few comparable stations nearby. */
    NOT_COMPARED,
    /** Far below the local price: possibly a data error. Never shown as cheap. */
    TO_VERIFY,
}

/** Which stations a station is compared with. */
enum class CompareGroup {
    ROAD,
    /** Motorway service areas, compared only with each other (they are dearer by design). */
    MOTORWAY,
    /** Outside the EU customs area (fuel legitimately much cheaper), compared only with each other. */
    DUTY_FREE,
}

class RankedPrice(
    val priceMilli: Long,
    val updatedEpochSeconds: Long,
    val group: CompareGroup,
    val priceClass: PriceClass,
    /** Price minus the median of the neighbours, in thousandths of a euro; null if not compared. */
    val diffFromMedianMilli: Double?,
)

/**
 * Compares each station's price with the **median of its nearest stations**
 * selling the same fuel/mode, in the same [CompareGroup]. Depends only on the
 * data and the fuel choice, never on what is on screen, so colours don't
 * change while panning. See DEVELOPMENT.md ("Price comparison") for the
 * reasoning behind the numbers, measured on real data.
 */
object PriceRanking {
    /** Who counts as a neighbour. */
    data class GroupRule(val neighbours: Int, val maxDistanceKm: Double, val minNeighbours: Int)

    val GROUP_RULES: Map<CompareGroup, GroupRule> = mapOf(
        CompareGroup.ROAD to GroupRule(neighbours = 25, maxDistanceKm = 50.0, minNeighbours = 5),
        CompareGroup.DUTY_FREE to GroupRule(neighbours = 25, maxDistanceKm = 50.0, minNeighbours = 5),
        // Service areas are ~30 km apart: fewer, farther neighbours.
        CompareGroup.MOTORWAY to GroupRule(neighbours = 8, maxDistanceKm = 100.0, minNeighbours = 3),
    )

    /**
     * Per fuel: [bandMilli] = how far from the local median counts as cheap or
     * expensive; [outlierMilli] = how far *below* it a price is flagged "to verify".
     * In thousandths of a euro (20 = 2 cents).
     */
    data class Thresholds(val bandMilli: Int, val outlierMilli: Int)

    val THRESHOLDS: Map<FuelKind, Thresholds> = mapOf(
        FuelKind.PETROL to Thresholds(bandMilli = 20, outlierMilli = 350),
        FuelKind.DIESEL to Thresholds(bandMilli = 20, outlierMilli = 350),
        FuelKind.LPG to Thresholds(bandMilli = 20, outlierMilli = 200),
        FuelKind.CNG to Thresholds(bandMilli = 20, outlierMilli = 500),
        FuelKind.LNG to Thresholds(bandMilli = 20, outlierMilli = 500),
        FuelKind.OTHER to Thresholds(bandMilli = 20, outlierMilli = 500),
    )

    /** Stations per parallel work unit. */
    private const val CHUNK = 256

    /** Municipalities outside the EU customs area (MIMIT spelling). */
    val DUTY_FREE_MUNICIPALITIES = setOf("LIVIGNO")

    fun groupOf(station: Station): CompareGroup = when {
        station.municipality.uppercase() in DUTY_FREE_MUNICIPALITIES -> CompareGroup.DUTY_FREE
        station.motorway == 1 -> CompareGroup.MOTORWAY
        else -> CompareGroup.ROAD
    }

    /** Ranks every station that sells [choice]; the others are absent from the result (by station id). */
    fun rank(stations: List<Station>, choice: FuelChoice, fuelIndices: Set<Int>): Map<Int, RankedPrice> {
        val thresholds = THRESHOLDS.getValue(choice.fuel)
        val priced = stations.mapNotNull { station -> station.priceFor(choice, fuelIndices)?.let { station to it } }
        val result = HashMap<Int, RankedPrice>(priced.size * 2)
        priced.groupBy { (station, _) -> groupOf(station) }.forEach { (group, members) ->
            val rule = GROUP_RULES.getValue(group)
            val points = members.map { (station, price) -> Point(station.lat, station.lon, price.priceMilli) }
            val index = GridIndex(points)
            // Each station is compared independently: spread the work over the CPU
            // cores, each worker with its own scratch buffers (a Searcher).
            val diffs = arrayOfNulls<Double>(members.size)
            IntStream.range(0, (members.size + CHUNK - 1) / CHUNK).parallel().forEach { chunk ->
                val searcher = index.Searcher()
                for (i in chunk * CHUNK until minOf(members.size, (chunk + 1) * CHUNK)) {
                    val neighbourPrices = searcher.nearestPrices(i, rule.neighbours, rule.maxDistanceKm)
                    diffs[i] = if (neighbourPrices.size < rule.minNeighbours) {
                        null
                    } else {
                        points[i].priceMilli - median(neighbourPrices)
                    }
                }
            }
            members.forEachIndexed { i, (station, price) ->
                result[station.id] = RankedPrice(
                    priceMilli = price.priceMilli,
                    updatedEpochSeconds = price.updatedEpochSeconds,
                    group = group,
                    priceClass = classify(diffs[i], thresholds),
                    diffFromMedianMilli = diffs[i],
                )
            }
        }
        return result
    }

    fun classify(diffMilli: Double?, thresholds: Thresholds): PriceClass = when {
        diffMilli == null -> PriceClass.NOT_COMPARED
        diffMilli <= -thresholds.outlierMilli -> PriceClass.TO_VERIFY
        diffMilli <= -thresholds.bandMilli -> PriceClass.CHEAP
        diffMilli >= thresholds.bandMilli -> PriceClass.EXPENSIVE
        else -> PriceClass.AVERAGE
    }

    private fun median(values: LongArray): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private class Point(val lat: Double, val lon: Double, val priceMilli: Long)

    /**
     * Finds nearest neighbours with a lat/lon grid: look at the station's cell,
     * then rings of cells around it, until enough stations are known to be
     * closer than any unvisited cell (or the distance limit is reached).
     * Runs ~20k times per fuel choice, so it avoids trigonometry per pair,
     * square roots, sorting and boxed numbers.
     */
    private class GridIndex(private val points: List<Point>) {
        // A plain 2D array of cells over the stations' bounding box (~600×600 for
        // all of Italy): no hashing or boxed keys in the hot loop.
        private val minRow = points.minOfOrNull { rowOf(it.lat) } ?: 0
        private val minCol = points.minOfOrNull { colOf(it.lon) } ?: 0
        private val rows = (points.maxOfOrNull { rowOf(it.lat) } ?: 0) - minRow + 1
        private val cols = (points.maxOfOrNull { colOf(it.lon) } ?: 0) - minCol + 1
        private val cells = arrayOfNulls<IntArray>(rows * cols)

        init {
            val counts = IntArray(rows * cols)
            points.forEach { counts[cellOf(it)]++ }
            val filled = IntArray(rows * cols)
            points.forEachIndexed { i, p ->
                val cell = cellOf(p)
                val members = cells[cell] ?: IntArray(counts[cell]).also { cells[cell] = it }
                members[filled[cell]++] = i
            }
        }

        private fun cellOf(p: Point) = (rowOf(p.lat) - minRow) * cols + (colOf(p.lon) - minCol)

        /** Does the searches; one per thread (it reuses its buffers between searches). */
        inner class Searcher {
            private var dist2 = DoubleArray(64) // squared distances, km²
            private var idx = IntArray(64)

            /** Prices of the (up to) [count] nearest other stations within [maxKm], in no particular order. */
            fun nearestPrices(i: Int, count: Int, maxKm: Double): LongArray {
                val p = points[i]
                val row = rowOf(p.lat) - minRow
                val col = colOf(p.lon) - minCol
                // Locally, one degree of longitude is cos(latitude) times shorter than one of latitude.
                val kmPerDegLon = KM_PER_DEG_LAT * cos(p.lat * PI / 180)
                val maxKm2 = maxKm * maxKm
                // After visiting rings 0..r, every station within r cells' width is known
                // (east-west is the narrower side of a cell).
                val cellKm = CELL_DEG * kmPerDegLon
                var n = 0
                var ring = 0
                while (true) {
                    if (ring == 0) {
                        n = visit(i, row, col, kmPerDegLon, maxKm2, n)
                    } else {
                        for (c in col - ring..col + ring) {
                            n = visit(i, row - ring, c, kmPerDegLon, maxKm2, n)
                            n = visit(i, row + ring, c, kmPerDegLon, maxKm2, n)
                        }
                        for (r in row - ring + 1 until row + ring) {
                            n = visit(i, r, col - ring, kmPerDegLon, maxKm2, n)
                            n = visit(i, r, col + ring, kmPerDegLon, maxKm2, n)
                        }
                    }
                    val covered = ring * cellKm
                    if (covered >= maxKm) break
                    val covered2 = covered * covered
                    var closeEnough = 0
                    for (k in 0 until n) if (dist2[k] <= covered2) closeEnough++
                    if (closeEnough >= count) break
                    ring++
                }
                val k = minOf(count, n)
                selectSmallest(k, n)
                return LongArray(k) { points[idx[it]].priceMilli }
            }

            /** Adds the stations of cell (r, c) within range of station [i]; returns the new count. */
            private fun visit(i: Int, r: Int, c: Int, kmPerDegLon: Double, maxKm2: Double, count: Int): Int {
                if (r < 0 || r >= rows || c < 0 || c >= cols) return count
                val members = cells[r * cols + c] ?: return count
                var n = count
                val p = points[i]
                for (j in members) {
                    if (j == i) continue
                    val q = points[j]
                    val x = (q.lon - p.lon) * kmPerDegLon
                    val y = (q.lat - p.lat) * KM_PER_DEG_LAT
                    val d2 = x * x + y * y
                    if (d2 > maxKm2) continue
                    if (n == dist2.size) {
                        dist2 = dist2.copyOf(n * 2)
                        idx = idx.copyOf(n * 2)
                    }
                    dist2[n] = d2
                    idx[n] = j
                    n++
                }
                return n
            }

            /** Moves the [k] smallest of the first [n] distances to the front (quickselect). */
            private fun selectSmallest(k: Int, n: Int) {
                if (k <= 0 || k >= n) return
                var lo = 0
                var hi = n - 1
                while (lo < hi) {
                    val pivot = dist2[(lo + hi) ushr 1]
                    var a = lo
                    var b = hi
                    while (a <= b) {
                        while (dist2[a] < pivot) a++
                        while (dist2[b] > pivot) b--
                        if (a <= b) {
                            swap(a, b)
                            a++
                            b--
                        }
                    }
                    if (k - 1 <= b) hi = b else if (k - 1 >= a) lo = a else return
                }
            }

            private fun swap(a: Int, b: Int) {
                val d = dist2[a]; dist2[a] = dist2[b]; dist2[b] = d
                val j = idx[a]; idx[a] = idx[b]; idx[b] = j
            }
        }

        private companion object {
            const val CELL_DEG = 0.02 // ~2.2 km north-south, ~1.6 km east-west in Italy
            const val KM_PER_DEG_LAT = 111.2

            fun rowOf(lat: Double) = floor(lat / CELL_DEG).toInt()
            fun colOf(lon: Double) = floor(lon / CELL_DEG).toInt()
        }
    }
}
