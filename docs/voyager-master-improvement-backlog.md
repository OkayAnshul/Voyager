# Voyager — Master Improvement Backlog

_Date: 2026-05-19 · The capstone. Companions: `voyager-ux-design-blueprint.md`, `voyager-feature-depth-reality-check.md`, `competitive-readiness-and-improvement-plan.md`, `docs/research/*`._

The single, exhaustive, deduplicated registry of **every improvement** that can
make Voyager the best application in its space. Pulled from the design blueprint
(Doc 1), the depth reality-check (Doc 2), the four `docs/research/*` audits, the
competitive-readiness doc, the roadmap logbook, and fresh analysis — deduplicated
against what is already shipped. **If it can be improved, it is in here.**

---

## Part 1 — How to read this

Every item: **ID · Title — type · scope · effort · leverage. _Source._ Files.**

- **Type:** visual · UX · feature · depth · performance · battery · size ·
  architecture · hardening · monetization · testing · content.
- **Scope:** `partial` (tune/extend existing) · `full` (rebuild / new subsystem).
- **Effort:** S (<1 day) · M (1–3 d) · L (1 wk) · XL (multi-week).
- **Leverage:** high · med · low — impact on "best app" outcome.
- **Status:** all items below are **open** unless marked `[blocked]` or `[done]`.

**Already shipped (not repeated as items):** Phases 0–3 of the master plan; the
3-week hardening sprint; Phase 4 partials (Google Timeline import + onboarding
entry, `.voyager` restore, OSM place names, geocoding overhaul, rough-timeline,
Photo Day Story, trip detection, carbon); Phase 5 build (entitlement, FeatureGate,
billing code, paywall); export date-range + raw-sample import.

---

## Part 2 — Categorized registry

### A. Visual & UI

- **A1 — Light theme** — visual · partial · M · high. Most rivals are light-first;
  Voyager is dark-only. _Doc 1 §4.2._ `presentation/theme/VoyagerColors.kt`, theme wiring.
- **A2 — Motion layer** — visual · partial · L · high. Predictive-back on every
  stacked screen, count-up numerals, activity-ring draw-on, shared-element
  list→detail, calm 200ms easing. _Doc 1 §4.5; readiness P1._ presentation-wide.
- **A3 — Dashboard visual redesign** — visual · partial · L · high. Per-persona
  module stacks, hero status card. _Doc 1 §7.1, §8._ `screen/dashboard/*`.
- **A4 — Timeline ribbon redesign** — visual · partial · M · high. Continuous
  Arc-style spine, no card gaps. _Doc 1 §7.1; Doc 2 #4._ `screen/timeline/*`.
- **A5 — Map as hero** — visual · partial · M · med. Full-bleed, floating
  controls, polished bottom sheet. _Doc 1 §7.1._ `screen/map/*`.
- **A6 — Heatmap / Year-in-Review** — visual+feature · partial · M · high. A
  `HeatmapCalendar` component + annual recap; the shareable moment rivals have.
  _Doc 2 #10; readiness P1._ new component, rollup queries.
- **A7 — Design-system finish** — visual · partial · M · med. Kill inline
  literals, spacing tokens, light/dark component parity. _Doc 1 §4–5._
- **A8 — Iconography & illustration set** — visual · partial · M · med. Persona
  illustrations, empty-state art, consistent icon family. _Doc 1 §7.4._
- **A9 — Onboarding visual polish** — visual · partial · M · high. Calm, airy,
  one-action-per-screen. _Doc 1 §7.4; Doc 2 #13._ `screen/onboarding/*`.
- **A10 — First-hour "capturing now" pulse** — visual+UX · partial · S · high.
  Live distance/steps on the dashboard before places exist; kills empty start.
  _Doc 2 #13; competitor-analysis §3b._ `screen/dashboard/*`.
- **A11 — Detail-sheet redesign** — visual · partial · M · med. Place/Visit/
  Segment/Trip sheets per Doc 1 §7.2.

### B. UX & Information Architecture

- **B1 — Persona-scoped surfaces** — UX · full · L · high. Persona drives
  bottom-nav tabs + dashboard modules + quick actions + voice. The core
  anti-clutter mechanism. _Doc 1 §3, §6._ `domain/model/Job.kt`, `MainActivity`,
  `VoyagerDestination`, dashboard.
- **B2 — Re-thought 5-persona model** — UX · full · M · high. Keeper / Navigator
  / Professional / Athlete / Wanderer; bundled onboarding choice. _Doc 1 §3._
  `Job.kt`, `PersonaPickScreen`, `SettingsPresets`.
- **B3 — Feature Library screen** — UX · full · M · high. Opt-in catalogue;
  default-off features stay invisible. _Doc 1 §6.1._ new screen + Settings.
- **B4 — Settings restructure** — UX · partial · M · med. "Essentials" + collapsible
  "Everything"; persona-aware. _Doc 1 §7.5._ `screen/settings/*`.
- **B5 — Progressive disclosure of Insights tabs** — UX · partial · S · med. Show
  persona-relevant tabs first, "+" to add. _Doc 1 §7.1._
- **B6 — Privacy-first modifier** — UX · partial · M · med. One toggle: no
  geocoding, short retention, `flagSecureEnabled`, coordinate-only names.
  _Doc 1 §3.3._ settings + theme.
- **B7 — Accessibility pass** — UX · partial · M · high. Font-scale 130/180/200%,
  TalkBack list-fallback for the map, color-blind-safe mode chips (icons on every
  colour). _edge-cases U4/U7/U8, I5–I7._
- **B8 — Internationalization** — UX · partial · L · med. `strings.xml`
  extraction complete, `<plurals>`, RTL, localized number/date formats.
  _edge-cases I1–I4; hardening A5._
- **B9 — Empty/loading/error-state coverage** — UX · partial · S · med. Every
  screen has all three via `EmptyStateComposable`/`ShimmerCard`/`ErrorState`.

### C. Feature depth (from Doc 2 Part 4)

- **C1 — POI prior into place confirmation** — depth/logic · partial · M · high.
  Overpass POI as a confidence + naming prior; `PlaceRollup.repeatabilityScore`
  to confirm faster. _Doc 2 #1._ `DetectVisitUseCase`, place confirmation.
- **C2 — Accelerometer signature for transit/drive/cycle** — depth/logic ·
  partial · M · med. Accel-variance signal in fusion. _Doc 2 #2._
  `FuseActivityStateUseCase`, `capture/ActivityCapture`.
- **C3 — Trip storytelling depth** — depth/data+UI · full · L · high. User fields
  on `TripEntity` (title, notes, cover photo, captions) + schema migration;
  photo-forward `TripDetail` + `TripBookPdfExporter`. _Doc 2 #5._
- **C4 — Day Story "On this day"** — depth · partial · S · med. Resurfacing card.
  _Doc 2 #7._ rollup history.
- **C5 — Custom map style + offline tiles + camera persistence** — depth/UI ·
  partial · M · med. _Doc 2 #9; edge-cases U1._ map module, `offlineMapsEnabled`.
- **C6 — Evidence prominence** — depth/UI · partial · S · high. Surface
  `EvidenceCard` higher; Professional-persona hero. _Doc 2 #11 — press the moat._

### D. New features

- **D1 — Fitness / workout recording** — feature · full · XL · high · ✅ **BUILT 2026-07
  (Phases 0–5).** Delivered: `Activity` entity + migrations (DB v10→v13), foreground WORKOUT
  tier, Record + Activities + **ActivityDetail** + **Segments** screens, live route map,
  auto-pause + moving-time, per-km splits, **barometric elevation** + profile, GPX **import +
  export** (`<ele>`/`<time>`), on-device **personal records / best-efforts**, private
  **race-yourself segments**, and **auto-suggest** a workout from passive run/ride/walk segments.
  Public social/leaderboards deliberately excluded (contradicts the on-device moat). Unblocks the
  Athlete persona. _Doc 2 #8._ built on `LocationCapture`/`LocationKalmanFilter`.
- **D2 — Family one-bit handshake** — feature · full · XL · high `[blocked: needs
  security design review]`. Encrypted "I'm safe" bit, no live stream; unlocks the
  Guardian persona / Life360 segment. _readiness P2; competitor-analysis §2.5._
- **D3 — Duress mode / panic-wipe** — feature · full · L · med `[blocked: needs
  security design review]`. Cryptographically complete SQLCipher key destruction.
  _readiness P2; edge-cases Sec10._
- **D4 — OSM contribution loop** — feature · full · XL · med. OAuth 2.0 + changeset
  API + conflict handling; thread OSM element IDs through. Turns the geocoding
  data gap into a brand story. _readiness P2; Doc 2 #3._ Overpass stack, new
  network layer.
- **D5 — Heatmap / Year-in-Review** — see **A6** (cross-listed).

### E. Performance

- **E1 — Baseline Profiles** — performance · partial · S · high. Startup speed.
  `app/build.gradle.kts`, new baseline-profile module.
- **E2 — Lazy engine init** — performance · partial · S · med. Don't block cold
  start on the tracking engine; App Startup ordering.
- **E3 — Compose recomposition audit** — performance · partial · M · med.
  `derivedStateOf` on hot paths; clamp over-emitting flows to ≤1 Hz.
  _edge-cases U9/X7; hardening O5._
- **E4 — DB query projections** — performance · partial · S · low. Projection
  POJOs for `SELECT *` offenders. _hardening P3._

### F. Battery & tracking architecture

- **F1 — Formalize 5 tracking tiers** — battery · partial · M · high.
  Off/Passive/Balanced/Accurate/Workout as a named user-facing model over
  `AdaptiveSamplingPolicy`. _Doc 2 #12; battery brainstorm._
- **F2 — Battery-budget mode** — battery · partial · M · high. User sets ≤X%/day;
  engine self-tunes the tier. Surface on the battery card.
- **F3 — Passive tier** — battery · partial · M · high. Passive-provider +
  AR + geofence + step-counter only; near-zero cost; the Athlete background
  default.
- **F4 — Batched location requests** — battery · partial · S · med.
  `setMaxUpdateDelayMillis` so the radio sleeps and delivers in bursts.
  `LocationCapture`.
- **F5 — Honest per-tier battery numbers in UI** — battery/UX · partial · S ·
  med. Show "~%/day" per tier in the tier selector. _Doc 1 §7.5._

### G. App size & modularization

- **G1 — Measure the real release size** — size · partial · S · high. Build a
  release AAB; confirm Play's ABI/density/language splits — the per-user download
  may already be fine. _battery/size brainstorm._
- **G2 — R8 full mode + resource shrink audit** — size · partial · S · med.
  Verify keep-rules; drop unused MapLibre style assets. _hardening O3/B7._
- **G3 — Dynamic Feature Modules** — size/architecture · full · L · med
  `[do after G1]`. Optional/Pro features (Mileage, Trips, Day Story, Carbon,
  Fitness, Google import) as on-demand modules; clean pay→download. Play flavor
  only.
- **G4 — F-Droid "lite" build** — size · partial · M · low. F-Droid can't do DFM;
  a minimal monolithic flavor.

### H. Architecture & extensibility

- **H1 — `ExportFormatPlugin` interface** — architecture · partial · M · med.
  Refactor GPX/GeoJSON/CSV/JSON into a DI-set of plugins. _hardening A4/A3.3._
  `data/repository/ExportRepositoryImpl.kt`.
- **H2 — `PipelineGateway` interface** — architecture · partial · M · med.
  Pipeline depends on a `domain/` interface, not DAOs (KMP/iOS seam).
  _hardening A6/A3.5._
- **H3 — Typed-ID value classes** — architecture · partial · M · low.
  `PlaceId`/`VisitId`/`SegmentId`. _hardening A8/A3.6._
- **H4 — `userId` / multi-user column** — architecture · full · L · low
  `[do before family/B2B/cloud]`. _hardening A2/A3.2._
- **H5 — `SyncManager` interface (NoOp default)** — architecture · partial · M ·
  low. Makes optional cloud an opt-in plugin, not a refactor. _hardening §8._
- **H6 — Lint rule: no `Dao` import outside `data/`** — architecture · partial ·
  S · low. _hardening A3.18._

### I. Hardening & correctness

- **I1 — Remove last 3 `!!` in screens** — hardening · partial · S · high. The
  hardening gate wanted zero. `presentation/screen/**`.
- **I2 — 7-day crash-free dogfood across 4 OEMs** — hardening · partial · L ·
  high. Pixel + Xiaomi + Samsung + OnePlus. _edge-cases §14; hardening gate._
- **I3 — OEM matrix sign-off** — hardening · partial · M · med. Doze/kill behaviour
  documented per OEM. _hardening §6._
- **I4 — Audit remaining edge cases** — hardening · partial · M · med. Confirm
  closure of time/IDL/DST, captive-portal, storage-full, force-stop banner.
  _edge-cases §1–§13._

### J. Monetization & launch

- **J1 — Create Play Console products** — monetization · partial · M · high.
  `pro_monthly`/`pro_yearly`/`pro_lifetime` + pricing + license testers. Without
  this the built billing cannot transact. _readiness P0._
- **J2 — End-to-end billing test on device** — monetization/testing · partial ·
  S · high `[blocked: needs J1]`. _readiness P0._
- **J3 — Play data-safety form** — monetization · partial · S · high.
  _edge-cases LG4._
- **J4 — Privacy policy public + linked** — monetization · partial · S · med.
  _hardening A3.25; edge-cases LG2._
- **J5 — F-Droid reproducible build verified** — monetization · partial · M ·
  low. _edge-cases B5._
- **J6 — Store listing assets** — monetization/visual · partial · M · med.
  Screenshots, feature graphic, copy — per persona.
- **J7 — Background-location yearly re-justification** — monetization · partial ·
  S · low. Calendar reminder. _edge-cases LG9._

### K. Testing & quality

- **K1 — Concurrent 24h synthetic pipeline test** — testing · partial · M · high.
  _hardening §6; edge-cases §14._
- **K2 — Property tests on Segmenter/Kalman/DetectVisit** — testing · partial ·
  M · med. _hardening §6._
- **K3 — Permission-revocation UI test** — testing · partial · S · med.
- **K4 — Worker-concurrency test** — testing · partial · S · med.
- **K5 — Encryption round-trip test** — testing · partial · S · med.
- **K6 — Backup-restore round-trip test** — testing · partial · S · med.
  (Partly covered by `ExportRepositoryRoundTripTest`; extend to signing-key
  change.)
- **K7 — CI: unit tests on PR + monthly OWASP dependency-check** — testing ·
  partial · S · med. _hardening 0.3/0.4._

### L. Data & content

- **L1 — Geocoding/POI naming quality pass** — content · partial · M · med.
  Tune provider order, surface alternatives well, expand POI tags queried.
  _Doc 2 #3._
- **L2 — Place-category inference quality** — content/logic · partial · M · low.
  Improve auto-categorization confidence.
- **L3 — Microcopy pass** — content · partial · M · med. Apply the Doc 1 §2 voice
  rules to every string; persona-specific tone.
- **L4 — Notification copy & cadence** — content · partial · S · med. Calm,
  skippable, persona-aware.

---

## Part 3 — Master prioritized roadmap

### P0 — Ship-blockers (must precede a public launch) · ~1–2 weeks
J1, J2, J3, J4 (monetization can't earn / store can't list) · I1 (gate) ·
I2 (crash-free dogfood) · G1 (measure size before deciding anything).

### P1 — Polish & lightness (the perceived-quality leap) · ~4–6 weeks
A1, A2, A3, A4, A5, A9, A10, A11 (visual + motion) · B1, B2, B3, B4, B7
(persona surfaces + IA + accessibility) · F1, F2, F3, F5 (battery model) ·
A6 (heatmap) · C6 (evidence prominence) · E1, E2, E3 (performance) · L3, L4.
**This tier is the single biggest lever** — it closes the Arc gap and the
"heavy/cluttered/battery" complaints. Ship before any marketing push.

### P2 — Differentiators & depth (own the whitespace) · per-feature, each its own plan
D1 (fitness — unblocks a whole persona) · C1, C2, C3, C5 (depth gaps) ·
A8, B5, B6, B8, C4, L1, L2 · D4 (OSM loop) · D2, D3 `[blocked on security
review — schedule the review first]`.

### P3 — Future-proofing (before cloud / iOS / B2B becomes real)
H1, H2, H3, H4, H5, H6 (extensibility seams) · G3, G4 (modularization) ·
K1–K7 (the deeper test suites) · I3, I4, J5, J7.

**Sequencing logic:** P0 to *earn and launch*; P1 to *impress* (and stop feeling
heavy); P2 to *dominate the whitespace*; P3 only when cloud/iOS/B2B is a dated
commitment. Within a tier, do high-leverage / low-effort first.

---

## Part 4 — Coverage assertion ("nothing skipped")

Every gap named across the source documents maps to ≥1 backlog item above:

| Source | Its gaps | Covered by |
|---|---|---|
| Doc 1 — Design Blueprint | light theme, motion, per-screen redesign, persona surfaces, Feature Library, settings restructure, heatmap, onboarding polish, accessibility | A1–A11, B1–B9 |
| Doc 2 — Depth Reality-Check | all 11 Part-4 items (workout, POI prior, trip depth, ribbon UI, mileage swipe, tiers, accel signature, heatmap, maps, on-this-day, evidence) | D1, C1–C6, A4, A6, F1–F5 |
| `competitive-readiness…md` | P0 billing/`!!`/dogfood; P1 visual/motion/heatmap/first-hour/accessibility; P2 family/duress/OSM; P3 plugin interfaces/multi-user | J1–J4, I1–I2, A1–A2/A6/A10, B7, D2–D4, H1–H5 |
| `core-hardening-audit.md` + `hardening-execution-plan.md` | plugin interfaces, PipelineGateway, typed IDs, userId, lint rule, strings.xml, test suites | H1–H6, B8, K1–K7 |
| `edge-cases-and-hidden-bugs.md` | time/OEM/restore/captive-portal/storage edge cases, i18n/RTL/plurals, a11y, recomposition, map-state, duress | I3–I4, B7–B8, E3, C5, D3 |
| Battery / lightness brainstorm | 5 tiers, battery budget, passive tier, batching, DFM, persona-scoped UI, AAB sizing | F1–F5, G1–G4, B1 |
| Roadmap logbook | OSM loop, family, duress, Play Console, Phase 2 redesign/motion | D2–D4, J1–J2, A2–A3 |

**Guarantee:** if a future improvement is identified, it is added here — this
document is the permanent home of the Voyager improvement program. Nothing is
tracked anywhere else; nothing is lost.

---

_End. Build order: Part 3. What each thing should look like: Doc 1. Why each gap
matters and how deep the fix runs: Doc 2._
