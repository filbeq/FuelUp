package io.github.filbeq.fuelup.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.data.AppSettings
import io.github.filbeq.fuelup.data.MapStyleMode
import io.github.filbeq.fuelup.data.RefreshResult
import io.github.filbeq.fuelup.data.ThemeMode
import io.github.filbeq.fuelup.data.isDark
import io.github.filbeq.fuelup.data.mapStyleForAutomatic
import io.github.filbeq.fuelup.data.mapStyleForDark
import io.github.filbeq.fuelup.data.themeForDark
import io.github.filbeq.fuelup.data.themeForFollowSystem
import io.github.filbeq.fuelup.ui.map.ManualUpdate
import io.github.filbeq.fuelup.ui.map.dataDateText
import io.github.filbeq.fuelup.ui.map.rememberDataDateClock
import io.github.filbeq.fuelup.ui.readableWidth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Theme, map style, language and "Update data now", plus the way to About. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    language: AppLanguage,
    onThemeChange: (ThemeMode) -> Unit,
    onMapStyleChange: (MapStyleMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    /** "Update data now": its state, when the server was last checked, the prices shown now. */
    dataUpdate: ManualUpdate,
    lastChecked: Instant?,
    currentPricesAt: String?,
    onUpdateNow: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    // What's on screen right now (the Theme setting is already applied).
    val darkNow = isSystemInDarkTheme()
    val mapDarkNow = settings.mapStyle.isDark(darkNow)

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
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).readableWidth()) {
            SectionTitle(R.string.settings_theme)
            SwitchRow(
                label = R.string.settings_follow_system_theme,
                checked = settings.theme == ThemeMode.SYSTEM,
                onChange = { onThemeChange(themeForFollowSystem(it, currentlyDark = darkNow)) },
            )
            SwitchRow(
                label = R.string.settings_dark_theme,
                checked = darkNow,
                enabled = settings.theme != ThemeMode.SYSTEM,
                onChange = { onThemeChange(themeForDark(it)) },
            )

            SectionTitle(R.string.settings_map_style)
            SwitchRow(
                label = R.string.settings_automatic_map_style,
                supporting = R.string.settings_automatic_map_style_detail,
                checked = settings.mapStyle == MapStyleMode.AUTOMATIC,
                onChange = { onMapStyleChange(mapStyleForAutomatic(it, mapCurrentlyDark = mapDarkNow)) },
            )
            SwitchRow(
                label = R.string.settings_dark_map,
                checked = mapDarkNow,
                enabled = settings.mapStyle != MapStyleMode.AUTOMATIC,
                onChange = { onMapStyleChange(mapStyleForDark(it)) },
            )

            SectionTitle(R.string.settings_language)
            LanguageMenu(language, onLanguageChange, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SectionTitle(R.string.settings_data)
            UpdateNowRow(dataUpdate, lastChecked, currentPricesAt, onUpdateNow)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_title)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenAbout),
            )
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

/**
 * "Update data now": asks the server even if today's data is already here, then
 * says what happened. Under it, when the server was last checked.
 */
@Composable
private fun UpdateNowRow(update: ManualUpdate, lastChecked: Instant?, currentPricesAt: String?, onUpdateNow: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val running = update == ManualUpdate.Running
    val checkedText = lastChecked?.let {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
        stringResource(R.string.settings_last_checked, formatter.format(it.atZone(ZoneId.systemDefault())))
    } ?: stringResource(R.string.settings_never_checked)
    val outcome: Pair<String, Boolean>? = when (update) {
        ManualUpdate.Idle -> null
        ManualUpdate.Running -> stringResource(R.string.settings_updating) to false
        is ManualUpdate.Done -> when (val result = update.result) {
            is RefreshResult.Updated -> pricesAtText(R.string.update_result_updated, result.snapshot.meta.pricesAt) to false
            RefreshResult.UpToDate -> currentPricesAt?.let {
                pricesAtText(R.string.update_result_up_to_date, it) to false
            }
            RefreshResult.Offline -> stringResource(R.string.update_result_offline) to true
            RefreshResult.Failed -> stringResource(R.string.update_result_failed) to true
            RefreshResult.UpdateRequired -> stringResource(R.string.error_update_required) to true
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_update_now)) },
        supportingContent = {
            Column {
                outcome?.let { (text, isError) ->
                    Text(text, color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
                Text(checkedText)
            }
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
        trailingContent = if (running) {
            { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        } else {
            null
        },
        modifier = Modifier.clickable(enabled = !running, role = Role.Button, onClick = onUpdateNow),
    )
}

/** "Updated to yesterday's prices" / "… prices of Tuesday, Oct 6", as the date pill words it. */
@Composable
private fun pricesAtText(@StringRes text: Int, pricesAt: String): String =
    stringResource(text, dataDateText(pricesAt, rememberDataDateClock(), inSentence = true))

/** A whole-row switch; disabled rows are greyed and announced as disabled. */
@Composable
private fun SwitchRow(
    @StringRes label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    @StringRes supporting: Int? = null,
) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = supporting?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

/** Language as a dropdown: system default / Italiano / English. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageMenu(language: AppLanguage, onChange: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_system),
        // Language names are written in their own language.
        AppLanguage.ITALIAN to stringResource(R.string.language_italian),
        AppLanguage.ENGLISH to stringResource(R.string.language_english),
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.first { it.first == language }.second,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.settings_language)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        if (value != language) onChange(value)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
