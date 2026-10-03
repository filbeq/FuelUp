package io.github.filbeq.fuelup.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import io.github.filbeq.fuelup.PerfLog
import io.github.filbeq.fuelup.data.CompareGroup
import io.github.filbeq.fuelup.data.PriceClass
import io.github.filbeq.fuelup.data.RankedPrice
import io.github.filbeq.fuelup.data.Station
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.ImageContent
import org.maplibre.android.maps.ImageStretches
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
import org.maplibre.android.style.expressions.Expression.product
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
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.iconTextFit
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
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
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
    /** Cluster outline (also the price pill's); also its fill, at [StationLayers.CLUSTER_FILL_OPACITY]. */
    val cluster: Int,
    val selected: Int,
    val labelText: Int,
    /** Halo around map labels; also the background of the cluster price pill. */
    val labelHalo: Int,
    /** Outline of the favourite star (drawn in [cluster]): the opposite tone of the map. */
    val favoriteOutline: Int,
)

/**
 * Stations on the map, grouped by MapLibre's built-in GeoJSON clustering:
 * one source, and layers for clusters (circle with the station count, "from"
 * price pill underneath) and single stations (icon, price, selection ring).
 */
object StationLayers {
    private const val SOURCE_ID = "fuelup-stations"
    private const val CLUSTER_LAYER_ID = "fuelup-clusters"
    private const val COUNT_LAYER_ID = "fuelup-cluster-count"
    private const val PILL_LAYER_ID = "fuelup-cluster-price"
    private const val PILL_IMAGE = "fuelup-pill"
    private const val AREA_IMAGE = "fuelup-cluster-area"
    /** Side of [AREA_IMAGE] in dp, scaled per cluster size with `icon-size`. */
    private const val AREA_SIZE_DP = 10f
    /**
     * Share of a circle's diameter kept free of other clusters' pills. The whole
     * circle hid most prices at national zoom; 60% keeps pills off the counts
     * and lets them touch only a neighbour's rim.
     */
    private const val AREA_SHARE = 0.6f
    private const val PRICE_LAYER_ID = "fuelup-station-price"
    private const val STATION_LAYER_ID = "fuelup-station"
    private const val SELECTED_LAYER_ID = "fuelup-selected"
    private const val FAVORITE_LAYER_ID = "fuelup-favorite"
    private const val FAVORITE_IMAGE = "fuelup-favorite-star"
    /** The star's size and where it sits: at the top right of the 20 dp marker, half outside the selection ring. */
    private const val FAVORITE_STAR_DP = 13f
    private const val FAVORITE_OFFSET_DP = 11f
    /** A selected station without a marker (it doesn't sell the chosen fuel): its own point. */
    private const val OFF_MAP_SOURCE_ID = "fuelup-selected-off-map"
    private const val OFF_MAP_DOT_LAYER_ID = "fuelup-selected-off-map-dot"
    private const val OFF_MAP_RING_LAYER_ID = "fuelup-selected-off-map-ring"
    private const val ID_PROPERTY = "id"
    private const val CLASS_PROPERTY = "c"
    private const val PRICE_PROPERTY = "p"
    /** The station's price as a candidate for its cluster's "from" price (see [fromPrice]). */
    private const val FROM_PRICE_PROPERTY = "fp"
    /** Cluster property: the cheapest [FROM_PRICE_PROPERTY] in the group. */
    private const val MIN_PRICE_PROPERTY = "minPrice"
    /** Stands in for "no usable price" (e.g. every station in the cluster to verify). */
    const val NO_PRICE = 1_000_000
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
    private const val CLUSTER_STROKE_DP = 2f

    private const val PILL_TEXT_SP = 11f
    /** Space between a cluster circle's outline and its price pill (dp). */
    private const val PILL_GAP_DP = 3f

    private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /**
     * GeoJSON with one point per station that sells the chosen fuel (the
     * stations missing from [ranked]). Properties: the station id as a number
     * (MapLibre returns feature ids as text, so tap handling reads this), `p` =
     * price in thousandths of a euro, `c` = [PriceClass] name, `fp` = [fromPrice].
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
                append(""","fp":""").append(fromPrice(price))
                append(""","c":"""").append(price.priceClass.name).append("\"}}")
            }
            append("]}")
        }

    /**
     * What a station contributes to its cluster's "from" price: its price, or
     * [NO_PRICE] if it is to verify (possibly an error) or duty-free (Livigno:
     * a price nobody outside it can get).
     */
    fun fromPrice(price: RankedPrice): Long = when {
        price.priceClass == PriceClass.TO_VERIFY -> NO_PRICE.toLong()
        price.group == CompareGroup.DUTY_FREE -> NO_PRICE.toLong()
        else -> price.priceMilli
    }

    /** Adds the source and layers to a freshly loaded style (initially empty). */
    fun addTo(style: Style, colors: StationColors, font: String, density: Float, labels: MapLabels) {
        val options = GeoJsonOptions()
            .withCluster(true)
            .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
            .withClusterRadius(CLUSTER_RADIUS)
            // Each cluster carries its cheapest price (see fromPrice).
            .withClusterProperty(MIN_PRICE_PROPERTY, min(accumulated(), get(MIN_PRICE_PROPERTY)), get(FROM_PRICE_PROPERTY))
        style.addSource(GeoJsonSource(SOURCE_ID, EMPTY, options))

        style.addLayer(
            CircleLayer(CLUSTER_LAYER_ID, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    circleColor(colors.cluster),
                    circleOpacity(CLUSTER_FILL_OPACITY),
                    circleRadius(clusterStep(literal(CLUSTER_SMALLEST)) { literal(it) }),
                    circleStrokeColor(colors.cluster),
                    circleStrokeWidth(CLUSTER_STROKE_DP),
                ),
        )
        // "da 1,990" in a pill under the circle, only where there is room. Added
        // below the counts: MapLibre places the upper layer first, so pills give
        // way to the circles (see the count layer) as well as to each other.
        style.addPillImage(PILL_IMAGE, colors, density)
        style.addLayer(
            SymbolLayer(PILL_LAYER_ID, SOURCE_ID)
                // No pill if every price in the cluster is to verify.
                .withFilter(all(has("point_count"), lt(get(MIN_PRICE_PROPERTY), literal(NO_PRICE))))
                .withProperties(
                    textField(
                        concat(
                            literal(labels.clusterPricePrefix),
                            priceText(get(MIN_PRICE_PROPERTY), labels.localeTag),
                            literal(labels.clusterPriceSuffix),
                        ),
                    ),
                    textFont(arrayOf(font)),
                    textSize(PILL_TEXT_SP),
                    textColor(colors.labelText),
                    // Top of the text just under the circle (offset in ems, by circle size).
                    textAnchor(Property.TEXT_ANCHOR_TOP),
                    textOffset(clusterStep(pillOffset(CLUSTER_SMALLEST)) { pillOffset(it) }),
                    iconImage(PILL_IMAGE),
                    iconTextFit(Property.ICON_TEXT_FIT_BOTH),
                    // When pills compete for room, bigger groups win.
                    symbolSortKey(product(literal(-1), get("point_count"))),
                ),
        )
        // Station count inside the circle ("1.234" / "1,234"): always shown. Its
        // invisible icon, a square over most of the circle, makes MapLibre keep
        // pills off circles (circle layers don't take part in label placement).
        style.addImage(AREA_IMAGE, createBitmap((AREA_SIZE_DP * density).toInt(), (AREA_SIZE_DP * density).toInt()))
        style.addLayer(
            SymbolLayer(COUNT_LAYER_ID, SOURCE_ID)
                .withFilter(has("point_count"))
                .withProperties(
                    iconImage(AREA_IMAGE),
                    iconSize(clusterStep(areaScale(CLUSTER_SMALLEST)) { areaScale(it) }),
                    iconAllowOverlap(true),
                    textField(numberFormat(get("point_count"), locale(labels.localeTag))),
                    textFont(arrayOf(font)),
                    textSize(11f),
                    textColor(colors.labelText),
                    textHaloColor(colors.labelHalo),
                    textHaloWidth(1.5f),
                    textAllowOverlap(true),
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
                .withProperties(*selectedRing(colors)),
        )
        // A small star on the marker's top-right edge for favourites (none: matches
        // nothing), above the selection ring. In the cluster colour, so it can't be
        // mistaken for a price class.
        style.addImage(FAVORITE_IMAGE, drawStar(colors, density))
        style.addLayer(
            SymbolLayer(FAVORITE_LAYER_ID, SOURCE_ID)
                .withFilter(favoriteFilter(emptySet()))
                .withProperties(
                    iconImage(FAVORITE_IMAGE),
                    iconOffset(arrayOf(FAVORITE_OFFSET_DP, -FAVORITE_OFFSET_DP)),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
        )
        // A station picked in the search that has no marker (it doesn't sell the
        // chosen fuel): a plain hollow dot, like "not compared", inside the same ring.
        style.addSource(GeoJsonSource(OFF_MAP_SOURCE_ID))
        style.addLayer(
            CircleLayer(OFF_MAP_DOT_LAYER_ID, OFF_MAP_SOURCE_ID).withProperties(
                circleRadius(5f),
                circleColor(colors.labelHalo),
                circleStrokeColor(colors.labelText),
                circleStrokeWidth(2f),
            ),
        )
        style.addLayer(CircleLayer(OFF_MAP_RING_LAYER_ID, OFF_MAP_SOURCE_ID).withProperties(*selectedRing(colors)))
    }

    private fun selectedRing(colors: StationColors) = arrayOf<PropertyValue<*>>(
        circleRadius(13f),
        circleOpacity(0f),
        circleStrokeColor(colors.selected),
        circleStrokeWidth(3f),
    )

    /** A value per cluster size: [smallest] below the first [CLUSTER_SIZES] step, then [byRadius] of each radius. */
    private fun clusterStep(smallest: Expression, byRadius: (Float) -> Expression) = step(
        get("point_count"),
        smallest,
        *CLUSTER_SIZES.map { (count, radius) -> stop(count, byRadius(radius)) }.toTypedArray(),
    )

    /** `icon-size` that makes [AREA_IMAGE] cover [AREA_SHARE] of a circle of [radius] dp. */
    private fun areaScale(radius: Float) = literal(AREA_SHARE * 2 * radius / AREA_SIZE_DP)

    /** Text offset (ems) that puts a pill [PILL_GAP_DP] under a circle of [radius] dp. */
    private fun pillOffset(radius: Float) =
        literal(arrayOf(0f, (radius + CLUSTER_STROKE_DP / 2 + PILL_GAP_DP + PILL_PADDING_Y_DP) / PILL_TEXT_SP))

    private const val PILL_PADDING_X_DP = 6f
    private const val PILL_PADDING_Y_DP = 2f

    /**
     * Adds the pill background, stretched by MapLibre to fit each label
     * (`icon-text-fit`): surface colour with a thin outline in the cluster
     * colour. Only the straight middle stretches, so the ends stay round.
     */
    private fun Style.addPillImage(name: String, colors: StationColors, density: Float) {
        val corner = 8f * density
        val stroke = 1.5f * density
        val width = (2 * corner + 4 * density).toInt()
        val height = (2 * corner).toInt() + 2
        val bitmap = createBitmap(width, height)
        val rect = RectF(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val canvas = Canvas(bitmap)
        paint.color = colors.labelHalo
        canvas.drawRoundRect(rect, corner, corner, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = colors.cluster
        canvas.drawRoundRect(rect, corner, corner, paint)
        addImage(
            name,
            bitmap,
            listOf(ImageStretches(corner, width - corner)),
            listOf(ImageStretches(corner, height - corner)),
            // Where the text goes: the rest is padding.
            ImageContent(
                PILL_PADDING_X_DP * density,
                PILL_PADDING_Y_DP * density,
                width - PILL_PADDING_X_DP * density,
                height - PILL_PADDING_Y_DP * density,
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

    /** Puts the favourite star on stations [ids]. */
    fun setFavorites(style: Style, ids: Set<Int>) {
        val layer = style.getLayerAs<SymbolLayer>(FAVORITE_LAYER_ID) ?: return
        layer.setFilter(favoriteFilter(ids))
    }

    private fun favoriteFilter(ids: Set<Int>) = if (ids.isEmpty()) {
        selectedFilter(NO_STATION)
    } else {
        all(not(has("point_count")), Expression.`in`(get(ID_PROPERTY), literal(ids.toTypedArray<Any>())))
    }

    /** A five-pointed star in the cluster colour with an outline in the map's opposite tone. */
    private fun drawStar(colors: StationColors, density: Float): android.graphics.Bitmap {
        val size = (FAVORITE_STAR_DP * density).toInt()
        val bitmap = createBitmap(size, size)
        val outline = 1.5f * density
        val outer = size / 2f - outline
        val inner = outer * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = Math.PI / 5 * i - Math.PI / 2
            val x = size / 2f + (r * cos(angle)).toFloat()
            // Slightly lower: a star's visual centre is below its outer circle's.
            val y = size / 2f + outline / 2 + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.favoriteOutline
            style = Paint.Style.STROKE
            strokeWidth = outline * 2
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = colors.cluster
        canvas.drawPath(path, paint)
        return bitmap
    }

    /** Shows a selected station that has no marker at [lat], [lon]; null clears it. */
    fun setSelectedOffMap(style: Style, position: Pair<Double, Double>?) {
        val source = style.getSourceAs<GeoJsonSource>(OFF_MAP_SOURCE_ID) ?: return
        val features = listOfNotNull(position?.let { (lat, lon) -> Feature.fromGeometry(Point.fromLngLat(lon, lat)) })
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /**
     * Handles a tap at [point] (screen pixels): a station → [onStationClick] with its
     * id; a cluster (circle or price pill) → zoom in until it splits. Returns false if nothing was hit.
     */
    fun handleTap(map: MapLibreMap, style: Style, point: PointF, density: Float, onStationClick: (Int) -> Unit): Boolean {
        val slop = TAP_SLOP_DP * density
        val box = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)

        map.queryRenderedFeatures(box, STATION_LAYER_ID).nearestTo(map, point)?.let { station ->
            val id = station.getNumberProperty(ID_PROPERTY)?.toInt() ?: return false
            onStationClick(id)
            return true
        }
        map.queryRenderedFeatures(box, CLUSTER_LAYER_ID, PILL_LAYER_ID).nearestTo(map, point)?.let { cluster ->
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
