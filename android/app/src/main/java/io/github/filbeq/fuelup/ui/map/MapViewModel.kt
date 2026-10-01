package io.github.filbeq.fuelup.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.filbeq.fuelup.BuildConfig
import io.github.filbeq.fuelup.PerfLog
import io.github.filbeq.fuelup.data.HttpFetcher
import io.github.filbeq.fuelup.data.RefreshResult
import io.github.filbeq.fuelup.data.Snapshot
import io.github.filbeq.fuelup.data.StationRepository
import io.github.filbeq.fuelup.map.StationLayers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the data part of the map screen shows. */
data class MapUiState(
    /** The data on screen; null until the cache is read or the first download ends. */
    val snapshot: Snapshot? = null,
    /** [snapshot]'s stations as GeoJSON for the map, built off the main thread. */
    val stationsGeoJson: String? = null,
    val status: DataStatus = DataStatus.Loading,
)

enum class DataStatus {
    /** Reading the cache or talking to the server. */
    Loading,
    /** Showing the newest data available. */
    Ready,
    /** No connection: showing the cache, if any. */
    Offline,
    /** Server error or invalid download: showing the cache, if any. */
    Failed,
    /** Published data needs a newer app: showing the cache, if any. */
    UpdateRequired,
}

/**
 * Loads the station data when the app starts: the cache first (shown at once),
 * then a refresh from the server if newer data can exist. All file and network
 * work runs on background threads; the ViewModel survives screen rotation.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    // Created on first use, which is always on a background thread:
    // Android touches the disk when it first resolves filesDir.
    private val repository by lazy {
        StationRepository(
            dir = File(application.filesDir, "data"),
            fetcher = HttpFetcher(userAgent = "FuelUp/${BuildConfig.VERSION_NAME} (Android)"),
            trace = { label, ms -> PerfLog.log("$label: $ms ms") },
        )
    }

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refreshJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { PerfLog.timeWithHeap("cache") { repository.loadCached() } }
            _state.value = MapUiState(cached, cached?.let { geoJson(it) }, DataStatus.Loading)
            refresh(force = false)
        }
    }

    /** "Retry" button: check the server now, ignoring the hourly limit. */
    fun retry() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { refresh(force = true) }
    }

    private suspend fun refresh(force: Boolean) {
        _state.update { it.copy(status = DataStatus.Loading) }
        val current = _state.value.snapshot
        val result = withContext(Dispatchers.IO) {
            PerfLog.timeWithHeap("refresh") { repository.refresh(current, force) }
        }
        val updatedGeoJson = (result as? RefreshResult.Updated)?.let { geoJson(it.snapshot) }
        _state.update {
            when (result) {
                RefreshResult.UpToDate -> it.copy(status = DataStatus.Ready)
                is RefreshResult.Updated -> MapUiState(result.snapshot, updatedGeoJson, DataStatus.Ready)
                RefreshResult.Offline -> it.copy(status = DataStatus.Offline)
                RefreshResult.Failed -> it.copy(status = DataStatus.Failed)
                RefreshResult.UpdateRequired -> it.copy(status = DataStatus.UpdateRequired)
            }
        }
    }

    private suspend fun geoJson(snapshot: Snapshot): String = withContext(Dispatchers.Default) {
        PerfLog.time("build GeoJSON") { StationLayers.buildGeoJson(snapshot.stations.stations) }
    }
}
