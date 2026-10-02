package io.github.filbeq.fuelup.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.MapStyleMode
import io.github.filbeq.fuelup.data.isDark
import io.github.filbeq.fuelup.map.CameraCommand
import io.github.filbeq.fuelup.map.CameraMove
import io.github.filbeq.fuelup.map.CurrentMapProvider
import io.github.filbeq.fuelup.map.LabelLanguage
import io.github.filbeq.fuelup.map.MapCamera
import io.github.filbeq.fuelup.map.MapLabels
import io.github.filbeq.fuelup.map.MapLibreMap
import io.github.filbeq.fuelup.map.StationColors
import io.github.filbeq.fuelup.map.styleUrl
import io.github.filbeq.fuelup.ui.station.StationDetails
import io.github.filbeq.fuelup.ui.station.StationSheetBody
import io.github.filbeq.fuelup.ui.station.StationSheetHeader
import io.github.filbeq.fuelup.ui.station.stationDetails
import io.github.filbeq.fuelup.ui.theme.ClusterColorDark
import io.github.filbeq.fuelup.ui.theme.ClusterColorLight
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The map with a non-modal station sheet. The map stays interactive while the
 * sheet is open; tapping another station switches the sheet to it.
 * Sheet states: hidden (nothing selected), collapsed (header), expanded (all).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: MapUiState,
    onRetry: () -> Unit,
    onChoiceChange: (FuelChoice) -> Unit,
    mapStyle: MapStyleMode,
    camera: MapCamera,
    onCameraChange: (MapCamera) -> Unit,
    selectedStationId: Int?,
    onStationClick: (Int) -> Unit,
    onDismissStation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    nearMe: NearMeState,
    /** "My location" tapped (before any permission question). */
    onOpenNearMe: () -> Unit,
    /** Permission granted: find the position. */
    onLocate: () -> Unit,
    /** Permission refused; true if the system won't ask again. */
    onLocationDenied: (Boolean) -> Unit,
    onCloseNearMe: () -> Unit,
) {
    val provider = CurrentMapProvider
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Saveable: after rotation the sheet comes back collapsed or expanded as it was.
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val currentOnDismiss by rememberUpdatedState(onDismissStation)
    val currentOnCloseNearMe by rememberUpdatedState(onCloseNearMe)

    // Location permission, asked only when "my location" is tapped (approximate only).
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            onLocate()
        } else {
            val willAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            onLocationDenied(!willAskAgain)
        }
    }
    val requestLocation = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onLocate() else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val details = remember(state.snapshot, selectedStationId) {
        selectedStationId?.let { id -> state.snapshot?.stationDetails(id) }
    }
    // Keep showing the last station while the sheet slides away.
    var shownDetails by remember { mutableStateOf<StationDetails?>(null) }
    if (details != null) shownDetails = details

    // The sheet shows the selected station, else the "near me" panel; with neither, it hides.
    val sheetWanted = selectedStationId != null || nearMe.open
    LaunchedEffect(sheetWanted) {
        if (sheetWanted) {
            if (sheetState.currentValue == SheetValue.Hidden) sheetState.partialExpand()
        } else if (sheetState.currentValue != SheetValue.Hidden) {
            sheetState.hide()
        }
    }
    // Swiping the sheet away deselects the station and closes "near me".
    LaunchedEffect(sheetState) {
        var previous = sheetState.currentValue
        snapshotFlow { sheetState.currentValue }.collect { value ->
            if (value == SheetValue.Hidden && previous != SheetValue.Hidden) {
                currentOnDismiss()
                currentOnCloseNearMe()
            }
            previous = value
        }
    }
    // The station is gone from newly downloaded data: close the sheet.
    LaunchedEffect(details, state.snapshot) {
        if (selectedStationId != null && state.snapshot != null && details == null) currentOnDismiss()
    }
    // Back: expanded → collapsed → closed; a station opened from "near me" goes back to it.
    BackHandler(enabled = sheetWanted) {
        scope.launch {
            when {
                sheetState.currentValue == SheetValue.Expanded -> sheetState.partialExpand()
                selectedStationId != null -> onDismissStation()
                else -> onCloseNearMe()
            }
        }
    }

    // Collapsed height = drag handle + header (+ gesture/navigation bar), measured.
    var handleHeightPx by remember { mutableIntStateOf(0) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    val peekHeight = with(density) { (handleHeightPx + headerHeightPx + navBarPx).toDp() }

    // Where the sheet's top edge is, so the map credits can stay above it.
    var sheetTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var mapBottomPx by remember { mutableFloatStateOf(0f) }
    var mapHeightPx by remember { mutableIntStateOf(0) }
    // Height of the controls over the top of the map (fuel chips, status card).
    var topControlsPx by remember { mutableIntStateOf(0) }

    // A new position: fit the search circle between the top controls and the sheet.
    var cameraCommand by remember { mutableStateOf<CameraCommand?>(null) }
    LaunchedEffect(nearMe.fixCount) {
        val position = nearMe.position ?: return@LaunchedEffect
        cameraCommand = CameraCommand(
            id = nearMe.fixCount,
            move = CameraMove.FitCircle(
                position.lat,
                position.lon,
                NEAR_ME_RADIUS_KM,
                topPx = topControlsPx,
                bottomPx = (mapHeightPx * SHEET_SHARE).roundToInt(),
            ),
        )
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetDragHandle = {
            Box(Modifier.onSizeChanged { handleHeightPx = it.height }) { BottomSheetDefaults.DragHandle() }
        },
        sheetContent = {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { sheetTopPx = it.positionInWindow().y - handleHeightPx }) {
                val station = shownDetails
                // The station has priority; while the sheet slides away, the last content stays.
                if (station != null && (selectedStationId != null || !nearMe.open)) {
                    StationSheetHeader(
                        details = station,
                        choice = state.choice,
                        ranked = state.ranking[station.id],
                        onOpenStation = onStationClick,
                        modifier = Modifier.onSizeChanged { size -> headerHeightPx = size.height },
                    )
                    StationSheetBody(station, state.choice)
                } else if (nearMe.open) {
                    NearMeStatusPanel(
                        state = nearMe,
                        onTryAgain = requestLocation,
                        modifier = Modifier.onSizeChanged { size -> headerHeightPx = size.height },
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                // Top bar only: the sheet floats over the map, which stays full height.
                .padding(top = padding.calculateTopPadding())
                .onGloballyPositioned {
                    mapBottomPx = it.boundsInWindow().bottom
                    mapHeightPx = it.size.height
                },
        ) {
            MapLibreMap(
                styleUrl = provider.styleUrl(mapStyle, darkTheme = isSystemInDarkTheme()),
                camera = camera,
                onCameraIdle = onCameraChange,
                stationsGeoJson = state.stationsGeoJson,
                stationColors = StationColors(
                    // Clusters contrast with the map itself: dark on the light map, pale on the dark one.
                    cluster = (if (mapStyle.isDark(darkTheme = isSystemInDarkTheme())) ClusterColorDark else ClusterColorLight).toArgb(),
                    selected = MaterialTheme.colorScheme.tertiary.toArgb(),
                    labelText = MaterialTheme.colorScheme.onSurface.toArgb(),
                    labelHalo = MaterialTheme.colorScheme.surface.toArgb(),
                ),
                labelFont = provider.labelFont,
                mapLabels = mapLabels(),
                labelLanguage = LabelLanguage.forLocale(LocalConfiguration.current.locales[0]),
                selectedStationId = selectedStationId,
                onStationClick = onStationClick,
                userPosition = nearMe.position,
                searchRadiusKm = NEAR_ME_RADIUS_KM.takeIf { nearMe.open },
                locationColor = MaterialTheme.colorScheme.primary.toArgb(),
                cameraCommand = cameraCommand,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier.align(Alignment.TopCenter).onSizeChanged { topControlsPx = it.height },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FuelSelector(choice = state.choice, onChoiceChange = onChoiceChange)
                DataStatusCard(state = state, onRetry = onRetry)
            }
            // Lift the credits and the button above the sheet so they're never covered.
            val aboveSheet = Modifier.offset { IntOffset(0, -(mapBottomPx - sheetTopPx).coerceAtLeast(0f).roundToInt()) }
            MapAttributionBar(onClick = onOpenAbout, modifier = Modifier.align(Alignment.BottomStart).then(aboveSheet))
            SmallFloatingActionButton(
                onClick = {
                    onOpenNearMe()
                    requestLocation()
                },
                // Above the credits line, at the right edge.
                modifier = Modifier.align(Alignment.BottomEnd).then(aboveSheet).padding(end = 12.dp, bottom = 40.dp),
            ) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.action_my_location))
            }
        }
    }
}

/**
 * Map credits, always visible in a corner of the map (OSMF attribution
 * guidelines). Tapping them opens About, which has the links.
 */
@Composable
private fun MapAttributionBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = CurrentMapProvider.attributions.map { stringResource(it.label) }.joinToString(" ")
    Surface(
        modifier = modifier.padding(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .clickable(onClickLabel = stringResource(R.string.action_show_credits), onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** "Near me" search radius. */
private const val NEAR_ME_RADIUS_KM = 10.0

/** Share of the map's height the sheet covers when it opens, kept clear when fitting the camera. */
private const val SHEET_SHARE = 0.4f

/** Map label texts in the app language (see [MapLabels]). */
@Composable
private fun mapLabels(): MapLabels {
    val locale = LocalConfiguration.current.locales[0]
    val template = stringResource(R.string.cluster_from_price)
    val (prefix, suffix) = template.split("%1\$s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return MapLabels(localeTag = locale.toLanguageTag(), clusterPricePrefix = prefix, clusterPriceSuffix = suffix)
}
