package io.github.filbeq.fuelup.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.DataDateLabel
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The data date in words, in the app language ([DataDateLabel]): "Prezzi di
 * ieri" / "Prezzi di mar 06/10" on its own (the date pill), or "prezzi di
 * ieri" inside a sentence ([inSentence], Settings' "Update data now"). No time:
 * it is always 08:00, which About explains.
 */
@Composable
fun dataDateText(pricesAt: String, now: Instant, inSentence: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val label = DataDateLabel.of(pricesAtDate(pricesAt), now)
    return when (label.day) {
        DataDateLabel.Day.TODAY -> stringResource(if (inSentence) R.string.prices_today_in_sentence else R.string.status_prices_today)
        DataDateLabel.Day.YESTERDAY -> stringResource(if (inSentence) R.string.prices_yesterday_in_sentence else R.string.status_prices_yesterday)
        DataDateLabel.Day.OTHER -> stringResource(
            if (inSentence) R.string.prices_of_in_sentence else R.string.status_prices_at,
            formatPricesDate(pricesAt, stringResource(R.string.prices_weekday_date_pattern), locale),
        )
    }
}

/**
 * The current time, updated when the data date's wording can change (midnight
 * and the 14:00 deadline in Italy) and whenever the app comes back to the
 * front (a timer doesn't run while the phone sleeps).
 */
@Composable
fun rememberDataDateClock(): Instant {
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
