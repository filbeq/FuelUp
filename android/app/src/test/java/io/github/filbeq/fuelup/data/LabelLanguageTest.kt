package io.github.filbeq.fuelup.data

import io.github.filbeq.fuelup.map.LabelLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LabelLanguageTest {
    @Test
    fun italianAppShowsItalianNames() {
        assertEquals("it", LabelLanguage.forLocale(Locale.ITALY))
        assertEquals("it", LabelLanguage.forLocale(Locale.forLanguageTag("it-CH")))
    }

    @Test
    fun everyOtherLanguageShowsEnglishNames() {
        // The app itself falls back to English for any other language.
        assertEquals("en", LabelLanguage.forLocale(Locale.UK))
        assertEquals("en", LabelLanguage.forLocale(Locale.GERMANY))
    }
}
