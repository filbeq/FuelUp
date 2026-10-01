package io.github.filbeq.fuelup.map

import android.graphics.PointF
import android.graphics.RectF
import io.github.filbeq.fuelup.PerfLog
import io.github.filbeq.fuelup.data.PriceClass
import io.github.filbeq.fuelup.data.RankedPrice
import io.github.filbeq.fuelup.data.Station
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import kotlin.math.hypot

/**
 * Colours (ARGB) from the app theme. Clusters are neutral on purpose: the brand
 * green would read as "cheap". Station markers use [StationIcons].
 */
data class StationColors(val clusterFill: Int, val clusterText: Int, val stroke: Int, val selected: Int)

/**
 * Stations on the map, grouped by MapLibre's built-in GeoJSON clustering:
 * one source and three layers (cluster circles, cluster counts, single stations).
 */
object StationLayers {
    private const val SOURCE_ID = "fuelup-stations"
    private const val CLUSTER_LAYER_ID = "fuelup-clusters"
    private const val COUNT_LAYER_ID = "fuelup-cluster-count"
    private const val STATION_LAYER_ID = "fuelup-station"
    private const val SELECTED_LAYER_ID = "fuelup-selected"
    private const val ID_PROPERTY = "id"
    private const val CLASS_PROPERTY = "c"
    private const val NO_STATION = -1

    /** Taps this close to a marker count as a tap on it (markers are only 6 dp). */
    private const val TAP_SLOP_DP = 24f

    /** Above this zoom, stations are shown one by one. */
    private const val CLUSTER_MAX_ZOOM = 13
    private const val CLUSTER_RADIUS = 50

    private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /**
     * GeoJSON with one point per station that sells the chosen fuel (the
     * stations missing from [ranked]). Properties: the station id as a number
     * (MapLibre returns feature ids as text, so tap handling reads this), `p` =
     * price in thousandths of a euro, `c` = [PriceClass] name.
     * Built as a string: MapLibre parses it natively, without creating ~21k
     * Java objects.
     */
    fun buildGeoJson(stations: List<Station>, ranked: Map<Int, RankedPrice>): String =
        buildString(stations.size * 140) {
            append("""{"type":"FeatureCollection","features":[""")
            var first = true
            for (station in stations) {
                val price = ranked[station.id] ?: continue
                if (!first) append(',')
                first = false
                append("""{"type":"Feature","id":""").append(station.id)
                append(""","geometry":{"type":"Point","coordinates":[""")
                append(station.lon).append(',').append(station.lat)
                append("""]},"properties":{"id":""").append(station.id)
                append(""","p":""").append(price.priceMilli)
                append(""","c":"""").append(price.priceClass.name).append("\"}}")
            }
            append("]}")
        }

    /** Adds the source and layers to a freshly loaded style (initially empty). */
    fun addTo(style: Style, colors: StationColors, font: String, density: Float) {
        val options = GeoJsonOptions()
            .withCluster(true)
            .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
            .withClusterRadius(CLUSTER_RADIUS)
        style.addSource(GeoJsonSource(SOURCE_ID, EMPTY, options))

        style.addLayer(
            CircleLayer(CLUSTER_LAYER_ID, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    circleColor(colors.clusterFill),
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
                    textColor(colors.clusterText),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )
        // Single stations: one icon per price class (colour + shape, see StationIcons).
        PriceClass.entries.forEach { style.addImage(StationIcons.imageName(it), StationIcons.draw(it, density)) }
        style.addLayer(
            SymbolLayer(STATION_LAYER_ID, SOURCE_ID)
                .withFilter(not(has("point_count")))
                .withProperties(
                    iconImage(
                        match(
                            get(CLASS_PROPERTY),
                            literal(StationIcons.imageName(PriceClass.NOT_COMPARED)),
                            *PriceClass.entries.map { stop(it.name, StationIcons.imageName(it)) }.toTypedArray(),
                        ),
                    ),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
        )
        // Ring around the selected station (none selected: matches nothing).
        style.addLayer(
            CircleLayer(SELECTED_LAYER_ID, SOURCE_ID)
                .withFilter(selectedFilter(NO_STATION))
                .withProperties(
                    circleRadius(13f),
                    circleOpacity(0f),
                    circleStrokeColor(colors.selected),
                    circleStrokeWidth(3f),
                ),
        )
    }

    /** Highlights station [id] (null = none). */
    fun setSelected(style: Style, id: Int?) {
        val layer = style.getLayerAs<CircleLayer>(SELECTED_LAYER_ID) ?: return
        layer.setFilter(selectedFilter(id ?: NO_STATION))
    }

    private fun selectedFilter(id: Int) = all(not(has("point_count")), eq(get(ID_PROPERTY), id))

    /**
     * Handles a tap at [point] (screen pixels): a station → [onStationClick] with its
     * id; a cluster → zoom in until it splits. Returns false if nothing was hit.
     */
    fun handleTap(map: MapLibreMap, style: Style, point: PointF, density: Float, onStationClick: (Int) -> Unit): Boolean {
        val slop = TAP_SLOP_DP * density
        val box = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)

        map.queryRenderedFeatures(box, STATION_LAYER_ID).nearestTo(map, point)?.let { station ->
            val id = station.getNumberProperty(ID_PROPERTY)?.toInt() ?: return false
            onStationClick(id)
            return true
        }
        map.queryRenderedFeatures(box, CLUSTER_LAYER_ID).nearestTo(map, point)?.let { cluster ->
            val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return false
            val zoom = source.getClusterExpansionZoom(cluster).toDouble()
            val center = (cluster.geometry() as? Point)?.let { LatLng(it.latitude(), it.longitude()) } ?: return false
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, zoom))
            return true
        }
        return false
    }

    /** The feature drawn closest to [point] on screen. */
    private fun List<Feature>.nearestTo(map: MapLibreMap, point: PointF): Feature? = minByOrNull { feature ->
        val p = feature.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
        val screen = map.projection.toScreenLocation(LatLng(p.latitude(), p.longitude()))
        hypot(screen.x - point.x, screen.y - point.y)
    }

    /** Replaces the stations shown (null = none). */
    fun setData(style: Style, geoJson: String?) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        PerfLog.time("setGeoJson (main thread)") { source.setGeoJson(geoJson ?: EMPTY) }
    }
}
