package io.github.filbeq.fuelup.data

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * The part of the map the area list covers: the four corners of the free part
 * of the screen (between the top controls, the sheet and the side panel), as
 * `[lat, lon]` pairs in screen order (top left, top right, bottom right, bottom
 * left). Four corners rather than a box, so a rotated map works too.
 */
class MapArea(val corners: List<DoubleArray>) {
    init {
        require(corners.size == 4) { "An area has 4 corners" }
    }

    // The same corners in Web Mercator, where the screen's straight edges are straight.
    private val xs = DoubleArray(4) { mercatorX(corners[it][1]) }
    private val ys = DoubleArray(4) { mercatorY(corners[it][0]) }

    /** The middle of the area, used as the origin of distances when the user's position is unknown. */
    val centreLat: Double get() = corners.sumOf { it[0] } / 4
    val centreLon: Double get() = corners.sumOf { it[1] } / 4

    /** The longer of the area's two sides, as the crow flies, measured through its middle. */
    val longerSideKm: Double
        get() {
            fun mid(a: Int, b: Int) = doubleArrayOf((corners[a][0] + corners[b][0]) / 2, (corners[a][1] + corners[b][1]) / 2)
            fun km(p: DoubleArray, q: DoubleArray) = Geo.distanceKm(p[0], p[1], q[0], q[1])
            val across = km(mid(0, 3), mid(1, 2))
            val down = km(mid(0, 1), mid(3, 2))
            return maxOf(across, down)
        }

    /** Too large to list: stations that far apart aren't worth comparing ([MAX_SIDE_KM]). */
    val tooLarge: Boolean get() = longerSideKm > MAX_SIDE_KM

    /** True if the point is inside the area (or on its edge). */
    fun contains(lat: Double, lon: Double): Boolean {
        val x = mercatorX(lon)
        val y = mercatorY(lat)
        // Inside a convex quadrilateral: on the same side of all four edges.
        var sign = 0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val cross = (xs[j] - xs[i]) * (y - ys[i]) - (ys[j] - ys[i]) * (x - xs[i])
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0) continue
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /** For saved state: the corners as 8 numbers. */
    fun toArray(): DoubleArray = DoubleArray(8) { corners[it / 2][it % 2] }

    companion object {
        /**
         * Above this the list says "zoom in". 80 km: a province, not a region.
         * Every municipality's frame fits (largest: Rome's, ≈ 70 km on a phone);
         * on 6 Oct 2026 a phone frame of this size held ~450 petrol stations at
         * the median place, one zoom level closer (~22 × 25 km) ~60.
         */
        const val MAX_SIDE_KM = 80.0

        fun fromArray(values: DoubleArray) = MapArea(List(4) { doubleArrayOf(values[it * 2], values[it * 2 + 1]) })

        private fun mercatorX(lon: Double) = Math.toRadians(lon)

        private fun mercatorY(lat: Double) = ln(tan(PI / 4 + Math.toRadians(lat) / 2))
    }
}
