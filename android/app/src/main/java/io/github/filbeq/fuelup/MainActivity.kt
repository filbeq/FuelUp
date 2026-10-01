package io.github.filbeq.fuelup

import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.filbeq.fuelup.ui.FuelUpApp
import io.github.filbeq.fuelup.ui.theme.FuelUpTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            // Debug builds log any disk or network access on the main thread.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        }
        // MapLibre must be initialised before any map view is created.
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            FuelUpTheme {
                FuelUpApp()
            }
        }
    }
}
