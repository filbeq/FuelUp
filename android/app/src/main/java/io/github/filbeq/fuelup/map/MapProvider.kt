package io.github.filbeq.fuelup.map

import androidx.annotation.StringRes
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.MapStyleMode

/** One credit line required by a map provider, with the page it links to. */
data class MapAttribution(@StringRes val label: Int, val url: String)

/** Where map styles/tiles come from, and whom we must credit for them. */
data class MapProvider(
    val lightStyleUrl: String,
    val darkStyleUrl: String,
    /** A font the style's glyph server provides, for our own labels (cluster counts). */
    val labelFont: String,
    val attributions: List<MapAttribution>,
)

/** The style to load for a map-style setting and the current app theme. */
fun MapProvider.styleUrl(mode: MapStyleMode, darkTheme: Boolean): String = when (mode) {
    MapStyleMode.AUTOMATIC -> if (darkTheme) darkStyleUrl else lightStyleUrl
    MapStyleMode.LIGHT -> lightStyleUrl
    MapStyleMode.DARK -> darkStyleUrl
}

/** OpenFreeMap: free vector tiles from OpenStreetMap data, no API key needed. */
val OpenFreeMap = MapProvider(
    lightStyleUrl = "https://tiles.openfreemap.org/styles/liberty",
    // "fiord" (dark blue-grey) rather than "dark": clearly more readable on a phone
    // at night (compared on a Redmi Note 9 Pro, October 2026).
    darkStyleUrl = "https://tiles.openfreemap.org/styles/fiord",
    labelFont = "Noto Sans Bold",
    attributions = listOf(
        MapAttribution(R.string.attribution_openfreemap, "https://openfreemap.org"),
        MapAttribution(R.string.attribution_openmaptiles, "https://www.openmaptiles.org"),
        MapAttribution(R.string.attribution_osm, "https://www.openstreetmap.org/copyright"),
    ),
)

/**
 * The provider used by the whole app (map, attribution bar, About screen).
 * To switch provider, define another [MapProvider] and change this line.
 */
val CurrentMapProvider: MapProvider = OpenFreeMap
