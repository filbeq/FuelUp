package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSwitchesTest {
    @Test
    fun followSystemTheme() {
        assertEquals(ThemeMode.SYSTEM, themeForFollowSystem(follow = true, currentlyDark = true))
        assertEquals(ThemeMode.SYSTEM, themeForFollowSystem(follow = true, currentlyDark = false))
        // Turning it off keeps what's on screen: no sudden flip.
        assertEquals(ThemeMode.DARK, themeForFollowSystem(follow = false, currentlyDark = true))
        assertEquals(ThemeMode.LIGHT, themeForFollowSystem(follow = false, currentlyDark = false))
    }

    @Test
    fun darkThemeSwitch() {
        assertEquals(ThemeMode.DARK, themeForDark(true))
        assertEquals(ThemeMode.LIGHT, themeForDark(false))
    }

    @Test
    fun automaticMapStyle() {
        assertEquals(MapStyleMode.AUTOMATIC, mapStyleForAutomatic(automatic = true, mapCurrentlyDark = false))
        assertEquals(MapStyleMode.DARK, mapStyleForAutomatic(automatic = false, mapCurrentlyDark = true))
        assertEquals(MapStyleMode.LIGHT, mapStyleForAutomatic(automatic = false, mapCurrentlyDark = false))
        assertEquals(MapStyleMode.DARK, mapStyleForDark(true))
        assertEquals(MapStyleMode.LIGHT, mapStyleForDark(false))
    }

    @Test
    fun mapIsDark() {
        assertTrue(MapStyleMode.AUTOMATIC.isDark(darkTheme = true))
        assertFalse(MapStyleMode.AUTOMATIC.isDark(darkTheme = false))
        assertTrue(MapStyleMode.DARK.isDark(darkTheme = false))
        assertFalse(MapStyleMode.LIGHT.isDark(darkTheme = true))
    }
}
