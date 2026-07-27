# Voyager — Master Feature Catalog & UI/UX Design Spec

> Single source of truth for **what Voyager does** and **how every screen should look**.
> Authored to be pasted — whole or section-by-section — into AI design tools (Figma Make / Stitch, Google Antigravity, Claude design) to generate on-brand screens.

---

## Part 0 — How to use this document

- **Part 1 — Feature Catalog**: everything Voyager does, organized by the three product pillars (Memory / Proof / Habits) plus the invisible Engine and the aspirational frontier. Read this to understand the product surface.
- **Part 2 — Information Architecture**: the navigation map and global chrome rules. Read this to understand how screens connect.
- **Part 3 — Per-Screen Specs**: a uniform spec block per screen (purpose, layout zones, components, data, states, motion, brand notes). Read this to design a specific screen.
- **Part 4 — Design Tokens**: exact, copy-ready colors, gradients, type, spacing, and component variants. **Any tool generating a screen must honor these** so output stays on-brand.

**Status tags** (used on every feature and screen):

| Tag | Meaning |
|-----|---------|
| ✅ | **Shipped** — built and working today |
| ◐ | **Parity-polish** — exists in code but the UI/UX needs work to match or beat rivals |
| ✦ | **New / aspirational** — not yet built; a competitive gap to close |

**Competitor shorthand:** **GT** = Google Timeline · **Arc** = Arc Timeline (iOS) · **Poly** = Polarsteps · **MileIQ** · **Strava** · **L360** = Life360.

**Identity in one line:** Voyager is a privacy-first, **local-first**, dark-only Android timeline app doing **three jobs in one** — **Memory** (where was I), **Proof** (tax mileage & legal-grade evidence), **Habits** (patterns & insights) — with everything encrypted on-device (SQLCipher), no cloud, no API keys, and an **evidence-backed UI** where every claim is traceable to recorded data.

---

## Part 1 — Feature Catalog

Each pillar lists features as **Feature · Function · Status · Competes-with · Surfaced on**.

### Pillar 1 — Memory · Daily Timeline & Places

| Feature | Function | Status | Competes | Surfaced on |
|---------|----------|--------|----------|-------------|
| Timeline construction | Assembles a day's segments, visits & routes into a `TimelineDay`; day-crossing segments assigned to start day | ✅ | GT, Arc | Timeline |
| Visit detection | State machine: 3-min dwell threshold, hysteresis, 30-min return detection, arrival/departure confidence | ✅ | GT, Arc | Timeline, Map, Place Detail |
| Movement segmentation | Classifies WALK / RUN / CYCLE / DRIVE / TRANSIT / GAP by speed + transport-mode confidence | ◐ | GT, Arc | Timeline, Map |
| Place clustering | Live hysteresis match + batch HDBSCAN discovery on unassigned visits; centroid, radius, confidence tiers | ✅ | GT | Place Detail, Map |
| Multi-provider geocoding | Provider chain [Overpass → Android Geocoder → Photon → Nominatim], 90-day cache, conflict resolution | ✅ | GT | Place Detail |
| Place naming | Priority: user name → category → provider → semantic (Home/Work) → coordinates | ◐ | GT, Arc | Place Detail, Place Review |
| Semantic inference | Auto-labels HOME / WORK from nighttime/weekday dwell patterns | ✅ | GT, Arc | Dashboard, Place Detail |
| Route encoding | Encoded polyline + Douglas-Peucker simplification + bounding box for map render | ✅ | GT, Arc | Map, Segment Detail |
| Gap handling | Explicit GAP segments with reason (PERMISSION/DOZE/PROCESS_DEAD/GPS_LOSS/MANUAL_PAUSE) — never silently hidden | ✅ | — *(unique honesty)* | Timeline |
| Full-text search | Room FTS4 over place names, categories, dayKey, segment type, geocode text; relevance boost for renamed places | ✅ | GT | Search |
| Photo Day Story | On-device MediaStore photo↔visit correlation by capture time + EXIF-GPS; "unplaced" bucket | ✅ (Pro) | GT, Poly | Day Story |
| First-visit place priors | POI + visit-history priors to name a place correctly on first visit | ✦ | GT (ML), Arc | Place Detail |
| On-this-day resurfacing | Anniversary memory cards ("a year ago you were…") | ✦ | GT, Poly | Dashboard, Insights |

### Pillar 2 — Proof · Mileage, Trips & Evidence

| Feature | Function | Status | Competes | Surfaced on |
|---------|----------|--------|----------|-------------|
| Mileage logging | Over DRIVE segments; purpose, notes, GPS evidence; IRS-2025 / HMRC rate PDF | ✅ (Pro) | MileIQ | Mileage |
| Court-grade GPS evidence | Each mileage row exports raw GPS samples for audit/legal use | ✅ (Pro) | — *(leads MileIQ)* | Mileage, Evidence sheets |
| Trip detection | Auto-detects away-from-home runs ≥2 days, Home-anchored, absorbs no-data gaps | ✅ | Poly, Arc | Trips, Trip Detail |
| TripBook PDF | Printable travel journal: cover + per-day entries | ✅ (Pro) | Poly | Trip Detail |
| Carbon footprint | DRIVE emissions by vehicle type + EPA rates; per-segment/day/week/month | ✅ | — *(differentiator)* | Insights (Carbon) |
| Multi-format export | GPX / GeoJSON / CSV / VoyagerJSON; date ranges; raw-sample toggle; coordinate stripping | ✅ | GT (Takeout) | Export |
| Import | `.voyager` round-trip restore + Google Timeline import | ✅ | — | Onboarding, Settings |
| Segment evidence | Speeds, sample count, activity votes, steps, heading, provider mix, **counter-evidence**, human explanation | ✅ | — *(moat)* | Segment Detail |
| Visit evidence | Entry/exit sample IDs, dwell curve, inside/outside counts, suppression reasons, confirmation rule | ✅ | — *(moat)* | Visit Detail |
| Place evidence | Cluster density, visit counts (7/30d), avg dwell, repeatability, naming candidates ranked, category reasoning | ✅ | — *(moat)* | Place Detail |

### Pillar 3 — Habits · Analytics & Insights

| Feature | Function | Status | Competes | Surfaced on |
|---------|----------|--------|----------|-------------|
| Dashboard hero | Activity rings, streak counter, top places, anomaly flags, live distance/steps before places exist | ◐ | GT, Arc | Dashboard |
| Daily rollups | Distance, steps, dwell, transit, active time, unique places, dominant mode, anomaly flags | ✅ | — | Insights, Dashboard |
| Weekly rollups | Avg/total distance & steps, active days, top-5 places, transport distribution, week-over-week | ✅ | — | Insights (Weekly) |
| Place rollups | Per-place visit counts (7/30/90d), dwell stats, dominant day-of-week & time-of-day | ✅ | GT | Place Detail |
| Weekly comparison | Period-over-period deltas with trend (UP/DOWN/STABLE) + highlights ("walked 23% more") | ✅ | — | Insights (Weekly) |
| Anomaly detection | >2σ deviation from 30-day baseline; MILD/NOTABLE/SIGNIFICANT severity | ✅ | — *(differentiator)* | Insights, Dashboard |
| Recurring patterns | Place visit patterns, dominant times, routine shifts | ✅ | — | Insights (Patterns) |
| Insights generation | Routine / trend / anomaly / place / achievement insights, worker-cached | ◐ | GT, Arc | Insights (Overview) |
| 7 insight tabs | Overview · Movement · Patterns · Places · Weekly · Carbon · Social (Pro) | ◐ | Arc, Strava | Insights |
| Heatmap calendar | GitHub-style activity calendar of daily intensity | ✦ | Strava, GT | Insights |
| Year-in-Review | Shareable annual recap (distance, places, trips, superlatives) | ✦ | Strava, Poly, Spotify-style | Insights |

### Pillar 4 — Control · Settings, Presets & Calibration

| Feature | Function | Status | Competes | Surfaced on |
|---------|----------|--------|----------|-------------|
| 4-tier settings | General · Detection · Privacy & Data · Advanced (15+ categories) | ◐ | all | Settings |
| Sampling presets | 5 general (Battery Saver, Daily Commuter, Cyclist, Privacy Max, Precision Max) | ✅ | — | Settings (General) |
| Traveler presets | 6 personas (City Explorer, Short Tripper, Long Traveler, Road Tripper, Transit Commuter, Backpacker) — reshape detection | ✅ | — *(differentiator)* | Settings, Onboarding |
| Persona behaviors | Each preset changes how the pipeline detects places, routes, insights | ✅ | — | Settings |
| User-correction loop | 13 correction types (rename, category, reclassify, merge, split, delete, adjust, confirm) logged as feedback | ✅ | Arc | Place Detail, Timeline, Place Review |
| Feedback calibration | 30-day correction aggregation auto-tunes confidence weights, HDBSCAN params, dwell thresholds | ✅ | — *(moat)* | (invisible) |

### Pillar 5 — Engine · Tracking, Reliability & Privacy *(mostly invisible; surfaced via Reliability/Dashboard)*

| Feature | Function | Status | Surfaced on |
|---------|----------|--------|-------------|
| Adaptive GPS sampling | 90s still / 12s walk / 7s drive; battery-saver multiplier | ✅ | Reliability, Settings |
| Dormant + sleep detection | GPS off after 4.5 min inactivity; sleep window 23:00–07:00; wakes on significant motion | ✅ | Reliability |
| Kalman filter | 4-state constant-velocity GPS smoothing | ✅ | (invisible) |
| Activity fusion | Merges AR API + pedometer + speed heuristics into one motion state | ◐ | (invisible) |
| WiFi fingerprinting | SSID/BSSID signal for indoor place matching | ✅ | (invisible) |
| 9 WorkManager workers | DiscoverPlaces, GeocodeBackfill, DailyRollup, WeeklyRollup, SemanticLabel, DataRetention, IntegrityRepair, SearchIndex, StepSync | ✅ | Reliability |
| Crash / boot restore | Detects dead session, writes CRASH_RESTORE GAP, resumes | ✅ | Reliability |
| Permission degradation | FINE → COARSE → AR-only → paused → background-restricted fallback chain | ✅ | Dashboard banner, Reliability |
| Health monitoring | Heartbeats on samples, notifications, workers, commits, latency; threshold alerts | ✅ | Reliability |
| SQLCipher encryption | All data encrypted at rest from day one | ✅ | (invisible) |
| FLAG_SECURE | Hides app content from screenshots/recents (privacy toggle) | ✅ | Settings (Privacy) |

### Pillar 6 — Aspirational Frontier *(✦ — Phase 5 + competitive gaps)*

| Feature | Function | Status | Competes | New screen(s) |
|---------|----------|--------|----------|---------------|
| Workout / active recording | Start/stop a recorded activity with live stats (pace, HR-less distance, splits), `Activity` entity, per-activity GPX export | ✦ | **Strava** | Workout Recording, Live Stats |
| Family "I'm safe" handshake | Encrypted one-bit safe signal to a trusted contact — no continuous location share | ✦ | **L360** | Family Safety |
| Duress / panic-wipe | Cryptographic SQLCipher key destruction for at-risk users (journalists, activists) | ✦ | — *(unique)* | Duress Setup |
| OSM contribution loop | Push place renames / missing POIs back to OpenStreetMap (OAuth + changeset API) | ✦ | — *(unique)* | OSM Contribution |
| Social / share surfaces | Shareable trip cards, year-in-review, place highlights | ✦ | Strava, Poly | Share sheets |

### Competitive Positioning Matrix

Cells: **▲ leads · = parity · ▽ gap**

| Capability | GT | Arc | Poly | MileIQ | Strava | L360 |
|-----------|:--:|:--:|:--:|:--:|:--:|:--:|
| Timeline construction | = | = | — | — | — | — |
| Place naming richness | ▽ | ▽ | — | — | — | — |
| Activity classification (ML vs heuristic) | ▽ | ▽ | — | — | ▽ | — |
| Honest gaps / explainability | ▲ | ▲ | ▲ | ▲ | ▲ | ▲ |
| Mileage + GPS evidence | ▲ | — | — | ▲ | — | — |
| Trip detection + printed book | = | = | = | — | — | — |
| Analytics breadth (anomaly, carbon) | ▲ | ▲ | ▲ | — | = | — |
| Data portability / export | ▲ | = | ▲ | ▲ | = | ▲ |
| Battery / tracking architecture | = | = | = | — | = | = |
| Privacy / on-device / encryption | ▲ | = | ▲ | ▲ | ▲ | ▲ |
| Fitness / workout recording | — | — | — | — | ▽ | — |
| Family safety | — | — | — | — | — | ▽ |

**Read:** Voyager **leads** on mileage-evidence, explainability/honest-gaps, analytics breadth, export, and privacy. It is at **parity** on timeline, trips, and battery. Its **gaps** are place-naming richness and ML activity classification (structural — OSM data + heuristics), plus two whole missing categories — **fitness recording** (Strava) and **family safety** (L360) — which the Aspirational Frontier targets.

---

## Part 2 — Information Architecture & Navigation

### Navigation map

```
ONBOARDING (first run / permission reset)
  Splash → Restore → Google-Timeline Import → Permissions → Persona Pick → Feature Walkthrough
        │
        ▼
MAIN SCAFFOLD  (top bar + bottom nav, dark-only)
  ├─ Top bar (visible only on the 4 tabs)
  │    Logo (Orion's-Belt 3-dot + "Voyager" Great Vibes → story sheet)
  │    Actions: Search · Notifications/Place-Review (badge) · Settings (gear)
  │
  ├─ BOTTOM NAV — 4 tabs (state-preserving, single-top)
  │    1. Home (Dashboard)   2. Map   3. Timeline   4. Insights (7 sub-tabs)
  │
  ├─ PUSH screens (full-screen, back pops)
  │    Settings (4 tabs) · Search · Place Review · Place Detail{id} · Categories
  │    Export · Reliability · Paywall · Mileage(Pro) · Trips · Trip Detail{id}
  │    Day Story(Pro) · Developer Profile · Open-source Licenses · Feedback
  │    [DEBUG] Debug Data Insertion · Pipeline Debug
  │
  ├─ MODAL SHEETS (overlay)
  │    Segment Detail{id} · Visit Detail{id} · Voyager Story · Rename Place dialog
  │
  └─ ✦ NEW attach points
       Workout Recording  → FAB on Dashboard/Map  (new primary action)
       Year-in-Review/Heatmap → Insights (new tab/card)
       Family Safety / Duress → Settings ▸ Privacy & Data
       OSM Contribution → Place Detail overflow + Settings ▸ Advanced
```

### Routes (authoritative, from `VoyagerDestination.kt`)

`home` · `map` · `timeline` · `insights` · `settings` · `search` · `export` · `place_detail/{placeId}` · `segment_detail/{segmentId}` · `visit_detail/{visitId}` · `place_review` · `categories` · `developer_profile` · `open_source_licenses` · `feedback` · `reliability` · `mileage` · `paywall` · `trips` · `trip_detail/{tripId}` · `day_story?dayKey={dayKey}` · `debug_data_insertion` · `pipeline_debug`.

> Note: there is **no dedicated `places` list route** — place browsing happens via Map markers, Search, Categories, and Place Review. ✦ A consideration: add a first-class **Places** browse surface (list/grid) if user testing shows demand.

### Global chrome rules

- **Top bar**: transparent container over `VoyagerGradients.topBar`; logo left, 3 actions right; visible **only** on the 4 bottom-nav tabs, hidden on push screens.
- **Bottom nav**: `NavigationBar` over `VoyagerGradients.navBar`; 4 items icon+label; `saveState`/`restoreState`/`launchSingleTop`.
- **Background**: every screen draws `VoyagerGradients.screenBackground(w,h)` behind content.
- **States convention**: every data screen defines **empty / loading / error / live**; loading = `ShimmerCard`, empty/error = `EmptyStateComposable` (typed: NO_TRACKING, NO_PERMISSION, NO_PLACES, NO_INSIGHTS) with an actionable CTA.
- **Pro gating**: wrap Pro content in `FeatureGate`; locked state uses **Premium gold** sparingly + routes to Paywall.
- **Privacy**: `FLAG_SECURE` toggles from Privacy settings (blanks app in recents/screenshots).
- **Honesty rule**: never hide missing data — render GAP/empty explicitly with reason.

---

## Part 3 — Per-Screen Design Specs

Each block: **Purpose · Status · Layout zones (top→bottom) · Components · Data · States · Motion · Brand notes.** Component names refer to the real library in Part 4.

### Core tabs

#### Dashboard (`home`) ◐
- **Purpose:** central hub — am I tracking, what happened today, what's notable.
- **Layout zones:** TopBar → Permission/degradation banner (conditional) → **Tracking-status hero** (live pulse + today's distance/steps) → Activity rings → Streak counter → Top places → Anomaly flags → Quick-action cards (Mileage, Trips, Export).
- **Components:** `VoyagerCard(HIGHLIGHTED)` hero, `PulsingDot`, ActivityRings (custom canvas), `VoyagerBadge`, `SectionHeader`, `ConfidenceBar`, `PermissionReminderBanner`, `ShimmerCard`, `EmptyStateComposable(NO_TRACKING)`.
- **Data:** runtime tracking state, daily rollup (distance/steps/active), streak, top-N places, anomaly list.
- **States:** *empty* = "I'm capturing now" live-pulse card so the first hour isn't blank; *loading* = shimmer rings; *live* = pulsing hero + rising counters; *error/permission* = degradation banner with fix CTA.
- **Motion:** animated counters, ring sweep on load, `PulsingDot` for live, predictive-back.
- **Brand:** hero uses `heroCard` (or `activeCard` when live); numbers in `MonoStatLarge`; rings glow via `primaryGlow`.

#### Map (`map`) ✅
- **Purpose:** spatial day view — routes + visit markers on MapLibre/OSM.
- **Layout zones:** Full-bleed map → `DayNavigator` overlay (top) → floating detail sheet (bottom, on marker/route tap).
- **Components:** `AndroidView`(MapLibre), `DayNavigator`, transport-colored polylines, visit markers, `VisitDetailSheet` / `SegmentDetailSheet`.
- **Data:** day routes (encoded polylines by mode), visit markers, day bounding box.
- **States:** *empty* = centered EmptyState over muted map; *loading* = shimmer over map; *live* = current position pulse.
- **Motion:** camera fly-to on day change, marker tap → sheet slide-up.
- **Brand:** polylines use Transport-mode colors (Walk green / Drive purple / Cycle blue / Transit orange / Gap grey-dashed); markers tinted by place category.

#### Timeline (`timeline`) ◐
- **Purpose:** chronological story of a day — segments, visits, gaps, routes.
- **Layout zones:** TopBar → `DayNavigator` → `PeriodSelectorBar` → vertical timeline (connector rail + segment/visit cards) → tap-through to detail sheets; day-header → Day Story deep-link.
- **Components:** `DayNavigator`, `PeriodSelectorBar`, segment cards (`VoyagerCard`), TimelineConnector rail, `ConfidenceBar`, `VoyagerBadge`(mode), `EmptyStateComposable`.
- **Data:** ordered segments (mode, start/end, distance, confidence), visits (place, dwell), GAP reasons, route refs.
- **States:** *empty* = "no movement recorded" honest card; *loading* = shimmer rows; *gap* = explicit dashed GAP card with reason; *user-corrected* = edited badge.
- **Motion:** connector draw-in, card tap → detail sheet, swipe to correct (reclassify).
- **Brand:** mode color on connector + badge; timestamps `MonoTimestamp`; distances `MonoStatSmall`. ◐ This is the highest-leverage screen for out-polishing GT/Arc.

#### Insights (`insights`) ◐ — 7 sub-tabs
- **Purpose:** analytics hub.
- **Layout zones:** TopBar → `ScrollableTabRow` (Overview · Movement · Patterns · Places · Weekly · Carbon · Social) → per-tab scrollable content → `FeatureGate` on Social.
- **Components:** `ScrollableTabRow` (M3 default indicator, `contentColor` for color — never custom SecondaryIndicator lambda), stat cards, charts, `ConfidenceBar`, `VoyagerBadge`, `FeatureGate`, ✦ heatmap calendar, ✦ Year-in-Review card.
- **Data per tab:** Overview = generated insights + anomalies; Movement = distance/steps/mode split; Patterns = recurring places/times; Places = top places + rollups; Weekly = comparison deltas; Carbon = emissions; Social = Pro share.
- **States:** *empty* = `EmptyStateComposable(NO_INSIGHTS)` ("keep moving to unlock insights"); *Pro-gated* = blurred preview + Premium unlock.
- **Motion:** tab slide, chart grow-in, counter animation.
- **Brand:** stats `MonoStatLarge/Medium`; trend arrows in Success/Error; Carbon uses AccentGreen; Pro in Premium gold.

### Places & corrections

#### Place Detail (`place_detail/{placeId}`) ◐
- **Purpose:** full profile of one place + corrections + evidence.
- **Zones:** Header (name + category + Home/Work badge) → map snippet → visit history → patterns (dominant day/time) → **evidence** (cluster density, naming candidates, category reasoning) → correction actions.
- **Components:** `RenamePlaceDialog`, category selector, `VoyagerCollapsibleSection` (evidence), `ConfidenceBar`, visit-history list, `VoyagerButton`(merge/split/rename).
- **Data:** place entity, place rollup, place evidence, geocode candidates ranked.
- **States:** *unnamed* = coordinate name + prompt to rename; *low-confidence* = amber ConfidenceBar.
- **Motion:** collapsible evidence expand; rename dialog.
- **Brand:** confidence-tier colors; naming candidates in `MonoData`; ✦ OSM "improve this place" in overflow.

#### Place Review (`place_review`) ◐
- **Purpose:** queue of unconfirmed visit candidates to confirm/edit/reject.
- **Zones:** TopBar → queue list (one card per candidate: where, when, dwell, confidence) → per-card actions (Confirm / Rename / Reject).
- **Components:** `VoyagerCard`, `ConfidenceBar`, `VoyagerBadge`(count on bell), swipe actions, `EmptyStateComposable`.
- **Data:** pending visit candidates + evidence.
- **States:** *empty* = "all caught up"; badge count drives top-bar bell.
- **Motion:** swipe-to-confirm/reject, card dismiss.
- **Brand:** badge on bell; confidence color.

#### Search (`search`) ✅
- **Purpose:** find places/days/visits via FTS + filters.
- **Zones:** Search field → filter chips (date range, categories, modes, dwell) → ranked results grouped by type.
- **Components:** search field, `FilterChip` row, `PeriodSelectorBar`, result cards, `EmptyStateComposable`.
- **Data:** FTS results (place/day/visit) with relevance boost.
- **States:** *idle* = recent/suggested; *no-results* = empty with query echo.
- **Brand:** matched terms highlighted in Primary; timestamps `MonoTimestamp`.

#### Categories (`categories`) ◐
- **Purpose:** manage place categories.
- **Zones:** category list/grid → per-category edit → place reassignment.
- **Components:** `VoyagerCard`, category chips, `SectionHeader`.
- **States:** standard empty/loading.

### Proof screens

#### Mileage (`mileage`) ✅ Pro
- **Purpose:** classify DRIVE segments by purpose; export IRS/HMRC PDF.
- **Zones:** TopBar → `PeriodSelectorBar` → summary (total miles, deductible $) → drive list (swipe to classify Business/Personal) → Export-PDF CTA.
- **Components:** `FeatureGate`, `VoyagerCard`, swipe actions, `PeriodSelectorBar`, `VoyagerButton`(export), `ConfidenceBar`.
- **Data:** DRIVE segments, purpose/notes, IRS rate, GPS evidence per row.
- **States:** *Pro-gated*, *empty* = "no drives yet".
- **Brand:** money + miles in `MonoStatLarge`; purpose chips; evidence link per row (court-grade differentiator).

#### Trips (`trips`) ✅ / Trip Detail (`trip_detail/{tripId}`) ✅ Pro PDF
- **Purpose:** list detected multi-day trips; deep-dive one trip.
- **Trips zones:** filter → trip cards (dates, distance, primary mode, place count).
- **Trip Detail zones:** Header (title, span, hero map) → per-day breakdown → segments/visits → **TripBook PDF** export.
- **Components:** `VoyagerCard`, MapLibre route hero, `DayNavigator`(within trip), `FeatureGate`(PDF), `VoyagerButton`.
- **Data:** trip entity (duration, distance, mode, visited places), per-day re-derivation.
- **States:** *empty* = "no trips detected yet"; ✦ user-authored title/notes/cover.
- **Brand:** hero uses `heroCard`; distances mono; PDF button Premium gold.

#### Day Story (`day_story?dayKey=`) ✅ Pro
- **Purpose:** photo-centric narrative of a day.
- **Zones:** day header → photo↔visit timeline → "unplaced photos" bucket.
- **Components:** `FeatureGate`, photo grid, visit cards, `EmptyStateComposable`.
- **Data:** MediaStore photos correlated to visits by time + EXIF-GPS.
- **States:** *no-photos* = friendly empty; *Pro-gated*.
- **Brand:** photo-forward, minimal chrome; timestamps mono.

#### Export (`export`) ✅
- **Purpose:** export data in chosen format/range with privacy controls.
- **Zones:** format selector (GPX/GeoJSON/CSV/VoyagerJSON) → date range → toggles (raw samples, strip coordinates) → Export CTA + Import entry.
- **Components:** segmented format chips, `PeriodSelectorBar`, toggles, `VoyagerButton`.
- **States:** *exporting* = progress; *success* = share sheet.
- **Brand:** privacy toggles emphasized; standard surfaces.

### Engine / system

#### Reliability (`reliability`) ◐
- **Purpose:** show tracking health, battery, worker status — build trust.
- **Zones:** tracking-state hero → battery self-report → worker heartbeats → health-log timeline → force-stop/optimization warnings.
- **Components:** `VoyagerCard`, `PulsingDot`, status rows, `VoyagerBadge`(health), `ConfidenceBar`(coverage).
- **Data:** runtime state, heartbeats (sample/notification/worker/commit/latency), health log.
- **States:** *healthy* = green; *degraded* = warning rows + fixes.
- **Brand:** Success/Warning/Error semantic; latency in `MonoData`.

#### Settings (`settings`) ◐ — 4 tabs
- **Purpose:** all configuration.
- **Zones:** `ScrollableTabRow` (General · Detection · Privacy & Data · Advanced) → grouped `VoyagerCollapsibleSection`s of toggles/selectors/presets.
- **Components:** `VoyagerCollapsibleSection`, switches, preset picker cards, `SectionHeader`, sliders. ✦ Family Safety / Duress live under Privacy & Data.
- **Data:** UserPreferences (15+), preset definitions, calibration profile.
- **States:** preset-applied confirmation; destructive (duress) double-confirm.
- **Brand:** Title-Case labels (Inter); Premium gold on Pro-linked rows; this large screen is a refactor/polish candidate (◐). Use M3 default tab indicator with `contentColor`.

#### Paywall (`paywall`) ◐
- **Purpose:** sell Pro.
- **Zones:** hero value-prop → Pro feature list (Mileage, Day Story, advanced insights, extended export) → pricing → purchase buttons → restore.
- **Components:** `FeatureGate` host, feature rows, `VoyagerButton`(buy), Premium accents.
- **Brand:** **Premium gold** is the lead accent here (the one screen where gold dominates); confident, not spammy.

### Onboarding set ✅

| Screen | Purpose | Key components | Brand |
|--------|---------|----------------|-------|
| Animated Splash | 2s branded color/scale animation | logo canvas | Great Vibes wordmark, animated gradient |
| Restore | one-time backup restore | `VoyagerButton`, file picker | minimal |
| Google Timeline Import | migrate from GT | import card, progress | reassurance copy |
| Permissions | sequential runtime permission flow | `PermissionRequestCard`, rationale | honest "why we ask" |
| Persona Pick | choose Memory/Proof/Habits + traveler preset | selectable persona cards | `heroCard` on selection |
| Feature Walkthrough | 3-page animated intro | pager, illustrations | motion-rich |

### Secondary / dev

| Screen | Status | Notes |
|--------|--------|-------|
| Developer Profile (`developer_profile`) | ✅ | credits + license entry |
| Open-source Licenses (`open_source_licenses`) | ✅ | scrollable attribution list |
| Feedback (`feedback`) | ✅ | category + text bug/feature form |
| Debug Data Insertion (`debug_data_insertion`) | ✅ DEBUG | synthetic test data |
| Pipeline Debug (`pipeline_debug`) | ✅ DEBUG | latency, confidence, provider override |
| Segment Detail sheet | ✅ | route stats + evidence |
| Visit Detail sheet | ✅ | visit stats + evidence + photos |

### ✦ New aspirational screens

#### Workout Recording + Live Stats ✦ *(Strava gap)*
- **Purpose:** explicitly record an activity with live feedback.
- **Zones:** big Start/Stop control → live stats grid (duration, distance, pace, splits) → mini live map → save (type, title) → post-save summary + GPX export.
- **Components:** large circular record button, `MonoStatLarge` stat tiles, live MapLibre, `PulsingDot`, save sheet.
- **Data:** new `Activity` entity (samples, splits, type), per-activity GPX.
- **States:** *idle / recording (active green) / paused / saved*.
- **Brand:** `activeCard` + AccentGreen while recording; stats dominate in mono; entry via Dashboard/Map **FAB**.

#### Year-in-Review / Heatmap ✦
- **Purpose:** shareable annual recap + activity heatmap.
- **Zones:** heatmap calendar → superlatives (most-visited place, longest trip, total distance) → shareable cards.
- **Components:** heatmap grid (intensity scale on Primary), stat cards, share sheet.
- **Brand:** intensity ramp from Surface → Primary; celebratory but on-brand dark; mono numbers.

#### Family Safety ✦ *(L360 gap)* / Duress Setup ✦
- **Family:** trusted-contact list + one-bit "I'm safe" send/receive; **no continuous location**. Privacy-forward copy. Lives in Settings ▸ Privacy.
- **Duress:** opt-in panic-wipe (key destruction) with strong double-confirmation, clear irreversible warning (Error red), and a decoy/duress PIN concept. Security-reviewed before ship.

#### OSM Contribution ✦
- **Purpose:** let a place rename / missing-POI flow write back to OpenStreetMap.
- **Zones:** "improve this place" entry (Place Detail overflow) → edit form → OAuth → changeset confirm.
- **Brand:** framed as community give-back; standard surfaces; success confirmation.

---

## Part 4 — Design Tokens *(honor these exactly)*

> Transcribed verbatim from `presentation/theme/VoyagerColors.kt` and `ui/theme/Type.kt`. **Dark-only. No dynamic color. No light mode.**

### 4.1 Color tokens

**Primary**
| Token | Hex | Use |
|-------|-----|-----|
| Primary | `#3B82F6` | interactive, selected, highlights |
| PrimaryDim | `#2563EB` | pressed / secondary interactive |
| PrimaryContainer | `#1E3A5F` | raised container backgrounds |

**Surfaces** (elevation by brightness: Background < Surface < SurfaceVariant < SurfaceBright)
| Token | Hex | Use |
|-------|-----|-----|
| Background | `#0F0F1A` | screen background (OLED) |
| Surface | `#1A1A2E` | cards |
| SurfaceVariant | `#252540` | elevated cards |
| SurfaceBright | `#2E2E4A` | elevated surface |
| SurfaceOverlay | `#2A2A4A` | bottom sheets, dialogs |

**Text**
| Token | Hex | Use |
|-------|-----|-----|
| OnSurface | `#E8E8F0` | body text (off-white) |
| OnSurfaceVariant | `#8888A0` | secondary / muted |
| OnPrimary | `#FFFFFF` | text on Primary |

**Status & accents**
| Token | Hex | | Token | Hex |
|-------|-----|---|-------|-----|
| Error | `#EF5350` | | AccentBlue | `#42A5F5` |
| ErrorContainer | `#3D1A1A` | | AccentPurple | `#AB47BC` |
| Success | `#66BB6A` | | AccentAmber | `#FFA726` |
| Warning | `#FFA726` | | AccentGreen | `#66BB6A` |
| | | | AccentRed | `#EF5350` |
| | | | AccentOrange | `#FF7043` |

**Premium (Pro — use sparingly)**
| Token | Hex | Use |
|-------|-----|-----|
| Premium | `#E6B450` | Pro tier (warm gold) |
| PremiumDim | `#B8902F` | gold borders / pressed |

**Transport modes** (segments & polylines)
| Mode | Hex |
|------|-----|
| Walk / Run | `#66BB6A` (green) |
| Drive | `#AB47BC` (purple) |
| Cycle | `#42A5F5` (light blue) |
| Transit | `#FF7043` (orange) |
| Gap | `#616161` (grey, dashed) |

**Severity** (anomalies): High = Error red · Medium = Warning amber · Low = AccentBlue · Info = Primary.
**Confidence ramp:** <40% = Error red · 40–70% = Warning amber · >70% = AccentGreen.

### 4.2 Gradients (`VoyagerGradients`)

| Name | Type | Definition | Use |
|------|------|-----------|-----|
| `screenBackground(w,h)` | radial | core `#1C0F42` → Background; center (0.50w, 0.08h), radius 0.60h | every screen backdrop (drawBehind) |
| `heroCard` | vertical | Primary @13% → AccentPurple @5% → Transparent | hero cards |
| `activeCard` | vertical | AccentGreen @10% → Transparent | live-tracking / recording cards |
| `primaryGlow(w,h)` | radial | Primary @28% → Transparent; center mid, radius max(w,h)·0.55 | focal glows (rings, hero icons) |
| `sectionDivider` | horizontal | Transparent → Primary @22% (0.2–0.8) → Transparent | section separators (1dp) |
| `topBar` | vertical | `#15102E` → Background | behind TopAppBar (transparent container) |
| `navBar` | vertical | Background → `#141428` | behind NavigationBar (floating feel) |

**Rule:** never inline `Brush` literals in screens — always pull from `VoyagerGradients`.

### 4.3 Typography — dual-font

Fonts: **Inter** (regular/medium/semibold/bold) for UI text · **JetBrains Mono** (regular/medium/bold) for all numbers/stats/timestamps/coordinates · **Great Vibes** for the branded wordmark.

**Inter (M3 scale)** — size / line / weight:
| Style | px | line | weight | Use |
|-------|----|----|--------|-----|
| displayLarge | 57 | 64 | Bold | big branding |
| displayMedium | 45 | 52 | Bold | |
| displaySmall | 36 | 44 | Bold | |
| headlineLarge | 32 | 40 | SemiBold | screen titles |
| headlineMedium | 28 | 36 | SemiBold | |
| headlineSmall | 24 | 32 | SemiBold | section headers |
| titleLarge | 22 | 28 | Medium | card/dialog titles |
| titleMedium | 16 | 24 | Medium | |
| titleSmall | 14 | 20 | Medium | |
| bodyLarge | 16 | 24 | Normal | main content |
| bodyMedium | 14 | 20 | Normal | descriptions |
| bodySmall | 12 | 16 | Normal | |
| labelLarge | 14 | 20 | Medium | buttons, chips |
| labelMedium | 12 | 16 | Medium | |
| labelSmall | 11 | 16 | Normal | |

**JetBrains Mono (data styles)** — **the rule: any number/stat uses mono:**
| Style | px | line | weight | Example |
|-------|----|----|--------|---------|
| MonoStatLarge | 28 | 36 | Bold | "12.4 km" |
| MonoStatMedium | 20 | 28 | Bold | "3h 24m" |
| MonoStatSmall | 14 | 20 | Medium | "92%" |
| MonoTimestamp | 13 | 18 | Normal | "08:00" |
| MonoData | 12 | 16 | Normal | coordinates |

Text is **Title Case**, never ALL-CAPS.

### 4.4 Shape & spacing

- **Shapes:** Card / sheet / dialog = `RoundedCornerShape(12.dp)` · Button = `8.dp` · avatars/badges = `CircleShape`.
- **Spacing (proposed named scale — currently raw dp; adopt for consistency):** `xs 4 · sm 8 · md 12 · lg 16 · xl 24`. Standard card padding = 16dp.

### 4.5 Component library & variants

| Component | Variants / args | Purpose |
|-----------|-----------------|---------|
| `VoyagerCard` | FLAT · RAISED · HIGHLIGHTED; `tintColor` | container; 12dp; click + haptic |
| `VoyagerButton` / `OutlinedButton` / `TextButton` / `IconButton` | — | actions; haptic on click; 48dp targets |
| `ConfidenceBar` | `confidence: Float`, `source: String?` | color-ramp progress + % |
| `VoyagerBadge` | `color`, `contentColor` | status / count |
| `SectionHeader` | `title`, `trailingAction` | Title-Case section head |
| `VoyagerCollapsibleSection` | expand/collapse + chevron | settings / evidence groups |
| `ShimmerCard` | `height` | loading skeleton |
| `EmptyStateComposable` | NO_TRACKING · NO_PERMISSION · NO_PLACES · NO_INSIGHTS | typed empty states |
| `PulsingDot` | `size`, `color` | live indicator |
| `DayNavigator` | `isToday`, `trailingContent` | prev/next day |
| `PeriodSelectorBar` | presets + custom range | date-range filter chips |
| `FeatureGate` | wraps Pro content | paywall gate |
| `PermissionReminderBanner` / `PermissionRequestCard` | dismissible / per-permission | permission prompts |
| `RenamePlaceDialog` · `SegmentDetailSheet` · `VisitDetailSheet` | M3 dialog / `ModalBottomSheet` | detail / edit overlays |

### 4.6 Design principles (must hold across all generated screens)

1. **Dark-only, OLED-first** — Background `#0F0F1A`; no light mode, no Material You dynamic color.
2. **Evidence-backed UI** — every claim (visit, mode, place) is traceable; expose confidence + evidence, including **counter-evidence**.
3. **Honest gaps** — never silently hide missing data; render GAP/empty explicitly with a reason.
4. **Numbers are mono** — all stats/timestamps/coordinates in JetBrains Mono; UI text in Inter.
5. **Pro reads distinct** — Premium gold used sparingly (Paywall + lock states only), so it signals value, not noise.
6. **Centralized brand** — pull colors/gradients/type from the design system; never inline literals.
7. **Accessibility** — `contentDescription` on icon buttons, color **plus** a second indicator (never color-alone), ≥48dp targets, AA contrast (Primary on Background passes).
8. **Motion with restraint** — pulsing live dots, animated counters, ring sweeps, predictive-back, shared-element transitions; subtle, never gratuitous.

---

### Appendix — Source-of-truth files

- Colors & gradients: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/theme/VoyagerColors.kt`
- Typography: `app/src/main/java/com/cosmiclaboratory/voyager/ui/theme/Type.kt`
- Components: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/theme/VoyagerComponents.kt`
- Navigation: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/navigation/VoyagerDestination.kt`, `app/src/main/java/com/cosmiclaboratory/voyager/MainActivity.kt`
- Related docs (cross-reference, avoid duplication): `docs/ui/README.md`, `docs/design/restart/06_NEXT_GEN_UI_AND_SMART_TRACKING_PLAN.md`, `docs/voyager-feature-depth-reality-check.md`

*End of document.*
