package io.github.filbeq.fuelup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * fixtures/stations.csv holds real rows from the 2026-09-30 MIMIT file, picked
 * for their quirks, plus synthetic rows (ids 9000xx, X900006 and a second 59183)
 * for quirks not present in that day's data.
 */
class StationsTest {
    private val result: StationFile = Fixtures.stations()
    private val stations: Map<Long, Station> = result.stations
    private val report = result.report

    @Test
    fun extractionDate() = assertEquals(LocalDate.of(2026, 9, 30), result.extractionDate)

    @Test
    fun keptStations() = assertEquals(
        listOf<Long>(3464, 34658, 40820, 41694, 44351, 45790, 49891, 51468, 54386, 59183, 60534, 63381),
        stations.keys.sorted(),
    )

    @Test
    fun normalRow() {
        val s = stations.getValue(3464)
        assertEquals("PO EST", s.name)
        assertEquals("Api-Ip", s.brand)
        assertTrue(s.motorway)
        assertEquals("Autostrada A13 BOLOGNA-PADOVA, Km. 43+400, dir. Nord - 44100", s.address)
        assertEquals("FERRARA", s.municipality)
        assertEquals("FE", s.province)
        assertEquals(44.88012 to 11.57083, s.lat to s.lon) // rounded to 5 decimals
    }

    @Test
    fun whitespaceIsCollapsedAndTrimmed() {
        assertEquals("SS.189 KM. 64+649 - C.DA SAN MICHELE S.N.C", stations.getValue(59183).address)
        assertFalse(stations.getValue(59183).motorway)
    }

    @Test
    fun tabsInsideFields() {
        assertEquals("19834 MONTALLEGRO", stations.getValue(63381).name)
        assertEquals("SS 10 KM.103 15040", stations.getValue(41694).address)
    }

    @Test
    fun extraSeparatorInNameIsRecovered() {
        val s = stations.getValue(40820)
        assertEquals("STOIL SIMPLE", s.name)
        assertEquals("Pompe Bianche", s.brand)
        assertEquals("STR. PROV.LE 82 SPINETTA SALE 15122", s.address)
        assertEquals("ALESSANDRIA", s.municipality)
    }

    @Test
    fun extraSeparatorsInOperatorAndNameAreRecovered() {
        val s = stations.getValue(54386)
        assertEquals("PRADELLI - MONTEOMBRARO", s.name)
        assertEquals("Pompe Bianche", s.brand)
        assertEquals("Via dei Martiri 255 41059", s.address)
        assertEquals(44.37912 to 11.00468, s.lat to s.lon)
    }

    @Test
    fun realSeparatorSurroundedBySpaces() {
        val s = stations.getValue(34658)
        assertEquals("SOGEDI - PV ENI MOLA", s.name)
        assertEquals("SP 111 MOLA - RUTIGLIANO KM 1+235 70042", s.address)
    }

    @Test
    fun coordinatesWithSurroundingSpaces() {
        assertEquals(13.37302, stations.getValue(44351).lon, 0.0)
        assertEquals(15.01481, stations.getValue(45790).lon, 0.0)
    }

    @Test
    fun utf8Text() {
        assertEquals("CARRÙ", stations.getValue(60534).municipality)
        assertEquals("56297 – San Paolo d’Argon", stations.getValue(49891).name)
    }

    @Test
    fun duplicateIdKeepsFirst() = assertEquals("19829 AGRIGENTO", stations.getValue(59183).name)

    @Test
    fun dropReasons() {
        assertEquals(
            mapOf(
                "missing coordinates" to 1,
                "zero coordinates" to 1,
                "coordinates outside Italy" to 1,
                "invalid coordinates" to 1,
                "duplicate station id" to 1,
                "wrong number of fields" to 2,
                "invalid station id" to 1,
            ),
            report.dropped,
        )
        assertEquals(listOf("60502"), report.samples("missing coordinates"))
        assertEquals(listOf("900004", "900005"), report.samples("wrong number of fields"))
        assertEquals(2, report.notes["extra separators recovered"])
        assertEquals(20, report.rowsRead)
        assertEquals(8, report.droppedTotal)
    }

    private fun parse(vararg lines: String) = parseStations(lines.asSequence())

    @Test(expected = FormatError::class)
    fun missingExtractionLine() {
        parse(HEADER[1], "1|a|b|Stradale|n|a|c|p|41.9|12.5")
    }

    @Test(expected = FormatError::class)
    fun changedColumns() {
        parse(HEADER[0], HEADER[1].replace("Bandiera", "Marchio"))
    }

    @Test
    fun emptyNameIsKept() {
        val result = parse(*HEADER, "1|op|Q8|Stradale||VIA X|ROMA|RM|41.9|12.5")
        assertEquals("", result.stations.getValue(1).name)
        assertEquals(1, result.report.notes["empty station name"])
    }

    @Test
    fun coordinatesLikePython() {
        assertEquals(44.88012, roundCoord(44.88011856623546), 0.0)
        assertEquals(0.00001, roundCoord(0.000005000000000000001), 0.0)
    }

    /** Real rows from the 2026-09-30 file (plus synthetic ids 9001xx). */
    private val misplacedRows = arrayOf(
        // Sorrento station registered with the coordinates of the Ventimiglia one.
        "62969|ENIMOOV S.P.A.|Agip Eni|Stradale|1235 VENTIMIGLIA|CORSO GENOVA  84/C 0204  |VENTIMIGLIA|IM|43.78874|7.6213346",
        "62970|ENIMOOV S.P.A.|Agip Eni|Stradale|8144 SORRENTO|CORSO ITALIA  No279  |SORRENTO|NA|43.78874|7.6213346",
        "20947|DE NICOLA ROBERTO|Agip Eni|Stradale|eni|C.so Genova 84 18039|VENTIMIGLIA|IM|43.788566639001374|7.6211816325409245",
        "35993|ENERGY OIL|Agip Eni|Stradale|08144|CORSO ITALIA 279 80067|SORRENTO|NA|40.62665221730716|14.382114708423615",
        // Islands: far from the rest of their province, but no other province is near.
        "40673|COSTANZO|Pompe Bianche|Stradale|Costanzo|CONTRADA GUITGIA SN 92010|LAMPEDUSA E LINOSA|AG|35.500892388423836|12.60155439376831",
        "60141|NAUTILUS|Pompe Bianche|Stradale|LINOSA|VIA POZZOLANA DI PONENTE SNC 92010|LAMPEDUSA E LINOSA|AG|35.861987|12.857395",
        "59183|ENIMOOV S.P.A.|Agip Eni|Stradale|19829 AGRIGENTO|SS.189 KM. 64+649|AGRIGENTO|AG|37.333935|13.595533",
        "54872|G P S.R.L.|Agip Eni|Stradale|Eni 51929|RESURREZIONE 87 90146|PALERMO|PA|38.165539468413655|13.320088127647391",
        // Near a province border: 1 km from another province, 10 km from its own.
        "900101|T|Q8|Stradale|BORDER|VIA A|PAESE A|AA|44.0|11.0",
        "900102|T|Q8|Stradale|OWN|VIA B|PAESE B|AA|44.09|11.0",
        "900103|T|Q8|Stradale|NEIGHBOUR|VIA C|PAESE C|BB|43.991|11.0",
        "900104|T|Q8|Stradale|NEIGHBOUR 2|VIA D|PAESE D|BB|43.9|11.0",
    )

    @Test
    fun copiedCoordinatesInAnotherProvinceAreDropped() {
        val result = parse(*HEADER, *misplacedRows)
        assertFalse(62970L in result.stations)
        assertTrue(62969L in result.stations)
        assertEquals(1, result.report.dropped["coordinates in another province"])
        assertTrue(result.report.samples("coordinates in another province")[0].startsWith("62970 "))
    }

    @Test
    fun islandsAreKept() {
        val result = parse(*HEADER, *misplacedRows)
        for (id in listOf<Long>(40673, 60141, 54872)) assertTrue("$id", id in result.stations)
    }

    @Test
    fun stationNearAProvinceBorderIsKept() {
        val result = parse(*HEADER, *misplacedRows)
        assertTrue(900101L in result.stations)
        assertTrue(900103L in result.stations)
    }

    @Test
    fun onlyStationOfItsProvinceIsKept() {
        val result = parse(*HEADER, misplacedRows[0], "1|op|Q8|Stradale|X|VIA X|NAPOLI|NA|43.79|7.62")
        assertTrue(1L in result.stations)
    }

    private companion object {
        val HEADER = arrayOf(
            "Estrazione del 2026-09-30",
            "idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine",
        )
    }
}
