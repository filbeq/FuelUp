"""Is the published data up to date with MIMIT?

Two commands for the publish workflow (GitHub may start scheduled runs hours
late, or drop them):

    python -m fuel_pipeline.freshness needs-publish
        "check" job: does MIMIT have a newer date than the published data?
        Writes needed=true/false, stale=true/false and published=<date> to
        $GITHUB_OUTPUT.
        MIMIT unreachable (it has answered only Italian IPs since 16 Oct
        2026): not needed, with a warning. Any other doubt (published data
        unreadable, MIMIT answering without a date): publish, the build
        decides.

    python -m fuel_pipeline.freshness check [--published-date D] [--now T]
        "alarm" job: exits 1 when the published data is behind. MIMIT
        unreachable: a warning, never a failure. The options fake the
        published date or the current time (dry runs).

Only the first ~200 bytes of the MIMIT prices file are read (its
"Estrazione del YYYY-MM-DD" line).
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import UTC, date, datetime, time, timedelta
from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

# Verified on the official dataset page on 2026-10-01:
# https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti
PRICES_URL = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
USER_AGENT = "fuel-up-freshness/0.2 (open data reuse; Python urllib)"
# First line of every MIMIT file.
EXTRACTION_RE = re.compile(r"^Estrazione del (\d{4}-\d{2}-\d{2})\s*$")

PUBLISHED_META_URL = "https://filbeq.github.io/FuelUp/meta.json"
ITALY = ZoneInfo("Europe/Rome")

# MIMIT has had newer prices for this long and we still publish the old ones:
# the morning publish runs failed or were missed.
MAX_LAG = timedelta(hours=3)
# From this time (Italy) yesterday's prices must be online. MIMIT publishes
# them around 08:45 Italian time, so this leaves a margin of several runs.
# The publish workflow has a run scheduled after it (13:10 UTC).
DEADLINE = time(14, 0)

TIMEOUT_SECONDS = 30


@dataclass(frozen=True)
class Sources:
    """What we know; None where a source could not be read."""

    published_date: date | None
    mimit_date: date | None
    mimit_modified: datetime | None  # Last-Modified of the MIMIT prices file (UTC)
    # False when MIMIT gave no answer at all (connection failed or timed out).
    # True with mimit_date None: MIMIT answered, but without a readable date.
    mimit_reachable: bool = True


def needs_publish(sources: Sources) -> bool:
    """Whether the publish workflow should build and deploy."""
    if not sources.mimit_reachable:
        return False
    if sources.published_date is None or sources.mimit_date is None:
        return True
    return sources.mimit_date > sources.published_date


def stale(sources: Sources, now: datetime) -> bool:
    """Whether publishing has failed for over a day.

    Until the morning publication the newest published data is from the day
    before yesterday, so only older data counts. The publish workflow then
    tries to build once a day instead of on every run (one failure email a
    day), and announces it when publishing works again.
    """
    oldest_normal = now.astimezone(ITALY).date() - timedelta(days=2)
    return sources.published_date is None or sources.published_date < oldest_normal


def problems(sources: Sources, now: datetime) -> list[str]:
    """Why the published data is behind (empty list: all good).

    Nothing is wrong when MIMIT can't be reached: it blocks GitHub, and phones
    in Italy download from MIMIT directly (the caller warns instead).
    """
    if not sources.mimit_reachable:
        return []
    found = []
    published = sources.published_date
    if (
        published is not None
        and sources.mimit_date is not None
        and sources.mimit_date > published
        and sources.mimit_modified is not None
        and now - sources.mimit_modified > MAX_LAG
    ):
        hours = (now - sources.mimit_modified).total_seconds() / 3600
        found.append(
            f"Publishing is behind MIMIT: MIMIT has had the prices of {sources.mimit_date} "
            f"for {hours:.1f} h (since {sources.mimit_modified:%Y-%m-%d %H:%M} UTC), "
            f"but the published data is still from {published}."
        )
    local = now.astimezone(ITALY)
    expected = local.date() - timedelta(days=1)
    if local.time() >= DEADLINE and (published is None or published < expected):
        what = "could not be read" if published is None else f"is from {published}"
        found.append(
            f"Published data is stale: by {DEADLINE:%H:%M} Italian time it should hold the "
            f"prices of {expected}, but it {what}."
        )
    return found


def read_published_date(url: str = PUBLISHED_META_URL) -> date | None:
    try:
        with urllib.request.urlopen(_request(url), timeout=TIMEOUT_SECONDS) as response:
            return date.fromisoformat(json.load(response)["dataDate"])
    except (OSError, ValueError, KeyError) as error:
        _warn(f"published meta.json not readable: {error}")
        return None


def read_mimit(url: str = PRICES_URL) -> tuple[date | None, datetime | None, bool]:
    """(extraction date, Last-Modified, reachable)."""
    try:
        with urllib.request.urlopen(_request(url), timeout=TIMEOUT_SECONDS) as response:
            lines = response.read(200).decode("utf-8", errors="replace").splitlines()
            modified = response.headers.get("Last-Modified")
    except urllib.error.HTTPError as error:
        _warn(f"MIMIT prices file not readable: {error}")
        return None, None, True
    except OSError as error:
        _warn(f"MIMIT not reachable: {error}")
        return None, None, False
    match = EXTRACTION_RE.match(lines[0].strip()) if lines else None
    if match is None:
        _warn(f"MIMIT prices file has no extraction date: {lines[:1]!r}")
        return None, None, True
    return date.fromisoformat(match.group(1)), parsedate_to_datetime(modified).astimezone(UTC) if modified else None, True


def _request(url: str) -> urllib.request.Request:
    return urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Cache-Control": "no-cache"})


def _warn(message: str) -> None:
    # "::warning::" shows up as an annotation on the GitHub run page.
    prefix = "::warning::" if os.environ.get("GITHUB_ACTIONS") else "warning: "
    print(prefix + message)


def _summary(lines: list[str]) -> None:
    """Also write to the run's summary page on GitHub."""
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as summary:
            summary.write("\n".join(lines) + "\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m fuel_pipeline.freshness", description=__doc__.split("\n\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("needs-publish", help="check job: is there newer MIMIT data? is the published data stale?")
    check = sub.add_parser("check", help="alarm job: fail if the published data is behind")
    check.add_argument("--published-date", type=date.fromisoformat, help="pretend the published data is from this date")
    check.add_argument("--now", type=datetime.fromisoformat, help="pretend it is this time (ISO, with offset)")
    args = parser.parse_args(argv)

    mimit_date, mimit_modified, reachable = read_mimit()
    published = getattr(args, "published_date", None) or read_published_date()
    sources = Sources(published, mimit_date, mimit_modified, reachable)
    modified = f"{mimit_modified:%Y-%m-%d %H:%M} UTC" if mimit_modified else "unknown"
    mimit = f"{mimit_date} (file changed {modified})" if reachable else "not reachable"
    status = f"published data: {published}; MIMIT: {mimit}"
    print(status)
    blocked = f"MIMIT blocks GitHub: the published fallback copy is from {published}; phones in Italy download directly"

    if args.command == "needs-publish":
        needed = needs_publish(sources)
        is_stale = stale(sources, datetime.now(UTC))
        if not reachable:
            _warn(blocked)
            _summary([f"⚠️ {blocked}."])
        print("publish needed" if needed else "nothing to publish: skipping the build")
        output = os.environ.get("GITHUB_OUTPUT")
        if output:
            with open(output, "a", encoding="utf-8") as out:
                out.write(f"needed={'true' if needed else 'false'}\n")
                out.write(f"stale={'true' if is_stale else 'false'}\n")
                out.write(f"published={published or ''}\n")
        return 0

    now = args.now.astimezone(UTC) if args.now else datetime.now(UTC)
    found = problems(sources, now)
    if args.published_date or args.now:
        print(f"DRY RUN with faked values (published date {published}, now {now:%Y-%m-%d %H:%M} UTC)")
    if not reachable:
        _warn(blocked)
        _summary([f"⚠️ {blocked}.", "", "Not a failure: nothing can be checked from here until MIMIT answers GitHub again."])
        return 0
    if not found:
        print("OK: the published data is up to date")
        _summary([f"✅ Published data is up to date ({status})"])
        return 0
    for problem in found:
        print(("::error::" if os.environ.get("GITHUB_ACTIONS") else "ERROR: ") + problem)
    _summary(["❌ **Published data is behind**", "", *[f"- {p}" for p in found], "", f"({status})",
              "", "Fix: Actions tab → *Publish fuel data* → *Run workflow*."])
    return 1


if __name__ == "__main__":
    sys.exit(main())
