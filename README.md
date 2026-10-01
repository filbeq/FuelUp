# Fuel Up — Italian fuel prices on a map

Android app that shows fuel station prices across Italy on a map, built on the
open data published by the Italian Ministry of Enterprises and Made in Italy (MIMIT).

> **This is not an official app.** It is not affiliated with or endorsed by MIMIT.
> Prices are those in force at 08:00 on the data date shown in the app — they are
> **not real time**.

## Data source and license

Fonte dati: Ministero delle Imprese e del Made in Italy — Osservaprezzi Carburanti
([dataset page](https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti)),
released under the [Italian Open Data License 2.0 (IODL 2.0)](https://www.dati.gov.it/content/italian-open-data-license-v20).

## Repository layout

```
pipeline/   Python data pipeline: download, clean and publish the price data as JSON
android/    Android app (Kotlin + Jetpack Compose) — not started yet
.github/    GitHub Actions workflows — not started yet
```

## Pipeline

Requires Python 3.12, no third-party packages. See [pipeline/README.md](pipeline/README.md).

## Android app

_To do (roadmap step 3)._

## Automated publishing

_To do (roadmap step 2)._
