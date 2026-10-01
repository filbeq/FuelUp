"""Price parser tests.

fixtures/prices.csv holds real rows from the 2026-09-30 MIMIT file (ids below 900000)
plus synthetic rows (ids 9000xx, X1, and the last three 54386 rows) for quirks not
present in that day's data.
"""

import unittest
from datetime import UTC, date, datetime
from pathlib import Path

from fuel_pipeline.csvfile import FormatError
from fuel_pipeline.fuels import FuelType
from fuel_pipeline.prices import load_prices, parse_prices

FIXTURE = Path(__file__).parent / "fixtures" / "prices.csv"
COLUMNS = "idImpianto|descCarburante|prezzo|isSelf|dtComu"


def by_key(prices):
    return {(p.station_id, p.fuel_name, p.is_self): p for p in prices}


class PriceFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.result = load_prices(FIXTURE)
        cls.prices = by_key(cls.result.prices)
        cls.report = cls.result.report

    def test_extraction_date(self):
        self.assertEqual(self.result.extraction_date, date(2026, 9, 30))

    def test_normal_row(self):
        p = self.prices[(3464, "Benzina", True)]
        self.assertEqual(p.price_milli, 2049)
        self.assertEqual(p.fuel.type, FuelType.PETROL)
        self.assertTrue(p.fuel.std)
        # 20:30:08 Italian summer time is 18:30:08 UTC
        self.assertEqual(p.updated.astimezone(UTC), datetime(2026, 9, 29, 18, 30, 8, tzinfo=UTC))
        self.assertFalse(self.prices[(3464, "Benzina", False)].is_self)

    def test_kept_count(self):
        self.assertEqual(len(self.result.prices), 18)

    def test_stale_prices_dropped(self):
        self.assertNotIn((40820, "Metano", False), self.prices)  # 12/09, 18 days old
        self.assertNotIn((6422, "Hi-Q Diesel", True), self.prices)  # 2013
        self.assertNotIn((13829, "Benzina", False), self.prices)  # 8.5 days old
        self.assertIn((10061, "Benzina", False), self.prices)  # 7.9 days old: kept
        self.assertIn((40820, "GPL", False), self.prices)

    def test_minutes_after_eight_are_kept(self):
        self.assertIn((5087, "Benzina", False), self.prices)

    def test_suspicious_but_plausible_price_is_kept(self):
        self.assertEqual(self.prices[(31493, "Benzina", True)].price_milli, 1199)

    def test_special_fuels_keep_their_name(self):
        self.assertEqual(self.prices[(7566, "Gasolio Artico", True)].fuel.type, FuelType.DIESEL)
        self.assertEqual(self.prices[(4960, "Gasolio artico", True)].fuel.type, FuelType.DIESEL)
        self.assertFalse(self.prices[(3493, "HVOlution", False)].fuel.std)
        self.assertEqual(self.prices[(4485, "L-GNC", False)].fuel.type, FuelType.CNG)
        self.assertEqual(self.prices[(4036, "GNL", True)].fuel.type, FuelType.LNG)

    def test_unknown_fuel_is_kept_as_other_and_noted(self):
        p = self.prices[(54386, "Etanolo E85", False)]
        self.assertEqual(p.fuel.type, FuelType.OTHER)
        self.assertEqual(self.report.notes["unknown fuel name 'Etanolo E85' -> OTHER"], 1)

    def test_duplicates_keep_newest(self):
        self.assertEqual(self.prices[(54386, "Benzina", True)].price_milli, 2146)
        self.assertEqual(self.prices[(54386, "Gasolio", True)].price_milli, 2299)

    def test_drop_reasons(self):
        self.assertEqual(
            dict(self.report.dropped),
            {
                "stale price (older than 8 days)": 4,
                "price out of plausible range": 4,
                "update date in the future": 1,
                "wrong number of fields": 1,
                "invalid price": 1,
                "invalid update date": 1,
                "invalid self/served flag": 1,
                "missing fuel name": 1,
                "invalid station id": 1,
                "duplicate price, older one dropped": 2,
            },
        )
        self.assertEqual(
            self.report.samples("price out of plausible range"),
            ["57122 Metano 0.100", "55136 Blue Super 0.100", "59612 HiQ Perform+ 0.113", "50753 Metano 4.999"],
        )
        self.assertEqual(self.report.rows_read, 35)


class PriceEdgeCaseTest(unittest.TestCase):
    def test_winter_time_conversion(self):
        result = parse_prices(
            ["Estrazione del 2026-01-15", COLUMNS, "1|Benzina|1.800|1|15/01/2026 07:00:00"]
        )
        updated = result.prices[0].updated.astimezone(UTC)
        self.assertEqual(updated, datetime(2026, 1, 15, 6, 0, tzinfo=UTC))

    def test_dst_change_inside_age_window(self):
        # Clocks went back on 25/10/2026; the 8-day window spans the change.
        result = parse_prices(
            [
                "Estrazione del 2026-10-30",
                COLUMNS,
                "1|Benzina|1.800|1|22/10/2026 09:30:00",  # 7 days 23.5 h before: kept
                "2|Benzina|1.800|1|22/10/2026 08:30:00",  # 8 days 0.5 h before: dropped
            ]
        )
        self.assertEqual([p.station_id for p in result.prices], [1])

    def test_nan_price_is_invalid(self):
        result = parse_prices(["Estrazione del 2026-09-30", COLUMNS, "1|Benzina|NaN|1|29/09/2026 07:00:00"])
        self.assertEqual(result.report.dropped["invalid price"], 1)

    def test_changed_columns(self):
        with self.assertRaises(FormatError):
            parse_prices(["Estrazione del 2026-09-30", "idImpianto|carburante|prezzo|isSelf|dtComu"])


if __name__ == "__main__":
    unittest.main()
