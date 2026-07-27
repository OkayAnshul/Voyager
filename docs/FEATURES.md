# Voyager — Complete Feature Guide

> Voyager is a private, **on-device** location timeline and "evidence engine" for Android.
> It remembers everywhere you've been, can **prove** it when you need to, and shows you your
> patterns — and it can always explain *why* it knows. No cloud, no account, no tracking by us.
>
> **Every feature described here is free.** Voyager ships with no paywall today. (The billing
> layer stays dormant in the code so optional subscriptions can return later without a rebuild.)

- **Package:** `com.cosmiclaboratory.voyager`
- **Platform:** Android 8.0+ (minSdk 26), targets Android 16 (targetSdk 36)
- **UI:** 100% Jetpack Compose + Material 3, custom "deep-space nebula" dark design system
- **Storage:** Room, **encrypted at rest with SQLCipher** (key in the Android Keystore) from day one
- **Positioning:** three jobs in one app — **Memory** (where was I?), **Proof** (prove it), **Habits** (my patterns)

---

## Table of contents
1. [The five core ideas](#the-five-core-ideas)
2. [Navigation & screens](#navigation--screens)
3. [Location tracking](#1-location-tracking)
4. [Timeline](#2-timeline)
5. [Map](#3-map)
6. [Places & geocoding](#4-places--geocoding)
7. [Insights (9 lenses)](#5-insights--9-analytics-lenses)
8. [Activities & fitness (Strava-parity)](#6-activities--fitness)
9. [Mileage & vehicles](#7-mileage--vehicles)
10. [Trips](#8-trips)
11. [Proof / evidence hub](#9-proof--evidence-hub)
12. [Day Story](#10-day-story)
13. [Search](#11-search)
14. [Export & import](#12-export--import)
15. [Reliability & permissions](#13-reliability--permissions)
16. [Privacy & security](#14-privacy--security)
17. [Under the hood](#under-the-hood)

---

## The five core ideas

| Idea | What it means in Voyager |
|---|---|
| **Local-first, encrypted by default** | Your history is stored in an SQLCipher-encrypted database whose key never leaves the device's hardware keystore. Privacy isn't a setting — it's the architecture. |
| **Evidence-backed** | Every visit, movement segment, place and drive carries the *reasons* it was inferred — including counter-evidence — so a record can be defended to yourself, an accountant, or an auditor. |
| **Honest gaps** | When tracking is interrupted (battery, OEM kill, GPS loss) Voyager shows an explicit **GAP** row instead of faking a smooth line. |
| **One record, many jobs** | The same on-device history powers your timeline, your mileage log, your trip book, your fitness records, and your insights. |
| **Explainable, not magical** | Voyager is a transparent **heuristic** engine, not a black-box model. Any timeline row can tell you exactly why it was classified the way it was. |

---

## Navigation & screens

Voyager opens through a short first-run flow — **Splash → Intro → Permissions → Persona → Main** —
where the Persona step (Everyday / Commuter / Athlete) tunes the default tracking preset.

The main app is a **six-tab** experience with a custom aurora-glass bottom bar:

| Tab | Purpose |
|---|---|
| **Today** | Home dashboard: live tracking control, today's stats, top places, insight teasers, anomalies |
| **Timeline** | Chronological day view of visits and movements, with honest gaps |
| **Map** | Dark cartographic map — the day's routes and visit markers, synced with the timeline |
| **Insights** | Nine analytics "lenses" told as a storybook |
| **Proof** | Evidence hub — mileage, trips and export, framed as audit-ready records |
| **Activities** | Athlete home — activity rings, workout feed, record button, segments |

A persistent top bar adds a **"Why Voyager?"** story sheet, global **Search**, a **review-bell** badge
(places awaiting confirmation), and **Settings**.

---

## 1. Location tracking

The engine that feeds everything else.

- **Foreground service** with `foregroundServiceType=location` (`LocationCaptureService`) keeps
  capture alive with a persistent notification, as Android requires for background location.
- **Adaptive sampling** (`AdaptiveSamplingPolicy`) changes GPS cadence by context — stationary,
  walking, or driving — to balance fidelity against battery. A near-zero-battery **passive tier**
  and a **dormant mode** stop active GPS when you haven't moved.
- **Multi-sensor fusion:** fused location, **activity recognition**, **accelerometer signature**,
  **barometer** (elevation), **step counter**, **significant-motion**, and **Wi-Fi fingerprinting**
  all contribute, admitted through a `SampleAdmissionGate` and quality checks.
- **Start / Pause / Stop** are real states. Pause is an in-memory, non-persisted hold; the first tap
  on the dashboard auto-starts tracking so you're never one setting away from data.
- **Geofencing & transitions:** `PlaceGeofenceManager` and activity-transition receivers wake the app
  efficiently around known places and mode changes.
- **Boot persistence:** tracking resumes after a reboot (`BootReceiver`).

## 2. Timeline

A truthful, chronological account of your day.

- Renders **visits** (named stops) interleaved with **movement segments** — Drive, Transit, Cycle,
  Walk — each colour-coded by transport mode.
- **Honest GAP rows** appear when data is missing, distinguishing *you didn't move* from
  *the phone wasn't recording* (GPS loss vs process death vs OEM kill).
- **"Current Location"** is pinned at the top for today when a visit is ongoing.
- **Explain any row:** an explainability use case (`ExplainTimelineRowUseCase`) shows the evidence
  behind each classification; you can **override a segment's transport type** if the heuristics got it wrong.
- Tapping a place or segment opens its detail; a **Day Story** deep link turns the day into a narrative.
- Day boundaries and reconciliation are timezone-aware, so travelling across zones doesn't scramble your days.

## 3. Map

*"The map is the app."*

- A **dark cartographic basemap** (MapLibre GL) that matches Voyager's OLED UI.
- Renders the selected day's **routes and visit markers**, with **bidirectional focus sync** —
  tap a timeline row to fly the map to it, or tap the map to scroll the timeline.
- Numbered visit pins, a live current-location indicator with an accuracy ring, and route arrows.
- Launches the **workout recorder** for a live athlete view.
- Everything is rendered from your on-device data over open basemap tiles — **no Google Maps API**.

## 4. Places & geocoding

Turning raw coordinates into names you recognise — without a cloud round-trip you don't control.

- **Place discovery** clusters your stops into places, then **matches** new visits to them live.
- **Name priority chain** (`GeocodingConflictResolver`): your own name › your category › a trusted
  provider name › **"Near [neighbourhood / street / city]"** › a semantic label › raw coordinates.
  You never see a bare "Unknown place."
- **Geocoding providers** fall back gracefully — the on-device Android Geocoder first, then OSM
  (Nominatim / Photon) and Overpass POI lookups — and results are cached locally.
- **Category inference** from OSM POI tags and your visit patterns (Home, Work, Gym, Restaurant,
  Shopping, Transit hub, Custom…).
- **Confidence & repeatability:** places you visit often **resist confidence decay**; stale one-offs
  fade. A background `MergePlacesWorker` collapses duplicate places by name/proximity.
- **Review queue:** low-confidence or uncategorised places surface behind the top-bar bell, where a
  tap lets you rename, categorise, or confirm — which permanently raises that place's trust.

## 5. Insights — 9 analytics lenses

Your history, read back to you as a **storybook**. Nine "lenses":

| Lens | What it tells you |
|---|---|
| **Overview** | Period summary — time tracked, places, distance, top locations |
| **Weekly** | This week vs last, plus your tracking **streak** |
| **Highlights** | Notable events, personal records, and "On this day" memories |
| **Patterns** | Recurring routines Voyager has learned, with human labels |
| **Rhythm** | Your day rhythm, **sleep** windows, and routine breaks |
| **Movement** | Place statistics and an **exploration score** (how much new ground you cover) |
| **Balance** | A **time budget** across life categories |
| **Carbon** | An estimated **CO₂ footprint** of your movement by mode |
| **Anomalies** | Days and events that don't fit your normal pattern |

Backing engines also power **commute analysis** and **next-place prediction**, a **heatmap**, and a
**Year-in-Review**. All nine lenses are free.

## 6. Activities & fitness

A private, on-device analogue to a fitness tracker — **Strava-parity where it counts**, without the cloud.

- **Live recorder** (`WorkoutRecorder`) for **Run, Walk, Cycle, Hike** with a live route map.
- **Auto-pause:** the clock stops when you drop below a walking crawl, so your pace isn't diluted at traffic lights.
- **Elevation gain** from GPS/barometer with a hysteresis band to reject jitter.
- **Per-kilometre splits** and an **elevation profile** on the activity detail screen.
- **Personal Records** computed across your activities (longest, fastest, biggest climb…).
- **Race-yourself segments:** save a favourite stretch as a **segment**; efforts are matched against
  your recorded activities on the fly — no stored leaderboard, no cloud.
- An **Activity Rings** "this week" hero and a workout feed with suggested workouts.
- Export any activity as **GPX** (with time and elevation) for other tools.

## 7. Mileage & vehicles

Turn detected drives into a defensible, tax-ready log.

- **Classify drives** (Business / Personal / Medical / …) with a swipe or tap.
- **Deductible value** computed from configurable **IRS / HMRC-style** per-distance rates, in your currency.
- **Vehicles** with fuel type and efficiency drive **fuel-cost and CO₂** estimates; a vehicle
  auto-assignment engine attributes drives.
- Every mileage row is **evidence-backed** — the underlying GPS trace and rule version travel with it,
  so the log stands up to scrutiny. Export to **CSV**.

## 8. Trips

- **Auto-detected multi-day journeys** away from home (`DetectTripsUseCase`), broken correctly across
  long data blackouts.
- Each trip opens as a shareable **story**: the places, the distance, the route.

## 9. Proof / evidence hub

The Proof tab frames Voyager's differentiator: **audit-ready by design.**

- Cards route to **Mileage**, **Trips**, and **Export**.
- Every record is **computed on-device** and carries its evidence; the tagline says it plainly —
  *"Computed on-device. Your records never leave your phone."*
- Evidence blocks summarise the *why* behind a place, visit, or segment, including counter-evidence.

## 10. Day Story

- A **photo day story**: your device's own photos for a day (via MediaStore + EXIF, on-device) pinned
  to the places you visited, turning a day into a scrollable memory.
- Entirely local — Voyager never uploads your photos.

## 11. Search

- Full-text search across places and days, backed by a maintained **search index** (`SearchIndexWorker`).
- **Filter chips:** date presets, place categories, and transport modes, wired through `SearchTimelineUseCase`.

## 12. Export & import

- **Export formats:** Voyager JSON (`Timeline.json` / `Records.json`), **GPX**, **GeoJSON**, **CSV**.
- **Import:** **Google Timeline** JSON — the migration wedge for people whose cloud Timeline was taken
  away — plus GPX import and full `.voyager` **restore**.
- You own your data, and can take it anywhere or delete it entirely.

## 13. Reliability & permissions

- A dedicated **Reliability** screen explains aggressive OEM background-kill behaviour and offers a
  **self-test** for sample gaps, so users understand why data can be missing on some phones.
- Clear, staged permission onboarding (location → background location → notifications → activity recognition).

## 14. Privacy & security

- **SQLCipher** full-database encryption, always on; the key is derived from an **Android Keystore**
  key and never persisted in plaintext.
- **Biometric app lock** and a **FLAG_SECURE** option that blocks screenshots and hides the app in Recents.
- **No cloud, no analytics-by-us, no ads, no account.** Network access is used only for optional
  geocoding and open map tiles — never to send your history anywhere.
- A `network_security_config` denies cleartext traffic.

---

## Under the hood

- **Architecture:** clean-architecture layering (domain / data / platform / presentation), Hilt DI,
  Kotlin coroutines + Flow.
- **The pipeline:** raw samples flow through a single serial pipeline — normalise → Kalman-filter →
  quality-score → dedup → commit-state → segment — with a gap watchdog and crash-recovery state store,
  keeping the timeline correct and explainable.
- **Background work:** WorkManager schedules ~20 jobs — place discovery/merge, category & semantic
  labelling, geocode backfill, trip detection, daily/weekly rollups, recaps and nudges, confidence
  decay, data retention, integrity repair, health checks, battery budgeting, and search indexing.
- **Distribution:** two build flavors — **Play** (with Play Billing v7, currently dormant) and
  **F-Droid** (no proprietary code). CI runs unit tests, lint, and assembles both flavors on every PR,
  plus a monthly dependency-security scan.

---

*Voyager is developed by Anshul (Cosmic Laboratory). All features listed are available for free.*
