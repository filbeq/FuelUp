package io.github.filbeq.fuelup.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.PriceClass
import io.github.filbeq.fuelup.data.PriceRanking
import io.github.filbeq.fuelup.data.ServiceMode
import io.github.filbeq.fuelup.map.StationIcons
import io.github.filbeq.fuelup.ui.station.choiceLabel

/**
 * Floating button over the map, like Google Maps' map-type button, that always
 * shows the current choice ("Gasolio · Self"): it says which stations are on
 * the map. Tapping it opens [FuelChoiceSheet].
 */
@Composable
fun FuelChoiceButton(choice: FuelChoice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.fuel_selector_action), onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(R.drawable.ic_local_gas_station), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(choiceLabel(choice), style = MaterialTheme.typography.titleSmall)
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The fuel choice in one place: fuel, self/served (petrol and diesel only) and
 * what the marker colours mean. A choice applies at once; the map updates
 * behind the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelChoiceSheet(choice: FuelChoice, onChoiceChange: (FuelChoice) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PanelTitle(R.string.fuel_panel_title)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelChoice.SELECTABLE.forEach { kind ->
                    FilterChip(
                        selected = choice.fuel == kind,
                        onClick = { onChoiceChange(choice.copy(fuel = kind)) },
                        label = { Text(stringResource(fuelLabel(kind))) },
                    )
                }
            }
            // Same height either way, so the chips above don't jump when the fuel changes.
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                if (choice.modeApplies) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = ServiceMode.entries
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = choice.mode == mode,
                                onClick = { onChoiceChange(choice.copy(mode = mode)) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            ) {
                                Text(stringResource(if (mode == ServiceMode.SELF) R.string.mode_self else R.string.mode_served))
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.mode_not_applicable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PanelTitle(R.string.legend_title, Modifier.padding(top = 8.dp))
            PriceLegend(bandCents = PriceRanking.THRESHOLDS.getValue(choice.fuel).bandMilli / 10)
        }
    }
}

@Composable
private fun PanelTitle(@StringRes title: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** Localized fuel name for the selector (the standard product of each type). */
fun fuelLabel(kind: FuelKind): Int = when (kind) {
    FuelKind.PETROL -> R.string.fuel_petrol
    FuelKind.DIESEL -> R.string.fuel_diesel
    FuelKind.LPG -> R.string.fuel_lpg
    FuelKind.CNG -> R.string.fuel_cng
    FuelKind.LNG -> R.string.fuel_lng
    FuelKind.OTHER -> R.string.fuel_other
}

/** What the marker shapes and colours mean (the same icons as on the map), in words. */
@Composable
private fun PriceLegend(bandCents: Int) {
    val density = LocalDensity.current.density
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(PriceClass.CHEAP, R.string.legend_cheap, pluralStringResource(R.plurals.legend_cheap_detail, bandCents, bandCents)),
            Triple(PriceClass.AVERAGE, R.string.legend_average, pluralStringResource(R.plurals.legend_average_detail, bandCents, bandCents)),
            Triple(PriceClass.EXPENSIVE, R.string.legend_expensive, pluralStringResource(R.plurals.legend_expensive_detail, bandCents, bandCents)),
            Triple(PriceClass.TO_VERIFY, R.string.legend_to_verify, stringResource(R.string.legend_to_verify_detail)),
            Triple(PriceClass.NOT_COMPARED, R.string.legend_not_compared, stringResource(R.string.legend_not_compared_detail)),
        ).forEach { (priceClass, label, detail) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val icon = remember(priceClass, density) { StationIcons.draw(priceClass, density).asImageBitmap() }
                Image(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Column {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
