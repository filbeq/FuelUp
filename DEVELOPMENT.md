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

## Data publishing (GitHub Pages)

The workflow [`.github/workflows/publish-data.yml`](.github/workflows/publish-data.yml)
runs the tests and the pipeline, then deploys the output to GitHub Pages. It runs
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

**Testing languages:** on Android 13+ use Settings → Apps → FuelUp → Language.
Older Android versions have no per-app language setting: change the phone's
language instead.

### Code map

| Path (under `android/app/src/main/`) | What it does |
|---|---|
| `java/…/MainActivity.kt` | Entry point: initialises MapLibre, sets the theme |
| `java/…/ui/FuelUpApp.kt` | Switches between the map and About; keeps the camera |
| `java/…/ui/map/MapScreen.kt` | Top bar, map, always-visible map credits |
| `java/…/map/MapLibreMap.kt` | MapLibre `MapView` inside Compose (all MapLibre glue) |
| `java/…/map/MapProvider.kt` | Map style URLs and credits: change `CurrentMapProvider` to switch provider |
| `java/…/ui/about/AboutScreen.kt` | Data source, notices, map credits |
| `java/…/ui/theme/` | Fixed FuelUp light/dark palette |
| `res/values/strings.xml`, `res/values-it/strings.xml` | English and Italian text |
| `res/xml/locales_config.xml` | Languages offered in Android 13+ settings |

### Localization rules

- No user-facing text in code: every string goes in `res/values/strings.xml`
  (English) **and** `res/values-it/strings.xml` (Italian). Lint fails the build
  if a translation is missing.
- Proper names (FuelUp, OpenFreeMap, …) are marked `translatable="false"`.
- When adding a language, add `res/values-xx/` and a line in
  `res/xml/locales_config.xml`.
