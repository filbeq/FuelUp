package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FavoriteStation
import io.github.filbeq.fuelup.data.Favorites
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.Geo
import io.github.filbeq.fuelup.data.PlaceNames
import io.github.filbeq.fuelup.data.RankedPrice
import io.github.filbeq.fuelup.data.Station
import io.github.filbeq.fuelup.data.StationsFile
import io.github.filbeq.fuelup.data.UserPosition
import io.github.filbeq.fuelup.data.station
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * What the empty search needs for the favourites: the list, the data on
 * screen ([file], null until loaded), the user's position if "near me" found
 * one (for distances), and what to do with a favourite missing from the data.
 */
class FavoritesInSearch(
    val list: List<FavoriteStation>,
    val file: StationsFile?,
    val position: UserPosition?,
    /** A missing favourite: show its last known spot. */
    val onShowSpot: (FavoriteStation) -> Unit,
    val onRemove: (Int) -> Unit,
    /** Move the star to the station now registered at the favourite's spot. */
    val onReplace: (FavoriteStation, Station) -> Unit,
)

/** A favourite with its station in today's data, or (missing) the station now at its spot, if any. */
class FavoriteRow(val favorite: FavoriteStation, val station: Station?, val successor: Station?)

@Composable
fun rememberFavoriteRows(favorites: FavoritesInSearch): List<FavoriteRow> = remember(favorites.list, favorites.file) {
    val file = favorites.file ?: return@remember emptyList()
    favorites.list.map { favorite ->
        val station = file.station(favorite.id)
        FavoriteRow(favorite, station, if (station == null) Favorites.successor(favorite, file) else null)
    }
}

/** "Favourites" heading and one row each; a tip when there are none yet. */
fun LazyListScope.favoritesSection(
    rows: List<FavoriteRow>,
    favorites: FavoritesInSearch,
    choice: FuelChoice,
    ranking: Map<Int, RankedPrice>,
    onStationClick: (Station) -> Unit,
) {
    item(key = "favorites") {
        Text(
            stringResource(R.string.favorites_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
        )
    }
    if (rows.isEmpty()) {
        item(key = "favorites-empty") {
            Text(
                stringResource(R.string.favorites_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
    }
    items(rows, key = { "f:${it.favorite.id}" }) { row ->
        val station = row.station
        val now = remember(row) { Instant.now() }
        if (station != null) {
            StationRow(
                station = station,
                brand = row.favorite.brand,
                price = ranking[station.id],
                choice = choice,
                where = favoriteWhere(row.favorite, favorites.position),
                now = now,
                onClick = { onStationClick(station) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            MissingFavoriteRow(row, favorites)
        }
    }
}

/** "Pisa (PI) · 2,4 km": the municipality, and the distance when the position is known. */
@Composable
private fun favoriteWhere(favorite: FavoriteStation, position: UserPosition?): String {
    val place = stringResource(R.string.place_with_province, PlaceNames.municipality(favorite.municipality), favorite.province)
    if (position == null) return place
    val km = Geo.distanceKm(position.lat, position.lon, favorite.lat, favorite.lon)
    val locale = LocalConfiguration.current.locales[0]
    // Favourites can be far away: whole km from 10 km ("250 km", not "250,4 km").
    val distance = if (km >= 10) {
        NumberFormat.getIntegerInstance(locale).format(km.roundToInt()) + " km"
    } else {
        formatDistance(km, position.accuracyMeters > PRECISE_METERS, locale)
    }
    return "$place · $distance"
}

/**
 * A favourite not in today's data: its last known name and place, since when
 * it's missing, and a button to remove it. If a station is now registered at
 * the same spot (MIMIT gives a new id e.g. after an operator change), a link
 * moves the star to it. Tapping the row shows the spot on the map.
 */
@Composable
private fun MissingFavoriteRow(row: FavoriteRow, favorites: FavoritesInSearch) {
    val favorite = row.favorite
    Column(Modifier.padding(horizontal = 16.dp)) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { favorites.onShowSpot(favorite) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_local_gas_station),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    favorite.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(favorite.brand.takeIf { favorite.name.isNotEmpty() && it.isNotEmpty() }, favoriteWhere(favorite, favorites.position))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.favorite_missing_since, formatDataDate(favorite.lastSeen)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { favorites.onRemove(favorite.id) }) {
                Icon(
                    painterResource(R.drawable.ic_star),
                    contentDescription = stringResource(R.string.action_remove_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        row.successor?.let { successor ->
            val name = successor.name.ifEmpty { favorites.file?.brands?.getOrElse(successor.brand) { "" }.orEmpty() }
            TextButton(
                onClick = { favorites.onReplace(favorite, successor) },
                contentPadding = PaddingValues(start = 32.dp, end = 8.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(stringResource(R.string.favorite_at_spot_now, name), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "02/10" / "Oct 2" for a data date like "2026-10-02" (the text as is if it can't be read). */
@Composable
private fun formatDataDate(date: String): String {
    val pattern = stringResource(R.string.prices_date_pattern)
    val locale = LocalConfiguration.current.locales[0]
    return try {
        DateTimeFormatter.ofPattern(pattern, locale).format(LocalDate.parse(date))
    } catch (e: DateTimeParseException) {
        date
    }
}
