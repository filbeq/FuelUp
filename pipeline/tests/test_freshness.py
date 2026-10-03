"""Freshness rules for the publish and checker workflows. No network."""

import io
import unittest
from contextlib import redirect_stdout
from datetime import UTC, date, datetime
from unittest.mock import patch

from fuel_pipeline.freshness import Sources, main, needs_publish, problems

# MIMIT published the prices of 2 Oct at 06:45 UTC on 3 Oct (CEST, UTC+2).
MIMIT_MODIFIED = datetime(2026, 10, 3, 6, 45, tzinfo=UTC)


def sources(published, mimit=date(2026, 10, 2), modified=MIMIT_MODIFIED):
    return Sources(published, mimit, modified)


class NeedsPublishTest(unittest.TestCase):
    def test_newer_mimit_data_is_published(self):
        self.assertTrue(needs_publish(sources(date(2026, 10, 1))))

    def test_same_date_is_skipped(self):
        self.assertFalse(needs_publish(sources(date(2026, 10, 2))))

    def test_unreadable_source_publishes_anyway(self):
        self.assertTrue(needs_publish(sources(None)))
        self.assertTrue(needs_publish(Sources(date(2026, 10, 2), None, None)))


class ProblemsTest(unittest.TestCase):
    def test_up_to_date_is_fine(self):
        self.assertEqual([], problems(sources(date(2026, 10, 2)), datetime(2026, 10, 3, 18, 0, tzinfo=UTC)))

    def test_short_lag_is_tolerated(self):
        # New MIMIT data 2 h ago, before the deadline: the next runs will catch up.
        self.assertEqual([], problems(sources(date(2026, 10, 1)), datetime(2026, 10, 3, 8, 45, tzinfo=UTC)))

    def test_lag_over_three_hours_fails(self):
        found = problems(sources(date(2026, 10, 1)), datetime(2026, 10, 3, 10, 0, tzinfo=UTC))
        self.assertEqual(1, len(found))
        self.assertIn("behind MIMIT", found[0])

    def test_old_data_after_deadline_fails_even_if_mimit_is_late(self):
        # MIMIT itself has not published yesterday's prices: still worth knowing.
        stale = Sources(date(2026, 10, 1), date(2026, 10, 1), datetime(2026, 10, 2, 6, 45, tzinfo=UTC))
        found = problems(stale, datetime(2026, 10, 3, 12, 30, tzinfo=UTC))  # 14:30 in Italy
        self.assertEqual(1, len(found))
        self.assertIn("stale", found[0])

    def test_deadline_follows_italian_time(self):
        stale = Sources(date(2026, 11, 1), None, None)
        # Winter time (CET, UTC+1): 12:30 UTC is 13:30 in Italy, before the deadline...
        self.assertEqual([], problems(stale, datetime(2026, 11, 3, 12, 30, tzinfo=UTC)))
        # ...and 13:30 UTC is 14:30, after it.
        self.assertEqual(1, len(problems(stale, datetime(2026, 11, 3, 13, 30, tzinfo=UTC))))

    def test_unreadable_published_data_fails_after_deadline(self):
        found = problems(sources(None), datetime(2026, 10, 3, 13, 0, tzinfo=UTC))
        self.assertTrue(any("could not be read" in p for p in found))


class DryRunTest(unittest.TestCase):
    @patch("fuel_pipeline.freshness.read_mimit", return_value=(date(2026, 10, 2), MIMIT_MODIFIED))
    def test_faked_published_date_fails(self, _read_mimit):
        report = io.StringIO()
        with redirect_stdout(report):
            code = main(["check", "--published-date", "2026-09-30", "--now", "2026-10-03T15:00:00+02:00"])
        self.assertEqual(1, code)
        self.assertIn("DRY RUN", report.getvalue())
        self.assertIn("behind MIMIT", report.getvalue())


if __name__ == "__main__":
    unittest.main()
