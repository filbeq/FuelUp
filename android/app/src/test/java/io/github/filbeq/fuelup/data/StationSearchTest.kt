package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StationSearchTest {
    private val brands = listOf("Agip Eni", "Q8", "Pompe Bianche")

    private fun station(id: Int, name: String, brand: Int, address: String, municipality: String, province: String, lat: Double, lon: Double) =
        Station(id, name, brand, 0, address, municipality, province, lat, lon, emptyList())

    // Real names and quirks from the MIMIT data. Map centre for distances: Pisa (43.72, 10.40).
    private val stations = listOf(
        station(1, "ENI PISA NORD", 0, "VIA AURELIA KM 335", "PISA", "PI", 43.75, 10.40),
        station(2, "S.ILARIO NORD", 0, "A1 KM 100", "SANT'ILARIO D'ENZA", "RE", 44.75, 10.45),
        station(3, "STAZIONE Q8", 1, "V.LE DELLE PIAGGE 4", "PISA", "PI", 43.71, 10.41),
        station(4, "", 2, "VIA S. GIUSEPPE 12", "SAN GIULIANO TERME", "PI", 43.76, 10.44),
        station(5, "DISTRIBUTORE ROSSI", 2, "VIA ROMA 1", "FORLÌ", "FC", 44.22, 12.04),
        station(6, "ENI CASCINA", 0, "VIA TOSCO ROMAGNOLA", "CASCINA", "PI", 43.68, 10.55),
        station(7, "ENI SANT'ELPIDIO", 0, "S.S. 16 KM 4", "SANT'ELPIDIO A MARE", "FM", 43.23, 13.69),
        station(8, "PISANO CARBURANTI", 2, "VIA DEL PORTO 2", "LIVORNO", "LI", 43.55, 10.31),
        station(9, "ENI PISA SUD", 0, "VIA EMILIA 2", "PISA", "PI", 43.70, 10.40),
    )
    private val search = StationSearch.build(StationsFile(1, "2026-10-02", brands, emptyList(), stations))

    // Stations 1, 3, 6, 9 sell the chosen fuel.
    private val ranking = listOf(1, 3, 6, 9).associateWith { RankedPrice(1800, 0, CompareGroup.ROAD, PriceClass.AVERAGE, 0.0) }

    private fun find(query: String) = search.search(query, ranking, 43.72, 10.40)
    private fun stationIds(query: String) = find(query).stations.map { it.id }
    private fun places(query: String) = find(query).municipalities.map { it.name }

    @Test
    fun queryWordsIgnoreCaseAccentsAndPunctuation() {
        assertEquals(listOf("forli"), SearchText.queryWords("Forlì"))
        assertEquals(listOf("sant", "elpidio"), SearchText.queryWords("Sant’Elpidio"))
        assertEquals(listOf("ss", "16"), SearchText.queryWords("S.S. 16"))
        assertEquals(listOf("s", "ilario"), SearchText.queryWords("S.ILARIO"))
    }

    @Test
    fun indexWordsAddSaintFormsAndAbbreviations() {
        val words = SearchText.indexWords("S.ILARIO")
        assertTrue(words.containsAll(listOf("s", "san", "santo", "santa", "sant", "ilario")))
        assertTrue("viale" in SearchText.indexWords("V.LE DELLE PIAGGE"))
        assertTrue("sm" in SearchText.indexWords("VIA S.M. GORETTI"))
        assertTrue("s" in SearchText.indexWords("VIA S.M. GORETTI"))
    }

    @Test
    fun tooShortQueriesFindNothing() {
        assertTrue(find("p").isEmpty)
        assertTrue(find(" ' ").isEmpty)
    }

    @Test
    fun municipalityIsCaseAndAccentInsensitive() {
        assertEquals(listOf("FORLÌ"), places("forli"))
        assertEquals(listOf("FORLÌ"), places("FORLÌ"))
    }

    @Test
    fun exactMunicipalityComesFirst() {
        // "PISA" exactly, before nothing else starting with "pisa" (Pisano is a station, not a town).
        assertEquals(listOf("PISA"), places("pisa"))
        assertEquals(listOf("SAN GIULIANO TERME"), places("san giu"))
    }

    @Test
    fun apostrophesAndSaintForms() {
        assertEquals(listOf("SANT'ELPIDIO A MARE"), places("sant'elpidio"))
        assertEquals(listOf("SANT'ELPIDIO A MARE"), places("santelpidio"))
        assertEquals(listOf("SANT'ELPIDIO A MARE"), places("s elpidio"))
        assertEquals(listOf("SANT'ILARIO D'ENZA"), places("ilario denza"))
        // "S." in the data matches "San", "Sant'", "Santo".
        assertEquals(listOf(2), stationIds("sant'ilario nord"))
        assertEquals(listOf(2), stationIds("san ilario"))
        assertEquals(listOf(4), stationIds("via san giuseppe"))
    }

    @Test
    fun partialWordsInAnyOrder() {
        assertEquals(listOf(3), stationIds("piag viale"))
        assertEquals(listOf(5), stationIds("rossi forl"))
    }

    @Test
    fun provinceCodeIsAWord() {
        assertEquals(listOf("SAN GIULIANO TERME"), places("san giuliano pi"))
        assertEquals(emptyList<String>(), places("san giuliano fc"))
    }

    @Test
    fun brandSearchSellersFirstThenNearest() {
        // Names starting with "eni": sellers of the chosen fuel by distance from Pisa, then
        // the others; then the Eni-branded station whose name doesn't start with it.
        assertEquals(listOf(9, 1, 6, 7, 2), stationIds("eni"))
    }

    @Test
    fun wholeWordsComeFirstThenNameMatches() {
        // The whole word in the name (9, 1: nearest first), then anywhere else (3, in Pisa),
        // then the start of a longer word ("PISANO").
        assertEquals(listOf(9, 1, 3, 8), stationIds("pisa"))
    }

    @Test
    fun brandAndMunicipalityTogether() {
        assertEquals(listOf(9, 1), stationIds("eni pisa"))
        assertEquals(listOf(3), stationIds("q8 pisa"))
    }

    @Test
    fun emptyNameIsFoundByBrand() {
        assertEquals(listOf(4, 8, 5), stationIds("pompe bianche"))
    }

    @Test
    fun resultsAreCapped() {
        val many = (1..200).map { station(it, "ENI $it", 0, "VIA ROMA", "ROMA", "RM", 41.9, 12.5 + it / 1000.0) }
        val big = StationSearch.build(StationsFile(1, "2026-10-02", brands, emptyList(), many))
        val results = big.search("eni", emptyMap(), 41.9, 12.5)
        assertEquals(StationSearch.MAX_STATIONS, results.stations.size)
        assertEquals(200, results.stationMatches)
        assertEquals(1, results.stations.first().id)
    }

    @Test
    fun framingLeavesOutAMisfiledStation() {
        // Like Pisa: stations ~2.6 km around the centre, Tirrenia 12 km out (a real part of Pisa),
        // and one filed under Pisa but in Capannoli, 26 km away.
        val town = (0 until 10).map {
            val angle = it * PI / 5
            station(100 + it, "S", 0, "", "PISA", "PI", 43.71 + 0.024 * sin(angle), 10.40 + 0.033 * cos(angle))
        } +
            station(200, "TIRRENIA", 0, "", "PISA", "PI", 43.631, 10.298) +
            station(201, "CAPANNOLI", 0, "", "PISA", "PI", 43.585, 10.676)
        val framed = Municipality("PISA", "PI", town).mainStations().map { it.id }
        assertTrue(200 in framed)
        assertTrue(201 !in framed)
        assertEquals(11, framed.size)
    }

    @Test
    fun framingKeepsEveryStationOfSmallTowns() {
        // Two stations: no telling which one is misfiled.
        val two = listOf(station(1, "A", 0, "", "X", "PI", 43.0, 10.0), station(2, "B", 0, "", "X", "PI", 44.0, 11.0))
        assertEquals(2, Municipality("X", "PI", two).mainStations().size)
    }
}
