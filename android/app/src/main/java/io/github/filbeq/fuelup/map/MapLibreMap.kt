package io.github.filbeq.fuelup.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
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
                map.addOnCameraIdleListener {
                    val position = map.cameraPosition
                    val target = position.target ?: return@addOnCameraIdleListener
                    currentOnCameraIdle.value(MapCamera(target.latitude, target.longitude, position.zoom))
                }
            }
        }
    }

    LaunchedEffect(mapView, styleUrl) {
        mapView.getMapAsync { map -> map.setStyle(Style.Builder().fromUri(styleUrl)) }
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
