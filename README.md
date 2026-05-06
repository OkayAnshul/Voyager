<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="Voyager Logo" />

# Voyager

**Privacy-first travel intelligence for Android**

Track everywhere you go. Own all your data. Understand your life's movement.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207)-brightgreen)](https://developer.android.com/tools/releases/platforms)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36%20(Android%2016)-blue)](https://developer.android.com/tools/releases/platforms)
[![License](https://img.shields.io/badge/License-MIT-orange)](LICENSE)

</div>

---

## What is Voyager?

Voyager is a local-first, privacy-first Android app that reconstructs your daily life as a meaningful timeline — where you went, how long you stayed, how you got there — without sending a single byte to the cloud.

It runs an **8-stage real-time processing pipeline** entirely on-device: raw GPS samples flow through Kalman filtering, quality scoring, deduplication, activity fusion, visit detection, movement segmentation, and place linking — all in a single-threaded serial channel to prevent race conditions.

**No Google Maps API. No cloud. No tracking by us.**

---

## Screenshots

> **Note:** Replace placeholders below with actual screenshots. Recommended: 5–6 screenshots in portrait (1080×2340), hosted in `/docs/screenshots/`.

<div align="center">

| Dashboard | Timeline | Map |
|:---------:|:--------:|:---:|
| <img src="docs/screenshots/dashboard.png" width="200" alt="Dashboard" /> | <img src="docs/screenshots/timeline.png" width="200" alt="Timeline" /> | <img src="docs/screenshots/map.png" width="200" alt="Map" /> |
| Live tracking stats, streak counter, activity rings | Day-by-day movement with segment cards | Interactive OSM map with route overlays |

| Insights | Settings | Place Detail |
|:--------:|:--------:|:------------:|
| <img src="docs/screenshots/insights.png" width="200" alt="Insights" /> | <img src="docs/screenshots/settings.png" width="200" alt="Settings" /> | <img src="docs/screenshots/place_detail.png" width="200" alt="Place Detail" /> |
| Weekly comparison, trends, anomaly detection | 4-tab deep-configuration panel | Visit history, rename, category correction |

</div>

---

## Video Demo

> **Note:** Add a demo GIF or link to a YouTube/Loom recording here.

```
[Demo video placeholder — record a 30–60 second screen recording showing:
  1. App opening with animated splash
  2. Live tracking active on dashboard
  3. Timeline scrolling through a day's segments
  4. Tapping a place to see visit history
  5. Map view with route polylines
]
```

<!--
Once recorded, replace the block above with:
[![Voyager Demo](docs/screenshots/demo_thumb.png)](https://youtu.be/YOUR_VIDEO_ID)
-->

---

## Features

### Real-Time Location Intelligence

| Feature | Detail |
|---------|--------|
| **Adaptive GPS sampling** | 90s when still, 12s walking, 7s driving — auto-adjusts to motion state |
| **Dormant mode** | GPS shuts off after 4.5 min stationary; wakes on significant motion sensor |
| **Visit detection** | Dwell-based state machine (3-min threshold, hysteresis, return detection within 30 min) |
| **Movement segmentation** | Classifies WALK / RUN / CYCLE / DRIVE / TRANSIT / GAP with speed thresholds |
| **Activity fusion** | Merges Activity Recognition API, pedometer, and speed heuristics into a single motion state |
| **WiFi fingerprinting** | Supplementary SSID/BSSID signal for indoor place matching |

### Privacy & Security

- **100% local** — no cloud sync, no analytics SDKs, no third-party data sharing
- **SQLCipher encryption** on the Room database from day one
- **Background location** used only for tracking; no silent location access
- **No API keys required** — OpenStreetMap + Android Geocoder + Nominatim (all free)
- **Correction system** — every inference is evidence-backed and user-correctable

### Screens & UI

- **Dashboard** — ActivityRings hero card, streak counter, top places, anomaly flags
- **Timeline** — Day-navigable movement timeline with segment cards and gradient rail
- **Map** — MapLibre OSM map, route overlays, geofence visualisation, place bottom sheet
- **Insights** — Weekly comparison, visit frequency, distance trends, time-of-day patterns
- **Settings** — 4-tab configuration: General, Detection, Privacy, Advanced (16 modular sections)
- **Place review** — Workflow to rename, recategorize, and correct inferred places
- **Export** — GPX, GeoJSON, CSV, and VoyagerJSON formats with share intent
- **Onboarding** — 3-page animated feature walkthrough + permission flow

---

## Architecture

Voyager uses **Clean Architecture** across 6 layers, with a stream-first processing pipeline as the core runtime:

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│   Jetpack Compose · MVVM · SharedUiState · Navigation   │
├─────────────────────────────────────────────────────────┤
│                      Domain Layer                        │
│   Use Cases · Repository Interfaces · Domain Models     │
├──────────────────────┬──────────────────────────────────┤
│      Data Layer      │         Platform Layer           │
│  Repositories (15)   │  Foreground Service · Workers   │
│  Geocoding (3 APIs)  │  Notifications · Receivers      │
├──────────────────────┴──────────────────────────────────┤
│                     Pipeline Layer                       │
│                                                         │
│  GPS/AR/Steps → [Normalize → Kalman → Quality →        │
│                  Dedup → Fuse → VisitDetect →           │
│                  Segment → Commit] → PlaceLink          │
│                                                         │
│              Single-threaded serial channel             │
├─────────────────────────────────────────────────────────┤
│                     Storage Layer                        │
│     Room + SQLCipher · DataStore · TimelineStateStore   │
└─────────────────────────────────────────────────────────┘
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Single-threaded pipeline | Eliminates race conditions in visit/segment state machines |
| SQLCipher from day 1 | Privacy-first — never store plaintext location history |
| MapLibre over Google Maps | No API key, no cost, OSM-powered |
| 3 geocoding providers | Android Geocoder (free/offline) → Photon → Nominatim fallback chain |
| Evidence-backed inference | Every place/visit decision stores supporting evidence entities |
| WorkManager for background | 9 typed workers for rollup, geocoding, integrity repair, export |

---

## Tech Stack

| Category | Library / Tool | Version |
|----------|---------------|---------|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | BOM 2024.09 |
| DI | Hilt | 2.51.1 |
| Database | Room + SQLCipher | 2.6.1 / 4.5.4 |
| Location | Google Play Services Location | 21.3.0 |
| Activity | Google Activity Recognition | 21.1.0 |
| Map | MapLibre Android | 11.8.0 |
| Background | WorkManager | 2.9.0 |
| Network | Retrofit + OkHttp + Ktor | 2.11 / 4.12 / 2.3.12 |
| Serialization | Kotlinx Serialization | 1.7.1 |
| Coroutines | Kotlin Coroutines | 1.8.1 |
| Fonts | Inter · Great Vibes · JetBrains Mono | — |
| Testing | JUnit 4 · MockK · Turbine · Truth | — |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog+ (2023.1.1 or later)
- JDK 11+
- Android device / emulator running **Android 7.0+ (API 24)**

### Build

```bash
# Clone
git clone https://github.com/OkayAnshul/Voyager.git
cd Voyager

# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest
```

> No API keys needed. The app uses only free, open-source services.

### Permissions

Voyager requests these at runtime:

| Permission | Purpose |
|-----------|---------|
| `ACCESS_FINE_LOCATION` | GPS tracking |
| `ACCESS_BACKGROUND_LOCATION` | Continuous background tracking |
| `ACTIVITY_RECOGNITION` | Motion state detection |
| `POST_NOTIFICATIONS` | Foreground service notification (Android 13+) |

Grant **"Allow all the time"** for background location to enable continuous tracking.

---

## Project Structure

```
app/src/main/java/com/cosmiclaboratory/voyager/
├── capture/          # GPS, activity, steps, WiFi, geofence capture
├── pipeline/         # 8-stage processing pipeline + place linking
│   └── stage/        # Normalizer, Kalman, Quality, Dedup, Committer, Segmenter
├── domain/
│   ├── model/        # Domain entities + enums
│   ├── repository/   # Repository interfaces (15)
│   └── usecase/      # Business logic (DetectVisit, FuseActivity, MatchPlace…)
├── data/
│   ├── api/          # Geocoding services (Android, Nominatim, Photon, Overpass)
│   ├── geocoding/    # Provider implementations + registry
│   └── repository/   # Repository implementations (15)
├── storage/
│   ├── database/     # Room schema — 20 entities, 20 DAOs
│   └── encryption/   # SQLCipher key management
├── platform/
│   ├── service/      # LocationCaptureService (foreground)
│   ├── worker/       # 9 WorkManager workers
│   ├── coordinator/  # TrackingRuntimeCoordinator, PermissionMonitor
│   └── map/          # MapLibreMapEngine
├── presentation/
│   ├── screen/       # 18 screens with paired ViewModels
│   ├── theme/        # VoyagerColors, VoyagerComponents, VoyagerGradients
│   ├── components/   # Shared UI primitives
│   └── state/        # SharedUiState, DayNavigationStateHolder
└── di/               # 8 Hilt modules
```

---

## Documentation

| Document | Description |
|---------|-------------|
| [Architecture](docs/architecture/ARCHITECTURE.md) | Clean Architecture layers and data flow |
| [Domain Models](docs/architecture/DOMAIN_MODELS.md) | All domain entities and enumerations |
| [Stream-First vs Visit-First](docs/research/STREAM_FIRST_VS_VISIT_FIRST_ARCHITECTURE_FINDINGS.md) | Architecture research findings |
| [Place Detection](docs/algorithms/PLACE_DETECTION.md) | Visit detection and place matching algorithms |
| [Design Evolution](docs/appendices/DESIGN_EVOLUTION.md) | 1-year development history |
| [Technology Stack](docs/appendices/TECHNOLOGY_STACK.md) | Tech choices and trade-offs |
| [QA Guide](docs/resources/QA_GUIDE.md) | Manual testing procedures |

---

## Development History

Voyager was built over ~1 year of continuous development with two major architectural versions:

- **V1 (Aug–Feb 2025):** Visit-first architecture — AppStateManager, DBSCAN clustering, basic pipeline
- **V2 (Mar 2026–present):** Stream-first architecture — 8-stage serial pipeline, Kalman filter, evidence layer, adaptive dormant mode

See [`docs/appendices/DESIGN_EVOLUTION.md`](docs/appendices/DESIGN_EVOLUTION.md) for the full story, and [`docs/appendices/FLAWS_AND_ADVANCES.md`](docs/appendices/FLAWS_AND_ADVANCES.md) for an honest technical assessment.

---

## License

MIT — see [LICENSE](LICENSE)

---

<div align="center">

Built for people who want to understand where their life actually happens — without handing that data to anyone else.

**[OkayAnshul](https://github.com/OkayAnshul)**

</div>
