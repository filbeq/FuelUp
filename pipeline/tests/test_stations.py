"""Station parser tests.

fixtures/stations.csv holds real rows from the 2026-09-30 MIMIT file, picked for their
quirks, plus synthetic rows (ids 9000xx, X900006 and a second 59183) for quirks not
present in that day's data.
"""

import unittest
from datetime import date
from pathlib import Path

from fuel_pipeline.csvfile import FormatError
from fuel_pipeline.stations import load_stations, parse_stations

FIXTURE = Path(__file__).parent / "fixtures" / "stations.csv"
HEADER = [
    "Estrazione del 2026-09-30",
    "idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine",
]


class StationFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.result = load_stations(FIXTURE)
        cls.stations = cls.result.stations
        cls.report = cls.result.report

    def test_extraction_date(self):
        self.assertEqual(self.result.extraction_date, date(2026, 9, 30))

    def test_kept_stations(self):
        self.assertEqual(
            sorted(self.stations),
            [3464, 34658, 40820, 41694, 44351, 45790, 49891, 51468, 54386, 59183, 60534, 63381],
        )

    def test_normal_row(self):
        s = self.stations[3464]
        self.assertEqual(s.name, "PO EST")
        self.assertEqual(s.brand, "Api-Ip")
        self.assertTrue(s.motorway)
        self.assertEqual(s.address, "Autostrada A13 BOLOGNA-PADOVA, Km. 43+400, dir. Nord - 44100")
        self.assertEqual(s.municipality, "FERRARA")
        self.assertEqual(s.province, "FE")
        self.assertEqual((s.lat, s.lon), (44.88012, 11.57083))  # rounded to 5 decimals

    def test_whitespace_is_collapsed_and_trimmed(self):
        self.assertEqual(self.stations[59183].address, "SS.189 KM. 64+649 - C.DA SAN MICHELE S.N.C")
        self.assertFalse(self.stations[59183].motorway)

    def test_tabs_inside_fields(self):
        self.assertEqual(self.stations[63381].name, "19834 MONTALLEGRO")
        self.assertEqual(self.stations[41694].address, "SS 10 KM.103 15040")

    def test_extra_separator_in_name_is_recovered(self):
        s = self.stations[40820]
        self.assertEqual(s.name, "STOIL SIMPLE")
        self.assertEqual(s.brand, "Pompe Bianche")
        self.assertEqual(s.address, "STR. PROV.LE 82 SPINETTA SALE 15122")
        self.assertEqual(s.municipality, "ALESSANDRIA")

    def test_extra_separators_in_operator_and_name_are_recovered(self):
        s = self.stations[54386]
        self.assertEqual(s.name, "PRADELLI - MONTEOMBRARO")
        self.assertEqual(s.brand, "Pompe Bianche")
        self.assertEqual(s.address, "Via dei Martiri 255 41059")
        self.assertEqual((s.lat, s.lon), (44.37912, 11.00468))

    def test_real_separator_surrounded_by_spaces(self):
        s = self.stations[34658]
        self.assertEqual(s.name, "SOGEDI - PV ENI MOLA")
        self.assertEqual(s.address, "SP 111 MOLA - RUTIGLIANO KM 1+235 70042")

    def test_coordinates_with_surrounding_spaces(self):
        self.assertEqual(self.stations[44351].lon, 13.37302)
        self.assertEqual(self.stations[45790].lon, 15.01481)

    def test_utf8_text(self):
        self.assertEqual(self.stations[60534].municipality, "CARRÙ")
        self.assertEqual(self.stations[49891].name, "56297 – San Paolo d’Argon")

    def test_duplicate_id_keeps_first(self):
        self.assertEqual(self.stations[59183].name, "19829 AGRIGENTO")

    def test_drop_reasons(self):
        self.assertEqual(
            dict(self.report.dropped),
            {
                "missing coordinates": 1,
                "zero coordinates": 1,
                "coordinates outside Italy": 1,
                "invalid coordinates": 1,
                "duplicate station id": 1,
                "wrong number of fields": 2,
                "invalid station id": 1,
            },
        )
        self.assertEqual(self.report.samples("missing coordinates"), ["60502"])
        self.assertEqual(self.report.samples("wrong number of fields"), ["900004", "900005"])
        self.assertEqual(self.report.notes["extra separators recovered"], 2)
        self.assertEqual(self.report.rows_read, 20)
        self.assertEqual(self.report.dropped_total, 8)


class StationHeaderTest(unittest.TestCase):
    def test_missing_extraction_line(self):
        with self.assertRaises(FormatError):
            parse_stations(HEADER[1:] + ["1|a|b|Stradale|n|a|c|p|41.9|12.5"])

    def test_changed_columns(self):
        with self.assertRaises(FormatError):
            parse_stations([HEADER[0], HEADER[1].replace("Bandiera", "Marchio")])

    def test_empty_name_is_kept(self):
        result = parse_stations(HEADER + ["1|op|Q8|Stradale||VIA X|ROMA|RM|41.9|12.5"])
        self.assertEqual(result.stations[1].name, "")
        self.assertEqual(result.report.notes["empty station name"], 1)


if __name__ == "__main__":
    unittest.main()
