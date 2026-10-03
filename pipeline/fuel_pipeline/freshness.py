"""Is the published data up to date with MIMIT?

Two commands for the GitHub workflows (GitHub may start scheduled runs hours
late, or drop them):

    python -m fuel_pipeline.freshness needs-publish
        Publish workflow: does MIMIT have a newer date than the published
        data? Writes needed=true/false to $GITHUB_OUTPUT. When in doubt
        (a source unreachable) the answer is "publish".

    python -m fuel_pipeline.freshness check [--published-date D] [--now T]
        Checker workflow: exits 1 when the published data is behind. The
        options fake the published date or the current time (dry runs).

Only the first ~200 bytes of the MIMIT prices file are read (its
"Estrazione del YYYY-MM-DD" line).
"""

import argparse
import json
import os
import sys
import urllib.request
from dataclasses import dataclass
from datetime import UTC, date, datetime, time, timedelta
from email.utils import parsedate_to_datetime
from zoneinfo import ZoneInfo

from fuel_pipeline.csvfile import EXTRACTION_RE
from fuel_pipeline.download import PRICES_URL, USER_AGENT

PUBLISHED_META_URL = "https://filbeq.github.io/FuelUp/meta.json"
ITALY = ZoneInfo("Europe/Rome")

# MIMIT has had newer prices for this long and we still publish the old ones:
# several publish runs (every 2 h) were missed.
MAX_LAG = timedelta(hours=3)
# From this time (Italy) yesterday's prices must be online. MIMIT publishes
# them around 08:45 Italian time, so this leaves a margin of several runs.
DEADLINE = time(14, 0)

TIMEOUT_SECONDS = 30


@dataclass(frozen=True)
class Sources:
    """What we know; None where a source could not be read."""

    published_date: date | None
    mimit_date: date | None
    mimit_modified: datetime | None  # Last-Modified of the MIMIT prices file (UTC)


def needs_publish(sources: Sources) -> bool:
    """Whether the publish workflow should build and deploy."""
    if sources.published_date is None or sources.mimit_date is None:
        return True
    return sources.mimit_date > sources.published_date


def problems(sources: Sources, now: datetime) -> list[str]:
    """Why the published data is behind (empty list: all good)."""
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


def read_mimit(url: str = PRICES_URL) -> tuple[date | None, datetime | None]:
    try:
        with urllib.request.urlopen(_request(url), timeout=TIMEOUT_SECONDS) as response:
            first_line = response.read(200).decode("utf-8", errors="replace").splitlines()[0]
            modified = response.headers.get("Last-Modified")
    except (OSError, IndexError) as error:
        _warn(f"MIMIT prices file not readable: {error}")
        return None, None
    match = EXTRACTION_RE.match(first_line.strip())
    if match is None:
        _warn(f"MIMIT prices file has no extraction date: {first_line!r}")
        return None, None
    return date.fromisoformat(match.group(1)), parsedate_to_datetime(modified).astimezone(UTC) if modified else None


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
    sub.add_parser("needs-publish", help="publish workflow: is there newer MIMIT data?")
    check = sub.add_parser("check", help="checker workflow: fail if the published data is behind")
    check.add_argument("--published-date", type=date.fromisoformat, help="pretend the published data is from this date")
    check.add_argument("--now", type=datetime.fromisoformat, help="pretend it is this time (ISO, with offset)")
    args = parser.parse_args(argv)

    mimit_date, mimit_modified = read_mimit()
    published = getattr(args, "published_date", None) or read_published_date()
    sources = Sources(published, mimit_date, mimit_modified)
    modified = f"{mimit_modified:%Y-%m-%d %H:%M} UTC" if mimit_modified else "unknown"
    status = f"published data: {published}; MIMIT: {mimit_date} (file changed {modified})"
    print(status)

    if args.command == "needs-publish":
        needed = needs_publish(sources)
        print("publish needed" if needed else "nothing new: skipping the build")
        output = os.environ.get("GITHUB_OUTPUT")
        if output:
            with open(output, "a", encoding="utf-8") as out:
                out.write(f"needed={'true' if needed else 'false'}\n")
        return 0

    now = args.now.astimezone(UTC) if args.now else datetime.now(UTC)
    found = problems(sources, now)
    if args.published_date or args.now:
        print(f"DRY RUN with faked values (published date {published}, now {now:%Y-%m-%d %H:%M} UTC)")
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
