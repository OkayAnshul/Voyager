# Voyager — Development Timeline & Process

A one-person Android project, built in the open over roughly a year. This document narrates
**how** Voyager was built, not just what it does — the milestones, the one deliberate architectural
U-turn, and the engineering process behind it.

> **Source of truth:** the git history. **208 commits**, first on **2025-08-02**, latest on
> **2026-07-27**. Some earlier narrative notes cite different "initial commit" dates; where they
> disagree with git, git wins. Voyager is **pre-production** — no real users yet — so any personas
> or UX scores in the design docs are rigorous hypotheses, not field findings.

## Commit cadence

The project accelerated steadily, with the heaviest build-out in the final quarter:

```
2025-08  ██████████████████████  22   Foundation
2025-09  ██████████             10
2025-10  ███████                7     V1 maturation
2025-11  █████████             9      Phase 8: battery/perf
2025-12  ██████████            10
2026-01  █████████             9
2026-02  █████████             9
2026-03  ████████████████      16     V2 "stream-first" rewrite
2026-04  ██████████████████████ 22    Productization + Pro tier
2026-05  ██████████████████████████████████████████ 42   Hardening waves
2026-06  ███████████████████████████████████████████████████ 51   Flagship push
2026-07  █                     1+     Publishing polish (this branch)
```

---

## Phase 1 — Foundation (Aug 2025)

The skeleton, built privacy-first from the very first commits:

- Clean-architecture layering (domain / data / platform / presentation), Hilt DI, core domain
  enums and models.
- **Room v1 schema, encrypted with SQLCipher from day one** — encryption was an architectural
  decision, not a later bolt-on.
- The capture layer: `LocationCapture`, an `AdaptiveSamplingPolicy` (stationary / walking / driving /
  dormant cadences), a **foreground service**, and a boot receiver.
- Early multi-sensor capture (step, Wi-Fi, significant-motion) and the first **evidence schema**.
- A geocoding provider registry (Android Geocoder → Nominatim → Photon, with Overpass POI).
- The initial four-tab navigation, theme, and Dashboard / Map / Timeline / Settings scaffolds.

Bug-fix commits from this era are telling: visit durations of zero, GPS spam, Doze kills — the
unglamorous realities of always-on Android location work, confronted early.

## Phase 2 — V1 maturation and an honest retrospective (Sep–Dec 2025)

Repositories, analytics rollups, an FTS search schema, and the first real use cases
(`DetectVisitUseCase`, `FuseActivityStateUseCase`, `MatchPlaceLiveUseCase`).

Then something unusual for a solo project: a wave of **self-audit** documentation —
`ARCHITECTURE.md`, `DESIGN_EVOLUTION.md`, and a candid `FLAWS_AND_ADVANCES.md` that graded the
codebase and estimated its technical debt. **Phase 8** (Nov 2025) tackled battery and performance
head-on. The retrospective set up the biggest decision of the project.

## Phase 3 — The V2 "stream-first" rewrite (Mar 2026)

The V1 timeline was **visit-first**: it detected stops, then inferred the movement between them.
That made gaps and mode changes hard to reason about. V2 inverted the model to **stream-first** —
a single, serial, explainable pipeline over the raw sample stream:

```
SampleNormalizer → LocationKalmanFilter → QualityScorer → DedupSuppressor → StateCommitter → Segmenter
```

…orchestrated by an 8-stage `PipelineConsumer` with a **gap watchdog** and a crash-recovery
`TimelineStateStore`. This is the architectural spine that makes "honest gaps" and per-row
explainability possible. The rewrite arrived with a new design-system (1,100+ lines of components,
Inter / Great Vibes / JetBrains Mono type, the "deep-space nebula" gradients), a custom app icon,
and ~18 screens.

## Phase 4 — Productization & the migration wedge (Apr–May 2026)

Turning an engine into a product:

- **Build flavors** (Play / F-Droid), a Pro entitlement layer (`EntitlementSource`,
  `ProEntitlementManager`, `FeatureGate`), **Play Billing v7**, and a paywall.
- **`feat: Google Timeline import — the migration wedge`** — the deliberate go-to-market wedge for
  people whose Google Timeline cloud sync was discontinued: *"Lost your Timeline? Bring it home."*
- `.voyager` restore, an OSM-based geocoding overhaul with real place names, a rough-timeline mode,
  **Photo Day Story**, **trip detection**, and a **carbon footprint** engine.

## Phase 5 — Hardening in "waves" (May–Jun 2026)

The most distinctive part of the process. A single living document — `FEATURE_TEST_CATALOG.md` —
became the hub, cataloguing every feature as **Waves 0 → 10**, ordered by real-world UX impact.
Each card carries a *"How to travel-test"* recipe and a *"Flagship bar"* the feature must clear.
The method: **verify each feature on a real travel-test, then raise it to the flagship bar.**

Representative commits show the discipline:

- `Wave-0 tracking-foundation hardening` → `Wave-1 timeline correctness (W1.1–W1.3)` →
  `Wave-2 geocoding/naming + place dedup` → `Wave-3 ViewModel verify + cleanups`.
- A **pipeline-package purge of Room** behind a typed `PipelineGateway` seam, locked in CI.
- **Typed entity IDs** (`PlaceId` / `VisitId` / `SegmentId`), a frozen NoOp sync seam, and a
  **mileage subsystem** (Wave 10) with an IRS/HMRC-ready calculator.
- A **Strava-parity fitness subsystem** (D1): Record + Activities screens, splits, elevation,
  auto-pause, race-yourself segments.
- A **heatmap + Year-in-Review** engine (A6), an **accelerometer-signature** activity classifier
  (C2), and **place repeatability** so recurring places resist confidence decay (C1).

The database schema evolved from v1 through **v13** across these waves, each migration exported and
tested.

## Phase 6 — Publishing polish (Jul 2026, current branch)

The `fix-timeline-map-correctness` branch is the final pre-launch pass:

- A deep correctness audit that removed the last `!!` ship-blocker, fixed the **"Near [area]"**
  naming so no "Unknown place" ever reaches the timeline, and cleaned UI insets/typos.
- Because Voyager is pre-production, the **database was collapsed back to a clean schema v1**
  (destructive downgrade — fine with no real users).
- The onboarding was reworked (**Splash → Intro → Permissions → Persona → Main**), tracking
  Start/Pause/Stop was rebuilt, and — for launch — **every feature was made free** while the
  billing layer stays dormant for a possible future subscription.

---

## Engineering process, in short

- **Conventional Commits** throughout (`feat(scope):`, `fix()`, `docs(catalog):`, `test()`), giving
  a legible history across 200+ commits.
- **Docs coupled to code** — feature commits land alongside `docs(catalog): mark … built` updates, so
  the catalog never drifts from reality.
- **A written flagship bar per feature**, and a habit of **testing on real travel**, not just unit tests.
- **CI on every PR:** per-flavor unit tests (`testPlayDebugUnitTest`, `testFdroidDebugUnitTest`),
  lint, and assembly of both flavors, plus a **monthly OWASP dependency scan**.
- **One engineer**, deliberate architecture, and a willingness to make a hard call — the visit-first →
  stream-first rewrite — when the retrospective said the foundation wouldn't scale.

*Written by Anshul (Cosmic Laboratory).*
