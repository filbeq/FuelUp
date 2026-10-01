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
.github/    GitHub Actions workflow that publishes the data
```

## Pipeline

Requires Python 3.12, no third-party packages.

```sh
cd pipeline
python3 -m unittest            # run the tests
python3 -m fuel_pipeline       # download MIMIT data, write out/stations.json + out/meta.json
```

Details, cleaning rules and the JSON format: [pipeline/README.md](pipeline/README.md).

## Android app

_To do (roadmap step 3)._

## Automated publishing

The workflow [`.github/workflows/publish-data.yml`](.github/workflows/publish-data.yml)
runs the tests and the pipeline, then publishes the result to GitHub Pages. It runs
twice a day (07:30 and 15:30 UTC; MIMIT publishes around 06:45 UTC) and on demand.
If the tests or the pipeline fail, nothing is deployed and the previous data stays
online. The data is never committed to the repository.

Published files:

| File | URL |
|---|---|
| Station data | https://filbeq.github.io/FuelUp/stations.json |
| Metadata (data date, counts, checksum) | https://filbeq.github.io/FuelUp/meta.json |

### One-time setup

In the repository: **Settings → Pages → Build and deployment → Source:
"GitHub Actions"**.

### Run it manually

1. Open the repository's **Actions** tab.
2. Select **Publish fuel data** in the list on the left.
3. Click **Run workflow** (branch `main`) → **Run workflow**.

The run takes about a minute. When it is green, the `deploy` job shows the
published URL. To check what is online:

```sh
curl -s https://filbeq.github.io/FuelUp/meta.json
```

`dataDate` there is the date the prices refer to (08:00 Italian time).

Note: GitHub disables scheduled workflows in public repositories after 60 days
without commits; re-enable it from the Actions tab if that happens.
