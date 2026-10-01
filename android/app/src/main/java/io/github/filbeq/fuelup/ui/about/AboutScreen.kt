package io.github.filbeq.fuelup.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.BuildConfig
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.map.CurrentMapProvider

private const val DATASET_URL =
    "https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti"
private const val LICENSE_URL = "https://www.dati.gov.it/content/italian-open-data-license-v20"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )

            Section(R.string.about_unofficial_title)
            Body(R.string.about_unofficial_body)

            Section(R.string.about_prices_title)
            Body(R.string.about_prices_body)

            Section(R.string.about_data_title)
            Body(R.string.about_data_source)
            Body(R.string.about_data_license)
            Link(stringResource(R.string.about_open_dataset), DATASET_URL)
            Link(stringResource(R.string.about_open_license), LICENSE_URL)

            Section(R.string.about_map_title)
            Body(R.string.about_map_body)
            CurrentMapProvider.attributions.forEach { Link(stringResource(it.label), it.url) }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int) {
    Spacer(Modifier.size(8.dp))
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

/** A text button that opens [url] in the browser. */
@Composable
private fun Link(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUri(url) }) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        // Decorative: the label already says what the button does.
        Icon(
            painter = painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}
