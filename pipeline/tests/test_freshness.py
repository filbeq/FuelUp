"""Freshness rules for the publish workflow (check and alarm jobs). No network."""

import io
import unittest
from contextlib import redirect_stdout
from datetime import UTC, date, datetime
from unittest.mock import patch

from fuel_pipeline.freshness import Sources, main, needs_publish, problems, stale

# MIMIT published the prices of 2 Oct at 06:45 UTC on 3 Oct (CEST, UTC+2).
MIMIT_MODIFIED = datetime(2026, 10, 3, 6, 45, tzinfo=UTC)


def sources(published, mimit=date(2026, 10, 2), modified=MIMIT_MODIFIED):
    return Sources(published, mimit, modified)


# MIMIT gave no answer (it blocks GitHub's IPs).
UNREACHABLE = Sources(date(2026, 10, 1), None, None, mimit_reachable=False)


class NeedsPublishTest(unittest.TestCase):
    def test_newer_mimit_data_is_published(self):
        self.assertTrue(needs_publish(sources(date(2026, 10, 1))))

    def test_same_date_is_skipped(self):
        self.assertFalse(needs_publish(sources(date(2026, 10, 2))))

    def test_unreadable_source_publishes_anyway(self):
        self.assertTrue(needs_publish(sources(None)))
        # MIMIT answered without a date: the build will fail loudly if the format changed.
        self.assertTrue(needs_publish(Sources(date(2026, 10, 2), None, None)))

    def test_unreachable_mimit_is_not_built(self):
        self.assertFalse(needs_publish(UNREACHABLE))


class StaleTest(unittest.TestCase):
    # 3 Oct, 09:00 in Italy. Normal: data of 2 Oct (published) or 1 Oct (not yet).
    NOW = datetime(2026, 10, 3, 7, 0, tzinfo=UTC)

    def test_yesterday_and_the_day_before_are_normal(self):
        self.assertFalse(stale(sources(date(2026, 10, 2)), self.NOW))
        self.assertFalse(stale(sources(date(2026, 10, 1)), self.NOW))

    def test_older_data_is_stale(self):
        self.assertTrue(stale(sources(date(2026, 9, 30)), self.NOW))

    def test_unreadable_published_data_is_stale(self):
        self.assertTrue(stale(sources(None), self.NOW))

    def test_day_follows_italian_time(self):
        # 22:30 UTC on 3 Oct is already 4 Oct in Italy (CEST): 1 Oct is now stale.
        self.assertTrue(stale(sources(date(2026, 10, 1)), datetime(2026, 10, 3, 22, 30, tzinfo=UTC)))
        self.assertFalse(stale(sources(date(2026, 10, 1)), datetime(2026, 10, 3, 21, 30, tzinfo=UTC)))


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

    def test_unreachable_mimit_is_never_a_problem(self):
        # Published data a week old, long after the deadline: still only a warning.
        old = Sources(date(2026, 9, 26), None, None, mimit_reachable=False)
        self.assertEqual([], problems(old, datetime(2026, 10, 3, 18, 0, tzinfo=UTC)))

    def test_unreadable_published_data_fails_after_deadline(self):
        found = problems(sources(None), datetime(2026, 10, 3, 13, 0, tzinfo=UTC))
        self.assertTrue(any("could not be read" in p for p in found))


class DryRunTest(unittest.TestCase):
    @patch("fuel_pipeline.freshness.read_mimit", return_value=(None, None, False))
    def test_blocked_mimit_passes_with_a_warning(self, _read_mimit):
        report = io.StringIO()
        with redirect_stdout(report):
            code = main(["check", "--published-date", "2026-09-30", "--now", "2026-10-03T15:00:00+02:00"])
        self.assertEqual(0, code)
        self.assertIn("MIMIT blocks GitHub", report.getvalue())

    @patch("fuel_pipeline.freshness.read_mimit", return_value=(date(2026, 10, 2), MIMIT_MODIFIED, True))
    def test_faked_published_date_fails(self, _read_mimit):
        report = io.StringIO()
        with redirect_stdout(report):
            code = main(["check", "--published-date", "2026-09-30", "--now", "2026-10-03T15:00:00+02:00"])
        self.assertEqual(1, code)
        self.assertIn("DRY RUN", report.getvalue())
        self.assertIn("behind MIMIT", report.getvalue())


if __name__ == "__main__":
    unittest.main()
