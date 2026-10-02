package io.github.filbeq.fuelup.data

import io.github.filbeq.fuelup.map.OpenFreeMap
import io.github.filbeq.fuelup.map.styleUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultsAndDecoding() {
        assertEquals(AppSettings(ThemeMode.SYSTEM, MapStyleMode.AUTOMATIC), AppSettings())
        assertEquals(AppSettings(), AppSettings.decode(null, null))
        assertEquals(AppSettings(ThemeMode.DARK, MapStyleMode.LIGHT), AppSettings.decode("DARK", "LIGHT"))
        assertEquals(AppSettings(), AppSettings.decode("SEPIA", "SATELLITE")) // unknown → default
    }

    @Test
    fun mapStyleForEveryModeAndTheme() {
        val light = OpenFreeMap.lightStyleUrl
        val dark = OpenFreeMap.darkStyleUrl
        assertEquals(light, OpenFreeMap.styleUrl(MapStyleMode.AUTOMATIC, darkTheme = false))
        assertEquals(dark, OpenFreeMap.styleUrl(MapStyleMode.AUTOMATIC, darkTheme = true))
        assertEquals(light, OpenFreeMap.styleUrl(MapStyleMode.LIGHT, darkTheme = true))
        assertEquals(dark, OpenFreeMap.styleUrl(MapStyleMode.DARK, darkTheme = false))
        assertEquals("https://tiles.openfreemap.org/styles/fiord", dark)
    }
}
