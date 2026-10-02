package io.github.filbeq.fuelup.ui.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.filbeq.fuelup.data.AppSettings
import io.github.filbeq.fuelup.data.AppSettingsStore
import io.github.filbeq.fuelup.data.MapStyleMode
import io.github.filbeq.fuelup.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App languages offered in Settings. SYSTEM = follow the phone's language. */
enum class AppLanguage(val tag: String) { SYSTEM(""), ITALIAN("it"), ENGLISH("en") }

/**
 * Theme, map style and language. Theme and map style are saved by us; the
 * language by AppCompat (Android < 13) or the system (13+), which also keeps
 * it in sync with the system's per-app language setting.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store by lazy {
        AppSettingsStore(application.getSharedPreferences(AppSettingsStore.PREFS_NAME, Application.MODE_PRIVATE))
    }

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch { _settings.value = withContext(Dispatchers.IO) { store.load() } }
    }

    fun setTheme(theme: ThemeMode) {
        update { it.copy(theme = theme) }
        // Recreates the screen in the new mode.
        AppCompatDelegate.setDefaultNightMode(theme.nightMode())
    }

    fun setMapStyle(mapStyle: MapStyleMode) {
        update { it.copy(mapStyle = mapStyle) }
    }

    /** The language currently applied to the app. */
    fun language(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()
        return AppLanguage.entries.firstOrNull { it.tag == tag && it != AppLanguage.SYSTEM } ?: AppLanguage.SYSTEM
    }

    /** Switches the app language (recreates the screen); AppCompat/the system remember it. */
    fun setLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    private fun update(change: (AppSettings) -> AppSettings) {
        _settings.update(change)
        val saved = _settings.value
        viewModelScope.launch(Dispatchers.IO) { store.save(saved) }
    }
}

fun ThemeMode.nightMode(): Int = when (this) {
    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}
