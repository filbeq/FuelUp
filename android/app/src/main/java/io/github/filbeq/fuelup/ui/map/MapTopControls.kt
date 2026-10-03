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
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R

/**
 * The controls over the top of the full-screen map: the search bar with the
 * settings button beside it, the data date pill and the fuel button.
 *
 * Narrow windows (portrait phone): the search bar across the top with the gear
 * on its right; under it the date pill on the left and the fuel button on the
 * right (the fuel button drops to a third row if both don't fit).
 * Wide windows ([wide]: landscape phone, tablet): one row. The search bar is on
 * the left, as wide as the side panel and aligned with it (the panel opens
 * under it), then the gear; the pill is centred in the rest of the row and the
 * fuel button is at the right end. If they don't fit, the pill and the fuel
 * button move to a second row, on the right.
 *
 * [onSearchRowHeight] reports the height of the search bar's row (the side panel
 * starts below it). The caller pads this for the system bars.
 */
@Composable
fun MapTopControls(
    state: MapUiState,
    onRetry: () -> Unit,
    onOpenFuel: () -> Unit,
    onOpenSettings: () -> Unit,
    searchBar: @Composable () -> Unit,
    wide: Boolean,
    onSearchRowHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(
            // 4 dp above and below: as tall as the gear with its padding, centres aligned.
            { Box(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) { searchBar() } },
            { DataStatusCard(state = state, onRetry = onRetry) },
            { FuelChoiceButton(choice = state.choice, onClick = onOpenFuel) },
            { SettingsButton(onClick = onOpenSettings) },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { (barPart, pillPart, fuelPart, gearPart), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = constraints.maxWidth
        val gear = gearPart.first().measure(loose)
        val fuel = fuelPart.first().measure(loose)
        // The side panel's column (margin + width), or all the width the gear leaves.
        val barWidth = (if (wide) (SIDE_PANEL_MARGIN + SIDE_PANEL_WIDTH).roundToPx() else width - gear.width).coerceIn(0, width)
        val bar = barPart.first().measure(loose.copy(minWidth = barWidth, maxWidth = barWidth))
        val rowHeight = maxOf(bar.height, gear.height)
        onSearchRowHeight(rowHeight)
        // The fuel button has no padding above and below: 8 dp lines it up with the pill.
        val fuelDrop = 8.dp.roundToPx()
        val gearX = if (wide) barWidth else width - gear.width
        // Where the pill and the fuel button go, on the search row (wide only) or below it.
        val restStart = if (wide) gearX + gear.width else 0
        val pillWanted = pillPart.first().maxIntrinsicWidth(Constraints.Infinity)
        val sameRow = wide && pillWanted + fuel.width <= width - restStart
        val left = if (sameRow) restStart else 0
        val pillSpace = (width - left - fuel.width).coerceAtLeast(0)
        val pill = pillPart.first().measure(loose.copy(maxWidth = if (wide) pillSpace else width))
        val pillFitsBeside = pill.width + fuel.width <= width - left
        val pillY = if (sameRow) 0 else rowHeight
        val fuelY = if (pillFitsBeside) pillY + fuelDrop else pillY + pill.height
        val height = maxOf(rowHeight, pillY + pill.height, fuelY + fuel.height)
        layout(width, height) {
            bar.place(0, (rowHeight - bar.height) / 2)
            gear.place(gearX, (rowHeight - gear.height) / 2)
            val pillX = when {
                // Centred in the rest of the row, shifted left only to keep clear of the fuel button.
                sameRow -> ((restStart + width - pill.width) / 2).coerceAtMost(pillSpace + left - pill.width).coerceAtLeast(left)
                // On the right, next to the fuel button (wide), or on the left under the bar (narrow).
                wide && pillFitsBeside -> width - fuel.width - pill.width
                else -> 0
            }
            pill.place(pillX, pillY)
            fuel.place(width - fuel.width, fuelY)
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
