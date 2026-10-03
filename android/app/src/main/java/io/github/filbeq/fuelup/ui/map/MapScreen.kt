package io.github.filbeq.fuelup.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import io.github.filbeq.fuelup.PerfLog
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FavoriteStation
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.MapStyleMode
import io.github.filbeq.fuelup.data.Municipality
import io.github.filbeq.fuelup.data.Nearby
import io.github.filbeq.fuelup.data.NearbySort
import io.github.filbeq.fuelup.data.SearchResults
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
import io.github.filbeq.fuelup.ui.isTallWindow
import io.github.filbeq.fuelup.ui.isWideWindow
import io.github.filbeq.fuelup.ui.station.StationDetails
import io.github.filbeq.fuelup.ui.station.StationSheetBody
import io.github.filbeq.fuelup.ui.station.StationSheetHeader
import io.github.filbeq.fuelup.ui.station.stationDetails
import io.github.filbeq.fuelup.ui.theme.ClusterColorDark
import io.github.filbeq.fuelup.ui.theme.ClusterColorLight
import io.github.filbeq.fuelup.ui.theme.LocationColorDark
import io.github.filbeq.fuelup.ui.theme.LocationColorLight
import io.github.filbeq.fuelup.ui.theme.LocationHaloDark
import io.github.filbeq.fuelup.ui.theme.LocationHaloLight
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The map with the station details and the "near me" list beside it: in a
 * non-modal bottom sheet on narrow windows (portrait phones), in a side panel
 * on wide ones ([isWideWindow]: landscape phones, tablets). The map stays
 * interactive; tapping another station switches the details to it.
 * Sheet states: hidden (nothing selected), collapsed (header), expanded (all).
 * The side panel always shows it all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    /** Another screen is on top: Back and accessibility belong to it. */
    covered: Boolean,
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
    onRadiusChange: (Int) -> Unit,
    onSortChange: (NearbySort) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onRemoveFavorite: (Int) -> Unit,
    onReplaceFavorite: (FavoriteStation, Int) -> Unit,
) {
    val provider = CurrentMapProvider
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Side panel instead of the bottom sheet. Switching means a rotation or a
    // window resize, which rebuilds the screen: saved state carries over.
    val wide = isWideWindow()
    val currentOnDismiss by rememberUpdatedState(onDismissStation)
    val currentOnCloseNearMe by rememberUpdatedState(onCloseNearMe)

    // Location permission, asked only when "my location" is tapped. Approximate
    // and precise together: on Android 12+ the user picks, approximate is enough.
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            onLocate()
        } else {
            val willAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            onLocationDenied(!willAskAgain)
        }
    }
    val requestLocation = {
        val granted = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) onLocate() else permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    val details = remember(state.snapshot, selectedStationId) {
        selectedStationId?.let { id -> state.snapshot?.stationDetails(id) }
    }
    // Keep showing the last station while the sheet slides away.
    var shownDetails by remember { mutableStateOf<StationDetails?>(null) }
    if (details != null) shownDetails = details
    // Where the station's details are scrolled to; back at the top for another station.
    val stationScroll = rememberSaveable(shownDetails?.id, saver = ScrollState.Saver) { ScrollState(0) }

    // The sheet shows the selected station, else the "near me" panel; with neither, it hides.
    val sheetWanted = selectedStationId != null || nearMe.open
    // Which of the two it shows, kept while it slides away (else closing "near
    // me" after visiting a station would show that station on the way out).
    var shownContent by remember { mutableStateOf(SheetContent.None) }
    if (sheetWanted) shownContent = if (selectedStationId != null) SheetContent.Station else SheetContent.NearMe
    val listShown = selectedStationId == null && nearMe.open && nearMe.status == NearMeStatus.Located && state.snapshot != null
    // An empty-map tap lowers the list to its header ("minimised"); the title,
    // a drag up or the my-location button bring it back. Kept across a station visit.
    var nearbyMinimised by rememberSaveable { mutableStateOf(false) }
    val nearbyListState = rememberLazyListState()
    // The sheet is expanded, or the side panel shows more than a collapsed sheet
    // would (it's scrolled): a rotation to the other layout keeps that.
    var sheetExpanded by rememberSaveable { mutableStateOf(false) }

    // Only the narrow layout has a sheet. It starts as saved here (hidden,
    // collapsed or expanded), whichever layout was on screen before a rotation.
    // Not the sheet's own saved state: it would outlive a stay in the side panel
    // and bring back how the sheet was before it.
    val sheetState = if (wide) {
        null
    } else {
        remember {
            SheetState(
                skipPartiallyExpanded = false,
                // Material's defaults for a standard bottom sheet.
                positionalThreshold = { with(density) { 56.dp.toPx() } },
                velocityThreshold = { with(density) { 125.dp.toPx() } },
                initialValue = when {
                    !sheetWanted -> SheetValue.Hidden
                    sheetExpanded -> SheetValue.Expanded
                    else -> SheetValue.PartiallyExpanded
                },
                skipHiddenState = false,
            )
        }
    }
    LaunchedEffect(sheetWanted, sheetState) {
        if (!sheetWanted) sheetExpanded = false
        if (sheetState == null) return@LaunchedEffect
        if (sheetWanted) {
            if (sheetState.currentValue == SheetValue.Hidden) sheetState.partialExpand()
        } else if (sheetState.currentValue != SheetValue.Hidden) {
            sheetState.hide()
        }
    }
    // Swiping the sheet away deselects the station and closes "near me".
    LaunchedEffect(sheetState) {
        if (sheetState == null) return@LaunchedEffect
        var previous = sheetState.currentValue
        snapshotFlow { sheetState.currentValue }.collect { value ->
            if (value == SheetValue.Hidden && previous != SheetValue.Hidden) {
                currentOnDismiss()
                currentOnCloseNearMe()
                nearbyMinimised = false
            }
            // Dragged all the way up: the list is back in full.
            if (value == SheetValue.Expanded) nearbyMinimised = false
            // Back to the collapsed sheet: show the first rows again.
            if (value == SheetValue.PartiallyExpanded && previous == SheetValue.Expanded) nearbyListState.scrollToItem(0)
            sheetExpanded = value == SheetValue.Expanded
            previous = value
        }
    }
    // Side panel: scrolling its contents counts as expanding the sheet;
    // a minimised list as a collapsed one.
    LaunchedEffect(wide, selectedStationId != null, stationScroll) {
        if (!wide) return@LaunchedEffect
        val station = selectedStationId != null
        snapshotFlow {
            val scrolled = if (station) {
                stationScroll.value > 0
            } else {
                nearbyListState.firstVisibleItemIndex > 0 || nearbyListState.firstVisibleItemScrollOffset > 0
            }
            scrolled to (!station && nearbyMinimised)
        }.collect { (scrolled, minimised) -> sheetExpanded = !minimised && (scrolled || sheetExpanded) }
    }
    // The station is gone from newly downloaded data: close the sheet.
    LaunchedEffect(details, state.snapshot) {
        if (selectedStationId != null && state.snapshot != null && details == null) currentOnDismiss()
    }

    // Another station, or back to the list: like the sheet, which shows it collapsed.
    // The last known spot of a favourite missing from the data, shown from the
    // search as a hollow dot (like a station without a marker); null = none.
    var missingSpot by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val selectStation = { id: Int ->
        missingSpot = null
        onStationClick(id)
        if (wide) sheetExpanded = false
    }
    val leaveStation = {
        onDismissStation()
        if (wide) sheetExpanded = false
    }
    // The panel's close button, and Back once the sheet is collapsed:
    // a station opened from "near me" goes back to the list, else close.
    val closePanel = {
        if (selectedStationId != null) {
            leaveStation()
        } else {
            onCloseNearMe()
            nearbyMinimised = false
        }
    }
    // Back: expanded → collapsed → closed; a station opened from "near me" goes back to it.
    BackHandler(enabled = sheetWanted && !covered) {
        scope.launch {
            if (sheetState != null && sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() else closePanel()
        }
    }
    // Minimising also lowers an expanded sheet.
    LaunchedEffect(nearbyMinimised, sheetState) {
        if (nearbyMinimised && sheetState?.currentValue == SheetValue.Expanded) sheetState.partialExpand()
    }
    // An empty spot on the map: leave the station (back to what was there before),
    // else lower the "near me" list.
    val onMapTapEmpty = {
        missingSpot = null
        when {
            selectedStationId != null -> leaveStation()
            listShown -> nearbyMinimised = true
        }
    }

    // Collapsed height = drag handle + header (+ gesture/navigation bar), measured.
    var handleHeightPx by remember { mutableIntStateOf(0) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // The "near me" list's header (the minimised sheet) and its collapsed height (header + first rows).
    var nearbyHeaderPx by remember { mutableIntStateOf(0) }
    var nearbyCollapsedPx by remember { mutableIntStateOf(0) }
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    val contentPeekPx = when {
        !listShown -> headerHeightPx
        nearbyMinimised -> nearbyHeaderPx
        else -> nearbyCollapsedPx
    }
    val peekHeight = with(density) { (handleHeightPx + contentPeekPx + navBarPx).toDp() }

    // The fuel choice panel (fuel, self/served, legend); kept open across rotation.
    var fuelPanelOpen by rememberSaveable { mutableStateOf(false) }
    if (fuelPanelOpen && !covered) {
        FuelChoiceSheet(choice = state.choice, onChoiceChange = onChoiceChange, onDismiss = { fuelPanelOpen = false })
    }

    // Search: the text survives rotation; open = keyboard and results over the map.
    val searchBarState = rememberSearchBarState()
    val searchText = rememberTextFieldState()
    val searchOpen = searchBarState.targetValue == SearchBarValue.Expanded
    // Results in a dropdown under the bar only where several fit above the
    // keyboard (tablets); landscape phones get the full-screen search, like portrait.
    val searchDocked = wide && isTallWindow()
    // Latest results, with the text they are for. Recomputed off the main thread on
    // every keystroke (the previous search is dropped), and when the fuel or data change.
    var searchResults by remember { mutableStateOf<Pair<String, SearchResults>?>(null) }
    val currentCamera by rememberUpdatedState(camera)
    LaunchedEffect(state.search, state.ranking) {
        val search = state.search ?: return@LaunchedEffect
        snapshotFlow { searchText.text.toString() }.collectLatest { query ->
            val results = withContext(Dispatchers.Default) {
                PerfLog.time("search '$query'") {
                    search.search(query, state.ranking, currentCamera.latitude, currentCamera.longitude)
                }
            }
            searchResults = query to results
        }
    }
    // Where the sheet's top edge is, so the map credits can stay above it.
    var sheetTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var mapBottomPx by remember { mutableFloatStateOf(0f) }
    var mapHeightPx by remember { mutableIntStateOf(0) }
    // Where the side panel ends (window x, 0 when closed) and the map starts.
    var panelRightPx by remember { mutableFloatStateOf(0f) }
    var mapLeftPx by remember { mutableFloatStateOf(0f) }
    // Height of the controls over the top of the map (fuel chips, status card).
    var topControlsPx by remember { mutableIntStateOf(0) }
    // The system bars and camera cutout beside the map (landscape).
    val layoutDirection = LocalLayoutDirection.current
    val safeLeftPx = WindowInsets.safeDrawing.getLeft(density, layoutDirection)
    val safeRightPx = WindowInsets.safeDrawing.getRight(density, layoutDirection)
    // The part of the map the open side panel covers, from its left edge: fixed,
    // for camera moves (the measured edge moves while the panel slides in).
    val panelCoverPx = if (wide) safeLeftPx + with(density) { (SIDE_PANEL_WIDTH + SIDE_PANEL_MARGIN * 2).roundToPx() } else 0
    // The same, as drawn right now, for the controls over the map.
    val panelShownPx = (panelRightPx - mapLeftPx).coerceAtLeast(0f).roundToInt()
    // Height of the search bar's row; on wide windows the side panel starts below it.
    var searchRowPx by remember { mutableIntStateOf(0) }
    val safeTopPx = WindowInsets.safeDrawing.getTop(density)

    // The map runs under the status bar: its icons follow the map's darkness
    // (dark icons on the light map), and the app theme while another screen is on top.
    val appDark = isSystemInDarkTheme()
    val mapIsDark = mapStyle.isDark(darkTheme = appDark)
    val view = LocalView.current
    // The full-screen search covers the map like another screen.
    val mapHidden = covered || (searchOpen && !searchDocked)
    LaunchedEffect(activity, mapHidden, mapIsDark, appDark) {
        val window = activity?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !(if (mapHidden) appDark else mapIsDark)
    }

    // The "near me" list: recomputed when the position, radius, order, fuel or data change (~ms).
    val nearby = remember(nearMe.position, nearMe.settings, state.ranking, state.snapshot) {
        val position = nearMe.position
        val snapshot = state.snapshot
        if (position == null || snapshot == null) {
            emptyList()
        } else {
            Nearby.find(snapshot.stations.stations, state.ranking, position.lat, position.lon, nearMe.settings.radiusKm, nearMe.settings.sort)
        }
    }

    // A new position or radius: fit the search circle between the top controls and the sheet
    // (or beside the side panel).
    var cameraCommand by remember { mutableStateOf<CameraCommand?>(null) }
    val radiusKm = nearMe.settings.radiusKm
    // The circle the camera last fitted, kept across rotation: a rebuilt screen
    // must not move the camera back to it once the user has moved the map.
    var fittedFix by rememberSaveable { mutableLongStateOf(0L) }
    var fittedRadiusKm by rememberSaveable { mutableIntStateOf(0) }
    // That fit used the measured list height (not the first estimate).
    var fittedMeasured by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(nearMe.fixCount, radiusKm, nearbyCollapsedPx > 0, wide) {
        val position = nearMe.position ?: return@LaunchedEffect
        if (!nearMe.open) return@LaunchedEffect
        // The side panel's width is known at once.
        val measured = wide || nearbyCollapsedPx > 0
        val sameCircle = nearMe.fixCount == fittedFix && radiusKm == fittedRadiusKm
        // Same circle: only refine an estimated fit once the list is measured.
        if (sameCircle && (fittedMeasured || !measured)) return@LaunchedEffect
        fittedFix = nearMe.fixCount
        fittedRadiusKm = radiusKm
        fittedMeasured = measured
        cameraCommand = CameraCommand(
            id = (cameraCommand?.id ?: 0) + 1,
            move = CameraMove.FitCircle(
                position.lat,
                position.lon,
                radiusKm.toDouble(),
                topPx = topControlsPx,
                bottomPx = when {
                    wide -> 0
                    nearbyCollapsedPx > 0 -> handleHeightPx + nearbyCollapsedPx + navBarPx
                    else -> (mapHeightPx * SHEET_SHARE).roundToInt()
                },
                leftPx = panelCoverPx,
            ),
        )
    }

    // Waits until the sheet has finished moving (after a selection opened, lowered
    // or closed it), so the free part of the map is known.
    suspend fun awaitSheetSettled() {
        val sheet = sheetState ?: return
        // The selection reaches the sheet's own effect a frame or two later.
        repeat(2) { withFrameNanos { } }
        withTimeoutOrNull(SHEET_SETTLE_TIMEOUT_MS) { snapshotFlow { sheet.currentValue == sheet.targetValue }.first { it } }
        withFrameNanos { }
    }
    val freeBottomPx = { if (wide) 0 else (mapBottomPx - sheetTopPx).coerceAtLeast(0f).roundToInt() }
    val moveCamera = { move: CameraMove -> cameraCommand = CameraCommand(id = (cameraCommand?.id ?: 0) + 1, move = move) }

    // A station from the "near me" list or the search: selected as by a tap,
    // then shown in the free part of the map, zoomed in enough to show it alone.
    val openStation = { lat: Double, lon: Double, id: Int ->
        selectStation(id)
        scope.launch {
            // From the expanded sheet: show the station collapsed (its chosen-fuel price).
            if (sheetState?.currentValue == SheetValue.Expanded) sheetState.partialExpand()
            awaitSheetSettled()
            moveCamera(CameraMove.Show(lat, lon, minZoom = STATION_ZOOM, topPx = topControlsPx, bottomPx = freeBottomPx(), leftPx = panelCoverPx))
        }
    }
    // A favourite missing from the data: its last known spot, nothing selected.
    val showSpot = { lat: Double, lon: Double ->
        onMapTapEmpty()
        missingSpot = lat to lon
        scope.launch {
            awaitSheetSettled()
            moveCamera(CameraMove.Show(lat, lon, minZoom = STATION_ZOOM, topPx = topControlsPx, bottomPx = freeBottomPx(), leftPx = if (nearMe.open) panelCoverPx else 0))
        }
    }
    // A municipality from the search: like a tap on empty map (leave the station,
    // lower the "near me" list), then frame all its stations.
    val openMunicipality = { municipality: Municipality ->
        onMapTapEmpty()
        scope.launch {
            awaitSheetSettled()
            moveCamera(
                CameraMove.FitPoints(
                    municipality.mainStations().map { doubleArrayOf(it.lat, it.lon) },
                    maxZoom = STATION_ZOOM + 1,
                    topPx = topControlsPx,
                    bottomPx = freeBottomPx(),
                    leftPx = if (nearMe.open) panelCoverPx else 0,
                ),
            )
        }
    }

    if (!covered) {
        val query = searchText.text.toString()
        MapSearchExpanded(
            searchBarState = searchBarState,
            textFieldState = searchText,
            docked = searchDocked,
            ready = state.search != null,
            query = query,
            results = searchResults?.takeIf { it.first == query }?.second,
            choice = state.choice,
            ranking = state.ranking,
            brands = state.snapshot?.stations?.brands.orEmpty(),
            favorites = FavoritesInSearch(
                list = state.favorites,
                file = state.snapshot?.stations,
                position = nearMe.position,
                onShowSpot = { favorite ->
                    scope.launch { searchBarState.animateToCollapsed() }
                    showSpot(favorite.lat, favorite.lon)
                },
                onRemove = onRemoveFavorite,
                onReplace = { favorite, station -> onReplaceFavorite(favorite, station.id) },
            ),
            onMunicipalityClick = { municipality ->
                scope.launch { searchBarState.animateToCollapsed() }
                openMunicipality(municipality)
            },
            onStationClick = { station ->
                scope.launch { searchBarState.animateToCollapsed() }
                openStation(station.lat, station.lon, station.id)
            },
        )
    }

    // The sheet's or the side panel's contents; the panel has a close button.
    val panelContent: @Composable (closeButton: (@Composable () -> Unit)?) -> Unit = { closeButton ->
        val station = shownDetails
        // The station has priority; while the sheet slides away, the last content stays.
        if (station != null && shownContent == SheetContent.Station) {
            StationSheetHeader(
                details = station,
                choice = state.choice,
                ranked = state.ranking[station.id],
                onOpenStation = selectStation,
                favorite = state.favorites.any { it.id == station.id },
                onToggleFavorite = { onToggleFavorite(station.id) },
                modifier = Modifier.onSizeChanged { size -> headerHeightPx = size.height },
                closeButton = closeButton,
            )
            StationSheetBody(station, state.choice, stationScroll)
        } else if (shownContent == SheetContent.NearMe && nearMe.status == NearMeStatus.Located && state.snapshot != null) {
            NearbyList(
                state = nearMe,
                choice = state.choice,
                stations = nearby,
                brands = state.snapshot.stations.brands,
                listState = nearbyListState,
                minimised = nearbyMinimised,
                onExpand = { nearbyMinimised = false },
                onRadiusChange = onRadiusChange,
                onSortChange = onSortChange,
                onStationClick = { item -> openStation(item.station.lat, item.station.lon, item.station.id) },
                onMeasured = { headerPx, collapsedPx ->
                    nearbyHeaderPx = headerPx
                    nearbyCollapsedPx = collapsedPx
                },
                // The minimised side panel is just the header (the sheet hides the rows by its height).
                showRows = !(wide && nearbyMinimised),
                closeButton = closeButton,
            )
        } else if (shownContent == SheetContent.NearMe) {
            NearMeStatusPanel(
                state = nearMe,
                onTryAgain = requestLocation,
                modifier = Modifier.onSizeChanged { size -> headerHeightPx = size.height },
                closeButton = closeButton,
            )
        }
    }

    // The map and the controls over it; on wide windows the side panel too.
    // Full screen, edge to edge: the sheet floats over the map, which stays full height.
    val mapArea: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    mapBottomPx = bounds.bottom
                    mapLeftPx = bounds.left
                    mapHeightPx = it.size.height
                },
        ) {
            MapLibreMap(
                styleUrl = provider.styleUrl(mapStyle, darkTheme = appDark),
                camera = camera,
                onCameraIdle = onCameraChange,
                stationsGeoJson = state.stationsGeoJson,
                stationColors = StationColors(
                    // Clusters contrast with the map itself: dark on the light map, pale on the dark one.
                    cluster = (if (mapIsDark) ClusterColorDark else ClusterColorLight).toArgb(),
                    selected = MaterialTheme.colorScheme.tertiary.toArgb(),
                    labelText = MaterialTheme.colorScheme.onSurface.toArgb(),
                    labelHalo = MaterialTheme.colorScheme.surface.toArgb(),
                    favoriteOutline = (if (mapIsDark) LocationHaloDark else LocationHaloLight).toArgb(),
                ),
                labelFont = provider.labelFont,
                mapLabels = mapLabels(),
                labelLanguage = LabelLanguage.forLocale(LocalConfiguration.current.locales[0]),
                selectedStationId = selectedStationId,
                // A station picked in the search that has no marker (doesn't sell the chosen fuel).
                favoriteIds = remember(state.favorites) { state.favorites.mapTo(HashSet()) { it.id } },
                // Or a missing favourite's last known spot.
                selectedOffMap = details?.takeIf { it.id !in state.ranking }?.let { it.lat to it.lon } ?: missingSpot,
                onStationClick = { id ->
                    selectStation(id)
                    // A station under the side panel's place or the controls: bring it out beside / below them.
                    val target = if (wide) state.snapshot?.stationDetails(id) else null
                    if (target != null) {
                        cameraCommand = CameraCommand(
                            id = (cameraCommand?.id ?: 0) + 1,
                            move = CameraMove.Reveal(target.lat, target.lon, leftPx = panelCoverPx, topPx = topControlsPx),
                        )
                    }
                },
                onMapTapEmpty = onMapTapEmpty,
                // Shown only while "near me" is open: closing it clears the map.
                userPosition = nearMe.position.takeIf { nearMe.open },
                searchRadiusKm = radiusKm.toDouble().takeIf { nearMe.open },
                // Like the clusters, by the map's darkness (the app theme may differ).
                locationColor = (if (mapIsDark) LocationColorDark else LocationColorLight).toArgb(),
                locationHalo = (if (mapIsDark) LocationHaloDark else LocationHaloLight).toArgb(),
                cameraCommand = cameraCommand,
                modifier = Modifier.fillMaxSize(),
            )
            // A faint wash of the map's own tone under the status bar, so place
            // names don't mix with the clock and icons.
            val scrim = if (mapIsDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.7f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { (WindowInsets.statusBars.getTop(density) * 1.5f).toDp() })
                    .background(Brush.verticalGradient(0f to scrim, 0.6f to scrim, 1f to Color.Transparent)),
            )
            MapTopControls(
                state = state,
                onRetry = onRetry,
                onOpenFuel = { fuelPanelOpen = true },
                onOpenSettings = onOpenSettings,
                searchBar = { MapSearchBar(searchBarState, searchText) },
                wide = wide,
                onSearchRowHeight = { searchRowPx = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Measured with the insets, from the top of the map: includes the status bar.
                    .onSizeChanged { topControlsPx = it.height }
                    // Below the status bar, clear of the cutout and a side navigation bar.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(
                        start = with(density) { safeLeftPx.toDp() },
                        end = with(density) { safeRightPx.toDp() },
                    ),
            )
            // Lift the credits and the button above the sheet so they're never covered;
            // move the credits beside the side panel.
            // A tall sheet covers them instead of pushing them up into the top controls and status bar.
            val maxLiftPx = (mapHeightPx - topControlsPx - with(density) { LIFT_CLEARANCE.toPx() }).coerceAtLeast(0f)
            val aboveSheet = Modifier.offset {
                IntOffset(0, -(mapBottomPx - sheetTopPx).coerceIn(0f, maxLiftPx).roundToInt())
            }
            val besidePanel = Modifier.offset { IntOffset(maxOf(panelShownPx, safeLeftPx), 0) }
            MapAttributionBar(
                onClick = onOpenAbout,
                modifier = Modifier.align(Alignment.BottomStart).then(if (wide) besidePanel else aboveSheet),
            )
            FloatingActionButton(
                onClick = {
                    nearbyMinimised = false
                    missingSpot = null
                    onOpenNearMe()
                    requestLocation()
                },
                // Above the credits line, at the right edge.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(if (wide) Modifier.offset { IntOffset(-safeRightPx, 0) } else aboveSheet)
                    .padding(end = 16.dp, bottom = 40.dp),
            ) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.action_my_location))
            }
            if (wide) {
                SidePanel(
                    visible = sheetWanted,
                    onClose = closePanel,
                    onRightEdge = { panelRightPx = it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // Under the search bar, which is aligned with it.
                        .padding(top = with(density) { (safeTopPx + searchRowPx).toDp() })
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                        .padding(start = SIDE_PANEL_MARGIN, end = SIDE_PANEL_MARGIN, bottom = SIDE_PANEL_MARGIN),
                ) { closeButton ->
                    panelContent(closeButton)
                }
            }
        }
    }

    // Behind another screen: hidden from TalkBack, which would otherwise read the map too.
    val screenModifier = if (covered) Modifier.clearAndSetSemantics { } else Modifier
    if (sheetState == null) {
        // Side panel layout: nothing but the map and what floats over it.
        Box(screenModifier) {
            mapArea()
        }
    } else {
        BottomSheetScaffold(
            modifier = screenModifier,
            scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
            sheetPeekHeight = peekHeight,
            // A shadow like the side panel's, so the sheet floats like the other controls.
            sheetShadowElevation = 3.dp,
            sheetDragHandle = {
                Box(Modifier.onSizeChanged { handleHeightPx = it.height }) { BottomSheetDefaults.DragHandle() }
            },
            sheetContent = {
                // Fully expanded, the sheet stops just below the status bar (the map runs under it).
                val sheetMaxPx = mapHeightPx - WindowInsets.statusBars.getTop(density) - handleHeightPx - with(density) { 8.dp.roundToPx() }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (sheetMaxPx > 0) Modifier.heightIn(max = with(density) { sheetMaxPx.toDp() }) else Modifier)
                        .onGloballyPositioned { sheetTopPx = it.positionInWindow().y - handleHeightPx },
                ) {
                    panelContent(null)
                }
            },
        ) {
            mapArea()
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
        // Explicit: a translucent surface isn't a theme colour, so Surface can't pick the text colour.
        contentColor = MaterialTheme.colorScheme.onSurface,
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

/** What the sheet (or side panel) shows; None until something is opened. */
private enum class SheetContent { None, Station, NearMe }

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

/** Zoom at which stations show one by one (clusters end at 13). */
private const val STATION_ZOOM = 14.0

/** Room the credits and the my-location button (and its margin) need below the top controls. */
private val LIFT_CLEARANCE = 150.dp

/** Longest wait for the sheet to stop moving before a camera move. */
private const val SHEET_SETTLE_TIMEOUT_MS = 1_500L

/** Share of the map's height the sheet is assumed to cover before the list is measured. */
private const val SHEET_SHARE = 0.4f

/** Map label texts in the app language (see [MapLabels]). */
@Composable
private fun mapLabels(): MapLabels {
    val locale = LocalConfiguration.current.locales[0]
    val template = stringResource(R.string.cluster_from_price)
    val (prefix, suffix) = template.split("%1\$s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return MapLabels(localeTag = locale.toLanguageTag(), clusterPricePrefix = prefix, clusterPriceSuffix = suffix)
}
