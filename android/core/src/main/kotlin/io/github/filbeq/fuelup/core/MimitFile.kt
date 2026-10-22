package io.github.filbeq.fuelup.core

import java.io.InputStream
import java.io.Reader
import java.nio.charset.CodingErrorAction
import java.time.LocalDate

/*
 * Shared helpers for the two MIMIT files: reading lines, header checks, text cleanup.
 *
 * Both files start with an extraction-date line ("Estrazione del 2026-09-30")
 * followed by a line of column names. Columns are separated by "|", with no
 * quoting, so lines are split by hand.
 *
 * The rules follow the original Python pipeline exactly (its whitespace and
 * line-break characters included), so both produce byte-identical output.
 */

const val SEPARATOR = "|"

private val EXTRACTION_RE = Regex("""^Estrazione del ([0-9]{4}-[0-9]{2}-[0-9]{2})$""")

/** The file does not look like the MIMIT format we know: stop, don't guess. */
class FormatError(message: String) : Exception(message)

/** Python's whitespace (`str.isspace`): what [cleanText] collapses and trims. */
private const val WHITESPACE =
    "\\u0009-\\u000d\\u001c-\\u0020\\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000"
internal val WHITESPACE_CLASS = "[$WHITESPACE]"

internal fun isSpace(c: Char): Boolean =
    c in '\u0009'..'\u000d' || c in '\u001c'..' ' || c == '\u0085' || c == ' ' ||
        c == ' ' || c in ' '..' ' || c == ' ' || c == ' ' ||
        c == ' ' || c == ' ' || c == '　'

/** Trim Python whitespace from both ends (Python's `str.strip()`). */
internal fun String.stripSpace(): String = trim(::isSpace)

/** Python's `not s.strip()`. */
internal fun String.isBlankSpace(): Boolean = all(::isSpace)

/**
 * Turn tabs and runs of whitespace into single spaces, and trim. A loop, not a
 * regex: it runs for every field of ~120k rows, and regexes are slow on Android.
 */
fun cleanText(value: String): String {
    // Fast path: nothing to change (the common case).
    var clean = value.isEmpty() || (!isSpace(value[0]) && !isSpace(value[value.length - 1]))
    var i = 0
    while (clean && i < value.length) {
        val c = value[i]
        if (isSpace(c) && (c != ' ' || isSpace(value[i + 1]))) clean = false
        i++
    }
    if (clean) return value

    val out = StringBuilder(value.length)
    var pendingSpace = false
    for (c in value) {
        if (isSpace(c)) {
            pendingSpace = out.isNotEmpty()
        } else {
            if (pendingSpace) out.append(' ')
            pendingSpace = false
            out.append(c)
        }
    }
    return out.toString()
}

/**
 * Lines of a MIMIT file, decoded as strict UTF-8 (a byte-order mark is skipped)
 * and split where Python's `str.splitlines` splits.
 */
fun <T> InputStream.useMimitLines(block: (Sequence<String>) -> T): T {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return java.io.InputStreamReader(this, decoder).use { reader ->
        try {
            block(splitLines(reader))
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw FormatError("file is not valid UTF-8")
        }
    }
}

/** Line breaks of Python's `str.splitlines`; "\r\n" counts as one. */
private fun isLineBreak(c: Int): Boolean =
    c in 0x0a..0x0d || c in 0x1c..0x1e || c == 0x85 || c == 0x2028 || c == 0x2029

internal fun splitLines(reader: Reader): Sequence<String> = sequence {
    val buffer = CharArray(64 * 1024)
    val line = StringBuilder()
    var atStart = true
    var afterCr = false
    while (true) {
        val n = reader.read(buffer)
        if (n < 0) break
        var from = 0
        if (atStart && n > 0) {
            atStart = false
            if (buffer[0] == '\uFEFF') from = 1
        }
        if (afterCr && from < n) {
            afterCr = false
            if (buffer[from] == '\n') from++
        }
        // Copy whole stretches between line breaks, not one character at a time.
        var i = from
        while (i < n) {
            val c = buffer[i].code
            if ((c <= 0x1e || c == 0x85 || c == 0x2028 || c == 0x2029) && isLineBreak(c)) {
                line.appendRange(buffer, from, i)
                yield(line.toString())
                line.setLength(0)
                i++
                if (c == 0x0d) {
                    if (i < n) {
                        if (buffer[i] == '\n') i++
                    } else {
                        afterCr = true
                    }
                }
                from = i
            } else {
                i++
            }
        }
        line.appendRange(buffer, from, n)
    }
    if (line.isNotEmpty()) yield(line.toString())
}

/** Validate the two header lines and return the extraction date. */
internal fun parseHeader(lines: Iterator<String>, expectedColumns: List<String>): LocalDate {
    if (!lines.hasNext()) throw FormatError("file has fewer than two header lines")
    val first = lines.next()
    if (!lines.hasNext()) throw FormatError("file has fewer than two header lines")
    val match = EXTRACTION_RE.matchEntire(first.stripSpace())
        ?: throw FormatError("unexpected first line: ${first.take(80)}")
    val columns = lines.next().split(SEPARATOR).map { it.stripSpace() }
    if (columns != expectedColumns) throw FormatError("unexpected columns: $columns")
    return try {
        LocalDate.parse(match.groupValues[1])
    } catch (e: java.time.format.DateTimeParseException) {
        throw FormatError("invalid extraction date: ${match.groupValues[1]}")
    }
}

/** Python's `int()` on already-cleaned text: optional sign, digits, single underscores. */
internal fun parsePyInt(text: String): Long? {
    val negative = text.startsWith('-')
    val start = if (negative || text.startsWith('+')) 1 else 0
    val end = digitPart(text, start)
    if (end == start || end != text.length) return null
    var value = 0L
    for (i in start until end) {
        val c = text[i]
        if (c == '_') continue
        if (value > (Long.MAX_VALUE - 9) / 10) return null // beyond Long: no real id is
        value = value * 10 + (c - '0')
    }
    return if (negative) -value else value
}

/**
 * Whether [text] is a number in Python's decimal syntax (as `float()` and
 * `Decimal()` accept it), without inf/nan:
 * `[+-]? (digits [. digits?] | . digits) ([eE] [+-]? digits)?`, where digits
 * may have single underscores between them.
 */
internal fun isPyNumber(text: String): Boolean {
    var i = if (text.startsWith('+') || text.startsWith('-')) 1 else 0
    val intEnd = digitPart(text, i)
    val hasInt = intEnd > i
    i = intEnd
    if (i < text.length && text[i] == '.') {
        val fracEnd = digitPart(text, i + 1)
        if (!hasInt && fracEnd == i + 1) return false
        i = fracEnd
    } else if (!hasInt) {
        return false
    }
    if (i < text.length && (text[i] == 'e' || text[i] == 'E')) {
        var j = i + 1
        if (j < text.length && (text[j] == '+' || text[j] == '-')) j++
        val expEnd = digitPart(text, j)
        if (expEnd == j) return false
        i = expEnd
    }
    return i == text.length
}

/** End of the digits starting at [start] (single underscores allowed between digits); [start] if none. */
private fun digitPart(text: String, start: Int): Int {
    var i = start
    if (i >= text.length || text[i] !in '0'..'9') return start
    i++
    while (i < text.length) {
        val c = text[i]
        if (c in '0'..'9') {
            i++
        } else if (c == '_' && i + 1 < text.length && text[i + 1] in '0'..'9') {
            i += 2
        } else {
            break
        }
    }
    return i
}

/** Python's `str.casefold()`, close enough for names: full upper then lower case. */
internal fun casefold(s: String): String = s.uppercase(java.util.Locale.ROOT).lowercase(java.util.Locale.ROOT)

/** Python's string order: by code point (Kotlin compares UTF-16 units). */
internal val CODE_POINT_ORDER = Comparator<String> { a, b ->
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a.codePointAt(i)
        val cb = b.codePointAt(j)
        if (ca != cb) return@Comparator ca.compareTo(cb)
        i += Character.charCount(ca)
        j += Character.charCount(cb)
    }
    (a.length - i).compareTo(b.length - j)
}
