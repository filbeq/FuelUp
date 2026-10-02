package io.github.filbeq.fuelup.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import io.github.filbeq.fuelup.data.Geo
import io.github.filbeq.fuelup.data.UserPosition
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.roundToInt

/**
 * The user's position and the "near me" search radius. The position is drawn
 * honestly: a translucent disc as big as the reported accuracy (with
 * approximate location, ~1–2 km across), plus a small centre mark, never a
 * precise-looking dot. The disc and the circle are added before
 * [StationLayers] ([addBelowStations]), so they lie under the stations; only the
 * small centre mark goes on top ([addAboveStations]), since a nearby cluster
 * would otherwise cover exactly where you are. Taps still reach the stations.
 */
object UserLocationLayers {
    private const val SOURCE_ID = "fuelup-user"
    private const val ACCURACY_FILL_ID = "fuelup-user-accuracy"
    private const val ACCURACY_HALO_ID = "fuelup-user-accuracy-halo"
    private const val ACCURACY_LINE_ID = "fuelup-user-accuracy-line"
    private const val RADIUS_HALO_ID = "fuelup-user-radius-halo"
    private const val RADIUS_LINE_ID = "fuelup-user-radius"
    private const val CENTRE_ID = "fuelup-user-centre"
    private const val CENTRE_IMAGE = "fuelup-user-centre"
    private const val KIND = "k"
    private const val ACCURACY = "accuracy"
    private const val RADIUS = "radius"
    private const val CENTRE = "centre"

    private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /**
     * [color] draws the position and the circle; [halo] (white on a light map,
     * near-black on a dark one) goes under every line so it stands out over
     * roads, water and parks.
     */
    fun addBelowStations(style: Style, color: Int, halo: Int) {
        style.addSource(GeoJsonSource(SOURCE_ID, EMPTY))
        val accuracy = eq(get(KIND), literal(ACCURACY))
        val radius = eq(get(KIND), literal(RADIUS))
        style.addLayer(FillLayer(ACCURACY_FILL_ID, SOURCE_ID).withFilter(accuracy).withProperties(fillColor(color), fillOpacity(0.2f)))
        style.addLayer(LineLayer(ACCURACY_HALO_ID, SOURCE_ID).withFilter(accuracy).withProperties(lineColor(halo), lineWidth(4f)))
        style.addLayer(LineLayer(ACCURACY_LINE_ID, SOURCE_ID).withFilter(accuracy).withProperties(lineColor(color), lineWidth(2f)))
        style.addLayer(LineLayer(RADIUS_HALO_ID, SOURCE_ID).withFilter(radius).withProperties(lineColor(halo), lineWidth(6f)))
        style.addLayer(
            LineLayer(RADIUS_LINE_ID, SOURCE_ID)
                .withFilter(radius)
                // Dash lengths are in line widths: 3 dp dashes of 9 dp, gaps of 6 dp.
                .withProperties(lineColor(color), lineWidth(3f), lineDasharray(arrayOf(3f, 2f))),
        )
    }

    /**
     * "You are here": small on purpose, so it never looks more precise than the
     * disc. A symbol rather than a circle, so it takes part in label placement:
     * a price label or "from" pill that would run into it is left out instead of
     * being drawn half-covered. Station icons are unaffected.
     */
    fun addAboveStations(style: Style, color: Int, halo: Int, density: Float) {
        style.addImage(CENTRE_IMAGE, centreMark(color, halo, density))
        style.addLayer(
            SymbolLayer(CENTRE_ID, SOURCE_ID)
                .withFilter(eq(get(KIND), literal(CENTRE)))
                .withProperties(iconImage(CENTRE_IMAGE), iconAllowOverlap(true)),
        )
    }

    /** A 12 dp dot in [color] with a 2 dp ring in [halo]. */
    private fun centreMark(color: Int, halo: Int, density: Float): Bitmap {
        val size = (16 * density).roundToInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val c = size / 2f
        paint.color = halo
        canvas.drawCircle(c, c, 8 * density, paint)
        paint.color = color
        canvas.drawCircle(c, c, 6 * density, paint)
        return bitmap
    }

    /** Shows [position] (null = nothing) and, if given, the search circle of [radiusKm]. */
    fun setData(style: Style, position: UserPosition?, radiusKm: Double?) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(buildGeoJson(position, radiusKm))
    }

    fun buildGeoJson(position: UserPosition?, radiusKm: Double?): String {
        if (position == null) return EMPTY
        val features = buildList {
            add(polygon(ACCURACY, Geo.circle(position.lat, position.lon, position.accuracyMeters / 1000.0)))
            if (radiusKm != null) add(line(RADIUS, Geo.circle(position.lat, position.lon, radiusKm)))
            add(
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${position.lon},${position.lat}]},""" +
                    """"properties":{"$KIND":"$CENTRE"}}""",
            )
        }
        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }

    private fun polygon(kind: String, ring: List<DoubleArray>) =
        """{"type":"Feature","geometry":{"type":"Polygon","coordinates":[${coordinates(ring)}]},"properties":{"$KIND":"$kind"}}"""

    private fun line(kind: String, ring: List<DoubleArray>) =
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":${coordinates(ring)}},"properties":{"$KIND":"$kind"}}"""

    private fun coordinates(ring: List<DoubleArray>) = ring.joinToString(",", "[", "]") { "[${it[0]},${it[1]}]" }
}
