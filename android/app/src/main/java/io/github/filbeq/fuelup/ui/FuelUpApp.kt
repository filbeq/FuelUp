package io.github.filbeq.fuelup.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.filbeq.fuelup.map.MapCamera
import io.github.filbeq.fuelup.ui.about.AboutScreen
import io.github.filbeq.fuelup.ui.map.MapScreen
import io.github.filbeq.fuelup.ui.map.MapViewModel
import kotlinx.serialization.Serializable

/** The app's screens. The back stack is a list of these (Navigation 3). */
@Serializable
data object MapRoute : NavKey

@Serializable
data object AboutRoute : NavKey

/**
 * Top-level UI. Navigation 3 keeps the back stack (a saved list of screens,
 * starting with the map) and handles system Back between screens.
 *
 * The map's data (ViewModel), camera and selected station live here, above
 * the navigation, so they survive moving to another screen and back.
 */
@Composable
fun FuelUpApp(mapViewModel: MapViewModel = viewModel()) {
    val mapState by mapViewModel.state.collectAsStateWithLifecycle()
    var camera by rememberSaveable(stateSaver = MapCamera.Saver) { mutableStateOf(MapCamera.Italy) }
    var selectedStationId by rememberSaveable { mutableStateOf<Int?>(null) }
    val backStack = rememberNavBackStack(MapRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<MapRoute> {
                MapScreen(
                    state = mapState,
                    onRetry = mapViewModel::retry,
                    onChoiceChange = mapViewModel::setChoice,
                    camera = camera,
                    onCameraChange = { camera = it },
                    selectedStationId = selectedStationId,
                    onStationClick = { selectedStationId = it },
                    onDismissStation = { selectedStationId = null },
                    onOpenAbout = { backStack.add(AboutRoute) },
                )
            }
            entry<AboutRoute> {
                AboutScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
            }
        },
    )
}
