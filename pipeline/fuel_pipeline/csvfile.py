"""Shared helpers for the two MIMIT files: header checks and text cleanup.

Both files start with an extraction-date line ("Estrazione del 2026-09-30") followed
by a line of column names. Columns are separated by "|", with no quoting, so we split
lines by hand instead of using the csv module (which would choke on stray quotes).
"""

import re
from datetime import date
from pathlib import Path

SEPARATOR = "|"
EXTRACTION_RE = re.compile(r"^Estrazione del (\d{4}-\d{2}-\d{2})\s*$")


class FormatError(Exception):
    """The file does not look like the MIMIT format we know: stop, don't guess."""


def read_lines(path: Path) -> list[str]:
    # utf-8-sig tolerates a byte-order mark should MIMIT ever add one.
    return path.read_text(encoding="utf-8-sig").splitlines()


def parse_header(lines: list[str], expected_columns: list[str]) -> date:
    """Validate the two header lines and return the extraction date."""
    if len(lines) < 2:
        raise FormatError("file has fewer than two header lines")
    match = EXTRACTION_RE.match(lines[0].strip())
    if not match:
        raise FormatError(f"unexpected first line: {lines[0][:80]!r}")
    columns = [c.strip() for c in lines[1].split(SEPARATOR)]
    if columns != expected_columns:
        raise FormatError(f"unexpected columns: {columns}")
    return date.fromisoformat(match.group(1))


def clean_text(value: str) -> str:
    """Turn tabs and runs of whitespace into single spaces, and trim."""
    return re.sub(r"\s+", " ", value).strip()
