package io.github.filbeq.fuelup.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.filbeq.fuelup.PerfLog
import io.github.filbeq.fuelup.data.AppSettingsStore
import io.github.filbeq.fuelup.data.Geo
import io.github.filbeq.fuelup.data.LocateResult
import io.github.filbeq.fuelup.data.Nearby
import io.github.filbeq.fuelup.data.NearbySettings
import io.github.filbeq.fuelup.data.NearbySettingsStore
import io.github.filbeq.fuelup.data.NearbySort
import io.github.filbeq.fuelup.data.UserLocator
import io.github.filbeq.fuelup.data.UserPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where "near me" is at. */
enum class NearMeStatus {
    /** Waiting for the permission dialog or for a position. */
    Locating,
    Located,
    /** Permission refused; the system will still ask again. */
    Denied,
    /** Permission refused for good: only the app's settings page can change it. */
    DeniedPermanently,
    LocationOff,
    Unavailable,
}

data class NearMeState(
    /** The "near me" panel is open (in the sheet). */
    val open: Boolean = false,
    val status: NearMeStatus = NearMeStatus.Locating,
    /** Last position found in this session; kept in memory only, never saved or sent. */
    val position: UserPosition? = null,
    /** Bumped on every new position, so the camera re-centres even if the position is the same. */
    val fixCount: Long = 0,
    /** List radius and order; remembered across launches. */
    val settings: NearbySettings = NearbySettings(),
    /** Radius of the launch view ([Nearby.LAUNCH_RADIUS_KM]) until the user picks one; null = [settings]' radius. */
    val radiusOverrideKm: Int? = null,
    /** Opened by the app at launch (not by the button): the list starts minimised. */
    val openedAtLaunch: Boolean = false,
) {
    /** The radius in use: the launch view's, else the saved one. */
    val radiusKm: Int get() = radiusOverrideKm ?: settings.radiusKm
}

/**
 * The "my location" feature: one position per tap (no tracking). The
 * permission dialog itself is shown by the screen, which reports the answer.
 */
class NearMeViewModel(application: Application) : AndroidViewModel(application) {
    private val locator = UserLocator(application)
    private val settingsStore by lazy {
        NearbySettingsStore(application.getSharedPreferences(AppSettingsStore.PREFS_NAME, Application.MODE_PRIVATE))
    }
    private val _state = MutableStateFlow(NearMeState())
    val state: StateFlow<NearMeState> = _state.asStateFlow()
    private var locateJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { settingsStore.load() }
            _state.update { it.copy(settings = saved) }
        }
    }

    fun setRadius(radiusKm: Int) {
        _state.update { it.copy(radiusOverrideKm = null) }
        updateSettings(_state.value.settings.copy(radiusKm = radiusKm))
    }

    fun setSort(sort: NearbySort) = updateSettings(_state.value.settings.copy(sort = sort))

    private fun updateSettings(settings: NearbySettings) {
        _state.update { it.copy(settings = settings) }
        viewModelScope.launch(Dispatchers.IO) { settingsStore.save(settings) }
    }

    /** "My location" tapped: open the panel; the screen then asks for the permission if needed. */
    fun open() {
        locateJob?.cancel()
        _state.update { it.copy(open = true, status = NearMeStatus.Locating, radiusOverrideKm = null, openedAtLaunch = false) }
    }

    /**
     * At launch, with the permission already granted: show where the user is
     * (5 km, list minimised), from a last known fix if there's a recent one,
     * then refine it with a fresh fix in the background. Nothing is shown while
     * locating, and a failure is silent: the app simply starts as usual.
     */
    fun locateOnLaunch() {
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            val result = locator.locate(recentMs = UserLocator.LAUNCH_RECENT_MS) as? LocateResult.Found ?: return@launch
            PerfLog.log("launch fix: ${if (result.lastKnown) "last known" else "fresh"}, ± ${result.position.accuracyMeters.toInt()} m")
            _state.update {
                it.copy(
                    open = true,
                    status = NearMeStatus.Located,
                    position = result.position,
                    fixCount = it.fixCount + 1,
                    radiusOverrideKm = Nearby.LAUNCH_RADIUS_KM,
                    openedAtLaunch = true,
                )
            }
            if (!result.lastKnown) return@launch
            val fresh = locator.freshLocation() as? LocateResult.Found ?: return@launch
            _state.value.position?.let { old ->
                val metres = (Geo.distanceKm(old.lat, old.lon, fresh.position.lat, fresh.position.lon) * 1000).toInt()
                PerfLog.log("launch fresh fix: moved $metres m, ± ${fresh.position.accuracyMeters.toInt()} m")
            }
            _state.update {
                val old = it.position
                if (!it.open || old == null) return@update it
                // Far enough to matter: a new fix, which re-frames the map (unless the user moved it, see MapScreen).
                if (Nearby.movedEnough(old, fresh.position)) {
                    it.copy(position = fresh.position, fixCount = it.fixCount + 1)
                } else {
                    it.copy(position = fresh.position)
                }
            }
        }
    }

    fun close() {
        locateJob?.cancel()
        _state.update { it.copy(open = false, radiusOverrideKm = null, openedAtLaunch = false) }
    }

    /** Permission granted: find the position. */
    fun locate() {
        locateJob?.cancel()
        _state.update { it.copy(open = true, status = NearMeStatus.Locating, radiusOverrideKm = null, openedAtLaunch = false) }
        locateJob = viewModelScope.launch {
            val result = locator.locate()
            _state.update {
                when (result) {
                    is LocateResult.Found -> it.copy(
                        status = NearMeStatus.Located,
                        position = result.position,
                        fixCount = it.fixCount + 1,
                    )
                    LocateResult.LocationOff -> it.copy(status = NearMeStatus.LocationOff)
                    LocateResult.Unavailable -> it.copy(status = NearMeStatus.Unavailable)
                    LocateResult.NoPermission -> it.copy(status = NearMeStatus.Denied)
                }
            }
        }
    }

    /** Permission refused; [permanently] when the system won't show the dialog again. */
    fun denied(permanently: Boolean) = _state.update {
        it.copy(open = true, status = if (permanently) NearMeStatus.DeniedPermanently else NearMeStatus.Denied)
    }
}
