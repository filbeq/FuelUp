# FuelUp

**Italian fuel prices on a map.** FuelUp is a free Android app that shows the
fuel prices of petrol stations across Italy, using the open data published by
the Italian Ministry of Enterprises and Made in Italy (MIMIT).

> **Unofficial app.** FuelUp is an independent project. It is not affiliated with
> or endorsed by MIMIT.

## Status

[![Published data](https://github.com/filbeq/FuelUp/actions/workflows/check-data.yml/badge.svg)](https://github.com/filbeq/FuelUp/actions/workflows/check-data.yml)
(green: the latest prices are online)

Early development, not yet on the Play Store. Today the app shows every
station in Italy on the map (grouped when zoomed out) and works offline with
the last downloaded data. Pick your fuel (and self or served) and each station
is marked cheap, average or expensive compared with the stations around it, with
prices shown on the map. Tap a station to see all its prices, when each was last
reported, and to start navigation in your favourite maps app. Tap the
location button to see the cheapest (or nearest) stations within 5, 10 or 20 km
of you. In Settings you
can choose a light or dark theme and map, and the language (Italiano or
English).

Planned features:

- every station in Italy on the map, with its prices
- prices per fuel (petrol, diesel, LPG, methane, …), self-service and served
- colours from cheapest to most expensive for the fuel you choose
- works offline with the last downloaded prices
- English and Italian

## About the prices

Prices are those **in force at 08:00 on the data date** shown in the app, as
reported by the stations to the Ministry. The data is updated once a day and is
**not real time**: always check the price at the pump.

## Privacy

FuelUp has no accounts, no ads, no analytics and no tracking. It collects no
personal data. It downloads the daily price file and the map tiles; nothing
else leaves your phone.

Your location is used only when you tap the location button, only while the
app is open, and only on your phone: it is never saved or sent anywhere.
Approximate location is enough (on Android 12 and newer you can choose it in
the permission dialog). Without the permission, everything else works.

## Credits

- **Price data:** Fonte dati: Ministero delle Imprese e del Made in Italy —
  Osservaprezzi Carburanti
  ([dataset](https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti)),
  [Italian Open Data License 2.0 (IODL 2.0)](https://www.dati.gov.it/content/italian-open-data-license-v20).
- **Map:** [OpenFreeMap](https://openfreemap.org),
  [© OpenMapTiles](https://www.openmaptiles.org),
  [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright).
- **Map engine:** [MapLibre Native](https://maplibre.org).

## Development

Building the app, running the data pipeline and publishing the data are
described in [DEVELOPMENT.md](DEVELOPMENT.md).
