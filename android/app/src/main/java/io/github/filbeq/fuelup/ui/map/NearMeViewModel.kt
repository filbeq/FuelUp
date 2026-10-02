package io.github.filbeq.fuelup.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.filbeq.fuelup.data.LocateResult
import io.github.filbeq.fuelup.data.UserLocator
import io.github.filbeq.fuelup.data.UserPosition
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

/**
 * The "my location" feature: one position per tap (no tracking). The
 * permission dialog itself is shown by the screen, which reports the answer.
 */
class NearMeViewModel(application: Application) : AndroidViewModel(application) {
    private val locator = UserLocator(application)
    private val _state = MutableStateFlow(NearMeState())
    val state: StateFlow<NearMeState> = _state.asStateFlow()
    private var locateJob: Job? = null

    /** "My location" tapped: open the panel; the screen then asks for the permission if needed. */
    fun open() = _state.update { it.copy(open = true, status = NearMeStatus.Locating) }

    fun close() {
        locateJob?.cancel()
        _state.update { it.copy(open = false) }
    }

    /** Permission granted: find the position. */
    fun locate() {
        locateJob?.cancel()
        _state.update { it.copy(open = true, status = NearMeStatus.Locating) }
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
