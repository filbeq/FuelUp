package io.github.filbeq.fuelup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.filbeq.fuelup.ui.map.MapScreen
import io.github.filbeq.fuelup.ui.theme.FuelUpTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MapLibre must be initialised before any map view is created.
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            FuelUpTheme {
                MapScreen()
            }
        }
    }
}
