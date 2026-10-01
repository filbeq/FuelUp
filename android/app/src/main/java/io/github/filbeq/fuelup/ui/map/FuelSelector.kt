package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.PriceClass
import io.github.filbeq.fuelup.data.ServiceMode
import io.github.filbeq.fuelup.map.StationIcons

/**
 * Fuel chips (Benzina · Gasolio · GPL · Metano · GNL) and, for petrol and
 * diesel only, a Self / Servito toggle. The map shows only stations selling
 * the chosen fuel.
 */
@Composable
fun FuelSelector(choice: FuelChoice, onChoiceChange: (FuelChoice) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FuelChoice.SELECTABLE.forEach { kind ->
                    FilterChip(
                        selected = choice.fuel == kind,
                        onClick = { onChoiceChange(choice.copy(fuel = kind)) },
                        label = { Text(stringResource(fuelLabel(kind))) },
                    )
                }
            }
            PriceLegend(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            if (choice.modeApplies) {
                SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 12.dp)) {
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
            }
        }
    }
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

/** What the marker shapes and colours mean (the same icons as on the map). */
@Composable
private fun PriceLegend(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            PriceClass.CHEAP to R.string.legend_cheap,
            PriceClass.AVERAGE to R.string.legend_average,
            PriceClass.EXPENSIVE to R.string.legend_expensive,
            PriceClass.TO_VERIFY to R.string.legend_to_verify,
        ).forEach { (priceClass, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val icon = remember(priceClass, density) { StationIcons.draw(priceClass, density).asImageBitmap() }
                Image(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
