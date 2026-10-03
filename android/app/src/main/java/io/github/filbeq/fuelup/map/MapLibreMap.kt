package io.github.filbeq.fuelup.map

import android.content.res.Resources
import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.filbeq.fuelup.data.Geo
import io.github.filbeq.fuelup.data.UserPosition
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Where the map is looking. Saved across screen rotation. */
data class MapCamera(val latitude: Double, val longitude: Double, val zoom: Double) {
    companion object {
        /** The whole of Italy, islands included, on a phone screen. */
        val Italy = MapCamera(latitude = 41.8, longitude = 12.6, zoom = 4.6)

        val Saver = Saver<MapCamera, DoubleArray>(
            save = { doubleArrayOf(it.latitude, it.longitude, it.zoom) },
            restore = { MapCamera(it[0], it[1], it[2]) },
        )
    }
}

/**
 * A one-off camera move asked for by the screen. [id] tells two identical
 * requests apart (e.g. tapping "my location" twice).
 */
data class CameraCommand(val id: Long, val move: CameraMove)

sealed interface CameraMove {
    /**
     * Show the whole circle of [radiusKm] around a point, keeping clear of the
     * controls on top, the sheet at the bottom and the side panel on the left
     * ([topPx], [bottomPx], [leftPx]).
     */
    data class FitCircle(
        val lat: Double,
        val lon: Double,
        val radiusKm: Double,
        val topPx: Int,
        val bottomPx: Int,
        val leftPx: Int = 0,
    ) : CameraMove

    /** Centre a point in the free area inside [topPx], [bottomPx] and [leftPx], zoomed in to at least [minZoom]. */
    data class Show(
        val lat: Double,
        val lon: Double,
        val minZoom: Double,
        val topPx: Int,
        val bottomPx: Int,
        val leftPx: Int = 0,
    ) : CameraMove

    /**
     * If the point is (or is about to be) hidden under the side panel, the
     * [leftPx] at the left of the map, or under the controls, the [topPx] at
     * the top, pan just enough to bring it out; else don't move.
     */
    data class Reveal(val lat: Double, val lon: Double, val leftPx: Int, val topPx: Int) : CameraMove

    /**
     * Frame all [points] (`[lat, lon]` pairs, e.g. a municipality's stations) in the free
     * area inside [topPx], [bottomPx] and [leftPx], zoomed in at most to [maxZoom].
     */
    class FitPoints(
        val points: List<DoubleArray>,
        val maxZoom: Double,
        val topPx: Int,
        val bottomPx: Int,
        val leftPx: Int = 0,
    ) : CameraMove
}

/**
 * MapLibre's classic [MapView] inside Compose.
 *
 * MapView is an old-style Android view that must be told about the screen's
 * lifecycle (start/resume/pause/stop/destroy); this composable does that, so the
 * rest of the app can treat the map like any other composable. All MapLibre glue
 * lives in this file.
 */
@Composable
fun MapLibreMap(
    styleUrl: String,
    camera: MapCamera,
    onCameraIdle: (MapCamera) -> Unit,
    /** Stations as GeoJSON (see [StationLayers.buildGeoJson]); null = none yet. */
    stationsGeoJson: String?,
    stationColors: StationColors,
    labelFont: String,
    mapLabels: MapLabels,
    /** Language for place names on the map, see [LabelLanguage]. */
    labelLanguage: String,
    /** Station drawn as selected (highlight ring), or null. */
    selectedStationId: Int?,
    /** Where the selected station is when it has no marker of its own, or a missing favourite's last known spot (`lat` to `lon`); else null. */
    selectedOffMap: Pair<Double, Double>?,
    /** Stations marked with the favourite star. */
    favoriteIds: Set<Int>,
    /** Called with the id of a tapped station. Taps on clusters zoom in. */
    onStationClick: (Int) -> Unit,
    /** A tap that hit no station and no cluster. */
    onMapTapEmpty: () -> Unit,
    /** The user's position (null = unknown or not asked), see [UserLocationLayers]. */
    userPosition: UserPosition?,
    /** "Near me" search circle around [userPosition], or null. */
    searchRadiusKm: Double?,
    /** Colours (ARGB) of the user's position and the search circle, see [UserLocationLayers]. */
    locationColor: Int,
    locationHalo: Int,
    /** Latest camera move asked for, or null. */
    cameraCommand: CameraCommand?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
    val currentColors = rememberUpdatedState(stationColors)
    val currentOnStationClick = rememberUpdatedState(onStationClick)
    val currentOnMapTapEmpty = rememberUpdatedState(onMapTapEmpty)
    val currentLocationColor = rememberUpdatedState(locationColor)
    val currentLocationHalo = rememberUpdatedState(locationHalo)
    // The style currently on screen, once fully loaded (null while loading).
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context)
            .camera(
                CameraPosition.Builder()
                    .target(LatLng(camera.latitude, camera.longitude))
                    .zoom(camera.zoom)
                    .build(),
            )
            // Credits are shown by our own attribution bar (see MapProvider).
            .attributionEnabled(false)
            .logoEnabled(false)
        MapView(context, options).apply {
            onCreate(null)
            getMapAsync { map ->
                map.addOnMapClickListener { latLng ->
                    val style = map.style?.takeIf { it.isFullyLoaded } ?: return@addOnMapClickListener false
                    val point = map.projection.toScreenLocation(latLng)
                    val hit = StationLayers.handleTap(map, style, point, resources.displayMetrics.density) {
                        currentOnStationClick.value(it)
                    }
                    if (!hit) currentOnMapTapEmpty.value()
                    hit
                }
                map.addOnCameraIdleListener {
                    if (width == 0 || height == 0) return@addOnCameraIdleListener
                    // The place in the middle of the view, not the camera's target: after
                    // fitting the "near me" circle the camera keeps padding (top controls,
                    // sheet), so its target is off-centre, and a map rebuilt from it (e.g.
                    // after rotation, without padding) would show a shifted view.
                    val centre = map.projection.fromScreenLocation(PointF(width / 2f, height / 2f))
                    currentOnCameraIdle.value(MapCamera(centre.latitude, centre.longitude, map.cameraPosition.zoom))
                }
            }
        }
    }

    // Changing the app language recreates the screen, so the language is fixed here.
    LaunchedEffect(mapView, styleUrl) {
        loadedStyle = null
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                LabelLanguage.apply(style, labelLanguage)
                // Added first, so it lies under the stations.
                UserLocationLayers.addBelowStations(style, currentLocationColor.value, currentLocationHalo.value)
                // A new style starts empty: add our source and layers every time.
                StationLayers.addTo(style, currentColors.value, labelFont, density, mapLabels)
                UserLocationLayers.addAboveStations(style, currentLocationColor.value, currentLocationHalo.value, density)
                loadedStyle = style
            }
        }
    }

    LaunchedEffect(loadedStyle, stationsGeoJson) {
        loadedStyle?.let { StationLayers.setData(it, stationsGeoJson) }
    }

    LaunchedEffect(loadedStyle, selectedStationId) {
        loadedStyle?.let { StationLayers.setSelected(it, selectedStationId) }
    }

    LaunchedEffect(loadedStyle, favoriteIds) {
        loadedStyle?.let { StationLayers.setFavorites(it, favoriteIds) }
    }

    LaunchedEffect(loadedStyle, selectedOffMap) {
        loadedStyle?.let { StationLayers.setSelectedOffMap(it, selectedOffMap) }
    }

    LaunchedEffect(loadedStyle, userPosition, searchRadiusKm) {
        loadedStyle?.let { UserLocationLayers.setData(it, userPosition, searchRadiusKm) }
    }

    LaunchedEffect(cameraCommand) {
        val move = cameraCommand?.move ?: return@LaunchedEffect
        mapView.getMapAsync { map -> moveCamera(map, move) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // Replays ON_START/ON_RESUME if the screen is already started.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // The map is leaving the screen (or the activity is closing):
            // walk it down to "destroyed" in the order MapView expects.
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun moveCamera(map: MapLibreMap, move: CameraMove) {
    val density = Resources.getSystem().displayMetrics.density
    when (move) {
        is CameraMove.FitCircle -> {
            val bounds = LatLngBounds.Builder()
                .includes(Geo.circle(move.lat, move.lon, move.radiusKm, points = 16).map { LatLng(it[1], it[0]) })
                .build()
            val side = (16 * density).toInt()
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, move.leftPx + side, move.topPx + side, side, move.bottomPx + side),
            )
        }
        is CameraMove.Show -> {
            val zoom = maxOf(map.cameraPosition.zoom, move.minZoom)
            // Centred in the free area: the padding is set every time, since a
            // circle fit leaves its own (a different sheet height) behind.
            val position = CameraPosition.Builder()
                .target(LatLng(move.lat, move.lon))
                .zoom(zoom)
                .padding(move.leftPx.toDouble(), move.topPx.toDouble(), 0.0, move.bottomPx.toDouble())
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position))
        }
        is CameraMove.FitPoints -> {
            val side = (FIT_MARGIN_DP * density).toInt()
            val padding = intArrayOf(move.leftPx + side, move.topPx + side, side, move.bottomPx + side)
            val first = move.points.firstOrNull() ?: return
            // One station (or all at one spot): no area to fit, centre it.
            val fitted = if (move.points.all { it[0] == first[0] && it[1] == first[1] }) {
                null
            } else {
                map.getCameraForLatLngBounds(
                    LatLngBounds.Builder().includes(move.points.map { LatLng(it[0], it[1]) }).build(),
                    padding,
                )
            }
            val position = CameraPosition.Builder()
                .target(fitted?.target ?: LatLng(first[0], first[1]))
                .zoom(minOf(fitted?.zoom ?: move.maxZoom, move.maxZoom))
                // Set every time, like Show: a circle fit leaves its own padding behind.
                .padding(padding[0].toDouble(), padding[1].toDouble(), padding[2].toDouble(), padding[3].toDouble())
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position))
        }
        is CameraMove.Reveal -> {
            val point = map.projection.toScreenLocation(LatLng(move.lat, move.lon))
            // Some room beside the panel and under the controls, so the marker isn't squeezed against them.
            val margin = REVEAL_MARGIN_DP * density
            val dx = (move.leftPx + margin - point.x).coerceAtLeast(0f)
            val dy = (move.topPx + margin - point.y).coerceAtLeast(0f)
            if (dx == 0f && dy == 0f) return
            // Move the camera's target left/up by the missing distance (the map
            // content goes right/down). Not scrollBy: it doesn't end in a camera-idle
            // event, so the camera saved for a rotation would miss the move.
            val target = map.projection.toScreenLocation(map.cameraPosition.target ?: return)
            val newTarget = map.projection.fromScreenLocation(PointF(target.x - dx, target.y - dy))
            map.animateCamera(CameraUpdateFactory.newLatLng(newTarget), 300)
        }
    }
}

private const val REVEAL_MARGIN_DP = 48

/** Room around a framed municipality, so its outermost stations aren't squeezed against the edges. */
private const val FIT_MARGIN_DP = 32
