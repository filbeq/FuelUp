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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import io.github.filbeq.fuelup.data.Geo
import io.github.filbeq.fuelup.data.Nearby
import io.github.filbeq.fuelup.data.NearbySort
import io.github.filbeq.fuelup.data.NearbyStation
import io.github.filbeq.fuelup.data.PlaceNames
import io.github.filbeq.fuelup.data.PriceClass
import io.github.filbeq.fuelup.data.RankedPrice
import io.github.filbeq.fuelup.data.Station
import io.github.filbeq.fuelup.data.UserPosition
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
 * The area list's own texts (see [NearbyList]): [place] is "Pisa (PI)" after a
 * municipality search, else null ("In this area"). [tooLarge]: nothing listed,
 * "zoom in". [fromCentre]: the user's position is unknown, so distances (for
 * the Distance order) are from the middle of the area.
 */
class AreaListInfo(val place: String?, val tooLarge: Boolean, val fromCentre: Boolean)

/**
 * "Near you": the stations within the chosen radius that sell [choice],
 * cheapest (or nearest) first. Only the header (title, order, radius) is
 * fixed; every row is in one scrolling list ([listState]).
 *
 * With [area], the same list for the stations in an area of the map instead:
 * its own title and subtitle, no radius chips, and rows showing the
 * municipality (plus the distance when the position is known).
 *
 * Heights are reported through [onMeasured] for the sheet: the header alone
 * (the minimised sheet: title, order, radius) and header + first [PEEK_ROWS]
 * rows (the collapsed sheet). When [minimised], tapping the title calls [onExpand].
 * Without [showRows] only the header is drawn (the minimised side panel; the
 * bottom sheet hides the rows by its height instead). [closeButton] is the
 * side panel's, placed at the end of the title row.
 */
@Composable
fun NearbyList(
    state: NearMeState,
    choice: FuelChoice,
    stations: List<NearbyStation>,
    brands: List<String>,
    listState: LazyListState,
    minimised: Boolean,
    onExpand: () -> Unit,
    onRadiusChange: (Int) -> Unit,
    onSortChange: (NearbySort) -> Unit,
    onStationClick: (NearbyStation) -> Unit,
    onMeasured: (headerPx: Int, collapsedPx: Int) -> Unit,
    modifier: Modifier = Modifier,
    showRows: Boolean = true,
    closeButton: (@Composable () -> Unit)? = null,
    area: AreaListInfo? = null,
) {
    val now = remember(stations) { Instant.now() }
    val locale = LocalConfiguration.current.locales[0]
    val settings = state.settings
    val approximate = (state.position?.accuracyMeters ?: Float.MAX_VALUE) > PRECISE_METERS
    var headerPx by remember { mutableIntStateOf(0) }
    val rowPx = remember { mutableStateListOf(0, 0) }
    val currentOnMeasured by rememberUpdatedState(onMeasured)
    LaunchedEffect(headerPx, rowPx.toList(), stations.size) {
        val rows = (0 until minOf(PEEK_ROWS, stations.size)).sumOf { rowPx[it] }
        if (headerPx > 0) currentOnMeasured(headerPx, headerPx + rows)
    }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).onSizeChanged { headerPx = it.height }) {
            val sortSwitch = @Composable { modifier: Modifier ->
                SingleChoiceSegmentedButtonRow(modifier) {
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
            // Title, then the order switch on the right; radius chips below.
            // In the narrower side panel the close button takes the right, and
            // the order switch gets a line of its own; so does it under an area
            // list's longer title ("Around Reggio nell'Emilia (RE)"), which has no chips.
            val switchBeside = closeButton == null && area == null
            Row(verticalAlignment = Alignment.CenterVertically) {
                val showList = stringResource(R.string.action_show_list)
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (minimised) Modifier.clickable(onClickLabel = showList, onClick = onExpand) else Modifier),
                ) {
                    val title = when {
                        area == null -> stringResource(R.string.near_me_title)
                        area.place != null -> stringResource(R.string.area_title_around, area.place)
                        else -> stringResource(R.string.area_title)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    val subtitle = when {
                        area == null -> state.position?.let {
                            stringResource(R.string.near_me_subtitle, choiceLabel(choice), formatAccuracy(it.accuracyMeters, locale))
                        }
                        area.tooLarge -> choiceLabel(choice)
                        else -> pluralStringResource(R.plurals.area_subtitle, stations.size, choiceLabel(choice), stations.size)
                    }
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // The Distance order without a position: say where distances are from.
                    if (area != null && !area.tooLarge && area.fromCentre && settings.sort == NearbySort.DISTANCE) {
                        Text(
                            stringResource(R.string.area_from_centre),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (switchBeside) sortSwitch(Modifier.padding(start = 8.dp)) else closeButton?.invoke()
            }
            if (!switchBeside) sortSwitch(Modifier.padding(top = 8.dp))
            if (area == null) {
                Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Nearby.RADII_KM.forEach { km ->
                    FilterChip(
                        selected = state.radiusKm == km,
                        onClick = { onRadiusChange(km) },
                        label = { Text(stringResource(R.string.radius_km, km)) },
                    )
                }
                }
            }
            if (area != null && stations.isEmpty()) {
                Text(
                    if (area.tooLarge) stringResource(R.string.area_zoom_in) else stringResource(R.string.area_empty, choiceLabel(choice)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else if (stations.isEmpty()) {
                Text(
                    stringResource(R.string.nearby_empty, state.radiusKm, choiceLabel(choice)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Nearby.RADII_KM.firstOrNull { it > state.radiusKm }?.let { wider ->
                    OutlinedButton(onClick = { onRadiusChange(wider) }, modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(stringResource(R.string.action_widen_radius, wider))
                    }
                }
            }
        }
        if (showRows) {
            LazyColumn(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), state = listState) {
                itemsIndexed(stations, key = { _, item -> item.station.id }) { index, item ->
                    val measure = if (index < PEEK_ROWS) Modifier.onSizeChanged { rowPx[index] = it.height } else Modifier
                    if (area == null) {
                        NearbyRow(item, choice, brands, approximate, now, onStationClick, measure)
                    } else {
                        StationRow(
                            station = item.station,
                            brand = brands.getOrElse(item.station.brand) { "" },
                            price = item.price,
                            choice = choice,
                            where = placeAndDistance(item.station.municipality, item.station.province, item.station.lat, item.station.lon, state.position),
                            now = now,
                            onClick = { onStationClick(item) },
                            modifier = measure,
                        )
                    }
                }
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
    modifier: Modifier = Modifier,
) {
    StationRow(
        station = item.station,
        brand = brands.getOrElse(item.station.brand) { "" },
        price = item.price,
        choice = choice,
        where = formatDistance(item.distanceKm, approximate, LocalConfiguration.current.locales[0]),
        now = now,
        onClick = { onClick(item) },
        modifier = modifier,
    )
}

/**
 * A station in a list ("near me", search): class icon, name, brand and [where]
 * (distance or municipality), motorway badge, class and report age, price of
 * [choice]. Without a [price] (it doesn't sell the choice), a plain pump and "Doesn't sell …".
 */
@Composable
internal fun StationRow(
    station: Station,
    brand: String,
    price: RankedPrice?,
    choice: FuelChoice,
    where: String,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val icon = remember(price?.priceClass, density) { price?.let { StationIcons.draw(it.priceClass, density).asImageBitmap() } }
    val info = price?.let { PriceInfo(it.priceMilli, Instant.ofEpochSecond(it.updatedEpochSeconds)) }
    Column(modifier) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painterResource(R.drawable.ic_local_gas_station),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    station.name.ifEmpty { brand },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        listOfNotNull(brand.takeIf { station.name.isNotEmpty() && it.isNotEmpty() }, where).joinToString(" · "),
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
                    if (price != null && info != null) {
                        "${classText(price.priceClass)} · ${reportedText(info, now)}"
                    } else {
                        stringResource(R.string.station_not_selling, choiceLabel(choice))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (info != null) {
                Text(
                    formatPrice(info, perKg = choice.fuel == FuelKind.CNG || choice.fuel == FuelKind.LNG),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
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

/**
 * "Pisa (PI)", plus " · 2,4 km" when the user's [position] is known. Places in
 * search and favourites can be far away: whole km from 10 km ("250 km").
 */
@Composable
internal fun placeAndDistance(municipality: String, province: String, lat: Double, lon: Double, position: UserPosition?): String {
    val place = stringResource(R.string.place_with_province, PlaceNames.municipality(municipality), province)
    if (position == null) return place
    val km = Geo.distanceKm(position.lat, position.lon, lat, lon)
    val locale = LocalConfiguration.current.locales[0]
    val distance = if (km >= 10) {
        NumberFormat.getIntegerInstance(locale).format(km.roundToInt()) + " km"
    } else {
        formatDistance(km, position.accuracyMeters > PRECISE_METERS, locale)
    }
    return "$place · $distance"
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
