package io.github.filbeq.fuelup.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import io.github.filbeq.fuelup.ui.map.NearMeViewModel
import io.github.filbeq.fuelup.ui.settings.SettingsScreen
import io.github.filbeq.fuelup.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

/** The app's screens. The back stack is a list of these (Navigation 3). */
@Serializable
data object MapRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object AboutRoute : NavKey

/**
 * Top-level UI. Navigation 3 keeps the back stack (a saved list of screens,
 * starting with the map) and handles system Back between screens.
 *
 * The map screen lives here too, outside the navigation and underneath it:
 * other screens are drawn over it, so the map (and its loaded style, sheet and
 * "near me" list) stays alive and is back at once, without a ~1 s reload. The
 * map's own back-stack entry is an empty placeholder.
 *
 * The map is not paused while covered: MapLibre draws only when something
 * changes, so an idle map behind Settings costs next to nothing, and pausing it
 * made it flash dark on the way back after the app had been in the background.
 */
@Composable
fun FuelUpApp(
    mapViewModel: MapViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    nearMeViewModel: NearMeViewModel = viewModel(),
) {
    val mapState by mapViewModel.state.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val nearMe by nearMeViewModel.state.collectAsStateWithLifecycle()
    var camera by rememberSaveable(stateSaver = MapCamera.Saver) { mutableStateOf(MapCamera.Italy) }
    var selectedStationId by rememberSaveable { mutableStateOf<Int?>(null) }
    val backStack = rememberNavBackStack(MapRoute)
    // Once per fresh start (not after a rotation or a restored process): if the
    // location may already be used, open on the user's position. Never asks.
    val context = LocalContext.current
    var launchLocateDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (launchLocateDone) return@LaunchedEffect
        launchLocateDone = true
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) nearMeViewModel.locateOnLaunch()
    }
    // Another screen is on top: the map gives up Back and accessibility.
    val mapCovered = backStack.last() != MapRoute

    Box {
        MapScreen(
            covered = mapCovered,
            state = mapState,
            onRetry = mapViewModel::retry,
            onChoiceChange = mapViewModel::setChoice,
            mapStyle = settings.mapStyle,
            camera = camera,
            onCameraChange = { camera = it },
            selectedStationId = selectedStationId,
            onStationClick = { selectedStationId = it },
            onDismissStation = { selectedStationId = null },
            onOpenSettings = { backStack.add(SettingsRoute) },
            onOpenAbout = { backStack.add(AboutRoute) },
            nearMe = nearMe,
            onOpenNearMe = {
                selectedStationId = null
                nearMeViewModel.open()
            },
            onLocate = nearMeViewModel::locate,
            onLocationDenied = nearMeViewModel::denied,
            onCloseNearMe = nearMeViewModel::close,
            onRadiusChange = nearMeViewModel::setRadius,
            onSortChange = nearMeViewModel::setSort,
            onToggleFavorite = mapViewModel::toggleFavorite,
            onRemoveFavorite = mapViewModel::removeFavorite,
            onReplaceFavorite = mapViewModel::replaceFavorite,
        )
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            entryProvider = entryProvider {
                // Nothing to draw: the map is underneath.
                entry<MapRoute> { }
                entry<SettingsRoute> {
                    SettingsScreen(
                        settings = settings,
                        language = settingsViewModel.language(),
                        onThemeChange = settingsViewModel::setTheme,
                        onMapStyleChange = settingsViewModel::setMapStyle,
                        onLanguageChange = settingsViewModel::setLanguage,
                        dataUpdate = mapState.manualUpdate,
                        lastChecked = mapState.lastChecked,
                        currentPricesAt = mapState.snapshot?.meta?.pricesAt,
                        onUpdateNow = mapViewModel::updateNow,
                        onOpenAbout = { backStack.add(AboutRoute) },
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                }
                entry<AboutRoute> {
                    AboutScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
                }
            },
        )
    }
}
