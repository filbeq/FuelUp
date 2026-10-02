package io.github.filbeq.fuelup.map

import android.content.res.Resources
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
     * controls on top and the sheet at the bottom ([topPx], [bottomPx]).
     */
    data class FitCircle(val lat: Double, val lon: Double, val radiusKm: Double, val topPx: Int, val bottomPx: Int) : CameraMove

    /** Centre a point in the free area between [topPx] and [bottomPx], zoomed in to at least [minZoom]. */
    data class Show(val lat: Double, val lon: Double, val minZoom: Double, val topPx: Int, val bottomPx: Int) : CameraMove
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
    /** Called with the id of a tapped station. Taps on clusters zoom in. */
    onStationClick: (Int) -> Unit,
    /** The user's position (null = unknown or not asked), see [UserLocationLayers]. */
    userPosition: UserPosition?,
    /** "Near me" search circle around [userPosition], or null. */
    searchRadiusKm: Double?,
    /** Colour (ARGB) of the user's position and the search circle. */
    locationColor: Int,
    /** Latest camera move asked for, or null. */
    cameraCommand: CameraCommand?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
    val currentColors = rememberUpdatedState(stationColors)
    val currentOnStationClick = rememberUpdatedState(onStationClick)
    val currentLocationColor = rememberUpdatedState(locationColor)
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
                    StationLayers.handleTap(map, style, point, resources.displayMetrics.density) {
                        currentOnStationClick.value(it)
                    }
                }
                map.addOnCameraIdleListener {
                    val position = map.cameraPosition
                    val target = position.target ?: return@addOnCameraIdleListener
                    currentOnCameraIdle.value(MapCamera(target.latitude, target.longitude, position.zoom))
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
                UserLocationLayers.addTo(style, currentLocationColor.value)
                // A new style starts empty: add our source and layers every time.
                StationLayers.addTo(style, currentColors.value, labelFont, density, mapLabels)
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
    when (move) {
        is CameraMove.FitCircle -> {
            val bounds = LatLngBounds.Builder()
                .includes(Geo.circle(move.lat, move.lon, move.radiusKm, points = 16).map { LatLng(it[1], it[0]) })
                .build()
            val side = (16 * Resources.getSystem().displayMetrics.density).toInt()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, side, move.topPx + side, side, move.bottomPx + side))
        }
        is CameraMove.Show -> {
            val zoom = maxOf(map.cameraPosition.zoom, move.minZoom)
            // Centre the point, then shift it to the middle of the free area.
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(move.lat, move.lon), zoom),
                object : MapLibreMap.CancelableCallback {
                    override fun onFinish() = map.scrollBy(0f, (move.bottomPx - move.topPx) / 2f, 150)
                    override fun onCancel() = Unit
                },
            )
        }
    }
}
