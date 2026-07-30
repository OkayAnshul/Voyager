# Voyager — Interview Prep (Deep Dive + Q&A)

> Your single source of truth for talking about Voyager in depth. Everything here is verified against the
> shipped code. Read §0 first — it's the honesty layer that keeps you from being caught out, and interviewers
> *reward* candid trade-off talk.

**Verified project facts (say these with confidence):** 212 commits over ~12 months (Aug 2025 → Jul 2026),
solo; ~57,200 lines of production Kotlin across 413 files; 660 unit/instrumented tests across 107 files; a
30-table SQLCipher-encrypted Room schema; 20 WorkManager jobs; 35 use-cases; 28 screens; 26 ViewModels;
100% Jetpack Compose.

---

## 0. Honesty layer — know these before you walk in

Being precise about what's real is a senior signal. Lead with strengths, but never overclaim:

- **Pre-production, no real users.** Battery/accuracy numbers are rigorous *estimates and bench figures*, not
  field data. Say "estimated ~30–50% battery savings in sleep mode," never "users saw."
- **It's a heuristic engine, not ML — on purpose.** Google/Arc have years of labelled data and trained models.
  Voyager doesn't try to win on raw inference accuracy; it wins by being **explainable, private, and bundled**.
  If asked "why not ML?": on-device, no training data, and every inference must be *explainable and correctable*
  — a black-box model fights the core value prop.
- **Some older docs describe an earlier design.** `docs/algorithms/PLACE_DETECTION.md` and
  `docs/appendices/{FLAWS_AND_ADVANCES,TECHNOLOGY_STACK}.md` (Dec 2025) describe a batch **DBSCAN + a 47K-line
  AppStateManager**. The **shipped** engine is the online, streaming **hysteresis pipeline** described below.
  If an interviewer quotes DBSCAN, say: "That's the earlier batch design; I moved to an online streaming detector
  — here's why." That *is* the story.
- **AI-assisted.** ~93 of 212 commits are co-authored with an AI pair-programmer. Be upfront: "I built it with
  AI pair-programming; the architecture, the trade-offs, and the product direction are mine — ask me about any
  line." Then prove it with the depth below.
- **"Zero `!!`"** is true for the core shipping surfaces; ~6 guarded `!!` remain inside `StatisticsScreen.kt`
  (each behind an `if (x != null)` block). Don't claim literally zero everywhere.
- **4 of 7 differentiators shipped.** The family one-bit handshake, duress mode, and OSM contribution loop are
  designed but not built. Framing: "I shipped the 4 that define the moat; the other 3 are the roadmap."

---

## 1. The pitch

**30 seconds:** "Voyager is a private, on-device location timeline for Android. It remembers everywhere you've
been, can *prove* it when you need to — audit-ready mileage, trips, presence — and shows you your patterns, and
it can always explain *why* it inferred what it did. No cloud, no account, encrypted on the phone from day one.
Think Google Timeline meets a tax-mileage app meets a private fitness tracker — one on-device record, many jobs."

**5 minutes (the arc):** the problem (Google killed Timeline cloud sync; location apps monetize your data) →
the wedge (import your Timeline, keep it on-device) → the hard part (turning a noisy GPS stream into a truthful,
*explainable* timeline in real time) → the architecture (an 8-stage single-writer pipeline) → the moat
(evidence + honest gaps + one-record-many-jobs) → the process (wave-by-wave, verify-then-flagship, 660 tests) →
honest limitations and what's next.

---

## 2. Architecture

**Clean architecture, dependency rule strictly inward.** Six packages: `platform/` (Android services, receivers,
20 WorkManager workers) → `capture/` (Location/Activity/Accel/Baro/Step) → `pipeline/` (filtering, inference,
segmentation) → `storage/` (Room + SQLCipher) → `domain/` (models, use-cases, repository **interfaces**) →
`presentation/` (Compose + ViewModels). Collapsed to the classic 3 rings: Presentation → Domain ← Data.

- **Repository interfaces live in `domain/repository/`; implementations in `data/repository/`** (e.g.
  `LocationRepository` vs `LocationRepositoryImpl`), wired by Hilt. ViewModels take use-cases/repositories,
  **never DAOs**. The rejected shortcut is called out in `ARCHITECTURE.md`: a use-case taking a `LocationDao`
  directly is marked "❌ Bad."
- **DI:** Hilt, all modules `@InstallIn(SingletonComponent::class)` — Database/Repository/Pipeline/Tracking/
  Worker/Network/Geocoding/DataStore. The pipeline seam is an abstract `@Binds bindPipelineGateway`.
- **Cost paid:** entity↔domain mapper boilerplate + interface duplication. **Payoff:** testability and a storage
  layer you can swap or lift off Android (see the KMP seam, §5).

---

## 3. The real-time pipeline (the heart of the app)

**`PipelineConsumer.processSample()` runs numbered stages on every accepted sample, on a single coroutine
draining one channel.** The actual code order (worth memorizing — it differs from the naive "filter first"):

| # | Stage | What it does |
|---|---|---|
| pre | Early timestamp commit | Records `lastAcceptedAt` *before* stages run, so the gap watchdog can't fire a false GPS_LOSS mid-processing |
| 1 | Persist raw | Append-only raw row written **before** any filtering — evidence is never lost |
| 2 | Normalize | Round lat/lng to 7 dp, clamp speed ≥ 0, wrap bearing `((x%360)+360)%360` |
| 2a | **Dedup (on RAW coords)** | Suppress if `distance < noiseFloor && Δt < 30s`, where `noiseFloor = max(3.0, avg accuracy)` — kills stationary jitter that produced *48+ samples/min* |
| 2a′ | **Anti-spoof (on RAW)** | Drop physically-impossible jumps *before* Kalman so a spoofed point can't corrupt the filter's reference |
| 2b | **Kalman filter** | 4-state constant-velocity smoothing (§3.1) |
| 3 | Quality score | Discard mock / accuracy > 200 m / stale (motion-aware staleness) |
| 4 | Fuse activity | Merge activity-recognition (freshness-gated 3 min) + Kalman speed + step-rate + accel signature; adaptive sampling; battery-saver; dormant |
| 5 | Displacement safety-net | If AR misses a transition: needs 2 strikes, >150 m, >1.5 m/s, accuracy ≤ 30 m, 5-min cooldown |
| 6 | **Visit detection** | Runs **before** the segmenter so dwell periods aren't fragmented |
| 7 | Segment | Build movement segments; emit an ephemeral in-progress snapshot for the live UI |
| 8 | Commit state | Under a lock, record last accepted sample id/timestamp + pipeline latency |

**Why single-threaded / serial (the key design idea):** the consumer loops over one
`Channel<RawSample>(capacity = 64, onBufferOverflow = SUSPEND)` on one coroutine. The stages
(DedupSuppressor, Kalman, Segmenter, the visit FSM) hold **mutable state with no locks** — correctness comes
from **sequential processing**, not synchronization. This removes read-modify-write races on the Kalman `x`/`P`
matrices, the dedup's last-sample, the segmenter buffer, and the dwell accumulator. Backpressure is `SUSPEND`
(not DROP) at capacity 64, so under load the oldest unprocessed samples are retained — **no data loss**. The
explicit cost: on session stop, `resetSessionState()` must manually clear every stage's mutable state so it
can't leak into the next session.

> **Interviewer: "How do you avoid race conditions without locks?"** → "The hot path is single-writer by
> construction: one channel, one consuming coroutine, stages run strictly in sequence. I only reach for a mutex
> in the three places where two coroutines genuinely meet — the segmenter (pipeline + the stop() path), the
> runtime-state store, and the state-commit. Everything else is serial-by-design, which is simpler *and* faster
> than fine-grained locking."

### 3.1 Kalman filter — a proof-of-depth detail
Hand-rolled **4-state `[x, y, vx, vy]` constant-velocity** filter in a **local tangent plane** (meters).
Process noise `2.0 m/s²`; measurement noise `R = accuracy²`; **resets** on `Δt > 300 s`; **re-anchors** the
flat-earth reference past **25 km** (approximation breaks down on multi-city days). Uses the **Joseph-form
covariance update** `P = (I−KH)P(I−KH)' + KRK'` plus forced symmetry — because it "guarantees P stays symmetric
positive-semi-definite across 10,000+ iterations, preventing filter divergence in multi-day sessions." Outputs
velocity-derived speed and a **continuous bearing** (`atan2(vx, vy)`), which raw GPS can't give at low speed.

> Why CV not constant-acceleration? Phone GPS + human motion is well modeled by constant velocity with a
> healthy accel process-noise term; CA adds two states and overfits jitter. Why Joseph form? Numerical
> stability over multi-day runs — the naive `(I−KH)P` form can lose symmetry and diverge.

### 3.2 Honest gaps + the gap watchdog
`GapWatchdogPolicy` is a **pure, unit-tested predicate**. A 60-second loop calls `shouldCreateGap()`, which
fires only when *all* hold: GPS is actually on (`expectedInterval > 0`), not in dormant grace, silence
`> expectedInterval × 5`, silence `≥ 10 min`, and it hasn't already logged this gap. `gapReason()` distinguishes
`DORMANT` (intentional GPS-off) from `GPS_LOSS` (unexpected). Gaps are written as **real, reason-coded
`MovementSegmentEntity(segmentType = GAP)` rows** (also PERMISSION / DOZE / PROCESS_DEAD / MANUAL_PAUSE), and a
`bridgeGapIfNeeded()` extends the latest gap's end to the next real sample so the timeline stays contiguous.
**This is the "honest gaps" moat in code: missing data is a first-class row with a reason, never a faked line.**

### 3.3 Crash recovery
In-progress dwell is serialized to `pendingVisitCandidateJson`, so process death doesn't lose it. On restart,
`activeSessionId != null && endedAt == null` ⇒ create a GAP for the dead period and resume with
`restartReason = CRASH_RESTORE`. The DB self-heals: it eagerly probes the encrypted file and, if unreadable,
recreates it fresh rather than crash-loop; every open runs `PRAGMA journal_mode=WAL` + `integrity_check`.

---

## 4. Data model (30 entities, evidence-first)

**Three-tier truth:** **raw** (append-only, never mutated — `raw_location_samples`, `raw_activity_samples`,
`raw_step_samples`, `tracking_sessions`) → **derived** (canonical timeline — `movement_segments`, `routes`) →
**semantic** (stable clusters `places`, immutable intervals `visits`). Plus ops (`current_runtime_state`,
`health_log`), analytics rollups, search, feedback, and the mileage/trips/workouts/vehicles cluster.

**Evidence lives in dedicated sidecar tables**, one per canonical object, each keyed PK = parent PK with FK
CASCADE:
- `segment_evidence` — speed stats, `activityVotesJson`, `decisionRuleVersion`, `explanationJson`, and
  **`counterEvidenceJson` — *why competing labels were rejected*.**
- `visit_evidence` — entry/exit sample ids, dwell curve, inside/outside counts, arrival/departure confidence.
- `place_evidence` — cluster density, visit counts (7d/30d), repeatability, naming candidates.

This is the **EvidencePolicy**: "no UI model may expose an inferred label without attached evidence." Every
"Walking" chip can show its speed range, sample count, AR confidence — and why "Cycling" was rejected. **No
competitor exposes counter-evidence; that's the trust moat.**

**Migration policy (a mature call):** schema evolved v1→v13 in dev, then — because there are no production DBs —
was **collapsed back to `version = 1`** (12 migration objects deleted, single `1.json` regenerated). Destructive
migration is enabled **only on downgrade**; a missing *upgrade* migration **throws at open time** ("caught in
development, never on a user's device — user data is sacred"). Inert `userId`/`revision`/`deletedAt` audit
columns were shipped early to avoid a future migration.

**Typed IDs:** `@JvmInline value class PlaceId/VisitId/SegmentId/RouteId(Long)` — zero runtime cost (inlines to a
`long`), compile-time safety so you can't pass a `VisitId` where a `PlaceId` is expected. Adopted gradually from
the repository boundary inward (DB keeps raw `Long`).

---

## 5. Algorithms (with the real constants)

For each: the problem, the approach, and *why heuristic beats ML here*.

- **Visit detection** (`DetectVisitUseCase`) — an online state machine, not batch clustering. Adaptive arrival
  radius scaled by GPS accuracy (×0.5 / ×0.75 / ×1.0), **entry + exit hysteresis** (needs N samples inside/
  outside), a **first-stable anchor** (latches the first sample inside `radius × 0.5` so "walking up to a place"
  ≠ "being there"), a **frozen post-confirmation centroid** (keeps place-matching stable), and an **honest
  departure timestamp** = last-inside sample (not the exit-confirming sample, which would inflate dwell). A 90-s
  grace preserves motion between visits. Why not DBSCAN? Batch clustering is O(n²), needs the whole day in memory,
  can't run live, and can't emit real-time arrival/departure or per-visit evidence.
- **Quick-return** (`QuickReturnPolicy`) — a return within **30 min** that lands back in-radius *continues* the
  prior visit (handles app-killed-while-sitting-still, which produces no movement segments). Broken only by
  "moved-away" evidence: a motorized gap, or a WALK covering `≥ max(2×radius, 100 m)`.
- **Geocoding conflict resolver** (`GeocodingConflictResolver`) — multiple free geocoders disagree; never state a
  wrong house number as fact, never dump raw coords. Composite score `priority×0.4 + confidence×0.3 +
  specificity×0.2 + recency×0.1`; a **name-priority chain** (user name > user category > provider name >
  **"Near [neighborhood/street/city]"** > semantic > coordinates); and an **accuracy gate** that coarsens to the
  precision the evidence supports (tiers 0.85 / 0.65 / 0.45), with a cross-provider agreement bump. Rule: "a
  nearby POI must never masquerade as the place's own name."
- **Dedup** (`DedupSuppressor`) — accuracy-aware noise floor `max(3, avg accuracy)` instead of a fixed 3 m,
  30-s window; also drops out-of-order FLP batches.
- **Anti-spoof** (`SpoofHeuristics`) — `isFromMockProvider` misses rooted injectors, so flag **teleportation**:
  a jump implying **> 340 m/s (~Mach 1, above any airliner)** within a 1 s–10 min window and ≥ 1 km. Runs *before*
  the Kalman filter. Conservative by design → real flights/jitter never trip it.
- **Accel-signature classifier** (`AccelSignatureClassifier`) — orientation-independent **variance of |a|** to
  break the DRIVE/TRANSIT/CYCLE speed-ambiguity: `< 0.5` STILL, `≥ 3.0` ON_FOOT, between = smooth motion; needs
  ≥ 8 samples. Explicitly a **hint fused with GPS/AR/steps, never a verdict** — cheap and explainable vs chasing
  Google's trained model.
- **Confidence decay + repeatability** (`PlaceConfidenceDecay` / `PlaceRepeatability`) — pure functions. 180-day
  grace, then ×0.99/day down to a 0.4 floor; a fully-recurring place earns up to +365 days grace and a 0.7 floor.
  Repeatability blends recency (30-day visits, saturates at 8) and history (lifetime, saturates at 20), weights
  0.7/0.3. Rationale: "'court/IRS-grade' trust shouldn't fade for the café you visit every week."
- **Mileage math** (`MileageCalculator`) — pure, primitives only. Emission factors (petrol 2.31, diesel 2.68,
  EV 0.71/kWh, hybrid 1.85, CNG 1.81 kg CO₂), unit normalization to L/km (handles MPG-US/UK, L/100km, kWh/100km),
  cost in **integer minor units** (avoids float summing error). A drive with no classification row is implicitly
  UNCLASSIFIED (the sparse table's absence *is* the state). Each row carries per-row GPS evidence — the edge over
  MileIQ (which exports a PDF, not the samples).
- **Workout** (`WorkoutRecorder` / `WorkoutStatsCalculator`) — **auto-pause** below 0.5 m/s (so pace isn't
  diluted at lights), average over *moving* time (like your watch), **elevation hysteresis** counts gain only
  once Δ ≥ 3 m (rejects baro/GPS jitter), glitch rejection > 50 m/s, accuracy gate 30 m. Live and save-time paths
  share thresholds so numbers reconcile; splits interpolate at each km boundary (Strava-style whole units).
- **Commute / Predict** — median commute (robust to outliers), 30-day lookback, min 3 legs, cap 3 h; next-place
  prediction gated to today's day-of-week routines with confidence ≥ 0.55, excluding ones done in the last 2 h.

---

## 6. Concurrency & locking

Three **single authorities**: `TrackingRuntimeCoordinator` (lifecycle: start/stop/pause/boot/crash/watchdog),
`TimelineStateStore` (the only writer of runtime state), `PipelineSerializer` (single-writer gate for the hot
pipeline). Only three long-lived flows (runtime state, latest motion, live timeline) — event-driven, not
polling. **No `GlobalScope`** anywhere; scopes are Application / Service / per-Worker.

Exactly **three mutexes**, each justified:
- `PipelineSerializer.stateCommitMutex` — guards the state-commit read-modify-write.
- `TimelineStateStore.updateMutex` — serializes the *application-level transform lambda* that Room's
  `@Transaction` does **not** serialize (two coroutines could read the same snapshot and lose a write; the DAO
  also bumps a monotonic `stateVersion` as an optimistic-concurrency breadcrumb).
- `Segmenter.mutex` — the one stage touched by two coroutines (pipeline + `closeCurrentSegment()` on stop); its
  live-snapshot reader uses `tryLock()` and returns null rather than block the pipeline.

Everything else is lock-free because it's serial-by-construction.

---

## 7. Security / encryption

SQLCipher is **always on — there is no unencrypted mode.** The passphrase is derived **deterministically** by
AES/GCM-encrypting a fixed string with an **AES-256 key that lives in the Android Keystore** (alias
`voyager_db_encryption_key`) and never leaves it — so the DB can only be opened on the device that created it.
The fixed IV looks alarming but is correct here: the "plaintext" is constant and the output is used only as a
passphrase, so `setRandomizedEncryptionRequired(false)` is required (a random IV would produce a different
passphrase every open and make the DB unreadable). Consequence: the DB is **device-bound and excluded from cloud
backup**, so device migration uses Voyager's own encrypted export.

> Great ownership line: "The design doc specified opt-in encryption with a random SecureRandom key. I hardened
> it to always-on, Keystore-derived, and device-bound — privacy should be the architecture, not a toggle."

---

## 8. Testing & CI

**660 tests / 107 files.** The load-bearing ones:
- `SyntheticPipelineTest` (instrumented) — drives the *real* Segmenter through `PipelineGatewayImpl` with a
  synthetic day and asserts pipeline invariants — the safety net for any pipeline refactor.
- `PipelineGatewayBoundaryTest` — a code-scanning test that fails CI if any file in `pipeline/` imports a Room
  DAO/entity (enforces the KMP seam) with a precise file list.
- `TimelineStateStoreConcurrencyTest` — concurrent updates don't lose writes (a `RacyFakeDao`; final value ==
  update count) — proves the H1 mutex.
- `MileageCalculatorTest` (14) — exact CO₂ constants, the EV kWh path, unit-invariance, integer-cent rounding.
- Plus `LocationKalmanFilterTest`, `SpoofHeuristicsTest`, `GapWatchdogPolicyTest`, `QuickReturnPolicyTest`,
  `DayBoundaryResolverTest`, `VisitIntegrityTest`, `StepRateCalculatorTest`, migration/DAO tests.

**CI (`.github/workflows/`):** `pr.yml` runs **per-flavor** unit tests (`testPlayDebugUnitTest` +
`testFdroidDebugUnitTest` — a real lesson: flavored projects have no bare `testDebugUnitTest`), assembles both
flavors, and lints. `dependency-check.yml` runs a **monthly OWASP scan failing on CVSS ≥ 7**. Commits are
Conventional Commits with a `catalog` scope so feature work lands *with* its doc update.

---

## 9. Key decisions & trade-offs (be ready to defend each)

- **Stream-first → hybrid, not pure either.** V1 was visit-first (laggy live UI, gaps hard to reason about).
  A naive stream-first fix put "stop truth" in multiple layers and hid duplicates at render time. The shipped
  model is a **deliberate hybrid**: visit-first for confirmed stops (durable rows), segment-first for movement/
  gaps, runtime-state for live UI, async geocoding for names. Cost: more moving parts; payoff: live *and*
  canonical *and* explainable.
- **The PipelineGateway seam** — the pipeline package imports **no Room**; it talks to a thin interface with
  projection/draft types. It's a KMP-ready seam (pure logic could lift off Android) and concentrates the atomic
  transaction (`commitClosedSegment` inserts segment+evidence+route atomically). Enforced by a CI test.
- **Schema collapse to v1** — threw away a throwaway 12-step dev migration chain because there are no user DBs.
  Iteration *plus* the judgment to delete scaffolding.
- **Heuristic engine, not ML** — explainable, private, no training data; every inference is correctable.
- **Encryption hardened beyond the design** — always-on, Keystore-derived (§7).
- **Typed value-class IDs, gradual rollout** — safety at the boundary without a big-bang refactor.

---

## 10. Honest limitations — framed as strengths + what's next

- **First-visit place accuracy** trails Google (no POI/visit priors). *Strength framing:* every place is
  correctable in one tap and *explains itself*; repeat visits raise confidence. *Next:* POI priors, an OSM
  contribution loop.
- **Activity classification is speed-ambiguous** (DRIVE/TRANSIT/CYCLE). *Strength:* the accel-variance signal
  narrows it, and every label is fused, explained, and correctable — not a black box. *Next:* a small on-device
  model *if* it can stay explainable.
- **OSM POI coverage < Google** — a data problem, not a code one. *Strength:* honest "Near [area]" naming +
  frictionless rename; *next:* the contribution loop.
- **First-hour emptiness** — the pipeline needs ~10 min of movement to show value. *Next:* the Google-Timeline
  import (already built) seeds history instantly — that's the wedge.
- **Test coverage started low** — the old engine was graded F on testing; the new engine has 660 tests including
  pipeline-invariant and boundary tests. *Story:* "I audited my own code, graded it honestly, and fixed the
  weakest axis."
- **Performance:** add DB indices (already partly done) and a spatial index for clustering; no pagination yet on
  some lists.
- **Accessibility & light mode:** dark-only today; custom-drawn charts lack TalkBack semantics — a known,
  scoped next step.
- **3 of 7 differentiators unbuilt** (family handshake, duress mode, OSM loop) — the roadmap.

---

## 11. War stories (STAR — pick 2–3 per interview)

1. **The Doze/OEM self-heal.** *S:* aggressive OEMs (Xiaomi/Samsung…) silently kill the receiver — a whole day
   could go dark. *T:* recover without user intervention, even during GPS loss. *A:* found that
   `ActivityCapture.start()` never reset `lastTransitionAt`, causing continuous AR-client thrash after a stale
   re-register; anchored it to registration time and drove self-heal off the gap-watchdog timer;
   `TrackingHealthCheckWorker` restarts a silently-dead service after 3 min. *R:* self-healing tracking + a
   Reliability screen that deep-links to the device's `dontkillmyapp.com` page.
2. **Dormant mode on sensor-less devices.** *S:* entering dormancy turned GPS off then armed the
   significant-motion sensor — on a device without that sensor, `startListening` silently no-ops, leaving
   tracking dark with no wake path. *A:* gated dormancy on `significantMotionDetector.isAvailable`; made
   `isDormant` `@Volatile` (written on the sensor thread, read on the pipeline thread). *R:* 6-case
   `DormantModeManagerTest`.
3. **GPS spam → GB database.** *S:* no filtering → 10 fixes/sec, DB ballooned to GB, battery drained. *A:*
   multi-stage filter (accuracy/adaptive-movement/speed/throttle), later hardened into the pipeline's
   DedupSuppressor + QualityScorer. *R:* ~60% fewer DB writes, ~40% battery improvement (bench).
4. **Spoofing beyond the OS flag.** *S:* `isFromMockProvider` misses rooted injectors. *A:* a pure teleport
   heuristic (> Mach 1) run *before* Kalman so a spoofed point can't corrupt the filter. *R:* 8-case test
   including a real-flight case that must *not* trip.
5. **Overnight double-counting.** *S:* the daily rollup summed each visit's full dwell on its arrival day, so a
   9 h overnight stay dumped all 9 h on day N. *A:* clamp dwell to each day's window via an `overlapMs` query;
   fixed the timezone in `computeYesterdayKey`. *R:* `DayBoundaryResolverTest` covers the split.
6. **The step-rate desk-shuffle (root cause vs symptom).** *S:* a desk shuffle classified as RUNNING. *A:* the
   wrong hypothesis was the fusion thresholds; the real cause was cadence = `steps / bucketSpan`, so 5 steps in
   a 2 s bucket extrapolated to 150 spm. Extracted a pure `StepRateCalculator` that floors the denominator at
   30 s. *R:* 8-case test; a lesson in fixing causes not symptoms.
7. **Dwell inflation.** *S:* departure recorded at the exit-*confirming* sample (hysteresis + the walk-out)
   inflated dwell. *A:* record `lastInsideSampleAt`. *R:* test asserts the exact departure time.
8. **The last `!!` ship-blocker.** *S:* `ReliabilityScreen` had `state.hoursSinceLastSample!!` — a delegated
   property can't smart-cast across `when` branches. *A:* hoist into a local `val` so the null-check
   smart-casts. *R:* zero user-facing `!!` on the core surfaces (verified by grep).

---

## 12. Q&A bank

**System design**
- *"Design an on-device location timeline."* → Capture (adaptive sampling + foreground service) → a single-writer
  pipeline (dedup → filter → quality → fuse → visit/segment) → three-tier storage (raw/derived/semantic) with
  evidence sidecars → honest gaps via a watchdog → async geocoding. Walk the 8 stages; emphasize serial-by-
  construction and crash recovery.
- *"Why a single-threaded pipeline? Doesn't that limit throughput?"* → GPS is ~1 sample/sec, not a throughput
  problem; correctness and simplicity dominate. Serial removes whole classes of races for free; backpressure is
  SUSPEND so nothing is lost. If I ever needed parallelism, the stages are pure enough to shard by session.
- *"How do you keep the timeline correct across process death?"* → persist the in-progress dwell as JSON; on
  restart, detect an unclosed session, insert a reason-coded GAP, resume with CRASH_RESTORE; the DB runs
  integrity_check and self-heals an unreadable file.

**Android specifics**
- *"How do you track in the background reliably?"* → a foreground service (`foregroundServiceType=location`) +
  adaptive sampling + a dormant tier woken by the hardware significant-motion sensor; a health-check worker
  restarts a silently-killed service; a Reliability screen guides OEM battery-exemption. Honest gaps make any
  remaining loss visible rather than faked.
- *"WorkManager vs the foreground service?"* → the service owns *live* capture; WorkManager owns *deferred*
  batch jobs (rollups, geocode backfill, trip detection, retention) — I removed WorkManager *constraints* after
  finding constrained workers "never ran" on some OEMs.
- *"Doze/battery?"* → motion-aware sample staleness (20-min tolerance in Doze states so Samsung/Xiaomi batching
  doesn't look like GPS loss), a sleep-schedule pause, adaptive intervals, a battery-saver ×2 tier.

**Concurrency / data**
- *"Avoiding races without locks?"* → §6 answer (single-writer channel; 3 justified mutexes).
- *"Why sidecar evidence tables instead of columns?"* → evidence is large, sparse, and 1:1 with a parent; a
  sidecar keeps the hot derived tables lean and CASCADE-deletes cleanly, and it's where `counterEvidenceJson`
  lives.
- *"Migration strategy?"* → a missing upgrade migration *throws* (never silently wipes); destructive only on
  downgrade; schemas exported and diffed; I collapsed the pre-release chain to v1 because there were no user DBs.

**Product / trade-offs**
- *"Why on-device / no cloud?"* → it's the moat no incumbent can copy without breaking their business model, and
  it removes an entire class of privacy/liability/cost problems (no Google Places bill, no breach surface).
- *"Why heuristics not ML?"* → §0. Explainable + private + no training data; and every inference is correctable,
  which a black box can't be.
- *"What would you do with a team / more time?"* → the 3 unbuilt differentiators, a spatial index, POI priors,
  light mode + accessibility semantics, and real-device dogfooding across OEMs for field battery data.

**Behavioral**
- *"Hardest bug?"* → the step-rate desk-shuffle (symptom vs root cause) or the Doze AR-thrash (self-healing
  distributed state).
- *"A decision you reversed?"* → visit-first → stream-first → and then *not* going pure stream-first when the
  research showed split ownership of stop-truth — landing on the hybrid. Shows I change my mind on evidence.
- *"Biggest weakness of the project?"* → §0 + §10, delivered candidly. That candor is the point.

---

*Keep this doc close before interviews. Every claim here maps to a file you can open and explain.*
