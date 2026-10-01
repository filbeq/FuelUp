package io.github.filbeq.fuelup.map

import io.github.filbeq.fuelup.PerfLog
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.SymbolLayer
import java.util.Locale

/**
 * Shows place names in the app language. OpenMapTiles data has `name:it`,
 * `name:en`, … plus `name` (the local name); the OpenFreeMap styles show
 * English. After a style loads, every label layer that displays a name is
 * switched to "name in the app language, otherwise the local name".
 * Labels that show something else (e.g. road numbers, `ref`) are left alone.
 */
object LabelLanguage {
    /** Map label language for the app's current locale: the app is in Italian or English. */
    fun forLocale(locale: Locale): String = if (locale.language == "it") "it" else "en"

    fun apply(style: Style, language: String) = PerfLog.time("localize labels ($language)") {
        val nameInLanguage = coalesce(get("name:$language"), get("name"))
        style.layers
            .filterIsInstance<SymbolLayer>()
            .filterNot { it.id.startsWith(OUR_LAYER_PREFIX) }
            .filter { it.textField.expression?.toString()?.contains("name") == true }
            .forEach { it.setProperties(textField(nameInLanguage)) }
    }

    private const val OUR_LAYER_PREFIX = "fuelup-"
}
