# Third-party notices

FuelUp is licensed under the [GNU General Public License v3.0](LICENSE). It
uses the data, maps and open-source components below, under their own
licences.

## Data

**Fuel prices and stations** — Fonte dati: Ministero delle Imprese e del Made
in Italy — Osservaprezzi Carburanti
([dataset](https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti)).
Licensed under the
[Italian Open Data License 2.0 (IODL 2.0)](https://www.dati.gov.it/content/italian-open-data-license-v20).
FuelUp cleans and reformats the data (see
[pipeline/README.md](pipeline/README.md)); it is not an official app and is not
endorsed by the Ministry.

**Map data** — © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright),
available under the Open Database License (ODbL).

**Map tiles and styles** — [OpenFreeMap](https://openfreemap.org),
[© OpenMapTiles](https://www.openmaptiles.org).

## Software included in the app

| Component | Licence |
|---|---|
| [MapLibre Native for Android](https://github.com/maplibre/maplibre-native) (`org.maplibre.gl:android-sdk`) | BSD 2-Clause (below) |
| [MapLibre Android gestures](https://github.com/maplibre/maplibre-gestures-android) | BSD 2-Clause |
| [MapLibre Java GeoJSON and Turf](https://github.com/maplibre/maplibre-java) | Apache 2.0 |
| [Android Jetpack (AndroidX)](https://developer.android.com/jetpack/androidx): Compose, Material 3, AppCompat, Activity, Lifecycle, Navigation 3 and their dependencies | Apache 2.0 |
| [Kotlin standard library](https://github.com/JetBrains/kotlin), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), [JetBrains annotations](https://github.com/JetBrains/java-annotations) | Apache 2.0 |
| [OkHttp](https://github.com/square/okhttp), [Okio](https://github.com/square/okio) (used by MapLibre) | Apache 2.0 |
| [Gson](https://github.com/google/gson), [Guava ListenableFuture](https://github.com/google/guava) (used by MapLibre, AndroidX) | Apache 2.0 |
| [Timber](https://github.com/JakeWharton/timber) (used by MapLibre) | Apache 2.0 |
| [JSpecify](https://github.com/jspecify/jspecify) | Apache 2.0 |
| [Material Symbols](https://github.com/google/material-design-icons) (icons, incl. the launcher icon) | Apache 2.0 |

MapLibre Native also bundles third-party libraries in its native code; see its
[repository](https://github.com/maplibre/maplibre-native) for their notices.

The full Apache License 2.0 text is at
<https://www.apache.org/licenses/LICENSE-2.0>.

### MapLibre Native (BSD 2-Clause)

```
Copyright (c) 2021 MapLibre contributors
Copyright (c) 2018-2021 MapTiler.com
Copyright (c) 2014-2020 Mapbox

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
