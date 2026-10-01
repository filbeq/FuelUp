package io.github.filbeq.fuelup.ui.map

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.filbeq.fuelup.BuildConfig
import io.github.filbeq.fuelup.data.HttpFetcher
import io.github.filbeq.fuelup.data.RefreshResult
import io.github.filbeq.fuelup.data.Snapshot
import io.github.filbeq.fuelup.data.StationRepository
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
            trace = { label, ms -> perfLog("$label: $ms ms") },
        )
    }

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refreshJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { withHeapLog("cache") { repository.loadCached() } }
            _state.value = MapUiState(cached, DataStatus.Loading)
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
            withHeapLog("refresh") { repository.refresh(current, force) }
        }
        _state.update {
            when (result) {
                RefreshResult.UpToDate -> it.copy(status = DataStatus.Ready)
                is RefreshResult.Updated -> MapUiState(result.snapshot, DataStatus.Ready)
                RefreshResult.Offline -> it.copy(status = DataStatus.Offline)
                RefreshResult.Failed -> it.copy(status = DataStatus.Failed)
                RefreshResult.UpdateRequired -> it.copy(status = DataStatus.UpdateRequired)
            }
        }
    }

    private inline fun <T> withHeapLog(label: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        val runtime = Runtime.getRuntime()
        // Collect garbage first so the numbers show memory actually kept (debug only).
        runtime.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val start = System.nanoTime()
        val result = block()
        val elapsed = (System.nanoTime() - start) / 1_000_000
        runtime.gc()
        val after = runtime.totalMemory() - runtime.freeMemory()
        perfLog(
            "$label: $elapsed ms total, Java heap kept ${before / MB} → ${after / MB} MB " +
                "(max ${runtime.maxMemory() / MB} MB)",
        )
        return result
    }

    private companion object {
        const val MB = 1024 * 1024

        // Plain Log on purpose: "use Timber" comes from MapLibre's lint rules, and
        // these lines only exist in debug builds.
        @SuppressLint("LogNotTimber")
        fun perfLog(message: String) {
            if (BuildConfig.DEBUG) Log.d("FuelUpPerf", message)
        }
    }
}
