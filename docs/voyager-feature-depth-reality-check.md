# Voyager — Feature Depth & Competitive Reality Check

_Date: 2026-05-19 · Companion: `voyager-ux-design-blueprint.md`, `voyager-master-improvement-backlog.md`._

The competitive-readiness doc answered _"can we compete and what's left."_ This
doc answers a harder question, **per feature**: how deep is the actual logic, the
data model, the parameters, and the UI — measured against the specialist who owns
that feature? It is traced from code (`pipeline/stage/*`, `domain/usecase/*`,
`storage/database/entity/*`), not from the roadmap's claims.

---

## Part 1 — Method & rating scale

Each feature is rated on **five axes**:

| Axis | Question |
|---|---|
| **Logic** | How sophisticated is the algorithm/inference? |
| **Data** | Does the schema capture enough to be rich and future-proof? |
| **Parameters** | Is it tunable — by the user and by the engine? |
| **UI** | Is the capability actually surfaced well to the user? |
| **Depth (overall)** | Net: would a user of the specialist app feel a downgrade? |

Each cell: **✓ Parity** (matches/beats the specialist) · **◐ Partial** (usable
but a noticeable step down) · **✗ Shallow** (placeholder-grade or absent).
Each feature also gets a **gap type** (logic / data / parameters / UI / depth)
and a **fix size** (partial = tune/extend · full = rebuild/new subsystem).

**The honest framing:** Voyager is a **heuristic engine**, not an ML one. Google
and Arc have years of labelled data and trained models. Voyager will not win on
raw inference accuracy. It wins by being *explainable, private, and bundled*.
This audit says where heuristics are "good enough," where they genuinely hurt,
and where the moat should be pressed instead of chasing parity.

---

## Part 2 — Feature-by-feature teardown

### 1. Place detection & clustering — vs Google Timeline, Arc

**Competitor:** Google/Arc cluster GPS into places with ML that fuses dwell,
WiFi/Bluetooth fingerprints, historical visit priors, and a global POI prior — so
a place "snaps" to a known venue fast and survives sparse data.

**Voyager (code):** `DetectVisitUseCase` (304 LOC) — hysteresis-based: candidate
accumulation, dwell confirmation (`minDwellMinutes`), exit detection with entry/
exit hysteresis counts and an `exitBufferM`. `MatchPlaceLiveUseCase` matches a
sample to the nearest place via geohash. `WifiFingerprinter` exists. Tunables:
`minDwellMinutes`, `placeRadiusM`, `entry/exitHysteresisCount`, `exitBufferM`,
`autoDiscoveryEnabled`. `PlaceEvidenceEntity` stores clusterDensity,
repeatabilityScore, visit counts.

**Verdict:** Logic ◐ · Data ✓ · Parameters ✓ · UI ✓ · Depth ◐. The hysteresis
logic is solid and well-tuned and the schema is rich. The gap vs Google is the
**absence of a POI prior and visit-history priors** — Voyager discovers a place
from geometry alone, so a first visit to a venue is a low-confidence "Place near
X" until repeat visits. **Fix (partial):** feed the Overpass POI result as a
*prior* into place confirmation (raise confidence + name when GPS centroid sits
on a known POI), and use `PlaceRollup` repeatability to boost confidence faster.

### 2. Activity / transport-mode classification — vs Google, Arc

**Competitor:** ML classifiers over accelerometer + GPS + the Activity Recognition
API, trained to separate walk/run/bike/car/bus/train/flight reliably.

**Voyager (code):** `FuseActivityStateUseCase` (121 LOC) fuses three signals — the
AR API, a speed heuristic with wide anti-oscillation dead zones, and step-rate
fusion — with hysteresis and a speed-vs-accuracy sanity gate (rejects phantom
speed spikes). `Segmenter` debounces transitions over 5 consecutive samples.
`SegmentType` has WALK/RUN/CYCLE/DRIVE/TRANSIT/FLIGHT.

**Verdict:** Logic ◐ · Data ✓ · Parameters ✓ · UI ✓ · Depth ◐. The fusion is
genuinely thoughtful — the dead zones, sanity gates and debounce are real
engineering. But **DRIVE vs TRANSIT vs CYCLE is speed-ambiguous** and Voyager has
no accelerometer-signature classifier, so transit is often mislabelled drive.
**Fix (partial):** an accelerometer-variance signal to separate the smooth-ride
modes; lean on the `EvidenceCard` to make mistakes *correctable and explained*
rather than chasing ML parity.

### 3. Place naming / geocoding — vs Google's POI graph (the hardest gap)

**Competitor:** Google reverse-geocodes against the richest commercial POI
database on earth — almost every venue has a name.

**Voyager (code):** a real multi-provider stack — `GeocodingProviderRegistry`
ordered `[OVERPASS, ANDROID_GEOCODER, PHOTON, NOMINATIM]`, an accuracy gate
(`safeDisplayName` coarsens by confidence tier), sequential short-circuit, rate
limiting, 90-day cache. Tunables: `providerOrder`, `geocodeLanguage`,
`autoGeocodeNewPlaces`, `coarsenGeocodeQueries`.

**Verdict:** Logic ✓ · Data ✓ · Parameters ✓ · UI ✓ · Depth ◐. The architecture
is excellent — better-engineered than most apps. The gap is **not code, it's data
coverage**: OSM/Overpass simply has fewer POIs than Google, so in low-coverage
regions places stay coordinate-named. This is structural and largely
unwinnable head-on. **Fix (partial + the OSM loop):** make user renaming
frictionless (it already is), surface geocode *alternatives* well, and ship the
**OSM contribution loop** so users improve the commons — turning the weakness
into the brand story ("you make the map better").

### 4. Timeline construction & accuracy — vs Arc, Google

**Competitor:** Arc's timeline is the gold standard — continuous, editable,
self-correcting.

**Voyager (code):** `TimelineReconciler` builds `TimelineDay` from
`movement_segments`; `Segmenter` produces VISIT + movement segments with
per-segment evidence; `LocationKalmanFilter` (a real 4-state constant-velocity
Kalman filter) smooths routes; gaps are first-class (`GapReason`). Editable —
`CorrectionType` has 13 values, corrections are logged.

**Verdict:** Logic ✓ · Data ✓ · Parameters ✓ · UI ◐ · Depth ✓. The timeline
*engine* is at parity — Kalman smoothing, evidence, full editability, honest
gaps. The shortfall is **UI presentation** (today it's card-segmented, not Arc's
continuous ribbon) — fixed by the Part 7.1 redesign. **Fix (partial, UI only).**

### 5. Trip detection & travel storytelling — vs Polarsteps

**Competitor:** Polarsteps auto-builds a beautiful illustrated journey, photo-
forward, with a polished printed book.

**Voyager (code):** `DetectTripsUseCase` — detects away-from-home runs (breaks on
a home day, absorbs no-data gaps, ≥2-day span, needs a Home anchor);
`BuildTripDetailUseCase` re-derives day-by-day; `TripBookPdfExporter` produces a
cover + per-day journal PDF.

**Verdict:** Logic ✓ · Data ◐ · Parameters ◐ · UI ◐ · Depth ◐. Detection logic is
sound. The gaps: **`TripEntity` has no user-authored fields** (title is derived,
no notes/cover photo/highlights — the doc-comment even says "no user-authored
fields yet"), and the trip *book* is functional but not Polarsteps-beautiful.
**Fix (partial→full):** add user fields to `TripEntity` (title, notes, cover
photo, per-day captions — needs a schema migration); upgrade the PDF/`TripDetail`
UI to photo-forward storytelling.

### 6. Mileage logging — vs MileIQ

**Competitor:** MileIQ — swipe-to-classify drives, IRS PDF. Cloud, $60/yr,
single-purpose, **no underlying GPS evidence**.

**Voyager (code):** `BuildMileageLogUseCase` over DRIVE segments +
`MileageClassificationEntity` (purpose, note); `MileagePdfExporter` with IRS-2025
rates; per-row GPS evidence available; raw-sample export.

**Verdict:** Logic ✓ · Data ✓ · Parameters ✓ · UI ◐ · Depth ✓ — **Voyager
leads.** It matches MileIQ and adds court-grade GPS evidence per row, for a
fraction of the price, bundled. The only gap is **UI**: no swipe-to-classify yet
(see blueprint Part 7.3). **Fix (partial, UI only).**

### 7. Photo correlation / Day Story — vs Google Timeline, Polarsteps

**Competitor:** Google correlates Google Photos to places automatically.

**Voyager (code):** `BuildDayStoryUseCase` — on-device `MediaStore` query,
photo↔visit correlation by capture time, EXIF-GPS tie-break, an "unplaced"
bucket. No DB migration (computed read-only).

**Verdict:** Logic ✓ · Data ✓ · Parameters ◐ · UI ◐ · Depth ✓. The correlation
logic is solid and fully private (Google needs the cloud). Gaps are UI polish
(photo-forward layout — blueprint Part 7.3) and no "this day last year"
resurfacing yet. **Fix (partial).**

### 8. Fitness / workout recording — vs Strava

**Competitor:** Strava — a Record button, live pace/distance/elevation/laps, a
shareable activity, segments/leaderboards, GPX.

**Voyager (code):** **absent.** There is no workout-recording mode, no live
activity screen, no Activity entity, no GPX-per-activity. Voyager records
movement *passively* into the timeline; it cannot record an intentional workout.

**Verdict:** Logic ✗ · Data ✗ · Parameters ✗ · UI ✗ · Depth ✗ — **a full gap.**
This is the single biggest missing capability and the reason the Athlete persona
cannot be served today. **Fix (full):** a new Workout subsystem — a foreground
high-accuracy tracking tier, an `Activity` entity, a Record screen + Activities
list, live stat computation, GPX export. The engine, Kalman filter and GPS
capture already exist to build on; this is a surface + a new tier, not a new
engine. Voyager will not match Strava's social/segments — and shouldn't try —
but workout *recording* parity is achievable and is the price of entry for the
Athlete persona.

### 9. Maps & route rendering — vs Google Maps / Mapbox

**Competitor:** Google Maps tiles — rich, labelled, 3D, instantly familiar.

**Voyager (code):** MapLibre + OSM/Carto tiles; route polylines with
Douglas-Peucker simplification and encoded polylines; per-mode colouring;
clustering at zoom.

**Verdict:** Logic ✓ · Data ✓ · Parameters ✓ · UI ◐ · Depth ◐. The rendering
stack is competent and the privacy stance (OSM, no Google) is deliberate. Gaps:
OSM tiles look plainer than Google's, no offline maps wired
(`offlineMapsEnabled` exists but unimplemented), map state lost on process death.
**Fix (partial):** a polished custom map style, wire offline tiles, persist
camera state.

### 10. Insights & analytics depth — vs Google, Strava

**Competitor:** Google's "year in review"/heatmaps; Strava's training trends.

**Voyager (code):** 7 Insights tabs (Overview free; Movement/Patterns/Places/
Social/Weekly/Carbon Pro), `WeeklyComparison` with trends, `Anomaly` detection
(σ-deviation), `PlaceRollup` with 7/30/90-day patterns.

**Verdict:** Logic ✓ · Data ✓ · Parameters ◐ · UI ◐ · Depth ✓. Analytics breadth
is actually *ahead* of most rivals (anomalies + carbon are differentiators). Gaps:
**no heatmap / Year-in-Review** visual (the shareable moment rivals have); the
Social tab is thin. **Fix (partial):** add a `HeatmapCalendar` + an annual
recap; deepen or cut Social.

### 11. Evidence / explainability — Voyager-unique (the moat)

**Competitor:** none. Every competitor is a black box.

**Voyager (code):** `SegmentEvidenceEntity` (activity votes, provider mix, speed
stats, heading consistency, **counter-evidence**, rule version),
`VisitEvidenceEntity` (arrival/departure confidence, inside/outside counts),
`BuildEvidenceSummaryUseCase` → `InferenceExplanation` with a human explanation.

**Verdict:** Logic ✓ · Data ✓ · Parameters ✓ · UI ✓ · Depth ✓ — **uncontested
lead.** This is the moat. The instruction here is the inverse of every other
feature: **do not chase parity — press the advantage.** Surface evidence more
prominently, make it the headline of the Professional persona, market it.

### 12. Battery & tracking architecture — vs Strava's foreground model

**Competitor:** Strava's battery is "acceptable" only because it tracks
high-accuracy GPS *solely during a user-started recording*; it does ~nothing in
the background.

**Voyager (code):** `AdaptiveSamplingPolicy` — STILL/MOVING/DORMANT states,
a sleep window, a battery-saver multiplier, a DORMANT state that turns GPS fully
off behind a significant-motion wake; geofence-based exit detection. `QualityScorer`
is Doze-aware.

**Verdict:** Logic ✓ · Data ✓ · Parameters ◐ · UI ◐ · Depth ◐. The adaptive
engine is genuinely good. The gaps are **conceptual/UX, not algorithmic**: the
tiers aren't exposed as a clear user-facing model, there is no battery-budget
mode, and there is no Passive-only tier for a fitness user who wants near-zero
background cost. **Fix (partial):** formalise the 5 tiers (Off/Passive/Balanced/
Accurate/Workout), a battery-budget setting, and surface honest per-tier numbers
(see blueprint Part 7.5). The single-app fitness user is the only real
battery-loss case; the multi-app user is a battery *win*.

### 13. Onboarding & UX polish — vs Arc

**Competitor:** Arc — minimal, beautiful, fast-to-value onboarding.

**Voyager (code):** a clean 7-phase `AppPhase` flow exists (splash → restore →
google-import → permissions → persona → walkthrough). Functional, not yet
beautiful; the first hour can feel empty before place detection triggers.

**Verdict:** Logic ✓ · Data ✓ · Parameters ✓ · UI ◐ · Depth ◐. Flow is right;
polish and motion are missing. **Fix (partial):** the visual/motion pass
(blueprint Parts 4–8) + a live "capturing now" pulse for the empty first hour.

### 14. Data portability / export-import — vs all

**Competitor:** most rivals are cloud-locked; exports are weak or absent.

**Voyager (code):** export GPX/GeoJSON/CSV/VoyagerJSON, date ranges, raw-sample
inclusion; import `.voyager` round-trip + Google Timeline import; coordinate
stripping.

**Verdict:** Logic ✓ · Data ✓ · Parameters ✓ · UI ✓ · Depth ✓ — **Voyager
leads.** Nothing to fix; market it as a GDPR-portability and anti-lock-in
strength. (The `ExportFormatPlugin` interface — see backlog H — is internal
extensibility, not a user gap.)

---

## Part 3 — The honest scorecard

| # | Feature | Logic | Data | Params | UI | Depth | Net vs specialist |
|---|---|---|---|---|---|---|---|
| 1 | Place detection | ◐ | ✓ | ✓ | ✓ | ◐ | slightly behind Google |
| 2 | Activity classification | ◐ | ✓ | ✓ | ✓ | ◐ | behind Google/Arc |
| 3 | Place naming/geocoding | ✓ | ✓ | ✓ | ✓ | ◐ | behind on data coverage only |
| 4 | Timeline construction | ✓ | ✓ | ✓ | ◐ | ✓ | parity (UI lagging) |
| 5 | Trip detection/stories | ✓ | ◐ | ◐ | ◐ | ◐ | behind Polarsteps on polish |
| 6 | Mileage logging | ✓ | ✓ | ✓ | ◐ | ✓ | **ahead of MileIQ** |
| 7 | Photo Day Story | ✓ | ✓ | ◐ | ◐ | ✓ | parity, more private |
| 8 | Fitness/workout | ✗ | ✗ | ✗ | ✗ | ✗ | **absent — full gap** |
| 9 | Maps & routes | ✓ | ✓ | ✓ | ◐ | ◐ | behind Google tiles |
| 10 | Insights/analytics | ✓ | ✓ | ◐ | ◐ | ✓ | **ahead on breadth** |
| 11 | Evidence/explainability | ✓ | ✓ | ✓ | ✓ | ✓ | **uncontested lead** |
| 12 | Battery/tracking arch | ✓ | ✓ | ◐ | ◐ | ◐ | parity (UX-exposed gap) |
| 13 | Onboarding/polish | ✓ | ✓ | ✓ | ◐ | ◐ | behind Arc on polish |
| 14 | Export/import | ✓ | ✓ | ✓ | ✓ | ✓ | **leads the field** |

**Reads:** Voyager **leads** on Mileage, Insights breadth, Evidence, Export — and
should market those loudly. It is at **parity** on the Timeline engine and
Battery architecture (the gaps there are UI/UX, cheap to close). It is
**genuinely behind** on place/activity inference (structural — heuristic vs ML)
and place-name coverage (structural — OSM vs Google data). It has **one outright
hole**: fitness/workout recording.

---

## Part 4 — Improvement backlog (depth gaps → actions)

Ordered by leverage. Each: gap type · fix size · files.

1. **Workout/fitness subsystem** — _feature · full._ New `Activity` entity +
   schema migration, foreground Workout tracking tier, Record + Activities
   screens, live stats, GPX-per-activity export. Builds on `LocationCapture`,
   `LocationKalmanFilter`, the export layer. **Unblocks the Athlete persona.**
2. **POI prior into place confirmation** — _logic · partial._ Feed
   `OverpassGeocodingProvider` POI hits as a confidence + naming prior in
   `DetectVisitUseCase` / place confirmation; use `PlaceRollup.repeatabilityScore`
   to confirm faster. Closes most of the Google place-detection gap cheaply.
3. **Trip storytelling depth** — _data + UI · full._ Add user-authored fields to
   `TripEntity` (title, notes, cover photo, per-day captions) via a schema
   migration; rebuild `TripDetail` + `TripBookPdfExporter` photo-forward.
4. **Timeline ribbon UI** — _UI · partial._ Rebuild `TimelineScreen` as the
   continuous Arc-style ribbon (blueprint Part 7.1).
5. **Mileage swipe-to-classify** — _UI · partial._ Swipe gestures on
   `MileageScreen` rows; fat deductible total.
6. **5-tier tracking model + battery budget** — _parameters + UI · partial._
   Formalise `AdaptiveSamplingPolicy` into Off/Passive/Balanced/Accurate/Workout;
   add a battery-budget setting; surface honest %/day numbers.
7. **Accelerometer signature for transit/drive/cycle** — _logic · partial._ Add
   an accelerometer-variance signal to `FuseActivityStateUseCase`.
8. **Heatmap / Year-in-Review** — _UI + logic · partial._ New `HeatmapCalendar`
   component + an annual-recap builder over the rollup tables.
9. **Custom map style + offline tiles** — _UI · partial._ A polished MapLibre
   style; wire `offlineMapsEnabled`; persist camera state.
10. **Day Story "On this day"** — _logic + UI · partial._ A resurfacing card from
    the rollup history.
11. **Evidence prominence** — _UI · partial._ Surface `EvidenceCard` higher; make
    it the Professional persona's hero. (Press the moat — not a gap, an investment.)

---

## Part 5 — Strategic read

**Where to invest depth (close the gap):**
- **Fitness/workout** — the only full hole; required for a whole persona.
- **Trip storytelling** — the difference between "competes with" and "beats"
  Polarsteps.
- **POI prior** — a cheap, high-impact way to narrow the Google place gap.

**Where to accept "good enough" (a generalist need not beat a specialist):**
- **Activity classification** — chasing Google's ML is a losing race; instead
  make every classification *correctable and explained*. The `EvidenceCard` +
  `CorrectionType` system already turns a wrong guess into a 2-tap fix — that is
  the right answer, not a bigger model.
- **Place-name coverage** — OSM will never match Google's POI database. Don't
  fight data with data; ship the OSM contribution loop and make it the story.
- **Maps** — OSM tiles are fine; a good custom style closes most of the gap.
- **Social insights** — thin and not core; deepen lightly or cut.

**Where to press the moat (don't catch up — pull ahead):**
- **Evidence / explainability** — uncontested. Make it louder.
- **Export / portability** — leads the field. Market as anti-lock-in + GDPR.
- **Mileage with GPS evidence** — already beats MileIQ. Polish the UI and sell it.
- **The bundle + privacy** — no competitor is private *and* broad on Android.

**The one-line strategic verdict:** Voyager does not need to out-ML Google or
out-social Strava. It needs to (a) fill the one hole — workout recording, (b)
polish the UI to Arc's bar, and (c) shout about the four things it already wins —
evidence, export, mileage, and the private bundle. The engine is deep enough; the
gaps are mostly surface and one missing subsystem.
