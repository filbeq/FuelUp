package io.github.filbeq.fuelup.data

import java.util.Locale

/**
 * Municipality names for display, in Italian title case: MIMIT writes them in
 * capitals ("REGGIO NELL'EMILIA"), shown as "Reggio nell'Emilia".
 *
 * Every word starts with a capital, also after a hyphen or an apostrophe
 * ("L'Aquila", "Antey-Saint-André"), except the small linking words
 * ([PARTICLES], [ELIDED]), which stay lowercase unless they start the name.
 * Checked against ISTAT's official list of municipalities: the few names it
 * gets wrong are listed in DEVELOPMENT.md ("Municipality names").
 */
object PlaceNames {
    /** Linking words kept lowercase inside a name ("San Lazzaro di Savena"). */
    private val PARTICLES = setOf(
        "di", "da", "dal", "dallo", "dalla", "dai", "dagli", "dalle",
        "de", "del", "dello", "della", "dei", "degli", "delle",
        "nel", "nello", "nella", "nei", "negli", "nelle",
        "sul", "sullo", "sulla", "sui", "sugli", "sulle",
        "al", "allo", "alla", "ai", "agli", "alle",
        "a", "ad", "e", "ed", "in", "con", "su", "per", "presso",
        // Articles: official names mostly write these lowercase ("Santa Maria la Carità",
        // "Alcara li Fusi"); "Lo" and "Le" are capitalised in every official name.
        "il", "la", "li",
        // French, in Aosta Valley names ("Saint-Rhémy-en-Bosses").
        "en",
    )

    /** The same before an apostrophe ("Fiorenzuola d'Arda", "Magliano de' Marsi", "Castelnovo ne' Monti"). */
    private val ELIDED = setOf("d", "de", "ne", "l", "dall", "dell", "nell", "sull", "all")

    private val WORD = Regex("""\p{L}+""")

    fun municipality(raw: String): String {
        val text = raw.lowercase(Locale.ITALIAN)
        val out = StringBuilder(text.length)
        var end = 0
        for (match in WORD.findAll(text)) {
            val start = match.range.first
            out.append(text, end, start)
            val word = match.value
            val before = text.getOrNull(start - 1)
            val elided = text.getOrNull(match.range.last + 1)?.let { it == '\'' || it == '’' } == true
            val lower = start > 0 && when {
                elided -> word in ELIDED
                // After a hyphen, articles start a name part: "Gressoney-La-Trinité".
                before == '-' -> word == "en"
                else -> word in PARTICLES
            }
            out.append(if (lower) word else word.replaceFirstChar { it.titlecase(Locale.ITALIAN) })
            end = match.range.last + 1
        }
        out.append(text, end, text.length)
        return out.toString()
    }
}
