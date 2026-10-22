package io.github.filbeq.fuelup.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values computed by Python itself (the original pipeline's rules). */
class MimitFileTest {
    @Test
    fun cleanTextLikePython() {
        assertEquals("", cleanText(""))
        assertEquals("a", cleanText("a"))
        assertEquals("a", cleanText(" a "))
        assertEquals("a b", cleanText("a  b"))
        assertEquals("a b", cleanText("a\u0009b"))
        assertEquals("a", cleanText("\u0009 a \u0009"))
        assertEquals("a b", cleanText("a\u00a0b"))
        assertEquals("a b", cleanText("a\u1680\u2000b"))
        assertEquals("a b", cleanText("a\u001cb"))
        assertEquals("a b", cleanText("a\u0085b"))
        assertEquals("", cleanText("  "))
        assertEquals("a b", cleanText("a b"))
        assertEquals("a b", cleanText("a \u000b b"))
        assertEquals("ab", cleanText("ab\u3000"))
        assertEquals("x\u200bx", cleanText("x\u200bx"))
        assertEquals("a b", cleanText("a\u202f\u205fb"))
    }

    @Test
    fun numbersLikePython() {
        assertEquals("1", true, isPyNumber("1"))
        assertEquals("-1", true, isPyNumber("-1"))
        assertEquals("+1", true, isPyNumber("+1"))
        assertEquals(".5", true, isPyNumber(".5"))
        assertEquals("5.", true, isPyNumber("5."))
        assertEquals("1e5", true, isPyNumber("1e5"))
        assertEquals("1E-5", true, isPyNumber("1E-5"))
        assertEquals("1_0", true, isPyNumber("1_0"))
        assertEquals("1__0", false, isPyNumber("1__0"))
        assertEquals("_1", false, isPyNumber("_1"))
        assertEquals("1_", false, isPyNumber("1_"))
        assertEquals("1.2.3", false, isPyNumber("1.2.3"))
        assertEquals("", false, isPyNumber(""))
        assertEquals("+", false, isPyNumber("+"))
        assertEquals(".", false, isPyNumber("."))
        assertEquals("e5", false, isPyNumber("e5"))
        assertEquals("1e", false, isPyNumber("1e"))
        assertEquals("1e+", false, isPyNumber("1e+"))
        assertEquals("1_0.0_1e1_0", true, isPyNumber("1_0.0_1e1_0"))
        assertEquals("0x1", false, isPyNumber("0x1"))
        assertEquals("1.5d", false, isPyNumber("1.5d"))
        assertEquals("inf", false, isPyNumber("inf"))
        assertEquals("nan", false, isPyNumber("nan"))
        assertEquals("1,5", false, isPyNumber("1,5"))
        assertEquals(" 1", false, isPyNumber(" 1"))
        assertEquals("1", 1L, parsePyInt("1"))
        assertEquals("-1", -1L, parsePyInt("-1"))
        assertEquals("+1", 1L, parsePyInt("+1"))
        assertEquals("1_0", 10L, parsePyInt("1_0"))
        assertEquals("1__0", null, parsePyInt("1__0"))
        assertEquals("_1", null, parsePyInt("_1"))
        assertEquals("1_", null, parsePyInt("1_"))
        assertEquals("", null, parsePyInt(""))
        assertEquals("+", null, parsePyInt("+"))
        assertEquals("0x1", null, parsePyInt("0x1"))
        assertEquals("1.0", null, parsePyInt("1.0"))
        assertEquals(" 1", null, parsePyInt(" 1"))
        assertEquals("007", 7L, parsePyInt("007"))
    }

    /** Python's `str.splitlines()` results; read whole and one character at a time. */
    @Test
    fun linesLikePythonSplitlines() {
        val cases = mapOf(
            "a\nb" to listOf("a", "b"),
            "a\r\nb" to listOf("a", "b"),
            "a\rb" to listOf("a", "b"),
            "a\n\nb\n" to listOf("a", "", "b"),
            "a\u000bb\u000cc\u001cd\u001de\u001ef\u0085g\u2028h\u2029i" to
                listOf("a", "b", "c", "d", "e", "f", "g", "h", "i"),
            "\uFEFFa\nb" to listOf("a", "b"),
            "a\r" to listOf("a"),
            "a\r\n" to listOf("a"),
            "\n" to listOf(""),
            "" to emptyList(),
            "a\u001fb" to listOf("a\u001fb"),
        )
        for ((text, expected) in cases) {
            assertEquals(text, expected, splitLines(java.io.StringReader(text)).toList())
            assertEquals(text, expected, splitLines(OneCharReader(text)).toList())
        }
    }

    /** Hands out one character per read, so "\r\n" spans two reads. */
    private class OneCharReader(private val text: String) : java.io.Reader() {
        private var pos = 0
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (pos >= text.length) return -1
            cbuf[off] = text[pos++]
            return 1
        }

        override fun close() = Unit
    }
}
