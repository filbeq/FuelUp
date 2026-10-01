package io.github.filbeq.fuelup.map

import androidx.annotation.StringRes
import io.github.filbeq.fuelup.R

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

/** OpenFreeMap: free vector tiles from OpenStreetMap data, no API key needed. */
val OpenFreeMap = MapProvider(
    lightStyleUrl = "https://tiles.openfreemap.org/styles/liberty",
    darkStyleUrl = "https://tiles.openfreemap.org/styles/dark",
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
