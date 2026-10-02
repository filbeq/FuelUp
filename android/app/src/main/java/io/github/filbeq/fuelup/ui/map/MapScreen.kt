package io.github.filbeq.fuelup.ui.map

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.MapStyleMode
import io.github.filbeq.fuelup.data.isDark
import io.github.filbeq.fuelup.map.CurrentMapProvider
import io.github.filbeq.fuelup.map.LabelLanguage
import io.github.filbeq.fuelup.map.MapCamera
import io.github.filbeq.fuelup.map.MapLabels
import io.github.filbeq.fuelup.map.MapLibreMap
import io.github.filbeq.fuelup.map.StationColors
import io.github.filbeq.fuelup.map.styleUrl
import io.github.filbeq.fuelup.ui.theme.ClusterColorDark
import io.github.filbeq.fuelup.ui.theme.ClusterColorLight
import io.github.filbeq.fuelup.ui.station.StationDetails
import io.github.filbeq.fuelup.ui.station.StationSheetBody
import io.github.filbeq.fuelup.ui.station.StationSheetHeader
import io.github.filbeq.fuelup.ui.station.stationDetails
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
) {
    val provider = CurrentMapProvider
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Saveable: after rotation the sheet comes back collapsed or expanded as it was.
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val currentOnDismiss by rememberUpdatedState(onDismissStation)

    val details = remember(state.snapshot, selectedStationId) {
        selectedStationId?.let { id -> state.snapshot?.stationDetails(id) }
    }
    // Keep showing the last station while the sheet slides away.
    var shownDetails by remember { mutableStateOf<StationDetails?>(null) }
    if (details != null) shownDetails = details

    // Selection drives the sheet: a selected station shows it (collapsed), none hides it.
    LaunchedEffect(selectedStationId) {
        if (selectedStationId != null) {
            if (sheetState.currentValue == SheetValue.Hidden) sheetState.partialExpand()
        } else if (sheetState.currentValue != SheetValue.Hidden) {
            sheetState.hide()
        }
    }
    // Swiping the sheet away deselects the station.
    LaunchedEffect(sheetState) {
        var previous = sheetState.currentValue
        snapshotFlow { sheetState.currentValue }.collect { value ->
            if (value == SheetValue.Hidden && previous != SheetValue.Hidden) currentOnDismiss()
            previous = value
        }
    }
    // The station is gone from newly downloaded data: close the sheet.
    LaunchedEffect(details, state.snapshot) {
        if (selectedStationId != null && state.snapshot != null && details == null) currentOnDismiss()
    }
    // Back: expanded → collapsed → closed.
    BackHandler(enabled = selectedStationId != null) {
        scope.launch {
            if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() else onDismissStation()
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

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetDragHandle = {
            Box(Modifier.onSizeChanged { handleHeightPx = it.height }) { BottomSheetDefaults.DragHandle() }
        },
        sheetContent = {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { sheetTopPx = it.positionInWindow().y - handleHeightPx }) {
                shownDetails?.let {
                    StationSheetHeader(
                        details = it,
                        choice = state.choice,
                        ranked = state.ranking[it.id],
                        onOpenStation = onStationClick,
                        modifier = Modifier.onSizeChanged { size -> headerHeightPx = size.height },
                    )
                    StationSheetBody(it, state.choice)
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
                .onGloballyPositioned { mapBottomPx = it.boundsInWindow().bottom },
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
                modifier = Modifier.fillMaxSize(),
            )
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                FuelSelector(choice = state.choice, onChoiceChange = onChoiceChange)
                DataStatusCard(state = state, onRetry = onRetry)
            }
            MapAttributionBar(
                onClick = onOpenAbout,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // Lift the credits above the sheet so they're never covered.
                    .offset { IntOffset(0, -(mapBottomPx - sheetTopPx).coerceAtLeast(0f).roundToInt()) },
            )
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

/** Map label texts in the app language (see [MapLabels]). */
@Composable
private fun mapLabels(): MapLabels {
    val locale = LocalConfiguration.current.locales[0]
    val template = stringResource(R.string.cluster_from_price)
    val (prefix, suffix) = template.split("%1\$s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return MapLabels(localeTag = locale.toLanguageTag(), clusterPricePrefix = prefix, clusterPriceSuffix = suffix)
}
