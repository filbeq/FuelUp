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
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.NumberFormatOption.locale
import org.maplibre.android.style.expressions.Expression.NumberFormatOption.maxFractionDigits
import org.maplibre.android.style.expressions.Expression.NumberFormatOption.minFractionDigits
import org.maplibre.android.style.expressions.Expression.accumulated
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.concat
import org.maplibre.android.style.expressions.Expression.division
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.lt
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.min
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.numberFormat
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
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
 * Texts for map labels in the app language: [localeTag] for number formatting
 * ("1,990" vs "1.990"), and the text around a cluster's cheapest price, from a
 * string like "da %1$s" → prefix "da ", suffix "".
 */
data class MapLabels(val localeTag: String, val clusterPricePrefix: String, val clusterPriceSuffix: String)

/**
 * Colours (ARGB) from the app theme. Clusters use a UI colour, never a price
 * colour. Station markers use [StationIcons].
 */
data class StationColors(
    /** Cluster outline; also its fill, at [StationLayers.CLUSTER_FILL_OPACITY]. */
    val cluster: Int,
    val selected: Int,
    val labelText: Int,
    val labelHalo: Int,
)

/**
 * Stations on the map, grouped by MapLibre's built-in GeoJSON clustering:
 * one source and three layers (cluster circles, cluster counts, single stations).
 */
object StationLayers {
    private const val SOURCE_ID = "fuelup-stations"
    private const val CLUSTER_LAYER_ID = "fuelup-clusters"
    private const val COUNT_LAYER_ID = "fuelup-cluster-count"
    private const val PRICE_LAYER_ID = "fuelup-station-price"
    private const val STATION_LAYER_ID = "fuelup-station"
    private const val SELECTED_LAYER_ID = "fuelup-selected"
    private const val ID_PROPERTY = "id"
    private const val CLASS_PROPERTY = "c"
    private const val PRICE_PROPERTY = "p"
    /** Cluster property: cheapest price in the group, ignoring prices "to verify". */
    private const val MIN_PRICE_PROPERTY = "minPrice"
    /** Stands in for "no usable price" (all stations in the cluster to verify). */
    private const val NO_PRICE = 1_000_000
    private const val NO_STATION = -1

    /** Taps this close to a marker count as a tap on it (markers are only 6 dp). */
    private const val TAP_SLOP_DP = 24f

    /** Above this zoom, stations are shown one by one. */
    private const val CLUSTER_MAX_ZOOM = 13
    /** How close (in screen pixels) stations must be to group together. */
    private const val CLUSTER_RADIUS = 40

    /**
     * Circle radius (dp) by number of stations: 2–9, 10–49, 50–199, 200–999, 1000+.
     * Clearly different steps, so the size says how many stations are inside.
     */
    private val CLUSTER_SIZES = listOf(10 to 12f, 50 to 16f, 200 to 21f, 1000 to 27f)
    private const val CLUSTER_SMALLEST = 9f

    /** Translucent fill so the map stays visible; the outline stays solid. */
    const val CLUSTER_FILL_OPACITY = 0.3f

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
    fun addTo(style: Style, colors: StationColors, font: String, density: Float, labels: MapLabels) {
        val options = GeoJsonOptions()
            .withCluster(true)
            .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
            .withClusterRadius(CLUSTER_RADIUS)
            // Each cluster carries its cheapest price; flagged prices don't count.
            .withClusterProperty(
                MIN_PRICE_PROPERTY,
                min(accumulated(), get(MIN_PRICE_PROPERTY)),
                switchCase(
                    eq(get(CLASS_PROPERTY), literal(PriceClass.TO_VERIFY.name)),
                    literal(NO_PRICE),
                    get(PRICE_PROPERTY),
                ),
            )
        style.addSource(GeoJsonSource(SOURCE_ID, EMPTY, options))

        style.addLayer(
            CircleLayer(CLUSTER_LAYER_ID, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    circleColor(colors.cluster),
                    circleOpacity(CLUSTER_FILL_OPACITY),
                    circleRadius(
                        step(
                            get("point_count"),
                            literal(CLUSTER_SMALLEST),
                            *CLUSTER_SIZES.map { (count, radius) -> stop(count, radius) }.toTypedArray(),
                        ),
                    ),
                    circleStrokeColor(colors.cluster),
                    circleStrokeWidth(2f),
                ),
        )
        style.addLayer(
            SymbolLayer(COUNT_LAYER_ID, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    // "da 1,990"; the station count if every price in it is to verify.
                    textField(
                        switchCase(
                            lt(get(MIN_PRICE_PROPERTY), literal(NO_PRICE)),
                            concat(
                                literal(labels.clusterPricePrefix),
                                priceText(get(MIN_PRICE_PROPERTY), labels.localeTag),
                                literal(labels.clusterPriceSuffix),
                            ),
                            get("point_count_abbreviated"),
                        ),
                    ),
                    textFont(arrayOf(font)),
                    textSize(11f),
                    // On small circles the text is wider than the circle: the halo
                    // keeps it readable over the map.
                    textColor(colors.labelText),
                    textHaloColor(colors.labelHalo),
                    textHaloWidth(1.5f),
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
        // Price under each single station ("1,990"). Labels that would collide are
        // dropped by MapLibre; the markers above always stay.
        style.addLayer(
            SymbolLayer(PRICE_LAYER_ID, SOURCE_ID)
                .withFilter(not(has("point_count")))
                .withProperties(
                    textField(priceText(get(PRICE_PROPERTY), labels.localeTag)),
                    textFont(arrayOf(font)),
                    textSize(12f),
                    textAnchor(Property.TEXT_ANCHOR_TOP),
                    textOffset(arrayOf(0f, 0.9f)),
                    textColor(colors.labelText),
                    textHaloColor(colors.labelHalo),
                    textHaloWidth(1.5f),
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

    /** A price in thousandths of a euro as "1,990" / "1.990" for [localeTag]. */
    private fun priceText(milli: Expression, localeTag: String) = numberFormat(
        division(milli, literal(1000)),
        locale(localeTag),
        minFractionDigits(3),
        maxFractionDigits(3),
    )

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
