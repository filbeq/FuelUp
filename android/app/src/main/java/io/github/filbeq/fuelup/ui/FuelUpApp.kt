package io.github.filbeq.fuelup.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.filbeq.fuelup.map.MapCamera
import io.github.filbeq.fuelup.ui.about.AboutScreen
import io.github.filbeq.fuelup.ui.map.MapScreen

/**
 * Top-level UI: the map, or the About screen on top of it.
 *
 * Two screens don't need a navigation library: a saved flag plus the system
 * Back gesture is enough. The camera lives here so the map comes back where
 * the user left it.
 */
@Composable
fun FuelUpApp() {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var camera by rememberSaveable(stateSaver = MapCamera.Saver) { mutableStateOf(MapCamera.Italy) }

    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
    } else {
        MapScreen(
            camera = camera,
            onCameraChange = { camera = it },
            onOpenAbout = { showAbout = true },
        )
    }
}
