"""Parse the station registry (anagrafica_impianti_attivi.csv)."""

import math
import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from .csvfile import SEPARATOR, clean_text, parse_header, read_lines
from .report import DropReport

COLUMNS = [
    "idImpianto", "Gestore", "Bandiera", "Tipo Impianto", "Nome Impianto",
    "Indirizzo", "Comune", "Provincia", "Latitudine", "Longitudine",
]
STATION_TYPES = ("Stradale", "Autostradale")
MOTORWAY = "Autostradale"

# Generous bounding box around Italy, San Marino and Vatican City.
LAT_RANGE = (35.0, 47.2)
LON_RANGE = (6.5, 18.6)
COORD_DECIMALS = 5  # ~1 m, plenty for a map pin

# Some station names carry a junk " | gestori.prezzibenzina.it" suffix, which also
# breaks the column count (the "|" is the separator).
_JUNK_RE = re.compile(r"\s*\|\s*gestori\.prezzibenzina\.it", re.IGNORECASE)


@dataclass(frozen=True)
class Station:
    id: int
    name: str
    brand: str
    motorway: bool
    address: str
    municipality: str
    province: str
    lat: float
    lon: float


@dataclass
class StationFile:
    extraction_date: date
    stations: dict[int, Station]
    report: DropReport


def load_stations(path: Path) -> StationFile:
    return parse_stations(read_lines(path))


def parse_stations(lines: list[str]) -> StationFile:
    extraction_date = parse_header(lines, COLUMNS)
    report = DropReport("stations")
    stations: dict[int, Station] = {}

    for line in lines[2:]:
        if not line.strip():
            continue
        report.rows_read += 1
        fields = line.split(SEPARATOR)
        ident = clean_text(fields[0])

        if len(fields) != len(COLUMNS):
            fields = _recover_fields(fields)
            if fields is None:
                report.drop("wrong number of fields", ident)
                continue
            report.note("extra separators recovered", ident)

        try:
            station_id = int(ident)
        except ValueError:
            report.drop("invalid station id", ident)
            continue

        coords = _parse_coords(fields[8], fields[9])
        if isinstance(coords, str):
            report.drop(coords, station_id)
            continue

        if station_id in stations:
            report.drop("duplicate station id", station_id)
            continue

        name = clean_text(_JUNK_RE.sub("", fields[4]))
        if not name:
            report.note("empty station name", station_id)
        stations[station_id] = Station(
            id=station_id,
            name=name,
            brand=clean_text(fields[2]),
            motorway=clean_text(fields[3]) == MOTORWAY,
            address=clean_text(fields[5]),
            municipality=clean_text(fields[6]),
            province=clean_text(fields[7]),
            lat=coords[0],
            lon=coords[1],
        )

    return StationFile(extraction_date, stations, report)


def _recover_fields(fields: list[str]) -> list[str] | None:
    """Rebuild a row that has extra "|" inside its text fields.

    The station type (Stradale/Autostradale) is used as an anchor: Gestore sits
    between the id and Bandiera, which is right before the type. The last five
    fields (Indirizzo, Comune, Provincia, lat, lon) never contain "|" in the data
    seen so far, so whatever is left between the type and them is the name.
    """
    if len(fields) < len(COLUMNS):
        return None
    anchors = [i for i, f in enumerate(fields) if f.strip() in STATION_TYPES]
    if len(anchors) != 1:
        return None
    t = anchors[0]
    if t < 3 or t > len(fields) - 7:
        return None
    return [
        fields[0],
        SEPARATOR.join(fields[1 : t - 1]),
        fields[t - 1],
        fields[t],
        SEPARATOR.join(fields[t + 1 : -5]),
        *fields[-5:],
    ]


def _parse_coords(lat_text: str, lon_text: str) -> tuple[float, float] | str:
    """Return (lat, lon), or a drop reason."""
    lat_text, lon_text = lat_text.strip(), lon_text.strip()
    if not lat_text or not lon_text:
        return "missing coordinates"
    try:
        lat, lon = float(lat_text), float(lon_text)
    except ValueError:
        return "invalid coordinates"
    if not (math.isfinite(lat) and math.isfinite(lon)):
        return "invalid coordinates"
    if lat == 0 or lon == 0:
        return "zero coordinates"
    if not (LAT_RANGE[0] <= lat <= LAT_RANGE[1] and LON_RANGE[0] <= lon <= LON_RANGE[1]):
        return "coordinates outside Italy"
    return round(lat, COORD_DECIMALS), round(lon, COORD_DECIMALS)
