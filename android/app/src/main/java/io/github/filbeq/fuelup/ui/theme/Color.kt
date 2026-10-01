package io.github.filbeq.fuelup.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fixed FuelUp palette: "fuel green" (#0B6E4F) as the brand colour, with an
// amber accent. Values follow the Material 3 tone scale (40/90/10 in light,
// 80/30/90 in dark) with slightly green-tinted neutrals.

val FuelGreen = Color(0xFF0B6E4F)

val LightColors = lightColorScheme(
    primary = FuelGreen,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA0F2CD),
    onPrimaryContainer = Color(0xFF002115),
    inversePrimary = Color(0xFF84D6B2),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF082016),
    tertiary = Color(0xFF7C5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF271900),
    background = Color(0xFFF5FBF5),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF5),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    surfaceTint = FuelGreen,
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF2ED),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    surfaceBright = Color(0xFFF5FBF5),
    surfaceDim = Color(0xFFD6DBD6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F0),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DF),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF84D6B2),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA0F2CD),
    inversePrimary = FuelGreen,
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1E352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFFABD42),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceTint = Color(0xFF84D6B2),
    inverseSurface = Color(0xFFDEE4DF),
    inverseOnSurface = Color(0xFF2C322E),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF404944),
    surfaceBright = Color(0xFF353B37),
    surfaceDim = Color(0xFF0F1512),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)
