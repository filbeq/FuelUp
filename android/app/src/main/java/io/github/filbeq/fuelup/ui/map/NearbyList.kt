package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.Nearby
import io.github.filbeq.fuelup.data.NearbySort
import io.github.filbeq.fuelup.data.NearbyStation
import io.github.filbeq.fuelup.data.PriceClass
import io.github.filbeq.fuelup.map.StationIcons
import io.github.filbeq.fuelup.ui.station.PriceInfo
import io.github.filbeq.fuelup.ui.station.choiceLabel
import io.github.filbeq.fuelup.ui.station.formatPrice
import io.github.filbeq.fuelup.ui.station.reportedText
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** Rows shown in the collapsed sheet, under the controls. */
private const val PEEK_ROWS = 2

/**
 * "Near you": the stations within the chosen radius that sell [choice],
 * cheapest (or nearest) first. The controls and the first rows form the
 * collapsed sheet ([headerModifier] measures them); the rest scrolls below.
 */
@Composable
fun NearbyList(
    state: NearMeState,
    choice: FuelChoice,
    stations: List<NearbyStation>,
    brands: List<String>,
    onRadiusChange: (Int) -> Unit,
    onSortChange: (NearbySort) -> Unit,
    onStationClick: (NearbyStation) -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
) {
    val now = remember(stations) { Instant.now() }
    val locale = LocalConfiguration.current.locales[0]
    val settings = state.settings
    val approximate = (state.position?.accuracyMeters ?: Float.MAX_VALUE) > PRECISE_METERS
    Column(modifier.fillMaxWidth()) {
        Column(headerModifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // Title, then the order switch on the right; radius chips below.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.near_me_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    state.position?.let {
                        Text(
                            stringResource(R.string.near_me_subtitle, choiceLabel(choice), formatAccuracy(it.accuracyMeters, locale)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.padding(start = 8.dp)) {
                    val sorts = NearbySort.entries
                    sorts.forEachIndexed { index, sort ->
                        SegmentedButton(
                            selected = settings.sort == sort,
                            onClick = { onSortChange(sort) },
                            shape = SegmentedButtonDefaults.itemShape(index, sorts.size),
                            // No check mark: keeps both labels on screen.
                            icon = {},
                        ) {
                            Text(stringResource(if (sort == NearbySort.PRICE) R.string.sort_price else R.string.sort_distance))
                        }
                    }
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Nearby.RADII_KM.forEach { km ->
                    FilterChip(
                        selected = settings.radiusKm == km,
                        onClick = { onRadiusChange(km) },
                        label = { Text(stringResource(R.string.radius_km, km)) },
                    )
                }
            }
            if (stations.isEmpty()) {
                Text(
                    stringResource(R.string.nearby_empty, settings.radiusKm, choiceLabel(choice)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Nearby.RADII_KM.firstOrNull { it > settings.radiusKm }?.let { wider ->
                    OutlinedButton(onClick = { onRadiusChange(wider) }, modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(stringResource(R.string.action_widen_radius, wider))
                    }
                }
            }
            stations.take(PEEK_ROWS).forEach { NearbyRow(it, choice, brands, approximate, now, onStationClick) }
        }
        if (stations.size > PEEK_ROWS) {
            LazyColumn(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                items(stations.drop(PEEK_ROWS), key = { it.station.id }) { NearbyRow(it, choice, brands, approximate, now, onStationClick) }
            }
        }
    }
}

@Composable
private fun NearbyRow(
    item: NearbyStation,
    choice: FuelChoice,
    brands: List<String>,
    approximate: Boolean,
    now: Instant,
    onClick: (NearbyStation) -> Unit,
) {
    val station = item.station
    val brand = brands.getOrElse(station.brand) { "" }
    val density = LocalDensity.current.density
    val icon = remember(item.price.priceClass, density) { StationIcons.draw(item.price.priceClass, density).asImageBitmap() }
    val price = PriceInfo(item.price.priceMilli, Instant.ofEpochSecond(item.price.updatedEpochSeconds))
    HorizontalDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick(item) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.name.ifEmpty { brand },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(
                        brand.takeIf { station.name.isNotEmpty() && it.isNotEmpty() },
                        formatDistance(item.distanceKm, approximate, LocalConfiguration.current.locales[0]),
                    )
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Only reachable from the motorway: worth knowing before driving there.
                if (station.motorway == 1) MotorwayBadge()
            }
            Text(
                "${classText(item.price.priceClass)} · ${reportedText(price, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            formatPrice(price, perKg = choice.fuel == FuelKind.CNG || choice.fuel == FuelKind.LNG),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MotorwayBadge() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            stringResource(R.string.station_motorway),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/**
 * Distance as the crow flies. With an approximate position (off by up to ~2 km):
 * "≈ 3 km" or "< 1 km", never more digits than it deserves. With a precise one:
 * "2,4 km" / "850 m".
 */
fun formatDistance(km: Double, approximate: Boolean, locale: Locale): String = when {
    approximate && km < 1 -> "< 1 km"
    approximate -> "≈ ${km.roundToInt()} km"
    km < 1 -> "${(km * 1000 / 50).roundToInt() * 50} m"
    else -> NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(km) + " km"
}

/** Above this reported accuracy, distances are shown as approximate. */
private const val PRECISE_METERS = 500f

@Composable
private fun classText(priceClass: PriceClass): String = stringResource(
    when (priceClass) {
        PriceClass.CHEAP -> R.string.legend_cheap
        PriceClass.AVERAGE -> R.string.legend_average
        PriceClass.EXPENSIVE -> R.string.legend_expensive
        PriceClass.TO_VERIFY -> R.string.legend_to_verify
        PriceClass.NOT_COMPARED -> R.string.class_not_compared
    },
)
