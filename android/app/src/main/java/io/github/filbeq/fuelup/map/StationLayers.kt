package io.github.filbeq.fuelup.map

import io.github.filbeq.fuelup.PerfLog
import io.github.filbeq.fuelup.data.Station
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

/** Marker colours (ARGB), taken from the app theme. */
data class StationColors(val fill: Int, val text: Int, val stroke: Int)

/**
 * Stations on the map, grouped by MapLibre's built-in GeoJSON clustering:
 * one source and three layers (cluster circles, cluster counts, single stations).
 */
object StationLayers {
    private const val SOURCE_ID = "fuelup-stations"
    private const val CLUSTER_LAYER_ID = "fuelup-clusters"
    private const val COUNT_LAYER_ID = "fuelup-cluster-count"
    private const val STATION_LAYER_ID = "fuelup-station"

    /** Above this zoom, stations are shown one by one. */
    private const val CLUSTER_MAX_ZOOM = 13
    private const val CLUSTER_RADIUS = 50

    private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /**
     * GeoJSON with one point per station (id + coordinates only; details come
     * from the parsed data when a station is tapped, in a later step).
     * Built as a string: MapLibre parses it natively, without creating ~21k
     * Java objects.
     */
    fun buildGeoJson(stations: List<Station>): String = buildString(stations.size * 110) {
        append("""{"type":"FeatureCollection","features":[""")
        stations.forEachIndexed { index, station ->
            if (index > 0) append(',')
            append("""{"type":"Feature","id":""").append(station.id)
            append(""","geometry":{"type":"Point","coordinates":[""")
            append(station.lon).append(',').append(station.lat)
            append("""]},"properties":{}}""")
        }
        append("]}")
    }

    /** Adds the source and layers to a freshly loaded style (initially empty). */
    fun addTo(style: Style, colors: StationColors, font: String) {
        val options = GeoJsonOptions()
            .withCluster(true)
            .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
            .withClusterRadius(CLUSTER_RADIUS)
        style.addSource(GeoJsonSource(SOURCE_ID, EMPTY, options))

        style.addLayer(
            CircleLayer(CLUSTER_LAYER_ID, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    circleColor(colors.fill),
                    // Bigger circles for bigger groups.
                    circleRadius(
                        step(get("point_count"), literal(14f), stop(50, 17f), stop(250, 21f), stop(1000, 26f)),
                    ),
                    circleStrokeColor(colors.stroke),
                    circleStrokeWidth(1.5f),
                ),
        )
        style.addLayer(
            SymbolLayer(COUNT_LAYER_ID, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    textField(get("point_count_abbreviated")),
                    textFont(arrayOf(font)),
                    textSize(12f),
                    textColor(colors.text),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )
        style.addLayer(
            CircleLayer(STATION_LAYER_ID, SOURCE_ID)
                .withFilter(not(has("point_count")))
                .withProperties(
                    circleColor(colors.fill),
                    circleRadius(6f),
                    circleStrokeColor(colors.stroke),
                    circleStrokeWidth(1.5f),
                ),
        )
    }

    /** Replaces the stations shown (null = none). */
    fun setData(style: Style, geoJson: String?) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        PerfLog.time("setGeoJson (main thread)") { source.setGeoJson(geoJson ?: EMPTY) }
    }
}
