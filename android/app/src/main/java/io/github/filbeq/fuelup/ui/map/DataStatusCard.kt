package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.DataDateLabel
import io.github.filbeq.fuelup.ui.theme.floatingSurfaceColor

/**
 * Small card over the map: the data date ("Prezzi di ieri, ore 8:00", or "Prezzi
 * di martedì 06/10, ore 8:00" when older, see [DataDateLabel]) with "update
 * delayed" under it when the data is older than it should be by now, and, when
 * relevant, loading / offline / error / "update the app" messages.
 */
@Composable
fun DataStatusCard(state: MapUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val snapshot = state.snapshot
    val message: Int? = when (state.status) {
        DataStatus.Loading -> if (snapshot == null) R.string.status_loading else null
        DataStatus.Ready -> null
        DataStatus.Offline -> if (snapshot == null) R.string.error_no_connection else R.string.status_offline
        DataStatus.Failed -> if (snapshot == null) R.string.error_download_failed else R.string.status_update_failed
        DataStatus.UpdateRequired -> R.string.error_update_required
    }
    val isError = snapshot == null && state.status in setOf(DataStatus.Offline, DataStatus.Failed) ||
        state.status == DataStatus.UpdateRequired
    val canRetry = snapshot == null && state.status in setOf(DataStatus.Offline, DataStatus.Failed)
    val now = currentTimeForDataDate()
    val label = snapshot?.let { DataDateLabel.of(pricesAtDate(it.meta.pricesAt), now) }

    Surface(
        // At least as tall as the buttons beside it.
        modifier = modifier.padding(8.dp).widthIn(max = 480.dp).heightIn(min = 48.dp),
        color = floatingSurfaceColor(),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot != null) {
                    Text(pricesAtLabel(snapshot.meta.pricesAt, label), style = MaterialTheme.typography.titleSmall)
                }
                if (state.status == DataStatus.Loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                if (snapshot == null && message != null && state.status == DataStatus.Loading) {
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Older than what should exist by now: said plainly, not as an error.
            if (message == null && label?.late == true) {
                Text(
                    stringResource(R.string.status_update_late),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (message != null && state.status != DataStatus.Loading) {
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canRetry) {
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

/** "Prezzi di ieri, ore 8:00" / "Prezzi di martedì 06/10, ore 8:00" in the app language. */
@Composable
private fun pricesAtLabel(pricesAt: String, label: DataDateLabel.Label?): String {
    val locale = LocalConfiguration.current.locales[0]
    val (date, time) = formatPricesAt(
        pricesAt,
        stringResource(R.string.prices_weekday_date_pattern),
        stringResource(R.string.prices_time_pattern),
        locale,
    )
    return when (label?.day) {
        DataDateLabel.Day.TODAY -> stringResource(R.string.status_prices_today, time)
        DataDateLabel.Day.YESTERDAY -> stringResource(R.string.status_prices_yesterday, time)
        else -> stringResource(R.string.status_prices_at, date, time)
    }
}

/**
 * The current time, updated when the label can change (midnight and the 14:00
 * deadline in Italy) and whenever the app comes back to the front (a timer
 * doesn't run while the phone sleeps).
 */
@Composable
private fun currentTimeForDataDate(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LifecycleResumeEffect(Unit) {
        val job = lifecycleScope.launch {
            while (true) {
                now = Instant.now()
                val wait = Duration.between(now, DataDateLabel.nextChange(now)).toMillis()
                // Capped, in case the phone's clock or time zone changes meanwhile.
                delay(wait.coerceIn(1_000L, MAX_WAIT_MS))
            }
        }
        onPauseOrDispose { job.cancel() }
    }
    return now
}

private const val MAX_WAIT_MS = 15 * 60 * 1_000L
