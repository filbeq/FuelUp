package io.github.filbeq.fuelup

import android.os.Bundle
import android.os.StrictMode
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.github.filbeq.fuelup.ui.FuelUpApp
import io.github.filbeq.fuelup.ui.theme.FuelUpTheme
import org.maplibre.android.MapLibre

/**
 * The app's only activity. An AppCompatActivity (rather than a plain
 * ComponentActivity) so AppCompatDelegate can change the app language and
 * light/dark mode at runtime, on every supported Android version.
 */
class MainActivity : AppCompatActivity() {
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
