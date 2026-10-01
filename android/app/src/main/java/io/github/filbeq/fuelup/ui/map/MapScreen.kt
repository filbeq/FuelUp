package io.github.filbeq.fuelup.ui.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.filbeq.fuelup.R
import io.github.filbeq.fuelup.map.CurrentMapProvider
import io.github.filbeq.fuelup.map.MapCamera
import io.github.filbeq.fuelup.map.MapLibreMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    var camera by rememberSaveable(stateSaver = MapCamera.Saver) { mutableStateOf(MapCamera.Italy) }
    val provider = CurrentMapProvider

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MapLibreMap(
                styleUrl = if (isSystemInDarkTheme()) provider.darkStyleUrl else provider.lightStyleUrl,
                camera = camera,
                onCameraIdle = { camera = it },
                modifier = Modifier.fillMaxSize(),
            )
            MapAttributionBar(modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Map credits, always visible in a corner of the map (OSMF attribution guidelines). */
@Composable
private fun MapAttributionBar(modifier: Modifier = Modifier) {
    val text = CurrentMapProvider.attributions.map { stringResource(it.label) }.joinToString(" ")
    Surface(
        modifier = modifier.padding(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
