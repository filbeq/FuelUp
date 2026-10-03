package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R

/**
 * The controls over the top of the full-screen map: the data date pill, the
 * fuel button and the settings button, all 48 dp tall.
 *
 * All on one line when they fit in [rowWidth] (landscape phone, tablet), which
 * saves a row of height: the pill centred, or as close to it as the buttons allow.
 * Otherwise (portrait phone, split screen) the pill is centred on top with the
 * gear on its right, and the fuel button under the gear, like Google Maps'
 * layer button. [rowWidth] is the width the controls have beside the open side
 * panel, so they don't rearrange while it slides in; null = the width given.
 * The caller pads this for the system bars and the side panel.
 */
@Composable
fun MapTopControls(
    state: MapUiState,
    onRetry: () -> Unit,
    onOpenFuel: () -> Unit,
    onOpenSettings: () -> Unit,
    rowWidth: Dp?,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(
            { DataStatusCard(state = state, onRetry = onRetry) },
            { FuelChoiceButton(choice = state.choice, onClick = onOpenFuel) },
            { SettingsButton(onClick = onOpenSettings) },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { (pillPart, fuelPart, gearPart), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = constraints.maxWidth
        val gear = gearPart.first().measure(loose)
        val fuel = fuelPart.first().measure(loose)
        val pillWanted = pillPart.first().maxIntrinsicWidth(Constraints.Infinity)
        val oneRow = pillWanted + fuel.width + gear.width <= (rowWidth?.roundToPx() ?: width)
        // The fuel button's own padding is only at its sides.
        val gap = 8.dp.roundToPx()
        if (oneRow) {
            val pillSpace = (width - fuel.width - gear.width).coerceAtLeast(0)
            val pill = pillPart.first().measure(loose.copy(maxWidth = pillSpace))
            val height = maxOf(pill.height, gear.height, gap + fuel.height)
            layout(width, height) {
                // Centred in the free map, shifted left only to keep clear of the buttons.
                pill.place(((width - pill.width) / 2).coerceAtMost(pillSpace - pill.width).coerceAtLeast(0), 0)
                fuel.place(pillSpace, gap)
                gear.place(width - gear.width, 0)
            }
        } else {
            // An empty slot as wide as the gear on the left keeps the pill centred.
            val pillSpace = (width - 2 * gear.width).coerceAtLeast(0)
            val pill = pillPart.first().measure(loose.copy(maxWidth = pillSpace))
            val rowHeight = maxOf(pill.height, gear.height)
            layout(width, rowHeight + fuel.height) {
                pill.place(gear.width + (pillSpace - pill.width) / 2, 0)
                gear.place(width - gear.width, 0)
                fuel.place(width - fuel.width, rowHeight)
            }
        }
    }
}

/** Round floating button to Settings, in the style of the fuel button. */
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(8.dp).size(SETTINGS_BUTTON_SIZE),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
        }
    }
}

private val SETTINGS_BUTTON_SIZE = 48.dp
