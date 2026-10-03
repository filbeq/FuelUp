package io.github.filbeq.fuelup.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Real municipality names as MIMIT writes them, with their official (ISTAT) spelling. */
class PlaceNamesTest {
    private fun check(raw: String, expected: String) = assertEquals(expected, PlaceNames.municipality(raw))

    @Test
    fun plainWordsAreCapitalised() {
        check("PISA", "Pisa")
        check("SAN GIULIANO TERME", "San Giuliano Terme")
        check("LU", "Lu")
    }

    @Test
    fun linkingWordsStayLowercase() {
        check("CASTIGLIONE DELLA PESCAIA", "Castiglione della Pescaia")
        check("SANT'ANTONINO DI SUSA", "Sant'Antonino di Susa")
        check("GRAVINA IN PUGLIA", "Gravina in Puglia")
        check("SANTA MARIA LA CARITÀ", "Santa Maria la Carità")
        check("BORGOFRANCO SUL PO", "Borgofranco sul Po")
    }

    @Test
    fun aNameStartsWithACapitalEvenOnALinkingWord() {
        check("LA SPEZIA", "La Spezia")
        check("L'AQUILA", "L'Aquila")
    }

    @Test
    fun apostrophes() {
        check("REGGIO NELL'EMILIA", "Reggio nell'Emilia")
        check("FIORENZUOLA D'ARDA", "Fiorenzuola d'Arda")
        check("MAGLIANO DE' MARSI", "Magliano de' Marsi")
        check("CASTELNOVO NE' MONTI", "Castelnovo ne' Monti")
        check("ACI SANT'ANTONIO", "Aci Sant'Antonio")
        check("CITTÀ SANT'ANGELO", "Città Sant'Angelo")
        check("COLLE DI VAL D'ELSA", "Colle di Val d'Elsa")
    }

    @Test
    fun hyphens() {
        check("ANTEY-SAINT-ANDRÉ", "Antey-Saint-André")
        check("SAINT-RHÉMY-EN-BOSSES", "Saint-Rhémy-en-Bosses")
        check("GRESSONEY-LA-TRINITÉ", "Gressoney-La-Trinité")
        check("TRENTOLA-DUCENTA", "Trentola-Ducenta")
    }

    @Test
    fun mixedCaseInputFollowsTheSameRule() {
        check("San Lazzaro Di Savena", "San Lazzaro di Savena")
        check("Morgex", "Morgex")
    }

    @Test
    fun knownMisses() {
        // Listed in DEVELOPMENT.md: the official spelling breaks the rule.
        check("BOFFALORA SOPRA TICINO", "Boffalora Sopra Ticino") // official: "sopra"
        check("MORRA DE SANCTIS", "Morra de Sanctis") // official: "De Sanctis"
    }
}
