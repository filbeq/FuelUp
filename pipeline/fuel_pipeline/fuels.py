"""Map MIMIT fuel names (``descCarburante``) to a small fixed set of fuel types.

MIMIT publishes the four "standard" fuels (Benzina, Gasolio, GPL, Metano, plus the
LNG variants GNL and L-GNC) and many branded or "speciale" products whose names are
chosen by each brand. Every name is mapped to a ``FuelType`` so the app can filter by
type; the original name is kept in the output for display.
"""

import re
from dataclasses import dataclass
from enum import StrEnum


class FuelType(StrEnum):
    PETROL = "PETROL"
    DIESEL = "DIESEL"
    LPG = "LPG"
    CNG = "CNG"
    LNG = "LNG"
    OTHER = "OTHER"


class Unit(StrEnum):
    LITRE = "L"
    KILOGRAM = "KG"


@dataclass(frozen=True)
class FuelInfo:
    type: FuelType
    unit: Unit
    # True for the official standard fuels, False for branded/special products.
    std: bool
    # False when the name was not in the known table (worth a look in the logs).
    known: bool


# Names exactly as seen in the MIMIT data, keyed by lookup key (see _key).
_STANDARD = {
    "benzina": FuelType.PETROL,
    "gasolio": FuelType.DIESEL,
    "gpl": FuelType.LPG,
    "metano": FuelType.CNG,
    "l-gnc": FuelType.CNG,  # LNG stored on site, dispensed as compressed gas to cars
    "gnl": FuelType.LNG,
}

_SPECIAL = {
    # Petrol
    "benzina speciale": FuelType.PETROL,
    "benzina speciale 98 ottani": FuelType.PETROL,
    "benzina 100 ottani": FuelType.PETROL,
    "benzina 102 ottani": FuelType.PETROL,
    "benzina plus 98": FuelType.PETROL,
    "benzina wr 100": FuelType.PETROL,
    "benzina shell v power": FuelType.PETROL,
    "benzina energy 98 ottani": FuelType.PETROL,
    "blue super": FuelType.PETROL,
    "hiq perform+": FuelType.PETROL,
    "hiq perform b100 ottani": FuelType.PETROL,
    "v-power": FuelType.PETROL,
    "verde speciale": FuelType.PETROL,
    "f101": FuelType.PETROL,
    "f-101": FuelType.PETROL,
    # Diesel, including HVO (renewable diesel)
    "gasolio speciale": FuelType.DIESEL,
    "gasolio premium": FuelType.DIESEL,
    "gasolio artico": FuelType.DIESEL,
    "gasolio artico igloo": FuelType.DIESEL,
    "gasolio alpino": FuelType.DIESEL,
    "gasolio gelo": FuelType.DIESEL,
    "gasolio oro diesel": FuelType.DIESEL,
    "gasolio energy d": FuelType.DIESEL,
    "gasolio ecoplus": FuelType.DIESEL,
    "gasolio prestazionale": FuelType.DIESEL,
    "gasolio plus": FuelType.DIESEL,
    "gasolio bio hvo": FuelType.DIESEL,
    "gasolio hvo": FuelType.DIESEL,
    "blue diesel": FuelType.DIESEL,
    "blu diesel alpino": FuelType.DIESEL,
    "supreme diesel": FuelType.DIESEL,
    "hi-q diesel": FuelType.DIESEL,
    "diesel shell v power": FuelType.DIESEL,
    "v-power diesel": FuelType.DIESEL,
    "dieselmax": FuelType.DIESEL,
    "s-diesel": FuelType.DIESEL,
    "e-diesel": FuelType.DIESEL,
    "gp diesel": FuelType.DIESEL,
    "excellium diesel": FuelType.DIESEL,
    "hvo": FuelType.DIESEL,
    "hvo100": FuelType.DIESEL,
    "hvolution": FuelType.DIESEL,
    "hvovolution": FuelType.DIESEL,
    "hvo future": FuelType.DIESEL,
    "hvo eco diesel": FuelType.DIESEL,
    "hvo energy diesel": FuelType.DIESEL,
    "diesel hvo": FuelType.DIESEL,
    "diesel hvo energy": FuelType.DIESEL,
    "rehvo": FuelType.DIESEL,
    "bchvo": FuelType.DIESEL,
}

# Fallback for names never seen before. Order matters: "V-Power Diesel" must be
# caught by the diesel rule before the petrol one.
_KEYWORDS = [
    (FuelType.LNG, ("gnl", "lng")),
    (FuelType.CNG, ("gnc", "cng", "metano")),
    (FuelType.LPG, ("gpl", "lpg")),
    (FuelType.DIESEL, ("diesel", "gasolio", "hvo")),
    (FuelType.PETROL, ("benzina", "super", "verde", "ottani", "v-power", "perform")),
]


def _key(name: str) -> str:
    return re.sub(r"\s+", " ", name).strip().casefold()


def _unit(fuel_type: FuelType) -> Unit:
    # MIMIT prices methane per kg; LNG is sold per kg as well.
    return Unit.KILOGRAM if fuel_type in (FuelType.CNG, FuelType.LNG) else Unit.LITRE


def classify(name: str) -> FuelInfo:
    """Return type, unit and flags for a MIMIT fuel name."""
    key = _key(name)
    if key in _STANDARD:
        fuel_type = _STANDARD[key]
        return FuelInfo(fuel_type, _unit(fuel_type), std=True, known=True)
    if key in _SPECIAL:
        fuel_type = _SPECIAL[key]
        return FuelInfo(fuel_type, _unit(fuel_type), std=False, known=True)
    for fuel_type, words in _KEYWORDS:
        if any(word in key for word in words):
            return FuelInfo(fuel_type, _unit(fuel_type), std=False, known=False)
    return FuelInfo(FuelType.OTHER, Unit.LITRE, std=False, known=False)
