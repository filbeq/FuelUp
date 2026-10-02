package io.github.filbeq.fuelup.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// FuelUp palette "Ink blue": Material 3 schemes generated from seed #2F5DA8 with
// Google's Material colour algorithm (hue 265). UI colours stay away from the
// price colours (green = cheap, vermillion = expensive): no green, no orange/red.

/** Brand colour (launcher icon background). */
val BrandColor = Color(0xFF2F5DA8)

/**
 * Station clusters on the map: a dark tone on the light map, a pale tint on the
 * dark map, so they differ in lightness from both "cheap" (green) and "average"
 * (light grey) markers.
 */
val ClusterColorLight = Color(0xFF2F5DA8)
val ClusterColorDark = Color(0xFFD7E2FF)

// The user's position and the "near me" circle: a more saturated blue than the
// clusters, chosen by map darkness, each line with a halo in the opposite tone.
val LocationColorLight = Color(0xFF0B57D0)
val LocationColorDark = Color(0xFFA8C7FA)
val LocationHaloLight = Color(0xFFFFFFFF)
val LocationHaloDark = Color(0xFF14171C)

val LightColors = lightColorScheme(
    primary = Color(0xFF2F5DA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E2FF),
    onPrimaryContainer = Color(0xFF0B458E),
    inversePrimary = Color(0xFFACC7FF),
    secondary = Color(0xFF565E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF3F4759),
    tertiary = Color(0xFF5E5A84),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4DFFF),
    onTertiaryContainer = Color(0xFF46426A),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFAF9FD),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceTint = Color(0xFF2F5DA8),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF2F0F4),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceBright = Color(0xFFFAF9FD),
    surfaceDim = Color(0xFFDBD9DD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F7),
    surfaceContainer = Color(0xFFEFEDF1),
    surfaceContainerHigh = Color(0xFFE9E7EC),
    surfaceContainerHighest = Color(0xFFE3E2E6),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFACC7FF),
    onPrimary = Color(0xFF002F68),
    primaryContainer = Color(0xFF0B458E),
    onPrimaryContainer = Color(0xFFD7E2FF),
    inversePrimary = Color(0xFF2F5DA8),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFC7C1F1),
    onTertiary = Color(0xFF2F2C52),
    tertiaryContainer = Color(0xFF46426A),
    onTertiaryContainer = Color(0xFFE4DFFF),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFACC7FF),
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF2F3033),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    surfaceBright = Color(0xFF38393C),
    surfaceDim = Color(0xFF121316),
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF1F1F23),
    surfaceContainerHigh = Color(0xFF292A2D),
    surfaceContainerHighest = Color(0xFF343538),
)
