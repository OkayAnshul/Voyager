# Voyager — UX Design Blueprint & Persona Architecture

_Date: 2026-05-19 · Companion docs: `voyager-feature-depth-reality-check.md`, `voyager-master-improvement-backlog.md`, `competitive-readiness-and-improvement-plan.md`._

This is the single source of truth for what Voyager should **look, feel and be**.
It re-thinks the persona model, maps every codebase capability to concrete UI, and
is written so any section can be pasted into a design tool. Verified against the
live codebase.

---

## Part 0 — How to use this document

- **Designers / Figma:** every screen (Part 7) and component (Part 5) ends with a
  fenced ` ```figma-prompt ` block — a self-contained description that can be
  pasted directly into Figma Make, the Figma MCP (`/figma-generate-design`), or
  any AI design tool with no extra context. Part 10 collects them.
- **Engineers:** Part 9 maps every UI element to its `UiState` field / domain
  model / entity, so the design is buildable against the existing code.
- **Founder / PM:** Parts 1–3 are the strategy — competitor patterns, brand voice,
  and the persona architecture that makes one app feel like five focused ones.
- **Token rule:** never hardcode a colour or size in design or code — use the
  named tokens in Part 4. The `VoyagerGradients` system is centralised; never
  inline a gradient.

---

## Part 1 — Competitor UX teardown

For each competitor, the **one pattern worth stealing** and why.

| App | What its UI does best | The pattern to adopt |
|---|---|---|
| **Google Timeline** | A horizontal **day strip** at the top; the day reads as a vertical list of place cards + trip rows; "trips" auto-surface as hero cards | Day-strip navigation + auto-surfaced trip/▸story cards on the timeline |
| **Arc (iOS)** | The timeline is one continuous **vertical ribbon** — visits and movements are segments of a single coloured spine; tap expands in place | The continuous timeline spine; no card gaps between visit and movement |
| **Strava** | A giant **Record button** as the home CTA; an activity is a **map hero + a stat grid** (pace, distance, time, elevation); shareable activity card | Record-first home for the Athlete persona; map-hero + mono stat grid |
| **Polarsteps** | The trip **is** a map with photos pinned along the route; the journey scrolls as a photo-forward story; printed book as the artefact | Photo-forward journey map; the trip-book as a tangible export |
| **Life360** | A live map with family avatars; a calm "everyone's safe" status; minimal chrome | One-glance status (the one-bit handshake); calm, not surveillance-y |
| **Day One** | A serene **journal aesthetic** — generous whitespace, big photography, a calendar/"On this day" resurfacing | Calm journal feel for the Keeper persona; "this day last year" resurfacing |
| **MileIQ** | A **swipe-to-classify** drive list (left = personal, right = business); a fat deductible total | Swipe-to-classify the Mileage list; a prominent deductible total |
| **OwnTracks** | Nothing to steal visually — but proves the **privacy-minimal** audience exists | A privacy-minimal mode that hides chrome and naming |
| **GeoTracker** | A clean GPX-export-first utility view | Frictionless export as a first-class action, not buried |

**Synthesis — what the best of them share:** light, airy layouts; one dominant
action per screen; data shown as big monospace numerals; maps used as *heroes*
not backgrounds; and aggressive progressive disclosure. Voyager today is dark,
dense, and shows every job at once. The redesign keeps Voyager's dark signature
as an *option* but adds a light theme and borrows the airiness and the
one-dominant-action discipline.

---

## Part 2 — Brand & product voice

**What Voyager is:** your life, recorded by you, kept by you. A calm, private,
honest place — never a surveillance tool, never an ad product.

**Personality:** Calm · Private · Honest · Quietly premium. Voyager is the
opposite of anxious. It never nags, never gamifies cheaply, never hides how it
reached a conclusion.

**Voice & tone rules:**
- **Plain, warm, first-person-friendly.** "You went to 3 places today" — not
  "3 places detected."
- **Honest about uncertainty.** When confidence is low, say so: "Probably the
  gym — tap to confirm." Never fake certainty.
- **No dark patterns.** Pro upsells state the price and what F-Droid users get
  free. No fake urgency.
- **Quiet by default.** Notifications are gentle and skippable.
- **Tagline:** _"Your location story, told by you, kept by you."_

**Emotional promise per surface:**
- Timeline / Day Story → _reassurance & delight_ ("here is your day, intact").
- Insights → _gentle self-knowledge_ ("here's a pattern, no judgement").
- Mileage / Evidence → _confidence_ ("this will hold up").
- Reliability / Battery → _trust_ ("we'll tell you the truth about cost").
- Paywall → _respect_ ("here's the honest deal").

**Microcopy principles:** verbs over nouns; numbers in monospace; never blame the
user for a gap ("tracking paused" not "you stopped tracking"); empty states
always offer the next step.

---

## Part 3 — Persona architecture

### 3.1 Why re-think personas

Today `Job` (`domain/model/Job.kt`) has 3 values — MEMORY / PROOF / HABITS — and
only reorders 3 dashboard modules (`DashboardScreen.kt:304-309`). The 11 presets
(`SettingsPresets.kt`) carry the real tracking config. The problem: **the persona
barely changes the app**, so every user sees all 22 screens and 10 jobs — the
"heavy and cluttered" complaint.

### 3.2 The model — a persona is a full *face* of the app

A persona drives **four** things, not one:
1. **Surface** — which bottom-nav tabs, dashboard modules, and quick actions appear.
2. **Engine tier** — the default tracking duty cycle (battery profile).
3. **Voice** — microcopy tone.
4. **Feature visibility** — what is on-surface vs parked in the **Feature Library**
   (a screen where any feature can be switched on).

Personas are chosen at onboarding (`PersonaPickScreen`), switchable anytime, and
non-exclusive — a Keeper can switch on Mileage from the Library. This is the
mechanism that makes one app feel like five focused apps.

### 3.3 The five personas

| Persona | Replaces | Real-world user | Engine tier |
|---|---|---|---|
| **Keeper** | Google Timeline, Day One | "I want a private diary of where my life happened" | Balanced |
| **Navigator** | quantified-self apps | "I want to understand my routines and habits" | Balanced |
| **Professional** | MileIQ | gig driver, consultant, anyone who needs proof | Accurate |
| **Athlete** | Strava | runner / cyclist who records workouts | Passive bg + Workout on demand |
| **Wanderer** | Polarsteps | traveller who wants journeys as stories | Balanced, trip-aware |

**Keeper** — _Voice: warm, gentle._ Wants the timeline and the day's photos.
Surfaces: Timeline (home), Day Story, Map, light Insights. Hidden: Mileage,
Carbon, Evidence depth, Reliability. JTBD: "show me my day; let me re-live it."

**Navigator** — _Voice: coaching, motivating._ Wants patterns. Surfaces:
Insights (home, with rings/streaks), anomalies, weekly comparison, Timeline.
Hidden: Mileage, Trips, Day Story. JTBD: "tell me how I'm moving and what changed."

**Professional** — _Voice: precise, dependable._ Wants defensible logs.
Surfaces: Mileage (home), Evidence cards prominent, Export, Reliability, Timeline.
Hidden: Day Story, Carbon, Social. JTBD: "give me a record that holds up."

**Athlete** — _Voice: energetic, minimal._ Wants to record activities.
Surfaces: a **Record** CTA (home), Activities list, Map. Background tracking is
**Passive** (near-zero battery); high-accuracy GPS only during a recorded
workout. Hidden: everything else until opted in. JTBD: "record my run, show pace
and route, let me export GPX."

**Wanderer** — _Voice: storyteller._ Wants journeys. Surfaces: Trips (home),
Day Story, Map, photo-forward timeline. JTBD: "turn my travels into a story I can
keep and print."

**Privacy-first** — a cross-cutting *modifier*, not a persona. Any persona can
flip it: disables auto-geocoding (`autoGeocodeNewPlaces=false`), shortens
retention, enables `flagSecureEnabled`, hides place names behind coordinates.

**Guardian** (future) — the 6th persona, unlocked when the family one-bit
handshake ships. Surfaces a calm "everyone's safe" status. Noted, not yet built.

### 3.4 How onboarding realises it

`PersonaPickScreen` becomes: pick one of 5 persona cards (illustration + name +
one-line JTBD + "what you'll see"). The choice writes `activeJob`, applies the
matching preset, and sets the surface pack. A "not sure?" option defaults to
Keeper. The current independent Job+preset selection is replaced by this single
bundled choice; presets remain tunable later in Settings.

---

## Part 4 — Design system (Figma-promptable tokens)

### 4.1 Colour — dark theme (current, verbatim from `VoyagerColors.kt`)

```figma-tokens
# Brand
Primary            #3B82F6   PrimaryDim        #2563EB   PrimaryContainer #1E3A5F
# Surfaces (OLED dark)
Background #0F0F1A  Surface #1A1A2E  SurfaceVariant #252540  SurfaceBright #2E2E4A  SurfaceOverlay #2A2A4A
# Text
OnSurface #E8E8F0  OnSurfaceVariant #8888A0  OnPrimary #FFFFFF
# Status
Error #EF5350  ErrorContainer #3D1A1A  Success #66BB6A  Warning #FFA726
# Accents
AccentBlue #42A5F5  AccentPurple #AB47BC  AccentAmber #FFA726  AccentGreen #66BB6A  AccentRed #EF5350  AccentOrange #FF7043
# Premium (Pro tier)
Premium #E6B450  PremiumDim #B8902F
# Transport
TransportWalk #66BB6A  TransportDrive #AB47BC  TransportCycle #42A5F5  TransportTransit #FF7043  TransportGap #616161
# Confidence
ConfidenceLow=Error  ConfidenceMedium=Warning  ConfidenceHigh=AccentGreen
```

### 4.2 Colour — proposed light theme (new — most rivals are light-first)

```figma-tokens
# Light theme — same hues, inverted surfaces, AA-contrast checked
Background #F7F8FB  Surface #FFFFFF  SurfaceVariant #EEF1F6  SurfaceBright #FFFFFF  SurfaceOverlay #FFFFFF
OnSurface #15151F  OnSurfaceVariant #5B5B72  OnPrimary #FFFFFF
Primary #2563EB (slightly deeper for light-bg contrast)  PrimaryContainer #DCE7FF
Transport/Accent/Premium hues unchanged; darken by ~8% where they sit on white.
```

Theme is user-selectable (System / Dark / Light) and the Privacy-first modifier
defaults to Dark.

### 4.3 Gradients (`VoyagerGradients` — centralised, never inline)

```figma-tokens
screenBackground  radial, deep-indigo nebula top → Background, radius 0.60h
heroCard          vertical, Primary 13% → AccentPurple 5% → transparent
activeCard        vertical, AccentGreen 10% → transparent  (live-tracking)
primaryGlow       radial halo, Primary 28%, radius 55%
sectionDivider    horizontal fade, transparent → Primary 22% → transparent
topBar / navBar   vertical faint-indigo → Background  (floating feel)
```

### 4.4 Typography (`ui/theme/Type.kt`)

```figma-tokens
Font families: Inter (UI), JetBrains Mono (data/stats), Great Vibes (the "Voyager" wordmark only)
Display L/M/S      57/45/36  Bold
Headline L/M/S     32/28/24  SemiBold
Title L/M/S        22/16/14  Medium
Body L/M/S         16/14/12  Normal
Label L/M/S        14/12/11  Medium
Mono: StatLarge 28 Bold · StatMedium 20 Bold · StatSmall 14 Medium · Timestamp 13 · Data 12
Rule: every number/measure/time/coordinate/confidence renders in JetBrains Mono.
```

### 4.5 Shape, spacing, elevation, motion

```figma-tokens
Radius:  card 12 · button 8 · chip 4/16 · sheet 20 (top corners)
Spacing scale (dp): 4 · 8 · 12 · 16 · 20 · 24 · 32   (screen padding 16, section gap 20)
Elevation: flat (1dp border) · raised (SurfaceVariant) · highlighted (Primary 1.5dp border)
Motion: 200ms standard ease; predictive-back on every stacked screen; counters
count-up on first paint; activity rings draw-on; shared-element transition
list→detail; PulsingDot for live states. Motion is calm — nothing bounces.
```

---

## Part 5 — Component library

Catalogue of every reusable composable (`VoyagerComponents.kt`,
`DesignSystemComponents.kt`), as Figma components with variants and the data each
binds to.

| Component | Variants / props | Binds to |
|---|---|---|
| **VoyagerCard** | FLAT / RAISED / HIGHLIGHTED · onClick · tintColor | any |
| **VoyagerButton / OutlinedButton / TextButton / IconButton** | enabled · haptic | actions |
| **VoyagerBadge** | color · contentColor | counts (e.g. pending reviews) |
| **PulsingDot** | size · color | live tracking |
| **VoyagerCollapsibleSection** | title · trailing · expanded | settings, evidence |
| **SectionHeader / StatItem** | value (mono) + label | dashboards |
| **ConfidenceBar** | percent · sourceLabel · low/med/high colour | place/segment confidence |
| **NameSourceIndicator** | source (Custom/Photon/Nominatim/Inferred/Coordinates) | place name provenance |
| **DataCard** | mono value · label · trend (UP/DOWN/STABLE) · sparkline · onClick | metrics |
| **CategoryChip** | category icon + name (16 `PlaceCategory` values) | place category |
| **TransportModeIcon** | per `SegmentType` colour+icon · size | timeline/map segments |
| **GapSegmentCard** | dashed border · reason (PERMISSION/DOZE/…) · duration | timeline gaps |
| **TrackingStatusPill** | ACTIVE/PAUSED/DEGRADED · pulsing dot | global status |
| **PermissionBanner** | severity · "Fix" action | permission degradation |
| **SegmentedProgressBar** | multi-colour time distribution | day composition |
| **SparklineChart** | points · colour | inline trends |
| **ActivityRings** | 3 rings: distance/steps/active-time · size | Navigator dashboard |
| **PresetCard** | name · description · selected · traveler flag | settings presets |
| **AnomalyAlertCard** | severity dot · metric · sigma · explanation | anomalies |
| **PlaceConfirmationPrompt** | name · confidence · suggestions · Confirm/Rename | pending places |
| **ShimmerPlaceholder / ShimmerCard** | shape · size | loading states |
| **EvidenceCard** | confidence · explanation · source chips · supporting/counter metrics · ruleVersion · expandable | Pro evidence |
| **DayNavigator** | prev/next · centre label · Today button · summary · trailing | Timeline/Map/DayStory |
| **PeriodSelectorBar** | Today/Week/Month/Last30/Custom chips | Insights/Mileage |
| **EmptyStateComposable** | NO_TRACKING/NO_PERMISSION/NO_PLACES/NO_INSIGHTS | empty screens |
| **ErrorStateComposable** | message · retry | error screens |

**New components the redesign adds:** `RecordButton` (Athlete home CTA),
`HeatmapCalendar` (Year-in-Review), `MapHero` (full-bleed map header),
`PersonaCard` (onboarding), `FeatureLibraryTile`, `WorkoutStatGrid`,
`OnThisDayCard`, `TrackingTierSelector` (the 5-tier battery control).

```figma-prompt
Build a Figma component library named "Voyager DS". Create components for every
row in the table above using the Part 4 tokens. Each component: a default state +
its listed variants, with auto-layout, the named radius, and the named colours.
Numbers use JetBrains Mono; labels use Inter. Build both a Dark and a Light
variant set. Group as: Foundations (color, type, elevation), Primitives (buttons,
cards, chips, badges), Data (DataCard, ConfidenceBar, ActivityRings, Sparkline,
SegmentedProgressBar, HeatmapCalendar), Patterns (EvidenceCard, AnomalyAlertCard,
DayNavigator, PeriodSelectorBar, TrackingStatusPill, PermissionBanner), States
(Shimmer, Empty, Error).
```

---

## Part 6 — Information architecture

### 6.1 The "shell + surface pack + library" model

- **Base shell** (every persona): top bar (Voyager wordmark, Search, Settings,
  pending-review bell badge), bottom nav, the tracking status pill.
- **Persona surface pack**: the bottom-nav tab set + dashboard module set +
  quick actions, swapped per persona.
- **Feature Library**: a screen listing every feature with a toggle; switching one
  on adds it to nav/quick-actions. Default-off features stay invisible.

### 6.2 Persona-scoped bottom navigation (4 tabs each)

| Persona | Tab 1 (home) | Tab 2 | Tab 3 | Tab 4 |
|---|---|---|---|---|
| Keeper | Timeline | Map | Day Story | Insights (light) |
| Navigator | Insights | Timeline | Map | Places |
| Professional | Mileage | Timeline | Map | Export/Reports |
| Athlete | Record | Activities | Map | Insights (movement) |
| Wanderer | Trips | Timeline | Map | Day Story |

Shared across all: Search and Settings (top bar). Detail screens (Place, Visit,
Segment, Trip) are pushed/sheet, persona-independent. `VoyagerDestination` routes
are unchanged — the persona only changes *which are surfaced*.

### 6.3 What divides vs what stays shared

- **Divides:** home screen, nav tabs, dashboard modules, quick actions, voice,
  default engine tier.
- **Shared:** the engine, the timeline data, all detail screens, Search,
  Settings, the design system. One codebase, one database — only the surface
  reshapes.

---

## Part 7 — Screen-by-screen UX spec

Format per screen: **purpose · persona(s) · data shown (UiState/model/entity) ·
layout · components · states · figma-prompt.**

### 7.1 Core screens

#### Dashboard / Home
- **Purpose:** the daily glance; reshaped per persona (see Part 8).
- **Personas:** all (different module sets).
- **Data (`DashboardUiState`):** dailySummary, weeklyComparison, anomalies,
  insights, topPlaces, stepChart, totalStepsToday, isTracking, lastSampleAt,
  sessionStartedAt, activeVisit, pendingCandidate, streakDays, activeJob,
  batteryPercentPerDay.
- **Layout:** top bar → live status card (heroCard/activeCard gradient) →
  persona module stack → quick-action row.
- **Components:** VoyagerCard, ActivityRings, DataCard, SegmentedProgressBar,
  AnomalyAlertCard, TrackingStatusPill, StatItem.
- **States:** empty (NO_TRACKING with "Start"), loading (ShimmerCard), live.

```figma-prompt
Design a mobile Home screen, dark theme, Voyager DS. Top bar: "Voyager" wordmark
(Great Vibes), search + settings icons, a bell with a "3" badge. Below: a hero
card with a soft Primary→Purple gradient showing live status — "Tracking active",
a pulsing green dot, "Last sample 22s ago", and 3 mono stats (distance 11.0 km /
steps 0 / time away 14m). Then a vertical stack of modules (per persona, see
Part 8). Bottom: a 4-tab nav. Calm, airy, generous 16dp padding, JetBrains Mono
for all numbers.
```

#### Timeline
- **Purpose:** one day as a continuous spine of visits + movements.
- **Personas:** Keeper (home), all.
- **Data (`TimelineUiState`):** dayKey, segments[`TimelineSegment`], totalDistanceM,
  totalSteps, focusedSegmentId, selectedSegmentEvidence, activeVisit,
  pendingCandidate, isTracking, isRoughMode. Each segment: type, start/end,
  duration, distance, confidence, place(`TimelinePlace`), route, gapReason,
  sequenceNumber.
- **Layout:** DayNavigator → continuous vertical ribbon (Arc-style): visit nodes
  (CategoryChip + name + dwell + ConfidenceBar) joined by movement legs
  (TransportModeIcon + distance + duration); GapSegmentCard for gaps;
  RoughLocationBanner when isRoughMode.
- **Components:** DayNavigator, TransportModeIcon, CategoryChip, ConfidenceBar,
  GapSegmentCard, NameSourceIndicator.
- **States:** empty (NO_TRACKING), loading (shimmer ribbon), rough-mode banner.

```figma-prompt
Design a Timeline screen, dark theme. Top: a DayNavigator (‹ "Today, May 19" ›
with a summary "4 places · 11.0 km"). Body: one continuous vertical ribbon — a
coloured spine running top to bottom. Place stops are nodes on the spine: a
circular category icon, place name in Inter, arrival→departure time + dwell in
mono, a thin confidence bar. Between stops, movement legs: a transport icon
(walk=green, drive=purple), distance + duration in mono, the spine tinted by
mode. One gap shown as a dashed card "Tracking gap · 25m · Doze". No card gaps —
it reads as one object.
```

#### Map
- **Purpose:** the day rendered spatially.
- **Personas:** all (Tab 3 for most).
- **Data (`MapUiState`):** dayKey, routes[`MapRoute` polylines], visitMarkers,
  bounds, focusedSegment/Route, currentLocation, activeVisitLocation,
  focusedVisit, centerOnUserRequested, fitBoundsRequested.
- **Layout:** full-bleed MapLibre canvas; floating DayNavigator (top), My-location
  + Fit-bounds FABs (bottom-right); a bottom sheet on marker/route tap.
- **Components:** DayNavigator (floating), VoyagerIconButton FABs, detail sheet.
- **States:** loading overlay, empty (no routes → "No movement this day").

```figma-prompt
Design a Map screen, dark theme. Full-bleed dark map. Floating pill DayNavigator
at top. Coloured route polylines (walk green, drive purple) with numbered visit
markers. Two circular FABs bottom-right: locate-me, fit-bounds. A peeking bottom
sheet showing a tapped visit: name, category chip, arrival/departure, dwell.
```

#### Insights
- **Purpose:** analytics; 7 tabs.
- **Personas:** Navigator (home), all.
- **Data (`StatisticsUiState`):** weeklyComparison, placePatterns, movementStats,
  socialStats, anomalies, carbonFootprint, periodLabel. Tabs: Overview (free),
  Movement / Patterns / Places / Social / Weekly / Carbon (Pro).
- **Layout:** PeriodSelectorBar → scrollable tab row → tab content of DataCards,
  charts, AnomalyAlertCards. Pro tabs show the locked card for free users.
- **Components:** PeriodSelectorBar, DataCard, SparklineChart, ActivityRings,
  SegmentedProgressBar, AnomalyAlertCard, FeatureGate locked card.

```figma-prompt
Design an Insights screen, dark theme. Top: period chips (Today/Week/Month/Last
30). A scrollable tab row: Overview, Movement, Patterns, Places, Social, Weekly,
Carbon. Show the Carbon tab: a big mono "3.1 kg CO₂e", a sub-line "≈ 0 tree-years
· 20 km travelled", and a per-mode breakdown — horizontal bars Driving / Transit
with distance and g/km. A gold "PRO" chip on the tab.
```

### 7.2 Detail screens & sheets

#### Place Detail
- **Data (`PlaceDetailUiState`):** place(`TimelinePlace`), analytics
  (visit frequency, dwell, last visited, dominant day/time), evidence
  (`ConfidenceBlock`), geocodeCandidates.
- **Actions:** Rename, SetCategory, SetEmoji, Confirm, MergeWith, RefreshGeocode.
- **Components:** CategoryChip, ConfidenceBar, NameSourceIndicator, DataCard,
  EvidenceCard (Pro).

```figma-prompt
Design a Place Detail sheet, dark theme. Header: emoji + editable place name +
NameSourceIndicator ("Custom name"). A confidence bar. A 3-stat row: visits, avg
dwell, last visited (mono). A mini map. Action row: Rename, Category, Emoji,
Confirm, Merge. Below, a collapsible "Why this place?" evidence card (Pro-gated).
```

#### Visit Detail
- **Data (`VisitDetailUiState`):** visit(`VisitEntity`: arrival, departure, dwell,
  confidence), place, evidence(`VisitEvidenceEntity`: arrival/departureConfidence,
  inside/outsideCount), confidenceBlock.
- **Actions:** ConfirmVisit, DeleteVisit, RenamePlace, AdjustTimes.

```figma-prompt
Design a Visit Detail sheet, dark theme. Place name + category chip. Arrival and
departure times (mono, editable). Dwell duration. An arrival/departure confidence
pair. Actions: Confirm, Adjust times, Rename, Delete. Optional Pro evidence card.
```

#### Segment Detail
- **Data (`SegmentDetailUiState`):** segment(`TimelineSegment`),
  evidence(`EvidenceBlock`: activityVotes, providerMix, speed, stepCount),
  explanation(`InferenceExplanation`: label, confidence, supportingMetrics,
  counterEvidence, ruleVersion, humanExplanation).
- **Actions:** ChangeType, SplitAt, MergeWithNext.

```figma-prompt
Design a Segment Detail sheet, dark theme. Header: transport icon + type ("DRIVE")
+ "Confidence 86%". Stat row: distance, duration, avg speed (mono). The hero is an
EvidenceCard: a plain-language "Why we think this was a drive", source chips (GPS,
Activity API), activity votes as tiny bars, and a "counter-evidence" line if any.
Actions: Change type, Split, Merge. Evidence section gated with a gold PRO lock
for free users.
```

#### Trip Detail
- **Data (`TripDetailUiState`):** detail(`TripDetail`: trip + days[`TripDay`] +
  places[`TripPlaceVisit`]), exportUri.
- **Actions:** ExportBook (PDF), ConsumeExportResult.

```figma-prompt
Design a Trip Detail screen, dark theme, photo-forward. Hero: a map of the whole
journey with the route drawn. Title "Trip to Varanasi", "2 days · 3 places ·
36 km". Then a per-day journal: each day a header date, a small route, place rows
with times. A prominent "Export trip book (PDF)" button.
```

### 7.3 Feature screens

#### Mileage (Pro)
- **Data (`MileageUiState`):** range, log(`MileageLog`: entries[`MileageEntry`],
  metersByPurpose, totalMiles, deductibleMeters, unclassifiedCount).
- **Actions:** SelectRange, Classify(purpose), ExportPdf.

```figma-prompt
Design a Mileage Log screen, dark theme. Top: period chips + a fat "Deductible
total: 84.2 mi" card. A list of drives — each row: date, time, distance (mono),
and a purpose tag. Rows are swipeable: swipe right = Business (green), left =
Personal (grey). A banner "8 drives still unclassified". Bottom: "Export tax PDF".
```

#### Trips (Pro)
- **Data (`TripsUiState`):** trips[`Trip`], hasHomeAnchor, isDetecting.

```figma-prompt
Design a Trips list, dark theme. If no Home place set, an explainer card. Else a
list of trip cards: title, date range, "2 days · 3 places · 36 km", a route
thumbnail, a gold PRO chip. Pull-to-refresh re-runs detection.
```

#### Day Story (Pro)
- **Data (`DayStoryUiState`):** story(`DayStory`: places[`DayStoryPlace`] with
  photos, unplacedPhotos, totalPhotoCount), hasPermission, isToday.

```figma-prompt
Design a Day Story screen, dark theme, photo-forward. DayNavigator at top. The
day as chapters: each place a section — emoji + name + time window, then a photo
grid of pictures taken there. A final "Elsewhere that day" section for photos
without a place. Just-in-time photo-permission prompt if not granted.
```

#### Carbon (Pro) — lives as the 7th Insights tab (see Insights prompt).

#### Export & Import
- **Data (`ExportUiState`):** format, startDate, endDate, isSingleDay, resultUri,
  importSummary.

```figma-prompt
Design an Export & Import screen, dark theme. Section 1 "Export your timeline": a
date-range card (opens a range picker), format radios (Voyager JSON recommended,
GPX, GeoJSON, CSV), an "Export & Share" button. Section 2 "Import": a "Choose
file" button. Section 3 "From Google Timeline": explainer + "Choose Google
export". A result card shows counts incl. "Raw samples imported".
```

#### Reliability
- **Data (`ReliabilityUiState`):** manufacturer, isAggressiveOem, lastSampleAt,
  hoursSinceLastSample, hasRecentGap.

```figma-prompt
Design a Reliability Check screen, dark theme. A big status: green "Tracking
healthy" or amber "Gap detected". Device card: manufacturer, an "aggressive OEM"
warning if so, with a "Fix battery settings" deep-link. A 24-hour self-test
result. A "last sample" timestamp.
```

#### Search
- **Data (`SearchUiState`):** query, results(`SearchResults`: places, segments,
  days), filters.

```figma-prompt
Design a Search screen, dark theme. A prominent search field "Search places,
days, trips…". Filter chips: category, transport mode, date range. Results
grouped: Places, Days, Trips — each a tappable row. Empty state explains what's
searchable.
```

#### Categories / Place Review
- **Categories data:** per-`PlaceCategory` visibility (showOnMap/Timeline/notify).
- **Review data (`PlaceReviewUiState`):** pendingPlaces (confidence <70% or
  UNKNOWN).

```figma-prompt
Design two screens, dark theme. (1) Categories: a list of the 16 place categories,
each with three toggles (Map, Timeline, Notify) and a count. (2) Place Review: a
stack of PlaceConfirmationPrompt cards — "Is this the Gym? 64% sure" with Confirm
/ Rename and name suggestions.
```

#### Athlete: Record + Activities (new screens)
- **Record:** a full-screen Workout mode — big Start, then live pace/distance/
  duration/route; Pause/Stop. Backed by a foreground Workout tracking tier.
- **Activities:** a list of recorded workouts (Strava-style cards: map thumb,
  mono stat grid, GPX export).

```figma-prompt
Design two Athlete screens, dark theme. (1) Record: a near-empty screen with one
huge circular "Start" button; once recording, a live map hero + a 2×2 mono stat
grid (Duration, Distance, Pace, Steps) + Pause/Stop. (2) Activities: a list of
workout cards — a route map thumbnail, activity type + date, a 4-stat mono grid,
a small GPX-export icon.
```

### 7.4 Onboarding

`AppPhase` flow: SPLASH → RESTORE → GOOGLE_IMPORT → ONBOARDING (permissions) →
PERSONA → WALKTHROUGH → MAIN.

```figma-prompt
Design the onboarding flow, dark theme, calm and airy. (1) Splash: the "Voyager"
wordmark in Great Vibes on the nebula gradient. (2) Restore: "Restore your
timeline" — restore-from-backup or start-fresh. (3) Google import: "Bring your
Google Timeline" — choose-export or skip. (4) Permissions: one card per
permission with a plain reason, just-in-time. (5) Persona pick: 5 large PersonaCard
tiles (illustration, name, one-line JTBD, "what you'll see"), plus "Not sure?".
(6) Walkthrough: 3 calm illustrated pages. Every screen: one dominant action.
```

### 7.5 Settings

- **Data (`SettingsUiState`):** settings(`UserSettings` — ~100 fields), grouped.
- **Redesign:** replace the 4 dense tabs (General/Detection/Privacy/Advanced)
  with **persona-aware grouping** — a short "Essentials" page (persona, tracking
  tier, battery budget, theme, notifications) and a "Everything" page (the full
  parameter set in collapsible sections). Pro, Feature Library, and the dev tools
  live here too.

```figma-prompt
Design a Settings screen, dark theme. A top "Essentials" card group: Persona,
Tracking tier (a 5-step selector Off→Passive→Balanced→Accurate→Workout with a
live "~%/day battery" estimate), Battery budget, Theme (System/Dark/Light),
Notifications. Below, collapsible sections for everything else (Detection, Map,
Timeline, Privacy & Data, Geocoding, Retention, Advanced). A "Voyager Pro" card
and a "Feature Library" entry. Calm, lots of whitespace, sliders and toggles.
```

---

## Part 8 — Per-persona Dashboard designs

The headline section — the same Home screen, five faces. Each lists module order
and the bottom nav.

### Keeper
Modules: ① **Today's timeline preview** (last 3 visits as a mini-ribbon) → ②
**On This Day** (a memory from a year ago) → ③ **Today's photos** (Day Story
strip) → ④ Places visited. Nav: Timeline · Map · Day Story · Insights. Voice
warm: "A quiet day — 3 places, all familiar."

### Navigator
Modules: ① **Activity rings** (distance / steps / active-time) + streak → ②
**Insights** (patterns, max 3) → ③ **This week vs last** (DataCards with trends)
→ ④ **Anomalies**. Nav: Insights · Timeline · Map · Places. Voice coaching:
"You're 1,200 steps ahead of yesterday."

### Professional
Modules: ① **Live status + today's drives** (count, unclassified badge) → ②
**Deductible-so-far** card (month) → ③ Places → ④ **Reliability** mini-card.
Nav: Mileage · Timeline · Map · Reports. Voice precise: "4 drives today, 2
unclassified."

### Athlete
Modules: ① a big **Record** CTA → ② **Last activity** card (map thumb + stats) →
③ **This week's movement** (distance, active time) → ④ steps. Nav: Record ·
Activities · Map · Insights. Background tier = Passive. Voice energetic: "Ready
when you are."

### Wanderer
Modules: ① **Current/last trip** hero (route + photos) → ② **Trips** list preview
→ ③ **Day Story** strip → ④ map of everywhere. Nav: Trips · Timeline · Map ·
Day Story. Voice storyteller: "Day 2 in Varanasi — 3 places, 36 km."

```figma-prompt
Design 5 variants of the Home screen, dark theme, same shell (top bar, live
status hero, 4-tab nav) but different module stacks: Keeper (timeline preview /
On This Day / photo strip / places); Navigator (activity rings + streak /
insights / week-vs-last / anomalies); Professional (drives + unclassified badge /
deductible card / places / reliability); Athlete (huge Record button / last
activity card / weekly movement / steps); Wanderer (trip hero with route+photos /
trips list / photo strip / map). Each uses its persona's voice in the copy.
```

---

## Part 9 — Feature → UI master table

| Feature | Use case | Domain model | Key entity fields | Component(s) | Screen | Persona | Tier |
|---|---|---|---|---|---|---|---|
| Timeline | TimelineReconciler | TimelineDay/Segment/Place | MovementSegment, Visit, Place | TransportModeIcon, ConfidenceBar, GapSegmentCard | Timeline | all | Free |
| Map | — | MapRoute, VisitMarker | Route.encodedPolyline, Place | MapLibre, DayNavigator | Map | all | Free |
| Place detection | DetectVisitUseCase, MatchPlaceLiveUseCase | TimelinePlace | Place(centroid, radiusM, confidence, category) | CategoryChip, ConfidenceBar | Place Detail | all | Free |
| Place naming | EnrichPlaceWithDetailsUseCase | GeocodeHint | GeocodeCandidate(provider, displayName, rank) | NameSourceIndicator | Place Detail | all | Free |
| Day summary | — | DailySummary | DailyRollup | DataCard, ActivityRings | Dashboard | all | Free |
| Weekly comparison | — | WeeklyComparison, MetricDelta | WeeklyRollup | DataCard (trend) | Insights/Weekly | Navigator | Pro |
| Anomalies | — | Anomaly | DailyRollup.anomalyFlags | AnomalyAlertCard | Insights, Dashboard | Navigator | Pro |
| Evidence | BuildEvidenceSummaryUseCase | EvidenceBlock, InferenceExplanation | Segment/VisitEvidence (activityVotes, providerMix, counterEvidence) | EvidenceCard | Segment/Visit Detail | Professional | Pro |
| Trips | DetectTripsUseCase, BuildTripDetailUseCase | Trip, TripDetail, TripDay | Trip(start/endDayKey, distanceMeters) | trip cards | Trips | Wanderer | Pro |
| Mileage | BuildMileageLogUseCase | MileageLog, MileageEntry | MileageClassification(purpose, note) | swipe rows, deductible card | Mileage | Professional | Pro |
| Day Story | BuildDayStoryUseCase | DayStory, DayStoryPlace, DevicePhoto | (computed; MediaStore) | photo grid | Day Story | Keeper, Wanderer | Pro |
| Carbon | BuildCarbonFootprintUseCase | CarbonFootprint, ModeFootprint | MovementSegment.distanceM | breakdown bars | Insights/Carbon | Navigator | Pro |
| Movement stats | — | MovementStats | MovementSegment, rollups | DataCard | Insights/Movement | Navigator, Athlete | Pro |
| Place patterns | — | PlacePatternSummary | PlaceRollup(visitCount 7/30/90d, dominantDay/time) | DataCard | Insights/Places | Navigator | Pro |
| Search (FTS) | — | SearchResults | SearchIndex | result rows | Search | all | Free |
| Reliability | — | — | HealthLog(SAMPLE_GAP…), TrackingSession | status card | Reliability | Professional | Free |
| Battery report | BatteryUsageReporter | — | RawLocationSample.batteryPct | DataCard | Dashboard, Settings | all | Free |
| Export/Import | ExportRepository | ImportSummary | all syncable entities | format radios | Export | all | Free |
| Workout (new) | (to build) | (Activity) | (new) | RecordButton, WorkoutStatGrid | Record/Activities | Athlete | Free/Pro |

---

## Part 10 — Figma prompt library

All prompts consolidated. Run them in order: tokens → components → screens →
persona dashboards.

1. **Design system:** Part 4 — paste the four `figma-tokens` blocks, then the
   Part 5 component-library `figma-prompt`.
2. **Core screens:** the Dashboard, Timeline, Map, Insights `figma-prompt` blocks
   (Part 7.1).
3. **Detail/sheets:** Place, Visit, Segment, Trip (Part 7.2).
4. **Feature screens:** Mileage, Trips, Day Story, Export, Reliability, Search,
   Categories/Review, Athlete Record/Activities (Part 7.3).
5. **Onboarding:** the flow prompt (Part 7.4).
6. **Settings:** Part 7.5.
7. **Per-persona dashboards:** the 5-variant prompt (Part 8).

**Global style preamble** to prepend to every prompt:
> _Voyager is a private, on-device location-timeline Android app. Visual style:
> calm, premium, airy; OLED-dark default with a light option; Inter for UI,
> JetBrains Mono for all numbers, Great Vibes only for the "Voyager" wordmark.
> One dominant action per screen. 16dp screen padding, 12dp card radius. Use the
> Voyager DS tokens and components. Maps are heroes, not backgrounds._

---

_End of blueprint. See `voyager-feature-depth-reality-check.md` for whether the
features behind these screens are deep enough to win, and
`voyager-master-improvement-backlog.md` for the consolidated build order._
