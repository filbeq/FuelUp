package io.github.filbeq.fuelup.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The "near me" part of the sheet while there is no position to show:
 * looking for it, or why there is none (permission refused, location off,
 * not found) with the way out. [closeButton] is the side panel's, at the end
 * of the title's line.
 */
@Composable
fun NearMeStatusPanel(
    state: NearMeState,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
    closeButton: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.near_me_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            closeButton?.invoke()
        }
        when (state.status) {
            NearMeStatus.Locating -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.near_me_locating), style = MaterialTheme.typography.bodyMedium)
            }
            NearMeStatus.Located -> state.position?.let {
                Text(
                    stringResource(R.string.near_me_located, formatAccuracy(it.accuracyMeters, LocalConfiguration.current.locales[0])),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            NearMeStatus.Denied -> Explanation(R.string.near_me_denied) {
                Button(onClick = onTryAgain) { Text(stringResource(R.string.action_retry)) }
            }
            NearMeStatus.DeniedPermanently -> Explanation(R.string.near_me_denied_permanently) {
                Button(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.action_open_app_settings)) }
                OutlinedButton(onClick = onTryAgain) { Text(stringResource(R.string.action_retry)) }
            }
            NearMeStatus.LocationOff -> Explanation(R.string.near_me_location_off) {
                Button(onClick = { openLocationSettings(context) }) { Text(stringResource(R.string.action_location_settings)) }
                OutlinedButton(onClick = onTryAgain) { Text(stringResource(R.string.action_retry)) }
            }
            NearMeStatus.Unavailable -> Explanation(R.string.near_me_unavailable) {
                Button(onClick = onTryAgain) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun Explanation(text: Int, buttons: @Composable () -> Unit) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
}

/** "800 m" / "1,5 km" (Italian) or "1.5 km" (English): how far off the position may be. */
fun formatAccuracy(meters: Float, locale: Locale): String = if (meters < 1000) {
    "${(meters / 50).roundToInt().coerceAtLeast(1) * 50} m"
} else {
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(meters / 1000.0) + " km"
}

/** The app's page in the system settings, where the location permission can be turned back on. */
private fun openAppSettings(context: Context) = startSettings(
    context,
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
)

private fun openLocationSettings(context: Context) = startSettings(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

private fun startSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
