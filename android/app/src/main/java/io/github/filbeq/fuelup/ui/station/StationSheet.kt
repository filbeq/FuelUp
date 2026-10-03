package io.github.filbeq.fuelup.ui.station

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.CompareGroup
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.PriceClass
import io.github.filbeq.fuelup.data.RankedPrice
import io.github.filbeq.fuelup.data.RefreshPolicy
import io.github.filbeq.fuelup.data.ServiceMode
import io.github.filbeq.fuelup.map.StationIcons
import io.github.filbeq.fuelup.ui.map.fuelLabel
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Header of the station sheet: what's visible when the sheet is collapsed
 * (name, brand, and the price of the chosen fuel with how it compares).
 * [StationSheetBody] follows it when expanded. [ranked] is the station's
 * price for [choice] (null if it doesn't sell it). Other stations at the same
 * spot are listed underneath; tapping one calls [onOpenStation]. [closeButton]
 * is the side panel's, at the end of the name's line.
 */
@Composable
fun StationSheetHeader(
    details: StationDetails,
    choice: FuelChoice,
    ranked: RankedPrice?,
    onOpenStation: (Int) -> Unit,
    modifier: Modifier = Modifier,
    closeButton: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                details.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            closeButton?.invoke()
        }
        val subtitle = buildList {
            if (details.name.isNotEmpty()) add(details.brand)
            if (details.motorway) add(stringResource(R.string.station_motorway))
        }.joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        ChosenPrice(choice, ranked)
        details.sameLocation.forEach { other ->
            TextButton(onClick = { onOpenStation(other.id) }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    // Most such pairs share the name (e.g. a motorway area with a separate diesel listing).
                    if (other.displayName == details.displayName) {
                        stringResource(R.string.station_other_listing)
                    } else {
                        stringResource(R.string.station_also_here, other.displayName)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The chosen fuel's price, its class icon and the comparison in words. */
@Composable
private fun ChosenPrice(choice: FuelChoice, ranked: RankedPrice?) {
    val choiceLabel = choiceLabel(choice)
    if (ranked == null) {
        Text(
            stringResource(R.string.station_does_not_sell, choiceLabel),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val perKg = choice.fuel == FuelKind.CNG || choice.fuel == FuelKind.LNG
    val price = PriceInfo(ranked.priceMilli, Instant.ofEpochSecond(ranked.updatedEpochSeconds))
    val now = remember(ranked) { Instant.now() }
    Text(choiceLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val density = LocalDensity.current.density
        val icon = remember(ranked.priceClass, density) {
            StationIcons.draw(ranked.priceClass, density).asImageBitmap()
        }
        Image(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(formatPrice(price, perKg), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
    Text(
        comparisonText(ranked),
        style = MaterialTheme.typography.bodyMedium,
        // Neutral for "to verify" too: an unusual price is not a warning about the station.
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        reportedText(price, now),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** "Gasolio · Self", or just "GPL" where self/served doesn't apply. */
@Composable
internal fun choiceLabel(choice: FuelChoice): String = buildString {
    append(stringResource(fuelLabel(choice.fuel)))
    if (choice.modeApplies) {
        append(" · ")
        append(stringResource(if (choice.mode == ServiceMode.SELF) R.string.mode_self else R.string.mode_served))
    }
}

/** "4,5 cent sotto il prezzo tipico della zona", "In linea con…", "Prezzo da verificare…". */
@Composable
private fun comparisonText(ranked: RankedPrice): String {
    val where = stringResource(
        when (ranked.group) {
            CompareGroup.ROAD -> R.string.compare_group_road
            CompareGroup.MOTORWAY -> R.string.compare_group_motorway
            CompareGroup.DUTY_FREE -> R.string.compare_group_duty_free
        },
    )
    val cents = ranked.diffFromMedianMilli?.let { formatCents(it, LocalConfiguration.current.locales[0]) }
    return when (ranked.priceClass) {
        PriceClass.CHEAP -> stringResource(R.string.compare_below, cents!!, where)
        PriceClass.EXPENSIVE -> stringResource(R.string.compare_above, cents!!, where)
        PriceClass.AVERAGE -> stringResource(R.string.compare_in_line, where)
        PriceClass.NOT_COMPARED -> stringResource(R.string.compare_not_compared)
        PriceClass.TO_VERIFY -> stringResource(R.string.compare_to_verify, cents!!, where)
    }
}

/**
 * The rest of the sheet, visible when expanded: address, Navigate, all prices.
 * [scrollState] is kept by the caller, so the position survives a rotation
 * between the bottom sheet and the side panel.
 */
@Composable
fun StationSheetBody(details: StationDetails, choice: FuelChoice, scrollState: ScrollState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = remember(details) { Instant.now() }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider()
        Text(
            "${details.address}\n${details.municipality} (${details.province})",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { navigateTo(context, details) }) {
            Icon(painterResource(R.drawable.ic_directions), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_navigate))
        }
        PriceTable(details, choice, now)
    }
}

/** One row per fuel; columns Self and Served. */
@Composable
private fun PriceTable(details: StationDetails, choice: FuelChoice, now: Instant) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text(
                stringResource(R.string.station_fuel_column),
                Modifier.weight(1.2f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.mode_self),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.mode_served),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        details.rows.forEach { row ->
            val (title, subtitle) = fuelTitle(row, kindLabel(row.kind))
            // The chosen fuel's row is highlighted.
            val rowModifier = if (row.isChosen(choice)) {
                Modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
            } else {
                Modifier
            }
            Row(rowModifier.padding(4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.2f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (row.isChosen(choice)) FontWeight.SemiBold else null,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PriceCell(row.self, row.perKg, now, Modifier.weight(1f))
                PriceCell(row.served, row.perKg, now, Modifier.weight(1f))
            }
        }
    }
}

/**
 * A price and, underneath in neutral grey, how long ago the station reported it.
 * Operators report weekly and on every change, so an old report usually just
 * means the price hasn't changed: no warning styling on purpose.
 */
@Composable
private fun PriceCell(price: PriceInfo?, perKg: Boolean, now: Instant, modifier: Modifier = Modifier) {
    if (price == null) {
        val notAvailable = stringResource(R.string.price_not_available)
        Text(
            "—",
            modifier.clearAndSetSemantics { contentDescription = notAvailable },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val priceText = formatPrice(price, perKg)
    val locale = LocalConfiguration.current.locales[0]
    val reportedAt = price.updated.atZone(RefreshPolicy.ITALY)
    val description = stringResource(
        R.string.reported_at_description,
        priceText,
        DateTimeFormatter.ofPattern(stringResource(R.string.prices_date_pattern), locale).format(reportedAt),
        DateTimeFormatter.ofPattern(stringResource(R.string.prices_time_pattern), locale).format(reportedAt),
    )
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Text(priceText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            reportedText(price, now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun reportedText(price: PriceInfo, now: Instant): String {
    val days = daysSinceReported(price.updated, now).toInt()
    return when (days) {
        0 -> stringResource(R.string.reported_today)
        1 -> stringResource(R.string.reported_yesterday)
        else -> pluralStringResource(R.plurals.reported_days_ago, days, days)
    }
}

/** "1,849 €/l" (Italian) or "€1.849/l" (English); per kg for methane and LNG. */
@Composable
internal fun formatPrice(price: PriceInfo, perKg: Boolean): String {
    val number = formatPriceNumber(price.priceMilli, LocalConfiguration.current.locales[0])
    return stringResource(if (perKg) R.string.price_per_kg else R.string.price_per_litre, number)
}

@Composable
private fun kindLabel(kind: FuelKind): String = stringResource(fuelLabel(kind))

/**
 * Opens the station in whatever navigation app the user has (a standard `geo:`
 * link: Google Maps, OsmAnd, Organic Maps, Waze, …). No app: a short message.
 */
private fun navigateTo(context: Context, details: StationDetails) {
    // Kotlin's Double.toString always uses a dot, whatever the phone's language.
    val coordinates = "${details.lat},${details.lon}"
    val uri = "geo:$coordinates?q=$coordinates(${Uri.encode(details.displayName)})".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_navigation_app, Toast.LENGTH_SHORT).show()
    }
}
