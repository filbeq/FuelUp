package io.github.filbeq.fuelup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** FuelUp's fixed palette; follows the system light/dark setting (no dynamic colour). */
@Composable
fun FuelUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/**
 * Background of everything that floats over the map (search bar, settings
 * button, date pill, fuel button, bottom sheet, side panel): white in the light
 * theme, a raised dark grey in the dark one. Follows the app theme, never the
 * map style, so the controls look the same over the light and the dark map.
 */
@Composable
fun floatingSurfaceColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest
