package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.FuelChoice
import io.github.filbeq.fuelup.data.FuelKind
import io.github.filbeq.fuelup.data.Municipality
import io.github.filbeq.fuelup.data.PlaceNames
import io.github.filbeq.fuelup.data.RankedPrice
import io.github.filbeq.fuelup.data.SearchResults
import io.github.filbeq.fuelup.data.SearchText
import io.github.filbeq.fuelup.data.Station
import io.github.filbeq.fuelup.data.StationSearch
import io.github.filbeq.fuelup.map.StationLayers
import io.github.filbeq.fuelup.ui.station.PriceInfo
import io.github.filbeq.fuelup.ui.station.formatPrice
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * The search bar over the map, collapsed (Google Maps style): tapping it opens
 * the search ([MapSearchExpanded]). Same look as the other floating controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchBar(searchBarState: SearchBarState, textFieldState: TextFieldState, modifier: Modifier = Modifier) {
    SearchBar(
        state = searchBarState,
        inputField = { SearchInput(searchBarState, textFieldState) },
        modifier = modifier,
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shadowElevation = 2.dp,
    )
}

/**
 * The open search: keyboard and results. Full screen, or [docked]: the results
 * drop down under the bar, in the side panel's column, with the map still in
 * view. Back or the bar's arrow closes it.
 *
 * [results] are for [query]; null while they're being computed. [ready] is
 * false until the search index is built.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchExpanded(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    docked: Boolean,
    ready: Boolean,
    query: String,
    results: SearchResults?,
    choice: FuelChoice,
    ranking: Map<Int, RankedPrice>,
    brands: List<String>,
    favorites: FavoritesInSearch,
    onMunicipalityClick: (Municipality) -> Unit,
    onStationClick: (Station) -> Unit,
) {
    val inputField = @Composable { SearchInput(searchBarState, textFieldState) }
    val colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    val content = @Composable {
        SearchResultsList(ready, query, results, choice, ranking, brands, favorites, onMunicipalityClick, onStationClick)
    }
    if (docked) {
        ExpandedDockedSearchBar(state = searchBarState, inputField = inputField, colors = colors) { content() }
    } else {
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField, colors = colors) { content() }
    }
}

/** The text field: a magnifier when collapsed, a back arrow when open; × clears the text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInput(searchBarState: SearchBarState, textFieldState: TextFieldState) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val open = searchBarState.targetValue == SearchBarValue.Expanded
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        // The results are already there: the keyboard's search key just makes room for them.
        onSearch = { keyboard?.hide() },
        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            if (open) {
                IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_close_search))
                }
            } else {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null)
            }
        },
        trailingIcon = if (textFieldState.text.isNotEmpty()) {
            {
                IconButton(onClick = { textFieldState.clearText() }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun SearchResultsList(
    ready: Boolean,
    query: String,
    results: SearchResults?,
    choice: FuelChoice,
    ranking: Map<Int, RankedPrice>,
    brands: List<String>,
    favorites: FavoritesInSearch,
    onMunicipalityClick: (Municipality) -> Unit,
    onStationClick: (Station) -> Unit,
) {
    val now = remember(results) { Instant.now() }
    val typed = remember(query) { SearchText.queryWords(query).sumOf { it.length } }
    val listState = rememberLazyListState()
    // Scrolling the results means reading them: make room by hiding the keyboard.
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) keyboard?.hide() }
    // A new query starts at the top.
    LaunchedEffect(results) { listState.scrollToItem(0) }

    val message = when {
        !ready -> stringResource(R.string.search_preparing)
        typed == 0 -> stringResource(R.string.search_intro)
        typed < StationSearch.MIN_QUERY_LENGTH -> pluralStringResource(R.plurals.search_too_short, StationSearch.MIN_QUERY_LENGTH, StationSearch.MIN_QUERY_LENGTH)
        results != null && results.isEmpty -> stringResource(R.string.search_no_results, query.trim())
        else -> null
    }
    val more = results?.takeIf { it.stationMatches > it.stations.size }?.let {
        pluralStringResource(R.plurals.search_more, it.stationMatches, it.stations.size, it.stationMatches)
    }
    // With nothing typed (Google Maps style): the favourites first, once the data is there.
    val showFavorites = typed == 0 && favorites.file != null
    val favoriteRows = rememberFavoriteRows(favorites)
    LazyColumn(Modifier.fillMaxWidth().imePadding(), state = listState) {
        if (showFavorites) favoritesSection(favoriteRows, favorites, choice, ranking, onStationClick)
        if (message != null) {
            item { SearchMessage(message) }
            return@LazyColumn
        }
        if (results == null) return@LazyColumn
        items(results.municipalities, key = { "m:${it.name}:${it.province}" }) { municipality ->
            MunicipalityRow(municipality, choice, ranking, onClick = { onMunicipalityClick(municipality) })
        }
        items(results.stations, key = { "s:${it.id}" }) { station ->
            StationRow(
                station = station,
                brand = brands.getOrElse(station.brand) { "" },
                price = ranking[station.id],
                choice = choice,
                where = stringResource(R.string.place_with_province, PlaceNames.municipality(station.municipality), station.province),
                now = now,
                onClick = { onStationClick(station) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (more != null) item { SearchMessage(more) }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/** A municipality: name and province, its number of stations and the cheapest usable price of the chosen fuel. */
@Composable
private fun MunicipalityRow(municipality: Municipality, choice: FuelChoice, ranking: Map<Int, RankedPrice>, onClick: () -> Unit) {
    // Like a cluster's "from" price: without prices to verify or duty-free ones.
    val fromMilli = remember(municipality, ranking) {
        municipality.stations.mapNotNull { ranking[it.id]?.let(StationLayers::fromPrice) }
            .filter { it < StationLayers.NO_PRICE }
            .minOrNull()
    }
    val count = municipality.stations.size
    val details = buildList {
        add(pluralStringResource(R.plurals.search_station_count, count, count))
        if (fromMilli != null) {
            val price = formatPrice(PriceInfo(fromMilli, Instant.EPOCH), perKg = choice.fuel == FuelKind.CNG || choice.fuel == FuelKind.LNG)
            add(stringResource(R.string.cluster_from_price, price))
        }
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_location_city),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.place_with_province, PlaceNames.municipality(municipality.name), municipality.province),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
