package io.github.filbeq.fuelup.map

import io.github.filbeq.fuelup.data.Geo
import io.github.filbeq.fuelup.data.UserPosition
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The user's position and the "near me" search radius. The position is drawn
 * honestly: a translucent disc as big as the reported accuracy (with
 * approximate location, ~1–2 km across), plus a small centre mark, never a
 * precise-looking dot. Added before [StationLayers], so it lies under the
 * stations and never blocks a tap on them.
 */
object UserLocationLayers {
    private const val SOURCE_ID = "fuelup-user"
    private const val ACCURACY_FILL_ID = "fuelup-user-accuracy"
    private const val ACCURACY_LINE_ID = "fuelup-user-accuracy-line"
    private const val RADIUS_LINE_ID = "fuelup-user-radius"
    private const val CENTRE_ID = "fuelup-user-centre"
    private const val KIND = "k"
    private const val ACCURACY = "accuracy"
    private const val RADIUS = "radius"
    private const val CENTRE = "centre"

    private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun addTo(style: Style, color: Int) {
        style.addSource(GeoJsonSource(SOURCE_ID, EMPTY))
        style.addLayer(
            FillLayer(ACCURACY_FILL_ID, SOURCE_ID)
                .withFilter(eq(get(KIND), literal(ACCURACY)))
                .withProperties(fillColor(color), fillOpacity(0.18f)),
        )
        style.addLayer(
            LineLayer(ACCURACY_LINE_ID, SOURCE_ID)
                .withFilter(eq(get(KIND), literal(ACCURACY)))
                .withProperties(lineColor(color), lineWidth(1f), lineOpacity(0.6f)),
        )
        style.addLayer(
            LineLayer(RADIUS_LINE_ID, SOURCE_ID)
                .withFilter(eq(get(KIND), literal(RADIUS)))
                .withProperties(lineColor(color), lineWidth(1.5f), lineOpacity(0.8f), lineDasharray(arrayOf(3f, 2f))),
        )
        style.addLayer(
            CircleLayer(CENTRE_ID, SOURCE_ID)
                .withFilter(eq(get(KIND), literal(CENTRE)))
                .withProperties(circleRadius(2.5f), circleColor(color), circleStrokeColor(0xFFFFFFFF.toInt()), circleStrokeWidth(1f)),
        )
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
