package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R

/**
 * Small card over the map: the data date ("Prezzi del 30/09, ore 8:00") and,
 * when relevant, loading / offline / error / "update the app" messages.
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

    Surface(
        modifier = modifier.padding(8.dp).widthIn(max = 480.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (snapshot != null) {
                    val locale = LocalConfiguration.current.locales[0]
                    val (date, time) = formatPricesAt(
                        snapshot.meta.pricesAt,
                        stringResource(R.string.prices_date_pattern),
                        stringResource(R.string.prices_time_pattern),
                        locale,
                    )
                    Text(stringResource(R.string.status_prices_at, date, time), style = MaterialTheme.typography.titleSmall)
                }
                if (state.status == DataStatus.Loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                if (snapshot == null && message != null && state.status == DataStatus.Loading) {
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                }
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
