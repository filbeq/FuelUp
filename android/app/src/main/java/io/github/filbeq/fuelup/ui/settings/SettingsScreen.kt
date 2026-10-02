package io.github.filbeq.fuelup.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.AppSettings
import io.github.filbeq.fuelup.data.MapStyleMode
import io.github.filbeq.fuelup.data.ThemeMode

/** Theme, map style and language, plus the way to About. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    language: AppLanguage,
    onThemeChange: (ThemeMode) -> Unit,
    onMapStyleChange: (MapStyleMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ChoiceGroup(
                title = R.string.settings_theme,
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                ),
                selected = settings.theme,
                onSelect = onThemeChange,
            )
            ChoiceGroup(
                title = R.string.settings_map_style,
                options = listOf(
                    MapStyleMode.AUTOMATIC to stringResource(R.string.map_style_automatic),
                    MapStyleMode.LIGHT to stringResource(R.string.map_style_light),
                    MapStyleMode.DARK to stringResource(R.string.map_style_dark),
                ),
                selected = settings.mapStyle,
                onSelect = onMapStyleChange,
            )
            ChoiceGroup(
                title = R.string.settings_language,
                options = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.language_system),
                    // Language names are written in their own language.
                    AppLanguage.ITALIAN to stringResource(R.string.language_italian),
                    AppLanguage.ENGLISH to stringResource(R.string.language_english),
                ),
                selected = language,
                onSelect = onLanguageChange,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_title)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                modifier = Modifier.selectable(selected = false, role = Role.Button, onClick = onOpenAbout),
            )
        }
    }
}

/** A titled group of radio buttons; the whole row is tappable. */
@Composable
private fun <T> ChoiceGroup(@StringRes title: Int, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
    Column(Modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = { RadioButton(selected = value == selected, onClick = null) },
                modifier = Modifier.selectable(
                    selected = value == selected,
                    role = Role.RadioButton,
                    onClick = { if (value != selected) onSelect(value) },
                ),
            )
        }
    }
}
