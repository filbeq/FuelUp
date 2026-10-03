package io.github.filbeq.fuelup.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos

/** A municipality found by the search, with its stations (MIMIT spelling in [name]). */
class Municipality(val name: String, val province: String, val stations: List<Station>) {
    /**
     * The stations to frame on the map: all but those far from the rest, which
     * are usually filed under the wrong municipality (one "PISA" station is in
     * Capannoli, 26 km away). Far = more than [OUTLIER_FACTOR] times the median
     * distance from the median point, and more than [OUTLIER_MIN_KM]. With fewer
     * than 3 stations there's no telling which one is wrong: all are kept.
     * Measured on the published 2 Oct 2026 data: leaves out 177 stations in 133
     * municipalities, at most 87 km away (wrong municipality in the right
     * province, or an outlying part of a large town).
     */
    fun mainStations(): List<Station> {
        if (stations.size < 3) return stations
        val lat = median(stations.map { it.lat })
        val lon = median(stations.map { it.lon })
        val distances = stations.map { Geo.distanceKm(lat, lon, it.lat, it.lon) }
        val limit = maxOf(OUTLIER_FACTOR * median(distances), OUTLIER_MIN_KM)
        return stations.filterIndexed { i, _ -> distances[i] <= limit }
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    companion object {
        const val OUTLIER_FACTOR = 5.0
        /** Some towns' stations sit within a few hundred metres: don't call their other parts outliers. */
        const val OUTLIER_MIN_KM = 10.0
    }
}

/** What a search finds, both lists already ranked. */
class SearchResults(
    val municipalities: List<Municipality>,
    val stations: List<Station>,
    /** All matching stations, of which [stations] are the best [StationSearch.MAX_STATIONS]. */
    val stationMatches: Int,
    /** The query is a place rather than a brand: show [municipalities] before [stations]. */
    val placesFirst: Boolean = true,
) {
    val isEmpty: Boolean get() = municipalities.isEmpty() && stations.isEmpty()

    companion object {
        val None = SearchResults(emptyList(), emptyList(), 0)
    }
}

/**
 * Offline search over the stations already on the phone: station name, brand,
 * address, municipality and province; and municipalities by name.
 *
 * Matching (see [SearchText]): case and accents don't matter, every word typed
 * must be the start of a word in the target, in any order ("eni pisa",
 * "s giuliano"), and "S." / "San" / "Sant'" match each other.
 *
 * Ranking: municipalities by exact name, then name starting with the text,
 * then all words matching; bigger towns first. Stations: those matching every
 * word in full before those matching only the start of a word ("roma":
 * stations in Rome before "ROMAIRONE"); within each, selling the chosen fuel,
 * then nearest to the reference point (the user, else the map centre).
 *
 * Municipalities come first only when the query is a place rather than a
 * brand ([SearchResults.placesFirst]): it is a municipality's exact name, or
 * more stations lie in the matching municipalities than carry a brand matching
 * it ("san": hundreds of towns; "eni", "api", "ip": brands).
 *
 * Speed: every word is stored once in a sorted vocabulary and each target
 * keeps its words as ids, so the words starting with a typed word are one
 * range of ids (two binary searches) and testing a station is a few integer
 * comparisons. Built once per data download, off the main thread.
 */
class StationSearch private constructor(
    private val vocabulary: Array<String>,
    private val stations: List<Station>,
    /** Per station: all its words (name, brand, address, municipality, province). */
    private val allWords: Array<IntArray>,
    /** Per brand: its words. */
    private val brandWords: Array<IntArray>,
    /** Per brand: how many stations carry it. */
    private val brandCounts: IntArray,
    private val municipalities: List<Municipality>,
    private val municipalityWords: Array<IntArray>,
    /** Per municipality: its name without spaces ("santelpidioamare"). */
    private val municipalityCompact: Array<String>,
) {
    /**
     * Finds what matches [query]. [ranking] tells which stations sell the chosen
     * fuel (they come first); then nearest to [refLat], [refLon] (the user's
     * position when known, else the map centre).
     */
    fun search(query: String, ranking: Map<Int, RankedPrice>, refLat: Double, refLon: Double): SearchResults {
        val words = SearchText.queryWords(query)
        val compact = words.joinToString("")
        if (compact.length < MIN_QUERY_LENGTH) return SearchResults.None
        val ranges = words.map { prefixRange(it) }
        // Each word's own id, if it is a whole word of the data (-1 if not).
        val exactIds = words.mapIndexed { i, word -> ranges[i].first.takeIf { !ranges[i].isEmpty() && vocabulary[it] == word } ?: -1 }
        // A word nothing starts with: no target can have all the words (municipalities may still match compactly).
        val allFound = ranges.none { it.isEmpty() }

        // Municipalities: 0 = exact, 1 = name starts with the text, 2 = all words match.
        val places = ArrayList<Pair<Int, Municipality>>()
        for (i in municipalities.indices) {
            val name = municipalityCompact[i]
            val tier = when {
                name == compact -> 0
                name.startsWith(compact) -> 1
                allFound && matchesAll(municipalityWords[i], ranges) -> 2
                else -> continue
            }
            places += tier to municipalities[i]
        }
        places.sortWith(
            compareBy<Pair<Int, Municipality>> { it.first }
                .thenByDescending { it.second.stations.size }
                .thenBy { it.second.name },
        )

        // A place or a brand? An exact name is a place; otherwise whichever more stations answer to.
        val placesFirst = places.firstOrNull()?.first == 0 || run {
            val inPlaces = places.sumOf { it.second.stations.size }
            val byBrand = if (allFound) brandWords.indices.sumOf { if (matchesAll(brandWords[it], ranges)) brandCounts[it] else 0 } else 0
            inPlaces >= byBrand
        }

        // Stations: 0 = every word matches a whole word, 1 = some word only the start of a longer one.
        val cosLat = cos(refLat * PI / 180)
        val hits = ArrayList<StationHit>()
        if (allFound) {
            for (i in stations.indices) {
                if (!matchesAll(allWords[i], ranges)) continue
                val tier = if (exactIds.all { id -> id >= 0 && id in allWords[i] }) 0 else 1
                val station = stations[i]
                val dLat = station.lat - refLat
                val dLon = (station.lon - refLon) * cosLat
                hits += StationHit(station, tier, station.id in ranking, dLat * dLat + dLon * dLon)
            }
        }
        hits.sortWith(
            compareBy<StationHit> { it.tier }
                .thenBy { !it.sellsChoice }
                .thenBy { it.distance2 }
                .thenBy { it.station.id },
        )
        return SearchResults(
            municipalities = places.take(MAX_MUNICIPALITIES).map { it.second },
            stations = hits.take(MAX_STATIONS).map { it.station },
            stationMatches = hits.size,
            placesFirst = placesFirst,
        )
    }

    private class StationHit(val station: Station, val tier: Int, val sellsChoice: Boolean, val distance2: Double)

    /** Ids of the vocabulary words starting with [prefix]: [first, last). */
    private fun prefixRange(prefix: String): IntRange {
        val first = lowerBound(prefix)
        val end = lowerBound(prefix + Char.MAX_VALUE)
        return first until end
    }

    private fun lowerBound(key: String): Int {
        var low = 0
        var high = vocabulary.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (vocabulary[mid] < key) low = mid + 1 else high = mid
        }
        return low
    }

    private fun matchesAll(words: IntArray, ranges: List<IntRange>): Boolean =
        ranges.all { range -> words.any { it >= range.first && it <= range.last } }

    companion object {
        /** Shorter queries (after normalising) show nothing: too many results to be useful. */
        const val MIN_QUERY_LENGTH = 2
        const val MAX_MUNICIPALITIES = 5
        const val MAX_STATIONS = 50

        fun build(file: StationsFile): StationSearch {
            val ids = HashMap<String, Int>()
            val words = ArrayList<String>()
            // A text's word ids; brands, municipalities and provinces repeat, so each is cut once.
            val cache = HashMap<String, IntArray>()
            fun idsOf(text: String): IntArray = cache.getOrPut(text) {
                SearchText.indexWords(text).map { w -> ids.getOrPut(w) { words.add(w); words.size - 1 } }.toIntArray()
            }
            // Always a new array: the ids are renumbered in place below.
            fun union(vararg parts: IntArray): IntArray {
                val all = IntArray(parts.sumOf { it.size })
                var n = 0
                for (part in parts) for (id in part) all[n++] = id
                all.sort()
                var unique = 0
                for (j in all.indices) if (j == 0 || all[j] != all[j - 1]) all[unique++] = all[j]
                return all.copyOf(unique)
            }
            val stations = file.stations
            val allWords = arrayOfNulls<IntArray>(stations.size)
            val brandCounts = IntArray(file.brands.size)
            val byMunicipality = LinkedHashMap<Pair<String, String>, MutableList<Station>>()
            for ((i, s) in stations.withIndex()) {
                val brand = file.brands.getOrElse(s.brand) { "" }
                allWords[i] = union(idsOf(s.name), idsOf(brand), idsOf(s.address), idsOf(s.municipality), idsOf(s.province))
                if (s.brand in brandCounts.indices) brandCounts[s.brand]++
                byMunicipality.getOrPut(s.municipality.uppercase(Locale.ROOT) to s.province) { ArrayList() } += s
            }
            val municipalities = byMunicipality.map { (key, list) -> Municipality(list[0].municipality, key.second, list) }
            val municipalityWords = Array(municipalities.size) { i ->
                union(idsOf(municipalities[i].name), idsOf(municipalities[i].province))
            }
            val brandWords = Array(file.brands.size) { union(idsOf(file.brands[it])) }
            val municipalityCompact = Array(municipalities.size) { i ->
                SearchText.queryWords(municipalities[i].name).joinToString("")
            }

            // Sort the vocabulary, so the words sharing a prefix get consecutive ids.
            val order = words.indices.sortedWith { a, b -> words[a].compareTo(words[b]) }
            val newId = IntArray(words.size)
            order.forEachIndexed { rank, old -> newId[old] = rank }
            fun remap(array: IntArray) = array.also { for (j in it.indices) it[j] = newId[it[j]] }

            return StationSearch(
                vocabulary = Array(words.size) { words[order[it]] },
                stations = stations,
                allWords = Array(stations.size) { remap(allWords[it]!!) },
                brandWords = Array(file.brands.size) { remap(brandWords[it]) },
                brandCounts = brandCounts,
                municipalities = municipalities,
                municipalityWords = Array(municipalities.size) { remap(municipalityWords[it]) },
                municipalityCompact = municipalityCompact,
            )
        }
    }
}

/**
 * How texts are cut into words for [StationSearch], the same for the data and
 * what the user types: lowercase, no accents ("Forlì" = "forli"), split at
 * anything but letters and digits, apostrophes included ("Sant'Elpidio" =
 * "sant elpidio"). Dotted abbreviations are joined ("S.S." = "ss",
 * "S.N.C." = "snc"); a single "S." before a name is the word "s" ("S.ILARIO").
 */
object SearchText {
    private const val APOSTROPHES = "'’`´"

    /** "Saint" in its forms: the abbreviation "S." in the data also matches what the user writes in full. */
    private val SAINT = listOf("san", "santo", "santa", "sant")

    /** Address abbreviations in the data, also searchable in full. */
    private val ABBREVIATIONS = mapOf(
        "v.le" to "viale",
        "c.so" to "corso",
        "p.za" to "piazza",
        "p.zza" to "piazza",
        "f.lli" to "fratelli",
    )

    /** A word and what follows it (spaces skipped). */
    private class Token(val word: String, val dot: Boolean, val apostrophe: Boolean)

    /** Words of what the user typed. */
    fun queryWords(text: String): List<String> = joinAcronyms(tokens(text))

    /**
     * Words of a field of the data: those of [queryWords], plus the single
     * letters of abbreviations ("S.M." also gives "s", "m"), words joined at
     * apostrophes ("D'ENZA" also gives "denza"), "san"/"santo"/"santa"/"sant"
     * for a lone "s", and address abbreviations in full.
     */
    fun indexWords(text: String): Set<String> {
        val tokens = tokens(text)
        val result = LinkedHashSet(joinAcronyms(tokens))
        for ((i, token) in tokens.withIndex()) {
            result += token.word
            val next = tokens.getOrNull(i + 1)?.word ?: continue
            if (token.apostrophe) result += token.word + next
            if (token.dot) ABBREVIATIONS["${token.word}.$next"]?.let { result += it }
        }
        if ("s" in result) result += SAINT
        return result
    }

    /** Two or more single letters each followed by a dot are one word: "s.s." / "s. p." / "s.n.c." = "ss" / "sp" / "snc". */
    private fun joinAcronyms(tokens: List<Token>): List<String> {
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            var end = i
            while (end < tokens.size && tokens[end].dot && tokens[end].word.length == 1 && tokens[end].word[0].isLetter()) end++
            if (end - i >= 2) {
                out += tokens.subList(i, end).joinToString("") { it.word }
                i = end
            } else {
                out += tokens[i].word
                i++
            }
        }
        return out
    }

    /** Lowercase words without accents, in one pass (no regular expressions: the index cuts ~100k texts). */
    private fun tokens(text: String): List<Token> {
        val plain = simplify(text)
        val out = ArrayList<Token>()
        var i = 0
        while (i < plain.length) {
            if (!plain[i].isLetterOrDigit()) {
                i++
                continue
            }
            val start = i
            while (i < plain.length && plain[i].isLetterOrDigit()) i++
            var next = i
            while (next < plain.length && plain[next] == ' ') next++
            val after = plain.getOrNull(next)
            out += Token(plain.substring(start, i), dot = after == '.', apostrophe = after != null && after in APOSTROPHES)
        }
        return out
    }

    private fun simplify(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.all { it.code < 128 }) return lower
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            for (c in decomposed) if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) append(c)
        }
    }
}
