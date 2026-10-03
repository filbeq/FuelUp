# FuelUp

**Italian fuel prices on a map.**

[![Latest release](https://img.shields.io/github/v/release/filbeq/FuelUp?include_prereleases&label=release)](https://github.com/filbeq/FuelUp/releases)
[![CI](https://github.com/filbeq/FuelUp/actions/workflows/ci.yml/badge.svg)](https://github.com/filbeq/FuelUp/actions/workflows/ci.yml)
[![Published data](https://github.com/filbeq/FuelUp/actions/workflows/check-data.yml/badge.svg)](https://github.com/filbeq/FuelUp/actions/workflows/check-data.yml)
[![Licence: GPL-3.0](https://img.shields.io/badge/licence-GPL--3.0-blue)](LICENSE)

FuelUp is a free Android app that shows the prices of every fuel station in
Italy, from the open data of the Italian Ministry of Enterprises and Made in
Italy (MIMIT). No account, no ads, no tracking.

> **Unofficial app.** FuelUp is an independent project. It is not affiliated
> with or endorsed by MIMIT.

## Features

- Every station in Italy on the map, with its prices, self-service and served.
- Pick your fuel: each station is marked cheap, average or expensive compared
  with the stations around it (by shape and words too, not by colour alone).
- The cheapest or nearest stations within 5, 10 or 20 km of you.
- Search stations, brands and towns; keep favourite stations.
- Navigation to a station with your own maps app.
- Works offline with the last downloaded prices.
- Light and dark theme, English and Italian.

## Install

FuelUp is in early testing (pre-release) and not yet on the Play Store.
It needs Android 8 or newer.

1. On your phone, open the
   [latest release](https://github.com/filbeq/FuelUp/releases) and download
   `FuelUp-<version>.apk`.
2. Open the downloaded file. Android will ask you to allow your browser (or
   file manager) to **install unknown apps**: tap **Settings**, turn on
   **Allow from this source**, go back and tap **Install**. If Google Play
   Protect warns about an unknown app, choose **Install anyway**.
3. To update, install the new version's APK the same way: your favourites and
   settings are kept.

The future Play Store version will be a separate app with a new name: it will
not update this one.

## About the prices

Prices are those **in force at 08:00 on the data date** shown in the app, as
reported by the stations to the Ministry. The data is updated once a day and is
**not real time**: always check the price at the pump.

## Privacy

FuelUp has no accounts, ads, analytics or tracking, and collects no personal
data. It only downloads the daily price file (from GitHub Pages) and the map
tiles (from OpenFreeMap).

- **Location** is used only when you tap the location button, or at launch if
  you already allowed it; only while the app is open, and only on your phone:
  it is never saved or sent. Approximate location is enough, and everything
  else works without it.
- **Favourite stations** stay on your phone. Android may include them (and only
  them) in your phone's own backup to your Google account; FuelUp itself never
  sends them anywhere.

## Data and credits

- **Fuel prices:** Fonte dati: Ministero delle Imprese e del Made in Italy —
  Osservaprezzi Carburanti
  ([dataset](https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti)),
  [Italian Open Data License 2.0](https://www.dati.gov.it/content/italian-open-data-license-v20).
- **Map:** [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright),
  tiles by [OpenFreeMap](https://openfreemap.org),
  [© OpenMapTiles](https://www.openmaptiles.org).
- **Map engine:** [MapLibre Native](https://maplibre.org).

Third-party components and their licences:
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Licence

Copyright © 2026 filbeq.

FuelUp is free software: you can redistribute and modify it under the terms of
the [GNU General Public License, version 3](LICENSE). It comes with absolutely
no warranty.

## Development

Building the app, the data pipeline and releases are described in
[DEVELOPMENT.md](DEVELOPMENT.md).
