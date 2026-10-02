package io.github.filbeq.fuelup.data

import android.content.SharedPreferences
import androidx.core.content.edit

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** AUTOMATIC uses the dark map style with the dark app theme and the light one otherwise. */
enum class MapStyleMode { AUTOMATIC, LIGHT, DARK }

/**
 * Appearance settings we store ourselves. (The app language is stored by
 * AppCompat on Android < 13 and by the system on 13+, see SettingsViewModel.)
 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val mapStyle: MapStyleMode = MapStyleMode.AUTOMATIC,
) {
    companion object {
        /** Rebuilds saved settings; anything unknown falls back to the default. */
        fun decode(theme: String?, mapStyle: String?) = AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM,
            mapStyle = MapStyleMode.entries.firstOrNull { it.name == mapStyle } ?: MapStyleMode.AUTOMATIC,
        )
    }
}

/** Saves [AppSettings] in the same SharedPreferences file as the fuel choice. */
class AppSettingsStore(private val prefs: SharedPreferences) {
    fun load(): AppSettings = AppSettings.decode(prefs.getString(KEY_THEME, null), prefs.getString(KEY_MAP_STYLE, null))

    fun save(settings: AppSettings) {
        prefs.edit {
            putString(KEY_THEME, settings.theme.name)
            putString(KEY_MAP_STYLE, settings.mapStyle.name)
        }
    }

    companion object {
        /** The app's SharedPreferences file (also used by [FuelChoiceStore]). */
        const val PREFS_NAME = "settings"
        private const val KEY_THEME = "theme"
        private const val KEY_MAP_STYLE = "mapStyle"
    }
}

// Settings show each choice as two switches: "follow automatically" and, when
// that is off, "dark". These map the switches to the stored values.

/** "Follow system theme" switched on/off. Turning it off keeps what's on screen now. */
fun themeForFollowSystem(follow: Boolean, currentlyDark: Boolean): ThemeMode = when {
    follow -> ThemeMode.SYSTEM
    currentlyDark -> ThemeMode.DARK
    else -> ThemeMode.LIGHT
}

/** "Dark theme" switched (only possible when not following the system). */
fun themeForDark(dark: Boolean): ThemeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT

/** "Automatic map style" switched on/off. Turning it off keeps the current map. */
fun mapStyleForAutomatic(automatic: Boolean, mapCurrentlyDark: Boolean): MapStyleMode = when {
    automatic -> MapStyleMode.AUTOMATIC
    mapCurrentlyDark -> MapStyleMode.DARK
    else -> MapStyleMode.LIGHT
}

/** "Dark map" switched (only possible when the map style isn't automatic). */
fun mapStyleForDark(dark: Boolean): MapStyleMode = if (dark) MapStyleMode.DARK else MapStyleMode.LIGHT

/** Whether the map currently uses its dark style. */
fun MapStyleMode.isDark(darkTheme: Boolean): Boolean = when (this) {
    MapStyleMode.AUTOMATIC -> darkTheme
    MapStyleMode.LIGHT -> false
    MapStyleMode.DARK -> true
}

