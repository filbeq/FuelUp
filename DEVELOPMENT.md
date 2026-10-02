# Development guide

How the project is organised and how to build, run and publish it. For what the
app is, see the [README](README.md).

```
pipeline/   Python data pipeline: download MIMIT data, clean it, write JSON
android/    Android app (Kotlin, Jetpack Compose, MapLibre)
.github/    GitHub Actions workflow that publishes the data to GitHub Pages
```

## Data pipeline

Requires Python 3.12, no third-party packages. Run from `pipeline/`:

```sh
python3 -m unittest            # run the tests
python3 -m fuel_pipeline       # download MIMIT data, write out/stations.json + out/meta.json

# rebuild from files already downloaded (no network)
python3 -m fuel_pipeline --prices data/prezzo_alle_8.csv \
                         --stations data/anagrafica_impianti_attivi.csv
```

`data/` and `out/` are git-ignored. Cleaning rules, safety checks and the JSON
format are documented in [pipeline/README.md](pipeline/README.md).

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and
pull request:

| Job | Checks |
|---|---|
| Pipeline tests and app contract | `python -m unittest` in `pipeline/`, then `python scripts/make_android_fixture.py --check` |
| App unit tests and lint | `./gradlew test lint` in `android/` (JDK 21; the Gradle wrapper is validated) |

The contract check regenerates the app's test fixture from the pipeline and
fails, with a diff, if it differs from the committed one. When it fails, the
pipeline's output format changed: run `python3 pipeline/scripts/make_android_fixture.py`,
run `./gradlew test` to see whether the app still reads it, and commit both.

Run the same checks locally before pushing:

```sh
(cd pipeline && python3 -m unittest && python3 scripts/make_android_fixture.py --check)
(cd android && ./gradlew test lint)
```

## Data publishing (GitHub Pages)

The workflow [`.github/workflows/publish-data.yml`](.github/workflows/publish-data.yml)
runs the pipeline tests and the same contract check as CI, then the pipeline,
then deploys the output to GitHub Pages. It runs
twice a day (07:30 and 15:30 UTC; MIMIT publishes around 06:45 UTC) and on demand.
If the tests or the pipeline fail, nothing is deployed and the previous data stays
online. The data is never committed to the repository.

| File | URL |
|---|---|
| Station data | https://filbeq.github.io/FuelUp/stations.json |
| Metadata (data date, counts, checksum) | https://filbeq.github.io/FuelUp/meta.json |

**One-time setup:** repository Settings → Pages → Build and deployment →
Source: "GitHub Actions".

**Run it manually:** Actions tab → **Publish fuel data** → **Run workflow**
(branch `main`) → **Run workflow**. When the run is green, check:

```sh
curl -s https://filbeq.github.io/FuelUp/meta.json   # dataDate = date the prices refer to
```

GitHub disables scheduled workflows in public repositories after 60 days without
commits; re-enable it from the Actions tab if that happens.

## Android app

### Toolchain

| Item | Version |
|---|---|
| Gradle (wrapper, checksum-pinned) | 9.8.0 |
| Android Gradle Plugin (Kotlin built in) | 9.4.1 |
| Kotlin / Compose compiler plugin | 2.4.20 |
| Compose BOM (Material 3) | 2026.09.00 |
| MapLibre Native for Android | 13.6.1 |
| SDK levels | min 26, target 36, compile 37 |

Versions live in `android/gradle/libs.versions.toml`. Requirements: Android
Studio (or JDK 17+ and the Android SDK with platform 37 for command-line builds).

### Open in Android Studio

File → Open → select the **`android/`** folder (not the repository root) →
Trust project → wait for the Gradle sync to finish.

### Build from the command line

```sh
cd android
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew lint                 # also checks that every string is translated
./gradlew installDebug         # install on the connected phone
```

No API keys are needed: the map uses OpenFreeMap tiles.

### Run on a phone

1. On the phone: Settings → About phone → tap **Build number** (on Xiaomi:
   **MIUI version**) seven times to unlock Developer options.
2. Developer options → enable **USB debugging**. On Xiaomi also enable
   **Install via USB** (MIUI may ask you to sign in to a Mi account).
3. Connect the phone with a USB cable and tap **Allow** on the "Allow USB
   debugging?" prompt (tick "Always allow from this computer").
4. Check it is visible: `adb devices` should list it as `device`.
5. Android Studio ▶ Run with the phone selected, or `./gradlew installDebug`.

If `adb devices` shows nothing although the cable is connected: unlock the
phone, look for the "Allow USB debugging?" prompt, or turn USB debugging off
and on again.

**Testing languages:** in the app, gear icon → Settings → Language (works on
every Android version). On Android 13+ the same choice also appears in the
system's Settings → Apps → FuelUp → Language.

### Code map

| Path (under `android/app/src/main/`) | What it does |
|---|---|
| `java/…/MainActivity.kt` | Entry point: initialises MapLibre, sets the theme |
| `java/…/ui/FuelUpApp.kt` | Screens and back stack (Navigation 3: map, settings, About); keeps the camera and the selected station |
| `java/…/ui/settings/SettingsScreen.kt` | Theme, map style and language choices; link to About |
| `java/…/ui/settings/SettingsViewModel.kt` | Saves theme/map style; applies theme and language through AppCompat |
| `java/…/data/AppSettings.kt` | Theme and map-style settings and their SharedPreferences store |
| `java/…/ui/map/MapScreen.kt` | Top bar, map, station sheet (non-modal), credits that follow the sheet |
| `java/…/map/MapLibreMap.kt` | MapLibre `MapView` inside Compose (all MapLibre glue) |
| `java/…/map/MapProvider.kt` | Map style URLs and credits: change `CurrentMapProvider` to switch provider |
| `java/…/ui/map/MapViewModel.kt` | Loads the cache, then refreshes in the background |
| `java/…/ui/map/DataStatusCard.kt` | Data date and loading / offline / error / update messages |
| `java/…/data/StationData.kt` | Kotlin mirror of the published JSON (schema v1) + parser |
| `java/…/data/StationRepository.kt` | Download, validation, file cache |
| `java/…/data/RefreshPolicy.kt` | When to contact the server |
| `java/…/data/DataSource.kt` | Where the data is published (one URL) |
| `java/…/map/StationLayers.kt` | Stations as clustered markers, selection ring, tap handling (cluster → zoom in, station → select) |
| `java/…/map/LabelLanguage.kt` | Map place names in the app language |
| `java/…/map/StationIcons.kt` | Marker icons per price class (colour + shape), also used by the legend |
| `java/…/data/FuelChoice.kt` | Chosen fuel/mode, its price per station, saved choice |
| `java/…/data/PriceRanking.kt` | Price comparison with nearby stations (constants in one place) |
| `java/…/ui/map/FuelSelector.kt` | Fuel chips, Self/Servito toggle, legend |
| `java/…/ui/station/StationDetails.kt` | Station + prices per fuel, from the cached data (pure Kotlin) |
| `java/…/ui/station/PriceFormat.kt` | Price numbers and "days since reported" |
| `java/…/ui/station/StationSheet.kt` | Station sheet content and the Navigate (`geo:`) intent |
| `java/…/ui/about/AboutScreen.kt` | Data source, notices, map credits |
| `java/…/ui/theme/` | Fixed FuelUp light/dark palette |
| `res/values/strings.xml`, `res/values-it/strings.xml` | English and Italian text |
| `res/xml/locales_config.xml` | Languages offered in Android 13+ settings |

### Station data: download, cache, refresh

On start the app shows the cached data at once, then decides whether to
contact the server (`data/RefreshPolicy.kt`):

1. No cache → download (`meta.json`, then `stations.json`).
2. The cache already holds **yesterday's** data → no network at all: MIMIT
   publishes day D's prices on the morning of D+1, so nothing newer exists.
3. Otherwise fetch `meta.json` (≈400 bytes) at most **once an hour**; the
   Retry button ignores this limit.
4. Download `stations.json` only if `meta.json` has a different date or
   checksum; check size, SHA-256, schema version and date, parse it, and only
   then replace the cache (`files/data/` in the app's private storage).
5. A `schemaVersion` the app doesn't know → keep the cache and ask the user to
   update the app.

Timeouts: 15 s to connect, 30 s per read; a timeout shows the offline state.

**Known limitation:** because of rule 2, a dataset **republished for the same
date** (for example after fixing a pipeline bug) does not reach phones that
already have that date. They get the fix with the next day's data. Publishing
a fix under a new date is not possible (the date is the price date), so if an
urgent fix is ever needed, a new app version must lower this rule.

**Test fixture:** the app's unit tests parse files generated by the pipeline
itself. After changing the JSON format, run
`python3 pipeline/scripts/make_android_fixture.py` and commit the result
(CI and the publish workflow fail until you do; see *Continuous integration*).

### Performance (measured)

Debug build on a Redmi Note 9 Pro (Android 11), 21.5k stations, 1 Oct 2026.
Release builds will be faster.

| What | Result |
|---|---|
| Download `stations.json` (gzip, Wi-Fi) | 0.4–1.1 s |
| Parse `stations.json` (background thread) | 0.55–0.9 s (one outlier 1.2 s) |
| Build GeoJSON for the map (background) | 55–135 ms |
| Hand GeoJSON to MapLibre (main thread) | 15–25 ms |
| Map labels in the app language (main thread) | 4–60 ms per style load |
| Java heap kept by the data | ~11 MB |
| Whole app memory (PSS) with map + data | ~225 MB (Java 30, native 59, graphics 65, debug code 55) |
| UI frames while the cache is parsed | 1–2% janky, 99th percentile 15–36 ms |
| Map frames while panning (SurfaceFlinger) | steady 16.7 ms (60 fps), none missed |
| Cold start | one long frame (~750 ms) from MapLibre start-up, with or without data |

To see the timings yourself: `adb logcat -s FuelUpPerf` (debug builds only).
Debug builds also enable StrictMode, which logs any disk or network access on
the main thread (MapLibre's own start-up shows up there; our code doesn't).

### Fuel filter and price comparison

**Fuel choice.** Chips for the standard product of each type (Benzina, Gasolio,
GPL, Metano incl. L-GNC, GNL); special products like "Blue Super" don't count.
Self/Servito only for petrol and diesel: on 30/09/2026, LPG, methane and LNG were
89–97% served-only (GPL self: 152 of 4,530 stations). Stations not selling the
choice are removed from the map, so clusters and their "from" prices only count
relevant stations. Saved in `SharedPreferences`; default Benzina self.

**Comparison** (`data/PriceRanking.kt`). For each station, the median price of
its **25 nearest** stations selling the same fuel/mode **within 50 km**, then the
difference in cents:

| Difference from the local median | Class | Marker |
|---|---|---|
| ≥ 2 c below | cheap | green `#009E73`, down chevron |
| within ±2 c | average | light grey, plain |
| ≥ 2 c above | expensive | vermillion `#D55E00`, up chevron |
| ≥ 35 c below (petrol/diesel; GPL 20 c, methane/LNG 50 c) | to verify | grey, "?" |
| fewer than 5 neighbours within 50 km | not compared | hollow |

Why this way (all measured on 30/09/2026 data):
- **Nearest stations, not the visible area:** colours must not change while
  panning; it depends only on the data and the fuel choice. The 25th
  neighbour is ~2–3 km away in cities, ~8 km in the median case, ~18 km in the
  countryside, so it adapts to density, unlike a fixed radius or provinces.
- **Cents, not ranks:** prices are clumped. Agip Eni sells petrol at exactly 1.990
  at 3,171 of 3,847 stations, 10–20 c under local prices. Percentiles would
  call a station 1 c above an Eni "expensive".
- **Groups:** motorway stations are compared only with each other (8 nearest,
  100 km), since their median is ~5.5 c higher. **Livigno** (duty-free, ~50 c
  cheaper) is compared only with itself; otherwise every Livigno station would be
  "to verify".
- **To verify:** the brand-priced stations sit at 10–20 c below; from ~35 c
  it's mostly scattered unbranded stations at 1.56–1.62 €/l. Petrol self: 103
  stations flagged, diesel self: 115 (~0.5%). Flagged prices stay visible but
  are never coloured cheap and never become a cluster's "from" price.
- Result for petrol self, ordinary roads: 36% cheap, 26% average, 37%
  expensive. The band and outlier thresholds are per fuel in
  `PriceRanking.THRESHOLDS`, ready to be tuned separately.

**Colour-blind safety.** Shape always carries the class; colour is extra. The
light grey for "average" differs in lightness from both colours, checked with
protanopia/deuteranopia/tritanopia simulations of screenshots.

**Performance** (Redmi Note 9 Pro, debug build): ranking all stations for a
fuel takes 200–260 ms on a background thread (spread over the CPU cores), plus
~35 ms to build the map data; nothing is recomputed while panning.

**Debug builds are arm64-only** (~26 MB instead of ~62 MB). To use an x86_64
emulator, add `"x86_64"` to `abiFilters` in `app/build.gradle.kts`.

### Localization rules

- No user-facing text in code: every string goes in `res/values/strings.xml`
  (English) **and** `res/values-it/strings.xml` (Italian). Lint fails the build
  if a translation is missing.
- Proper names (FuelUp, OpenFreeMap, …) are marked `translatable="false"`.
- When adding a language, add `res/values-xx/` and a line in
  `res/xml/locales_config.xml`.
