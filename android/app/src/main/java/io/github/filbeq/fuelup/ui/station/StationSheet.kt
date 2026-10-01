package io.github.filbeq.fuelup.ui.station

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.RefreshPolicy
import io.github.filbeq.fuelup.ui.map.fuelLabel
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Header of the station sheet: what's visible when the sheet is collapsed
 * (name, brand, main prices). [StationSheetBody] follows it when expanded.
 */
@Composable
fun StationSheetHeader(details: StationDetails, modifier: Modifier = Modifier) {
    val now = remember(details) { Instant.now() }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
        Text(
            details.displayName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
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
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            details.mainRows.forEach { row -> MainPrice(row, now) }
        }
    }
}

/** The rest of the sheet, visible when expanded: address, Navigate, all prices. */
@Composable
fun StationSheetBody(details: StationDetails, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = remember(details) { Instant.now() }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
        PriceTable(details, now)
    }
}

@Composable
private fun MainPrice(row: FuelRow, now: Instant) {
    val price = row.preferred ?: return
    val (title, _) = fuelTitle(row, kindLabel(row.kind))
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatPrice(price, row.perKg),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(if (price == row.self) R.string.mode_self else R.string.mode_served),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One row per fuel; columns Self and Served. */
@Composable
private fun PriceTable(details: StationDetails, now: Instant) {
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
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.2f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
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
private fun reportedText(price: PriceInfo, now: Instant): String {
    val days = daysSinceReported(price.updated, now).toInt()
    return when (days) {
        0 -> stringResource(R.string.reported_today)
        1 -> stringResource(R.string.reported_yesterday)
        else -> pluralStringResource(R.plurals.reported_days_ago, days, days)
    }
}

/** "1,849 €/l" (Italian) or "€1.849/l" (English); per kg for methane and LNG. */
@Composable
private fun formatPrice(price: PriceInfo, perKg: Boolean): String {
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
