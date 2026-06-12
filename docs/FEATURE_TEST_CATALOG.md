# Voyager — Feature & Function Test/Improvement Catalog

_The single working hub for taking Voyager from "feature-complete" to **flagship**._
_Date started: 2026-06-10._

This document catalogs **every feature** and the key functions behind it, so each can be
**travel-tested and improved one at a time**. It is a *living worklist*: tick the checkboxes
as you go.

All file paths are relative to `app/src/main/java/com/cosmiclaboratory/voyager/` unless noted.

## How to use this document

- Work **top to bottom** — Part A is ordered by *real travel-test UX impact*, not code domain.
  Wave 0 (does tracking even run?) matters more than Wave 10 (polish).
- For each card: do the **How to travel-test** step on a real trip, tick `verified` if it
  behaves, then raise it to the **Flagship bar** and tick `improved`.
- A feature isn't done until both boxes are ticked.

## Status legend

- `[ ] verified` — behaves correctly on a real-world test.
- `[ ] improved` — polished to the flagship bar.
- Part B/C statuses: ✅ done · ◐ partial / in progress · ☐ todo · ⊘ blocked.

## Part map

| Part | What it covers | Source of truth |
|------|----------------|-----------------|
| **A** | Implemented features — verify + improve (Waves 0–10) | this doc (authored from code) |
| **B** | Trust & correctness gaps in built features (T1–T15) | `yes-finish-phase-4-radiant-rabin.md` |
| **C** | Planned-but-unbuilt features (A1–L4, P0–P3) | [`voyager-master-improvement-backlog.md`](voyager-master-improvement-backlog.md) |
| **D** | Competitor feature matrix + differentiators | [`research/competitor-analysis.md`](research/competitor-analysis.md) |

---

# Part A — Implemented features: verify + improve

## Wave 0 — Tracking foundation: *does it run, the whole trip?*

### W0.1 — Foreground location capture & service lifecycle · Status: [ ] verified (device drive-test pending)  [x] improved 2026-06-10
**What it does:** Keeps the process alive and streams GPS via Fused Location Provider, with dedup and sanity gates.
**Key functions / files:**
- `platform/service/LocationCaptureService.kt` (`start`, `ensureCapturesStarted`, `onDestroy`)
- `capture/LocationCapture.kt` (`start`, `handleIncoming`, `processLocation`, AtomicLong dedup CAS gate; future >1h / past <-10min / stale-cached rejection)
**How to travel-test:** Start tracking, drive 30 min with the screen off; confirm the persistent notification stays and samples land continuously (check the day on return).
**Flagship bar:** Zero dropped sessions over a full day; no duplicate samples; no future/stale fixes accepted; survives screen-off + Doze.
**Verification (2026-06-10, code-level — device drive-test still required before ticking `verified`):** ✅ All claimed mechanisms present & correct — FGS with `foregroundServiceType=location` + `FOREGROUND_SERVICE_LOCATION`, `START_STICKY`, `startForeground` in `onCreate`, idempotent mutex+`AtomicBoolean` capture start (guards crash-restart vs user-start race), CAS dedup, future(>1h)/past(<-10min) sanity gates, and a persisted `lastAcceptedAt` seed to reject stale cached FLP fixes after restart.

**Improvement pass (2026-06-10) — compiled (`:app:testPlayDebugUnitTest`) + 11 new tests green:**
> - **F3 ✅** Tracking notification is now tappable (`setContentIntent` → opens app) with a warm, privacy-forward, time-of-day copy (no more "tracking your journey"); keeps Pause/Stop + proper `ic_notification_location` icon — `VoyagerNotificationManager.showTrackingNotification` / `trackingCopy`.
> - **F4 ✅** Removed the duplicate `tracking_status` channel + inline notification from `LocationCaptureService`; the service now reuses `VoyagerNotificationManager` (single source). Side benefit: `createChannels()` (previously never called) now runs, so the other channels exist too.
> - **F5 ✅** Extracted the dedup/sanity logic into pure `SampleAdmissionGate` and added `SampleAdmissionGateTest` (11 cases incl. concurrency) → contributes to Part C **K2**.
> - **F6 ✅ (found during F5):** dedup accepted *equal* timestamps, so the same fix delivered by both active+passive callbacks could pass twice. Fixed to strictly-greater (`getAndUpdate`); concurrent same-timestamp admits now accept exactly one.
> - **F1 / F2 (low, accepted):** out-of-order-within-10min fixes are dropped as duplicates (documented in a test), and the cold-start seed race is covered downstream by `QualityScorer` staleness. Left as-is — low risk, defense-in-depth exists.
> - **T15 ✅ (2026-06-12 — spoof detection beyond the OS flag):** `Location.isFromMockProvider` only catches the official mock-location API; rooted-device injectors feed coordinates without it. Added a pure `SpoofHeuristics` (in `pipeline/stage`) that flags **teleportation** — a jump from the last accepted fix that implies a physically impossible speed (> ~340 m/s ≈ Mach 1, above any commercial jet). Wired into `PipelineConsumer` right after dedup and **before Kalman** (so a spoofed point can't corrupt the filter's reference) — implausible jumps are dropped like a mock fix. Conservative by design: only fires inside continuous tracking (1 s–10 min deltas; large deltas are treated as legit travel-while-untracked) and above a 1 km jump floor, so real flights and GPS jitter are never flagged. Locked by `SpoofHeuristicsTest` (8 cases: teleport, cruise-speed flight, gap straddle, sub-second/negative deltas, jitter floor, ceiling boundary). Doubles as gross-glitch rejection. See Part B.

### W0.2 — Adaptive sampling & battery tiers · Status: [ ] verified (device drain-test pending)  [x] improved 2026-06-10
**What it does:** Scales GPS frequency to motion state and a battery budget.
**Key functions / files:**
- `capture/AdaptiveSamplingPolicy.kt` (`getCurrentPolicy`, `updateMotionStateWithHysteresis`, tiers OFF/PASSIVE/BALANCED/ACCURATE/WORKOUT)
- `platform/battery/BatteryBudgetController.kt`, `BatteryUsageReporter`, `BatteryBudgetWorker`
**How to travel-test:** Sit still 20 min (expect slow cadence), then walk (expect faster). Check overnight battery drain is modest. Set a tight budget (once F2 ships the UI) and confirm the downgrade notification fires.
**Flagship bar:** ≤ target %/day on Balanced; tier transitions are smooth; budget actually throttles (see T12).
**Verification (2026-06-10, code-level — device drain-test pending):** ✅ Tier structure correct — OFF/PASSIVE → no active GPS (interval 0), WORKOUT → fixed 1 Hz, BALANCED/ACCURATE → motion-based with `tierMultiplier` (ACCURATE = 0.5×). Sleep-window + DORMANT precedence correct (DORMANT wins, GPS off). Hysteresis requires 3 consecutive samples before switching (CAS-free, single-threaded on the pipeline). `BatteryBudgetController` is pure + fully unit-tested (`BatteryBudgetControllerTest`).
> - **Doc fix:** corrected the hysteresis comment ("2 consecutive" → 3) to match the code.
> - **Note (accepted):** a tier or battery-saver-multiplier change reaches the *live* FLP request only on the next motion transition (`PipelineConsumer` calls `updateSamplingPolicy()` on transitions, not every sample). Latency is ≤ one movement; fine in practice.
> - **Gap → F2 (planned P1):** there is no Settings UI to set `batteryBudgetPctPerDay` (defaults to 0 = off), so the budget path is currently unreachable by users. That UI is the planned **F2** "battery-budget mode" item, not T12.

### W0.3 — Dormant mode + significant-motion wake · Status: [ ] verified (device idle/wake test pending)  [x] improved 2026-06-10
**What it does:** Turns GPS off when stationary; wakes on real movement.
**Key functions / files:** `capture/DormantModeManager.kt` (`onActivityUpdate`, `enterDormant`, `wakeFromDormant`, 120s grace), `SignificantMotionDetector`.
**How to travel-test:** Stay home 30 min (GPS should idle), then leave; confirm tracking resumes within a minute and the departure is captured. Also test on a device *without* a significant-motion sensor — tracking must stay alive (now stays at STILL sampling, never goes dark).
**Flagship bar:** No battery burn while parked; wake within ~60s of leaving; no false wakes from phone handling.
**Verification (2026-06-10, code-level — device idle/wake test pending):** ✅ Entry after 3 consecutive STILL (~4.5 min @ 90s); wake via one-shot `TYPE_SIGNIFICANT_MOTION` (zero battery, hardware-filtered so phone handling won't false-wake — satisfies the flagship bar); resume at STILL (avoids the old aggressive-12s-on-wake bug); 120s exit grace integrates with `PipelineConsumer` gap/displacement logic. New `DormantModeManagerTest` (6 cases).
> - **D1 ✅ (real bug):** `enterDormant` turned GPS off and *then* armed the sensor — on a device with **no `TYPE_SIGNIFICANT_MOTION` sensor**, `startListening` silently no-ops, leaving tracking dark with no wake path. Entry is now gated on `significantMotionDetector.isAvailable` (and the existing `motionDetectionEnabled`); without a wake path it stays at low-cost STILL sampling.
> - **D2 ✅ (concurrency):** `isDormant` is now `@Volatile` — it's flipped on the sensor-trigger thread but read on the pipeline thread (gap-reason / debug), same contract as `dormantExitedAt`.

### W0.4 — Activity recognition + stale re-registration · Status: [ ] verified (device all-day test pending)  [x] improved 2026-06-10
**What it does:** Registers AR transitions (STILL/WALK/RUN/BICYCLE/VEHICLE) and self-heals when the OS silently drops the receiver.
**Key functions / files:** `capture/ActivityCapture.kt` (`start`, `reRegisterIfStale`, 30-min staleness), `ActivityTransitionReceiver`.
**How to travel-test:** Switch modes (walk→drive→walk) over an hour; confirm transitions are detected and still firing late in the day.
**Flagship bar:** AR never goes silent for a whole day; re-registration recovers within 30 min.
**Verification (2026-06-10, code-level — device all-day test pending):** ✅ Self-heal (`reRegisterIfStale`) is driven by the **gap-watchdog timer**, not the location-sample path — so it recovers even during GPS loss/dormant, within 30 min + one watchdog tick. `start()` removes the old PendingIntent first (no AR-registration leak); the receiver uses `goAsync()` + the singleton app scope (H8) so high-rate transitions don't leak per-broadcast scopes. New `ActivityCaptureTest` (4 cases on the staleness predicate).
> - **AR1 ✅ (real churn bug):** `ActivityCapture` is an app-launch singleton and `start()` never reset `lastTransitionAt`, so after a stale re-register (with no transition following) **every** subsequent watchdog tick would re-fire stop()/start() — continuous AR-client thrash. `start()` now anchors `lastTransitionAt` to (re)registration time, so a re-register buys a fresh 30-min window; also fixes a redundant re-register when tracking starts long after app launch.
> - **Note (known limitation):** transition confidence is *estimated* from a static table — the `ActivityTransition` API doesn't report confidence (unlike the legacy API). Acceptable; fusion (`FuseActivityStateUseCase`, W1.2) cross-checks against GPS/steps anyway.

### W0.5 — Step counting (reboot-reset handling) · Status: [ ] verified (device walk/reboot test pending)  [x] improved 2026-06-10
**What it does:** Batches the hardware step counter, handling reboot resets.
**Key functions / files:** `capture/StepCapture.kt` (`onSensorChanged` → `StepDeltaResolver`), `capture/StepDeltaResolver.kt`, `RawStepSampleDao`.
**How to travel-test:** Walk a known step count; reboot mid-day; confirm steps keep accumulating without a huge jump/drop. Also pause/resume mid-walk and confirm the tail steps aren't lost.
**Flagship bar:** Daily steps within a few % of a reference app; no reboot spikes (see T7).
**Verification (2026-06-10, code-level — device walk/reboot test pending):** ✅ Correct cumulative-counter→delta conversion; reboot detected by negative delta (baseline reset, no spurious spike); a process restart re-baselines on the first event (no double-count). Logic extracted to pure `StepDeltaResolver` with `StepDeltaResolverTest` (8 cases incl. reboot-no-spike + no-double-count).
> - **SC1 ✅ (accuracy leak):** the buffered tail (steps since the last 5s batch) was **dropped on every `stop()`** — each pause/resume and the end-of-day stop lost up to a window of steps. `stop()` now `flush()`es the pending delta.
> - **SC2 ✅ (lost writes):** `stop()` cancelled the per-session scope, which could **abort an in-flight batch insert**. Writes now go on the app-lifetime `VoyagerApplicationScope`, so they always complete; the per-session scope is gone.
> - **Note:** this is capture-side correctness. **T7** ("step-rate fusion misfires") turned out to be the *cadence derivation* (extrapolating a short burst over a tiny bucket span), now fixed in `StepRateCalculator` and consumed by fusion (W1.2) — accurate batches here feed it.

### W0.6 — Crash/boot recovery & session lifecycle · Status: [ ] verified (device crash/reboot test pending)  [x] improved 2026-06-10
**What it does:** Restores an active tracking session after crash/reboot/app-update; cleanly closes segments/visits on stop.
**Key functions / files:** `platform/coordinator/TrackingRuntimeCoordinator.kt` (`start/stop/pause/resume`, `restoreFromCrash`→`recoverSession`), `BootReceiver`, `VoyagerApplication.repairStrandedVisits`, `domain/usecase/IntegrityRepairUseCase.closeStaleVisits`, `TimelineStateStore`.
**How to travel-test:** While at a place, force-stop the app, wait, then reopen / reboot; confirm tracking restores and the open visit closed with a sensible (non-truncated) dwell — not "still here."
**Flagship bar:** No orphaned open visits; session resumes automatically post-boot.
**Verification (2026-06-10, code-level — device crash/reboot test pending):** ✅ Lifecycle sound — `start` is idempotent (rejects a second session); `stop` flushes the in-progress segment, closes the active visit, and clears state. `restoreFromCrash`→`recoverSession` is wired from three triggers (`LocationCaptureService` null-intent restart, `BootReceiver`, `TrackingHealthCheckWorker`); it dedupes the restore GAP against the watchdog's (upgrading it to `PROCESS_DEAD`), restores the last motion state, and preserves short (<1h) gaps to avoid home fragmentation. Cold-start net: `VoyagerApplication.repairStrandedVisits` closes any visit orphaned by process death. New `IntegrityRepairUseCaseTest` (4 cases).
> - **T3 ✅ (real bug):** `closeStaleVisits` closed a stranded visit at the **selection cutoff** (`lastKnownAlive − 30 min`), truncating its dwell by the whole stale-gap window. Split the API into `staleBeforeMs` (selection) + `closeAtMs` (departure); the cold-start path now closes at the true last-known-alive sample time (dwell clamped ≥ 0). See Part B.
> - **Note (observed, not changed):** two recovery paths can touch the open visit — the coordinator's `recoverSession` (service/boot/health-check; preserves <1h gaps) and the Application cold-start net. They're complementary, but a future pass could unify them so the anti-fragmentation window applies consistently across both.

### W0.7 — Reliability / health & OEM-kill detection · Status: [ ] verified (device OEM test pending)  [x] improved 2026-06-10
**What it does:** Detects aggressive OEMs, sample gaps, and surfaces fix-it guidance.
**Key functions / files:** `presentation/screen/reliability/ReliabilityScreen.kt`+VM, `reliability/OemReliability.kt`, `HealthLogEntity/Dao`, `TrackingHealthCheckWorker`, `reliability/ForceStopBanner.kt`.
**How to travel-test:** On a Xiaomi/Samsung device, leave the app overnight; confirm a gap is detected and the guide button opens the *device-specific* dontkillmyapp page.
**Flagship bar:** Accurate manufacturer detection; honest "hours since last sample"; actionable, non-alarmist guidance.
**Verification (2026-06-10, code-level — device OEM test pending):** ✅ 12-OEM aggressive-device list; `TrackingHealthCheckWorker` restarts a silently-dead service after 3 min (no battery constraint; delegates GAP creation to `restoreFromCrash`); screen shows a gap self-test hero + OEM autostart card + privacy reassurance. OEM logic extracted to pure `OemReliability` with `OemReliabilityTest` (5 cases).
> - **R1 ✅ (actionability):** the "Open setup guide for {manufacturer}" button opened the **generic** `dontkillmyapp.com/` homepage. Now deep-links to the device-specific page (`/samsung`, `/xiaomi`, …), with sub-brands (Redmi/POCO) mapped to the parent guide.
> - **R2 (copy):** sub-hour gaps rendered as "0h ago" — now "less than an hour"; ≥24h shows days.
> - **R3 (cleanup):** removed dead code in `TrackingHealthCheckWorker` (unused `MovementSegmentDao`, `dayKeyFormatter`, and 7 imports left over after GAP creation moved to `restoreFromCrash`).

### W0.8 — Permissions (fine/bg/AR/notif/battery) + rough mode · Status: [ ] verified (device permission-matrix test pending)  [x] improved 2026-06-11
**What it does:** Tracks granular permission state and degrades gracefully to coarse "rough" mode.
**Key functions / files:** `platform/coordinator/PermissionMonitor.kt` (`refresh`, `buildSnapshot`, `accuracyTag`, coarse-only detection), `onboarding/PermissionReminderBanner.kt` (`RoughLocationBanner`), `components/PermissionRequestCard`.
**How to travel-test:** Grant only approximate location; confirm the rough-mode banner appears (Timeline) and the timeline still renders sensibly. Toggle each permission off mid-session; confirm capture guards (FLP/AR re-register) don't crash.
**Flagship bar:** Every permission downgrade has a clear, non-blocking in-app explanation; revoking mid-session is handled.
**Verification (2026-06-11, code-level — device permission-matrix test pending):** ✅ Granular snapshot (fine/coarse/bg/AR/notif/battery) with API-level gating; `isApproximateLocationOnly` correctly drives rough mode (`coarse && !fine`); revocation is guarded in `LocationCapture.updateSamplingPolicy` and AR re-register. `PermissionSnapshot` is pure → `PermissionSnapshotTest` (5 cases).
> - **P1 ✅ (data-integrity bug):** `getPermissionSnapshot()` mapped the **composite** `PermissionState` to a tag, so `FULL` (fine+bg+AR) fell through to `"none"` and fine-without-background returned `"coarse"` — i.e. every fully-permissioned sample's provenance tag (stored on each row + exported in backups) was wrong. Now derived straight from the location grants via `PermissionSnapshot.accuracyTag` (`fine` whenever fine is granted).

## Wave 1 — Timeline correctness: *is what it recorded actually true?*

### W1.1 — Pipeline: normalize → dedup → Kalman → quality · Status: [ ] verified (device intercity test pending)  [x] improved 2026-06-11
**What it does:** Cleans each raw sample before segmentation.
**Key functions / files:** `pipeline/stage/SampleNormalizer.kt`, `DedupSuppressor.kt` (accuracy-aware jitter), `SpoofHeuristics.kt` (teleport gate), `LocationKalmanFilter.kt` (4-state, 25km auto-anchor), `QualityScorer.kt`, orchestrated by `pipeline/PipelineConsumer.kt`.
**How to travel-test:** Drive across a city, then a long intercity leg; check the route is smooth, not jittery, and bearings look right after the long leg (see T9).
**Flagship bar:** No GPS-jitter zigzag; Kalman reference resets on long travel; mock/spoofed/low-accuracy fixes rejected (see T15).
**Verification (2026-06-11, code-level — device intercity test pending):** ✅ All four stages sound: normalize (7-dp round, speed clamp, bearing), dedup (out-of-order reject + accuracy-aware noise floor), quality (mock / `>200m` / motion-aware staleness / score bands), Kalman (4-state, Joseph-form covariance for multi-day numerical stability, 25km reanchor, 300s-gap reset). Added **4 test files (~19 cases)** — the stages previously had only an integration test (contributes to Part C **K2**).
> - **T9 ✅ — already implemented** (`referenceResetDistanceM = 25_000` + long-haul reanchor in `filter()`); now locked with a `LocationKalmanFilter` re-anchor test. See Part B.
> - **Tiny fix:** `SampleNormalizer` bearing used `% 360`, which keeps the sign for negatives; now normalized to `[0,360)`.

### W1.2 — Motion fusion (AR + GPS + steps) · Status: [ ] verified (device drive/congestion test pending)  [x] improved 2026-06-11
**What it does:** Fuses activity recognition, GPS speed, and step rate into one motion state.
**Key functions / files:** `domain/usecase/FuseActivityStateUseCase.kt` (`fuse`, hysteresis dead zones, vehicle context, >140 spm→RUN / >100 spm→WALK, speed sanity); cadence from `pipeline/stage/StepRateCalculator.kt` (T7).
**How to travel-test:** Walk, then sit in slow traffic; confirm slow traffic is **drive**, not cycle (see T4), and brisk walking isn't called running. Shuffle at a desk; confirm it is **not** called running (see T7).
**Flagship bar:** No mode flips at boundaries; step override never misfires from a desk shuffle or while in a vehicle (see T7).
**Verification (2026-06-11, code-level — device drive/congestion test pending):** ✅ Speed validated against accuracy (rejects phantom spikes), wide hysteresis dead zones at band boundaries, AR gated by user confidence threshold, weighted fusion. Extended `FuseActivityStateUseCaseTest` with 4 vehicle-context cases (26 total).
> - **T4 ✅:** the prior fix covered the 3.0–4.5 m/s band, but **4.5–6.5 m/s still hard-mapped to CYCLING** — a car crawling in congestion with AR stale read as cycling. Added **vehicle context**: a confident IN_VEHICLE (AR) or clearly-driving (>8.5 m/s) reading arms it, and while armed a cycling-speed reading resolves to IN_VEHICLE. It's sticky across red lights and cleared only by real walking steps or a confident non-vehicle AR reading — so genuine cyclists (no prior driving) still classify as CYCLING. See Part B.
> - **T7 ✅ (2026-06-12 — root cause was the rate, not the thresholds):** the fusion thresholds (RUN >140, WALK >100, 80–100 desk-shuffle band excluded, STILL <5 spm with AR walking) were already tuned — but the *cadence feeding them* was computed as `totalSteps / bucketSpan`, dividing by the buckets' own covered period. A brief shuffle — 5 steps in a 2 s bucket — extrapolated to `(5/2 s)×60 = 150 spm` → **RUNNING**, defeating the thresholds entirely. Extracted `StepRateCalculator` (pure) that floors the denominator at 30 s: a real walk fills that window (~108 spm → WALK), a short burst divides by the floor instead of its own span (5 steps → 10 spm, correctly not walking), and genuine stillness still reads near-zero (preserves the STILL correction). Wired into `PipelineConsumer.computeStepRate`; locked by `StepRateCalculatorTest` (8 cases). See Part B.

### W1.3 — Segmenter (WALK/RUN/DRIVE/CYCLE/FLIGHT/VISIT/DWELL/GAP) · Status: [ ] verified (device flight/walk test pending)  [x] improved 2026-06-11
**What it does:** Turns the fused stream into typed segments with distance, evidence, and routes.
**Key functions / files:** `pipeline/stage/Segmenter.kt` (`processSample`, `closeCurrentSegment`, `getInProgressSnapshot`; 5-sample debounce, dominant-mode voting, 500-sample flush cap), tuning in `pipeline/PipelineConstants.kt`.
**How to travel-test:** Take a 30-min continuous walk; confirm it is **one** segment, not six (see T1). Take a flight; confirm takeoff/landing captured (see T13). Confirm stationary segments report 0 distance.
**Flagship bar:** Long single-mode trips = one row; no flapping at lights; FLIGHT only on real flights; stationary distance == 0.
**Verification (2026-06-11, code-level — device flight/walk test pending):** ✅ Sound: displacement override (when AR misses), 5-sample debounce against red-light/jitter flapping, dominant-mode voting on flush, day-boundary close, stationary segments report 0 distance, atomic segment+evidence+route commit via the gateway seam, non-blocking `getInProgressSnapshot` (tryLock). **No code change needed** — both target trust items were already implemented; added 2 FLIGHT tests (SegmenterTest now 14 cases).
> - **T1 ✅ — already implemented & tested** (non-VISIT segments have no time-only flush; the existing "long-running WALK… one row" test covers it).
> - **T13 ✅ — already implemented** (single-sample ≥200 m/s cruise OR sustained ≥80 m/s ×2 for takeoff/landing); previously **untested** — now locked with cruise + sustained FLIGHT tests. See Part B.

### W1.4 — Visit detection (hysteresis, return window, centroid) · Status: [ ] verified (device dwell/return test pending)  [x] improved 2026-06-11
**What it does:** Detects stays and forms visit candidates before segmentation (prevents DWELL fragmentation).
**Key functions / files:** `domain/usecase/DetectVisitUseCase.kt` (`processSample`, `forceDeparture`, `clearDepartureMemory`; accuracy-adaptive radius, return-window, quick-return).
**How to travel-test:** Visit a place for 45 min, step outside briefly, return; confirm it's one visit, not two (see T6), and dwell time matches reality (see T10).
**Flagship bar:** No double-counted overnight stays (see T11); dwell uses true arrival/departure; quick-return tuned.
**Verification (2026-06-11, code-level — device dwell/return test pending):** ✅ Sound: accuracy-adaptive radius, entry/exit hysteresis, place-anchor departure resistance, first-stable arrival anchor, post-confirmation centroid freeze, quick-return continuation gated by the pure `QuickReturnPolicy` (T6). `DetectVisitUseCaseTest` now 17 cases + `QuickReturnPolicyTest` (12 cases) lock the continuation decision.
> - **T10 ✅:** departure was recorded at the **exit-confirming** sample, which sits exit-hysteresis samples + the walk-out *after* the user actually left — inflating dwell. Added `lastInsideSampleAt` to the candidate and close the visit at that (the last truly-inside sample). Test asserts the exact departure time. See Part B.
> - **T6 ✅ (2026-06-12 — overeagerness closed):** the continuation decision is now a pure, fully-tested `QuickReturnPolicy` (extracted from inline logic). The "moved away" gate was broadened beyond the old DRIVE/CYCLE/RUN/FLIGHT set: **TRANSIT** (public-transport hops) now counts, and a **meaningful on-foot excursion** — gap WALK distance ≥ `max(2 × placeRadiusM, 100 m)` — now breaks continuation, so walking to a shop 400 m away and back is correctly two visits, not one. The window that anti-fragmentation depends on is untouched for its real case: a **PROCESS_DEAD** gap (app killed while the user sat still) produces *no* movement segments, so bridging still continues the visit. Short step-outside walks (below the threshold) still continue. Locked by `QuickReturnPolicyTest` (12 cases — decision matrix, threshold/floor scaling, accumulation, motorised set) plus 3 new `DetectVisitUseCaseTest` wiring cases (long walk → new visit, brief walk → continue, transit → new visit).

### W1.5 — Live place matching + Wi-Fi fingerprint · Status: [ ] verified (device venue test pending)  [x] improved 2026-06-11
**What it does:** Matches an active stay to a known place in real time.
**Key functions / files:** `domain/usecase/MatchPlaceLiveUseCase.kt`, `PlaceLinkingService`, `WifiFingerprinter`.
**How to travel-test:** Return to home/work; confirm the active-visit card names it immediately. Test a large venue (mall) where 200m is too tight (see T8).
**Flagship bar:** Search radius adapts to place size; indoor matches use Wi-Fi prior; no mismatches between adjacent places.
**Verification (2026-06-11, code-level — device venue test pending):** ✅ geohash-prefix candidate fetch (~5km), nearest-place selection, entry (×2) / exit (×3) hysteresis, place-radius + buffer membership test. New `MatchPlaceLiveUseCaseTest` (4 cases) — the use case previously had no tests.
> - **T8 ✅:** the *search* radius was already made adaptive on GPS accuracy (no longer a flat 200m), but the reachability gate still keyed **only** on that — so in good GPS (≈50m) a user 150m inside a 300m-radius venue was rejected before the venue's own footprint was checked. Gate now uses `max(searchRadius, place.radiusM + buffer)`, so large venues match while tight GPS still avoids wrong-place attribution among small adjacent places. Locked with a large-venue test. See Part B.
> - **Note:** Wi-Fi fingerprint prior (`WifiFingerprinter`) for indoor matching is a separate enhancement, not yet fused into the live match — tracked under Part C (place intelligence).

### W1.6 — Gap watchdog & GAP reasons · Status: [ ] verified (device tunnel test pending)  [x] improved 2026-06-11
**What it does:** Inserts GAP segments on tracking loss, distinguishing intentional (DORMANT) from GPS_LOSS.
**Key functions / files:** `pipeline/GapWatchdogPolicy.kt` (pure decision), `pipeline/PipelineConsumer.kt` (60s watchdog loop).
**How to travel-test:** Go into a tunnel/underground; confirm a GAP appears labeled GPS_LOSS, and dormant idle is labeled DORMANT.
**Flagship bar:** Gaps are explained, not silent; reason is correct; no false gaps during normal cadence.
**Verification (2026-06-11, code-level — device tunnel test pending):** ✅ 60s watchdog raises a GAP only when active GPS is running (interval > 0), silence exceeds **both** 5× the cadence **and** the 10-min floor, we're outside the post-dormant grace window, and no gap was already recorded for that last-sample (dedup). Reason = DORMANT (intentional GPS-off) vs GPS_LOSS. Extracted the decision into pure `GapWatchdogPolicy` (and reused its constants in `PipelineConsumer`); new `GapWatchdogPolicyTest` (7 cases) — previously the decision lived only inside the coroutine loop with no coverage.

### W1.7 — Day-boundary resolution · Status: [ ] verified (device overnight test pending)  [x] improved 2026-06-11
**What it does:** Resolves which day a segment belongs to (home-tz vs travel-aware).
**Key functions / files:** `domain/util/DayBoundaryResolver.kt` (`resolveDayKey`, `getDayStart/EndEpochMs`, `overlapMs`), `ResolveDayBoundaryUseCase`, `platform/worker/DailyRollupWorker.kt`, `VisitDao.getVisitsOverlapping`.
**How to travel-test:** Stay somewhere overnight and across a timezone change; confirm the overnight stay isn't double-counted on both days (see T11).
**Flagship bar:** Overnight + cross-tz days split cleanly; user's day-boundary preference respected.
**Verification (2026-06-11, code-level — device overnight test pending):** ✅ Resolver is a pure single-dayKey mapper (home-tz vs travel-aware) with day-window helpers. New `DayBoundaryResolverTest` (4 cases incl. the overnight split).
> - **T11 ✅:** `DailyRollupWorker` summed each visit's **full** `dwellMs` keyed to its arrival day, so a 9h overnight stay dumped all 9h on day N (and 0 on N+1) — while segments were already split at midnight. Dwell is now **clamped to each day's window** via `overlapMs` over visits that *overlap* the day (new `getVisitsOverlapping` query, open visits clamped to day-end): the 9h stay becomes 2h on N + 7h on N+1, summing to the true dwell. Also fixed `computeYesterdayKey` to use the home timezone (was the system default) so the day window and the pipeline-assigned dayKeys agree. See Part B.

### W1.8 — Timeline reconciliation / day rebuild · Status: [ ] verified (device rebuild test pending)  [x] improved 2026-06-11
**What it does:** Rebuilds a day's segments/visits from raw samples on demand.
**Key functions / files:** `domain/usecase/TimelineReconciler.kt`, `TimelineRepository.rebuildDay`.
**How to travel-test:** After a correction or import, trigger a rebuild; confirm the day regenerates consistently with no duplicates.
**Flagship bar:** Idempotent rebuilds; fast; preserves user corrections.
**Verification (2026-06-11, code-level — device rebuild test pending):** ✅ `TimelineReconciler` is pure: noise filter (keeps GAPs, drops sub-threshold transients), same-place visit-flush coalescing (placeless merges gated by time gap), same-type movement merge (≤2min gap), and optional unified-travel (dominant mode by distance, sub-segments preserved for evidence, >5min gap breaks the chain). New `TimelineReconcilerTest` (5 cases) — previously untested.
> - **Observation (not changed):** in the composed `reconcile()`, `filterNoise` runs before `absorbOrphanedDwells` and already removes the placeless sub-3min DWELLs that pass targets, making it a no-op in that path (it still works when called directly). Harmless; noted for a future tidy rather than reordered on a verify card.

## Wave 2 — Place intelligence & naming: *right names, right categories?*

### W2.1 — Multi-provider geocoding · Status: [ ] verified (device varied-places test pending)  [x] improved 2026-06-11
**What it does:** Resolves coordinates to names via Android/Nominatim/Photon/Overpass with fallback + rate limiting.
**Key functions / files:** `data/geocoding/*` providers, `GeocodingRepositoryImpl.activeProviders/reverseGeocode`, `data/api/RateLimiter`, `PoiCategoryMapper`.
**How to travel-test:** Visit varied places (chain store, indie cafe, park); confirm names resolve and providers fall back when one fails.
**Flagship bar:** Real names not "Unnamed road"; respectful of provider rate limits; offline-tolerant.
**Verification (2026-06-11, code-level — device varied-places test pending):** ✅ Well-built and (mostly) well-tested already: `GeocodingRepositoryImpl` orders providers by the user's `providerOrder` (priority fallback), **skips unavailable** providers, **short-circuits** on the first HIGH-tier result (typically 0–1 network calls), and applies privacy coarsening to network providers while the offline Android Geocoder gets exact coords. Existing tests cover the repository, Android + Overpass providers, the conflict resolver, and the POI mapper. Added `RateLimiterTest` (first call free; back-to-back call throttled to the interval) — the OSM 1-req/sec compliance limiter was untested.
> - **Note (dead code):** `GeocodingProviderRegistryImpl.getEnabledProviders()` has no callers — the repo uses an injected provider list and does its own ordering. Candidate for removal in a future tidy; left as-is on this verify card.

### W2.2 — Display-name resolution & candidate selection ("near to") · Status: [ ] verified (device picker test pending)  [x] improved 2026-06-11
**What it does:** Picks the best display name and offers alternatives from other providers.
**Key functions / files:** `storage/database/entity/PlaceEntity.displayName()`, `data/repository/TimelineRepositoryImpl.buildGeocodeHints` (POIs as "Near X" + alt candidates), `TimelinePlace.geocodeHints`, `TimelineViewModel.SelectGeocodeName` → `renamePlace`.
**How to travel-test:** Open a place with an imperfect name; confirm you can pick a better candidate and a "near to <landmark>" style hint shows.
**Flagship bar:** Best name chosen by default; clean candidate picker; "near to" hint when exact name is weak. _(User-priority feature.)_
**Verification (2026-06-11, code-level — device picker test pending):** ✅ The live path works: name shown = `PlaceEntity.displayName()` (user name → best provider name → coordinates); the picker (`geocodeHints`) is built from nearby Overpass POIs as **"Near {POI}"** plus alternative stored geocode candidates, and tapping one renames the place. Locked `displayName()` with `PlaceDisplayNameTest` (3 cases).
> - **Improvement:** `buildGeocodeHints` now dedups hints by name (`distinctBy { it.name }`) so the picker never shows the same address twice when two providers return it — cleaner picker.
> - **Note (dead code):** `GeocodingConflictResolver.resolveDisplayName` takes a documented `nearbyContext` ("Near …") param but its body never uses it, and its only caller — `GeocodingRepositoryImpl.resolveDisplayName(placeId)` — has no callers itself. Both are dead (the live "near to" runs through `buildGeocodeHints`). Candidates for wiring-or-removal in a future tidy; left untouched on this verify card.

### W2.3 — Category inference · Status: [ ] verified (device multi-day test pending)  [x] improved (already at bar) 2026-06-11
**What it does:** Auto-assigns HOME/WORK/GYM/RESTAURANT/etc. from patterns + OSM tags.
**Key functions / files:** `data/geocoding/PoiCategoryMapper.kt`, `domain/usecase/InferPlaceCategoryUseCase.kt`, `platform/worker/InferPlaceCategoryWorker.kt`, `DiscoverPlacesWorker`, `GeocodingRepositoryImpl.refreshGeocodeForPlace`.
**How to travel-test:** After a few days, confirm home/work auto-categorize and a cafe gets RESTAURANT from its OSM tag (see T2).
**Flagship bar:** Categories inferred from OSM tags, not just time-of-day; high-confidence ones need no user touch.
**Verification (2026-06-11, code-level — device multi-day test pending):** ✅ Both inference paths are live and tested — **no code change needed**:
> - **OSM-tag path (T2):** `PoiCategoryMapper.fromOsmType` (comprehensive amenity/shop/leisure/tourism/transit map, conservative null for ambiguous `office`/`building`) feeds `inferredCategory`, applied to `place.category` at **discovery** (`DiscoverPlacesWorker`, skipped only in rough mode) and on **refresh** (`refreshGeocodeForPlace`), both gated to UNKNOWN + no user override. Covered by `PoiCategoryMapperTest` (≈20 cases).
> - **Pattern path:** `InferPlaceCategoryUseCase` (HOME nightly / WORK weekday-9-5 / etc.) is the fallback for places OSM can't classify, run by `InferPlaceCategoryWorker`, covered by `InferPlaceCategoryUseCaseTest`.

### W2.4 — Place enrichment (POI detail) · Status: [ ] verified (device POI test pending)  [x] improved 2026-06-11
**What it does:** Enriches places with POI metadata.
**Key functions / files:** `domain/usecase/EnrichPlaceWithDetailsUseCase.kt`.
**How to travel-test:** Open a well-known POI; confirm enriched details appear.
**Flagship bar:** Enrichment improves name/category confidence visibly.
**Verification (2026-06-11, code-level — device POI test pending):** ✅ Thin, sound wrapper over the multi-provider `reverseGeocode` pipeline (W2.1): three entry points (`invoke`→name, `enrichWithSource`→result, `enrichFull`→result+candidates), prefers the accuracy-gated `safeDisplayName`, passes through the OSM `inferredCategory` (feeds W2.3/T2), and on failure returns an empty result without leaking coordinates to logs. New `EnrichPlaceWithDetailsUseCaseTest` (4 cases incl. safe-name preference and the graceful-failure path) — was untested.

### W2.5 — Place discovery / clustering · Status: [ ] verified (device discovery test pending)  [x] improved 2026-06-11
**What it does:** Discovers new places by clustering repeated stays.
**Key functions / files:** `worker/DiscoverPlacesWorker` (`densityCluster`, `mergeRadiusM`, `clusterRadiusFor`, `resolvePlaceConfidence`), `GeohashEncoder`.
**How to travel-test:** Visit a new spot a few times; confirm it becomes a discovered place (not fragmented across visits — see T5).
**Flagship bar:** One building → one place; discovery cadence is timely.
**Verification (2026-06-11, code-level — device discovery test pending):** ✅ Density clustering (HDBSCAN-like, 80m / 2km rough, min 3 points), POI-prior confidence lift, rough-mode handling — all with existing pure-helper tests.
> - **T5 (discovery half) ✅:** the existing-place dedup compared the cluster centroid to the existing **centroid** with a fixed 80m radius — so a cluster landing inside a large venue (>80m from its centroid) spawned a **duplicate** place (same root cause as T8). Now merges within `mergeRadiusM = max(clusterRadius, existing.radiusM + buffer)`, honoring the venue footprint (place radius is capped at 500m so the merge stays bounded). Locked with 3 `mergeRadiusM` tests. The other half of T5 — collapsing places that *already* fragmented — is **W2.6** (MergePlacesWorker).

### W2.6 — Place merging (dedup) · Status: [ ] verified (device merge test pending)  [x] improved 2026-06-11
**What it does:** Collapses near-duplicate places.
**Key functions / files:** `worker/MergePlacesWorker` (`nearestMergeable`, `mergeDistanceLimitM`), `PlaceRepository.mergePlaces`.
**How to travel-test:** If a place fragmented, confirm merge consolidates it and re-points visits.
**Flagship bar:** No visible duplicates of the same physical place; merges preserve history.
**Verification (2026-06-11, code-level — device merge test pending):** ✅ Conservative + safe: only CANDIDATE→CONFIRMED merges, requires a non-empty **exact name match** (case-insensitive), and reassigns visits + segments and marks MERGED inside one transaction (no dangling FKs). New `MergePlacesWorkerTest` (2 cases).
> - **T5 (merge half) ✅:** `nearestMergeable` capped the merge at a flat 200m from the confirmed centroid, so same-named fragments inside a large venue (campus/airport, >200m) never collapsed. Now uses `mergeDistanceLimitM = max(200m, confirmed.radiusM + buffer)`, honoring the footprint — safe because an exact name match is already required. Completes T5 (discovery half landed in W2.5).

### W2.7 — Confidence & decay · Status: [ ] verified (device long-absence test pending)  [x] improved (already at bar) 2026-06-11
**What it does:** Scores place confidence and decays it when not revisited.
**Key functions / files:** `domain/usecase/PlaceConfidenceDecay.kt`, `worker/ConfidenceDecayWorker`, `domain/model/EvidenceBlock.kt`.
**How to travel-test:** Stop visiting a place for weeks; confirm its confidence decays and it drops out of "frequent" (see T14).
**Flagship bar:** Stale places fade; confidence shown is trustworthy.
**Verification (2026-06-11, code-level — device long-absence test pending):** ✅ Fully wired, **no change needed**: `PlaceConfidenceDecay.decay` (grace period, daily multiplicative decay toward a floor) is **pure + tested** (`PlaceConfidenceDecayTest`); `ConfidenceDecayWorker` applies it to every active place (with a `MIN_DELTA` churn guard) and is **scheduled** daily at 03:30 (`WorkerScheduler.scheduleConfidenceDecay`, wired into `scheduleAll`). A revisit re-bumps confidence via the discovery/linking path. T14 ("no confidence decay") is already false.

### W2.8 — Geocode backfill · Status: [x] verified (code-level) 2026-06-12  · improved: n/a (sound, no change needed)
**What it does:** Fills missing names for older places when connectivity returns.
**Key functions / files:** `worker/GeocodeBackfillWorker`.
**How to travel-test:** Track offline, then reconnect; confirm previously-unnamed places get names.
**Flagship bar:** No permanently-unnamed places once online.
**Verification (2026-06-12, code-level — device offline→online test pending):** ✅ Sound, no change needed: backfills unnamed places (`bestProviderName == null`) in batches of 20 via the tested `refreshGeocodeForPlace`, respects the auto-geocode privacy lever (no network when off), continues past individual failures and only `retry`s when *all* fail (transient network). Scheduled every 4h with **network constraints** (`scheduleAll` → `scheduleGeocodeBackfill`), so it naturally fires when connectivity returns — offline-tolerant by design.

## Wave 3 — Viewing & navigating the timeline (daily-use screens)

### W3.1 — Timeline screen · Status: [ ] verified (device — screen is redesign WIP)  [x] improved 2026-06-12
**What it does:** Vertical rail of segments with day nav, live in-progress segment, active-visit overlay, quick reclassification.
**Key functions / files:** `presentation/screen/timeline/TimelineScreen.kt`+`TimelineViewModel` (`onIntent`: SelectSegment, CorrectSegmentType, SelectGeocodeName, RenamePlace, NavigatePrev/Next).
**How to travel-test:** Open today mid-trip; confirm the in-progress segment updates live and day nav works.
**Flagship bar:** Continuous Arc-style spine (no card gaps — see Part C A4); instant day switching.
**Verification (2026-06-12, code-level):** ✅ `TimelineViewModel` flow is correct — merges DB segments with the live in-progress segment, dedups segments overlapping the active-visit window (no double display), counts movement-only distance, and surfaces rough-mode. `TimelineScreen.kt` is the user's redesign WIP → device-verify, not touched.
> - **Cleanup:** `SelectSegment` fetched segment evidence and **discarded it** every tap; the detail sheet (`SegmentDetailViewModel`) loads its own. Removed the wasted per-tap DB read, the never-read `selectedSegmentEvidence` field, and the now-unused `EvidenceRepository` injection.
> - **Follow-up (test gap):** the merge/dedup/distance combine is valuable to lock but needs 6 mocked deps + StateFlow collection — candidate for Part C **K3** (ViewModel tests), not done here.

### W3.2 — Map screen · Status: [ ] verified (device — screen is redesign WIP)  [x] improved 2026-06-12
**What it does:** Mapbox/MapLibre routes + visit markers, fit-bounds, center-on-me, transport-mode colors.
**Key functions / files:** `presentation/screen/map/MapScreen.kt`+`MapViewModel` (`onIntent`: TapMarker/TapRoute/CenterOnUser/FitBounds), `MapRepository`.
**How to travel-test:** Open a travel day; tap a route and a marker; use fit-bounds and center-on-me.
**Flagship bar:** Smooth pan/zoom; markers/routes tappable; camera persists (see Part C C5).
**Verification (2026-06-12, code-level):** ✅ `MapViewModel` is sound — reactive day data + settings toggles (markers/polylines/colour-by-mode apply live), routes decoded from the already-reconciled segments (continuous polylines), focus collectors for segment/visit, live-location + active-visit observers, one-shot center/fit requests with consume-handlers. `MapScreen.kt` is redesign WIP → device-verify, not touched.
> - **Cleanup:** transport-colour `else` now reuses the `NEUTRAL_ROUTE_COLOR` constant instead of a duplicated literal.
> - **Finding (not changed):** an unmatched VISIT segment falls back to `visitMarkers.firstOrNull()` — could focus an *arbitrary* marker (wrong-place highlight). Left for on-device judgement since it's map-focus UX; candidate to return null instead.

### W3.3 — Dashboard / home · Status: [ ] verified (device — screen is redesign WIP)  [x] improved 2026-06-12
**What it does:** Live tracking status, daily stats, streak, battery/day, insights, active visit.
**Key functions / files:** `presentation/screen/dashboard/DashboardScreen.kt`+`DashboardViewModel`.
**How to travel-test:** Walk for a few minutes from a cold start; confirm live distance/steps move before any place exists (see Part C A10).
**Flagship bar:** No empty first hour; numbers count up smoothly; honest battery/day.
**Verification (2026-06-12, code-level):** ✅ `DashboardViewModel` composes dashboard + steps + tracking + live-timeline + settings into one reactive state; session start ticks immediately on start; honest battery/day from `BatteryUsageReporter`. `DashboardScreen.kt` is redesign WIP → device-verify.
> - **Bug fixed (streak):** the consecutive-days streak counted from **today**, so it showed **0 every morning** until you generated activity — even mid-streak. Extracted a pure `computeStreak` that counts from yesterday when today isn't active yet (a streak isn't broken until the day ends). New `DashboardViewModelTest` (5 cases).

### W3.4 — Cross-screen day sync · Status: [x] verified (code-level) 2026-06-12  [x] improved (tests added)
**What it does:** Keeps the selected day/segment in sync across Timeline/Map/Insights.
**Key functions / files:** `presentation/state/DayNavigationStateHolder.kt`, `SharedUiState.kt`.
**How to travel-test:** Change the day on Timeline; switch to Map — same day shown; tap a segment — it's focused on both.
**Flagship bar:** Zero desync; focus follows you across tabs.
**Verification (2026-06-12, code-level):** ✅ Sound, no change needed — single `@ActivityRetainedScoped` holder is the shared source of truth across screens. `focusSegment`/`focusVisit` are mutually exclusive (each clears the other — the right place to prevent the focus race noted in W3.2), `navigateNextDay` is future-guarded (can't pass today), day arithmetic uses `LocalDate` (month/year boundaries) with a malformed-key fallback, and any day change clears focus. New `DayNavigationStateHolderTest` (6 cases) — no deps, so directly testable.

### W3.5 — Search · Status: [ ] verified (device — screen is redesign WIP)  [x] improved 2026-06-12
**What it does:** Debounced full-text search over places/segments with category/mode/date filters.
**Key functions / files:** `presentation/screen/search/SearchScreen.kt`+`SearchViewModel`, `domain/usecase/SearchTimelineUseCase.kt`, `worker/SearchIndexWorker`.
**How to travel-test:** Search a place name and filter by date range; tap a result to open it.
**Flagship bar:** Sub-second results; filters compose; demoable "ask my timeline" moment.
**Verification (2026-06-12, code-level):** ✅ `SearchViewModel` is sound — 300ms-debounced query+filters via `flatMapLatest`, a ≥2-char gate (no search on a single letter), `isSearching` emitted on start. `SearchScreen.kt` is redesign WIP → device-verify.
> - **Cleanup + lock:** the two near-duplicate hand-rolled filter-toggle blocks (category/transport, with null-when-empty) are now one pure `toggleFilter` helper. New `SearchViewModelTest` (4 cases: add to empty, add new, remove present, collapse-to-null).

## Wave 4 — Corrections & trust: *can I fix what's wrong?*

### W4.1 — Place detail edit · Status: [x] verified (code-level) 2026-06-12  · improved: n/a (clean thin VM)
**What it does:** Rename, set category, emoji, merge, refresh geocode; shows analytics + confidence.
**Key functions / files:** `presentation/screen/place/PlaceDetailScreen.kt`+VM (`onIntent`: Rename/SetCategory/SetEmoji/MergeWith/RefreshGeocode) → `PlaceRepository`.
**How to travel-test:** Rename a place, set its category and emoji, refresh its name; reopen to confirm persistence.
**Flagship bar:** Every edit sticks and feeds the correction loop (W4.6); merge is reversible-feeling.
**Verification (2026-06-12, code-level):** ✅ Clean thin VM, no change needed — observes the place reactively, loads analytics/evidence/candidates, dispatches each intent to the right `PlaceRepository`/`GeocodingRepository` method, and re-fetches candidates after a geocode refresh. Screen is redesign WIP → device-verify. (The real edit logic lives in the repositories.)

### W4.2 — Visit detail · Status: [x] verified (code-level) 2026-06-12  · improved: n/a (clean VM)
**What it does:** Confirm, delete, adjust arrival/departure, rename; confidence card.
**Key functions / files:** `presentation/screen/visit/VisitDetailSheet.kt`+VM (`onIntent`: ConfirmVisit/DeleteVisit/RenamePlace/AdjustTimes).
**How to travel-test:** Adjust a visit's times and confirm it; delete a spurious visit.
**Flagship bar:** Time edits recompute dwell correctly; confirm marks it user-reviewed.
**Verification (2026-06-12, code-level):** ✅ Correct, no change — loads visit/place/evidence into a `ConfidenceBlock`; each intent applies immediately **and** logs a `CorrectionRepository` entry (Confirm marks user-corrected, Delete removes, Rename updates the display name, **AdjustTimes recomputes `dwellMs = departure − arrival`** and marks corrected). Sheet UI is WIP → device-verify.

### W4.3 — Segment detail · Status: [ ] verified (device split/merge — sheet is WIP)  [x] improved 2026-06-12
**What it does:** Reclassify transport mode, split, merge-with-next; evidence + inference explanation.
**Key functions / files:** `presentation/screen/segment/SegmentDetailSheet.kt`+VM, `domain/usecase/OverrideSegmentTypeUseCase.kt`.
**How to travel-test:** Reclassify a mislabeled drive→cycle; split a merged segment; confirm corrections log.
**Flagship bar:** Corrections are one tap, explained, and improve future inference (W4.6).
**Verification (2026-06-12, code-level):** ✅ `OverrideSegmentTypeUseCase` verified + locked with `OverrideSegmentTypeUseCaseTest` (4 cases): the override is stored separately from the classifier label (reclassification can't stomp it), the linked route's `transportMode` is synced to the effective type (reverting to the classifier label on clear), redundant route writes are skipped, and every change is health-logged. Reclassify is the correction-loop signal (W4.6). Split/merge live in the (WIP) `SegmentDetailSheet`/VM → device-verify.

### W4.4 — Place review queue · Status: [ ] verified (device badge test pending)  [x] improved 2026-06-12
**What it does:** Triage queue of low-confidence/unknown places with a nav badge.
**Key functions / files:** `presentation/screen/review/PlaceReviewScreen.kt`+VM (threshold 0.7; `confirmPlace`, `renamePlace`, `setCategory`).
**How to travel-test:** Let low-confidence places accrue; clear the queue from the bell icon; confirm the badge count updates.
**Flagship bar:** Queue is short and high-signal; clearing it visibly raises overall trust.
**Verification (2026-06-12, code-level):** ✅ Reactively filters the review queue and feeds the nav badge via `SharedUiState`. Extracted the queue predicate into a pure `pendingReviewPlaces` (confidence < 0.7 OR UNKNOWN category, lowest-confidence first) + `PlaceReviewViewModelTest` (2 cases: inclusion rules + ordering).

### W4.5 — Categories management · Status: [x] verified (code-level) 2026-06-12  · improved: n/a (sound)
**What it does:** Per-category visibility (map/timeline/notifications), assign places, presets.
**Key functions / files:** `presentation/screen/categories/CategoriesScreen.kt`+VM (`updateCategoryVisibility`, `showAll/hideAll`, `resetToDefaults`, `assignPlaceToCategory`).
**How to travel-test:** Hide a category from the map; confirm its places disappear there but stay on timeline.
**Flagship bar:** Visibility toggles apply instantly everywhere; assignment is quick.
**Verification (2026-06-12, code-level):** ✅ Sound — per-category visibility persisted to DataStore as JSON (defaults to all-visible), counts recomputed on change, show/hide-all + reset + assign all present. The serialize/deserialize round-trip is consistent (matching keys, true-defaults on missing) but uses Android's `org.json` (stubbed in plain unit tests) → an **instrumentation-test candidate**, not a JVM unit test. No change needed.

### W4.6 — Correction feedback loop & calibration · Status: [ ] verified (device error-reduction test pending)  ◐ improved — first slice built, more to come
**What it does:** Records user corrections and calibrates detection from them.
**Key functions / files:** `domain/repository/CorrectionRepository`, `domain/usecase/CorrectionCalibration.kt`, `worker/FeedbackCalibrationWorker`.
**How to travel-test:** Confirm/rename a few low-confidence places; confirm they drop out of the review queue and stay out (don't re-prompt).
**Flagship bar:** Measurable error reduction after corrections; calibration is conservative.
**Status (2026-06-12) — first calibration slice landed (branch `w4.6-feedback-calibration`):**
> - ✅ **Immediate corrections** already applied through the repositories (W4.1/W4.3) and recorded to `correction_feedback`.
> - ✅ **Place-trust calibration (new):** `FeedbackCalibrationWorker` no longer no-ops — for CONFIRM / CONFIRM_VISIT / RENAME / RECATEGORIZE it raises the corrected place's confidence to a high floor (`CorrectionCalibration`, monotonic — only ever raises), so user-validated places leave the W4.4 review queue and decay (W2.7) from a high base. Pure policy locked by `CorrectionCalibrationTest`. Resolves the earlier "marks propagated while doing nothing" concern: non-place-trust types are consumed here, but their aggregate slices read via `getByCorrectionTypeSince` (propagated-flag-independent).
> - ✅ **Systematic-bias detection (new, report-only):** an aggregate pass scans the last 30 days of RECLASSIFY_SEGMENT / CHANGE_TRANSPORT_MODE corrections, resolves each to a `(classifierType → userType)` pair from the segment itself, and flags any pattern recurring ≥3× as a `SYSTEMATIC_MISCLASSIFICATION` health-log entry (e.g. "DRIVE keeps being corrected to CYCLE"). **Deliberately does not auto-tune global detection** — a handful of corrections must not swing behaviour for everyone; this surfaces the bias for diagnostics and a future validated tuning pass. `CorrectionCalibration.systematicMisclassifications` locked by `CorrectionCalibrationTest` (8 cases total: threshold, below-threshold, same-type filtered, descending-count sort).
> - ☐ **Remaining slices (Part C):** the *tuning* half of RECLASSIFY_SEGMENT → transport-mode weights (bias is now detected; applying it is deferred); MERGE/SPLIT_PLACE → clustering params; DELETE_VISIT/ADJUST_TIMES → visit-detection thresholds. These are heuristic-tuning subsystems, deferred.

### W4.7 — Evidence/confidence display & explanations · Status: [ ] verified (device sheet test pending)  [x] improved 2026-06-12
**What it does:** Shows *why* a classification was made (the explainability moat).
**Key functions / files:** `components/EvidenceSheet`, `domain/usecase/BuildEvidenceSummaryUseCase.kt`, `ExplainTimelineRowUseCase.kt`, `domain/model/InferenceExplanation.kt`.
**How to travel-test:** Open the evidence sheet on a drive and a visit; confirm GPS accuracy, speed, AR votes, and a plain-language reason.
**Flagship bar:** Every row can answer "why did you say that?"; copy is human (see Part C C6).
**Verification (2026-06-12, code-level):** ✅ Both explainability use cases now tested. `ExplainTimelineRowUseCase` already had `ExplainTimelineRowUseCaseTest`; added `BuildEvidenceSummaryUseCaseTest` (3 cases) for `BuildEvidenceSummaryUseCase` — maps stored signals into an `EvidenceBlock`, labels the explanation by the top activity vote, returns null when there's no evidence, and degrades gracefully on malformed JSON (no crash). The `EvidenceSheet` UI renders it → device-verify.

### W4.8 — Integrity repair · Status: [x] verified (code-level) 2026-06-12  · [x] improved 2026-06-12
**What it does:** Cleans orphaned visits, overlapping segments, stale candidates.
**Key functions / files:** `domain/usecase/IntegrityRepairUseCase.kt` (`repairDay`, `closeStaleVisits`), `worker/IntegrityRepairWorker`.
**How to travel-test:** After crashes/imports, run/await repair; confirm no overlaps or orphans remain.
**Flagship bar:** Self-healing; never shows contradictory data.
**Verification (2026-06-12, code-level):** ✅ `repairDay` clamps overlapping visit departures to the next arrival and trims/deletes movement segments that collide with completed-visit windows; `closeStaleVisits` was already locked for the T3 dwell-truncation fix. Found + fixed one real hole: a movement segment **fully spanning a visit** hit the "starts-before → trim end" branch, which trimmed it to the visit arrival and **silently dropped the post-departure travel** — leaving a timeline gap (a "contradictory data" violation of the flagship bar). Now that case **splits** the segment (keep the pre-arrival half, insert a fresh post-departure half; distance stays on the original so the total isn't inflated). `IntegrityRepairUseCaseTest` extended to 11 cases (overlap clamp, enclosed-delete, trim-end, trim-start, span-split, stationary-untouched, clean-day no-op).

## Wave 5 — Insights & analytics (the "wow")

### W5.1 — Period summary / rollups · Status: [x] verified (ComputePeriodSummaryUseCaseTest) 2026-06-12  · [x] improved (T11 dwell clamp landed in W1.7)
**What it does:** Pre-aggregates daily/weekly metrics for fast dashboards.
**Key functions / files:** `domain/usecase/ComputePeriodSummaryUseCase.kt`, `worker/DailyRollupWorker`, `WeeklyRollupWorker`, rollup entities.
**How to travel-test:** Compare a day's dashboard totals against the raw timeline.
**Flagship bar:** Rollups match raw within rounding; dashboards read a single indexed row.

### W5.2 — Statistics screen · Status: [x] verified (code-level) 2026-06-12  · improved: n/a (sound; `compute*` helpers are DB-coupled → integration-test territory)
**What it does:** Period selector, week/month comparisons, movement stats, social/variety, anomalies, carbon.
**Key functions / files:** `presentation/screen/analytics/StatisticsScreen.kt`+`StatisticsViewModel` (`selectPeriod`, `refresh`).
**How to travel-test:** Switch periods (week/month/custom); confirm comparisons and trends look right.
**Flagship bar:** Insightful, not just numeric; Pro gating is clear and fair.

### W5.3 — Place statistics · Status: [x] verified (ComputePlaceStatisticsUseCaseTest) 2026-06-12
**What it does:** Per-place frequency, dwell distribution, dominant time/day.
**Key functions / files:** `domain/usecase/ComputePlaceStatisticsUseCase.kt`.
**How to travel-test:** Open a frequent place; confirm visit count, avg dwell, typical days match memory.
**Flagship bar:** Stats feel personal and accurate.

### W5.4 — Anomaly detection & alerts · Status: [ ] verified (device alert test pending)  [x] improved 2026-06-12
**What it does:** Flags unusual trips/places/records and can alert.
**Verification (2026-06-12):** ✅ Locked `DetectNotableEventsUseCase` via its existing pure seams (`computeFirstVisits`, `computeLongestDistanceDay`) — first-visit-in-window detection and record-day-beats-history — with `DetectNotableEventsUseCaseTest` (3 cases). `AnomalyAlertWorker` surfaces them on a schedule (integration-level).
**Key functions / files:** `domain/model/Anomaly.kt`, `DetectNotableEventsUseCase`, `worker/AnomalyAlertWorker`.
**How to travel-test:** Do something unusual (new far city); confirm it's flagged with sensible severity.
**Flagship bar:** Signal over noise; alerts are rare and meaningful.

### W5.5 — Recurring patterns · Status: [x] verified (DetectRecurringPatternsUseCaseTest) 2026-06-12
**What it does:** Identifies routines (commute, gym schedule).
**Key functions / files:** `domain/usecase/DetectRecurringPatternsUseCase.kt`.
**How to travel-test:** After a regular week, confirm commute/routine is recognized.
**Flagship bar:** Recognizes real routines without over-claiming.

### W5.6 — On-this-day memories · Status: [x] verified (BuildOnThisDayUseCaseTest) 2026-06-12
**What it does:** Resurfaces the same day from past years.
**Key functions / files:** `domain/usecase/BuildOnThisDayUseCase.kt`.
**How to travel-test:** With history present, confirm a memory card appears (see Part C C4).
**Flagship bar:** Delightful, well-timed resurfacing.

### W5.7 — Carbon footprint · Status: [x] verified (BuildCarbonFootprintUseCaseTest) 2026-06-12
**What it does:** CO₂ by transport mode.
**Key functions / files:** `domain/usecase/BuildCarbonFootprintUseCase.kt`, `domain/model/CarbonFootprint.kt`.
**How to travel-test:** After mixed travel, confirm per-mode CO₂ totals are plausible.
**Flagship bar:** Defensible factors; clear units.

### W5.8 — Steps analytics & stride calibration · Status: [x] verified (code-level; `StepSyncWorker` integration) 2026-06-12
**What it does:** Daily/hourly steps + stride calibration for distance.
**Key functions / files:** `domain/repository/StepsRepository`, `worker/StepSyncWorker`, `StrideCalibration`.
**How to travel-test:** Walk a measured distance; confirm step→distance is close.
**Flagship bar:** Calibrated per user; matches a reference within a few %.

### W5.9 — Recap & semantic labeling · Status: [x] verified (code-level; scheduled workers, integration) 2026-06-12
**What it does:** Evening recap notification + semantic place/activity labels.
**Key functions / files:** `worker/DailyRecapWorker`, `worker/SemanticLabelWorker`.
**How to travel-test:** At ~22:00 confirm a recap arrives ("moved 8km, 3 places"); tap to open the day.
**Flagship bar:** A daily reason to open the app; copy is calm and skippable (see Part C L4).

## Wave 6 — Proof hub: flagship differentiators

### W6.1 — Proof hub screen · Status: [ ] verified  [ ] improved
**What it does:** Aggregates Trips, Mileage, Export with Pro gating.
**Key functions / files:** `presentation/screen/proof/ProofScreen.kt`.
**How to travel-test:** Open the Proof tab; confirm Trips/Mileage/Export are reachable.
**Flagship bar:** Clear "proof of where you were" identity.

### W6.2 — Trip detection · Status: [x] verified (code-level) 2026-06-12  · [x] improved 2026-06-12
**What it does:** Auto-detects multi-day journeys away from a home anchor.
**Key functions / files:** `domain/usecase/DetectTripsUseCase.kt` (`detect`, `detectAndStore`, away-run scan), `worker/TripDetectionWorker`, `presentation/screen/trips/TripsScreen.kt`+VM.
**How to travel-test:** Take a 2–3 day trip; confirm it auto-appears with correct dates/destination.
**Flagship bar:** No false trips; manual re-detect works; home anchor respected.
**Verification (2026-06-12, code-level):** ✅ Run scan is correct — `VisitDao.getAllDayKeys` is `ORDER BY dayKey ASC … deletedAt IS NULL`, so the oldest→newest, deleted-excluded assumptions hold in production; home days break a run, single-day runs are filtered (`MIN_TRIP_SPAN_DAYS`), titles follow top-dwell place, `isOngoing` covers today/yesterday, distance sums the inclusive range, and `detectAndStore` atomically `replaceAll`s.
> - **Improvement — false-trip guard (flagship bar):** because `getAllDayKeys` only returns days *with* visits, any data gap mid-run was silently absorbed — a long blackout (e.g. phone off for ~3 weeks between two away-stays) fabricated **one continuous mega-trip**, claiming away-ness with no evidence. Added `MAX_ABSORBED_GAP_DAYS = 3`: a phone-off day or two still merges, but a longer blackout breaks the run so a later stay starts fresh (under-claim over over-claim). 
> - **Tests:** `DetectTripsUseCaseTest` 7 → 11 cases (short-gap absorbed, long-blackout splits into two trips, unnamed-destination → "N-day trip" title, `detectAndStore` replaceAll).

### W6.3 — Trip detail & PDF trip-book · Status: [ ] verified  [ ] improved
**What it does:** Day-by-day journal + photo-forward PDF export.
**Key functions / files:** `presentation/screen/trips/TripDetailScreen.kt`+VM, `domain/model/TripDetail.kt`/`TripDay.kt`.
**How to travel-test:** Open a trip; export the book; confirm the PDF reads like a story (see Part C C3).
**Flagship bar:** Polarsteps-quality book; captions/cover editable.

### W6.4 — Mileage log & tax classification · Status: [x] verified (code-level) 2026-06-12  · improved: n/a (sound)
**What it does:** Lists DRIVE segments with business/personal/medical/charitable purpose.
**Key functions / files:** `presentation/screen/mileage/MileageScreen.kt`+`MileageViewModel`, `domain/usecase/BuildMileageLogUseCase.kt`, `MileageClassificationDao`.
**How to travel-test:** Classify a few drives; switch periods; confirm deductible totals update.
**Flagship bar:** Fast swipe-to-classify; per-row GPS evidence (the MileIQ-beating moat).
**Verification (2026-06-12, code-level):** ✅ Drive↔classification join is correct: classifications are a sparse table (no row ⇒ `UNCLASSIFIED`, `MileagePurpose.fromName` falls back safely on unknown/legacy strings), `deductibleMeters` sums exactly the business/medical/charitable classes, and the "newest-first" log contract holds (`getByTypesBetween` is `ORDER BY startAt DESC`). `classify` deletes on UNCLASSIFIED+no-note but keeps a row when a note is attached (note survives); revision bumps on upsert. `BuildMileageLogUseCaseTest` 4 → 11 cases (all three deductible classes, no-drive purpose ⇒ 0, unknown-purpose fallback, out-of-range classification ignored, clearClassification, UNCLASSIFIED-with-note retention).

### W6.5 — Mileage calculator & vehicles · Status: [x] verified (MileageCalculatorTest) 2026-06-12  · improved: n/a (math verified correct)
**What it does:** Fuel/cost/CO₂ math + vehicle profiles, fuel price history, service log, auto-assignment.
**Key functions / files:** `domain/usecase/MileageCalculator.kt` (`fuelUsed`, `costMinor`, `co2Kg`), `VehicleAutoAssignmentEngine`, `VehicleDao`, `FuelPriceHistory`, `VehicleServiceLog`.
**How to travel-test:** Add a vehicle with efficiency + price; confirm a drive shows fuel/cost/CO₂; confirm auto-assignment picks the right vehicle.
**Flagship bar:** Correct math across fuel types/units; sensible vehicle auto-assignment.
**Verification (2026-06-12, code-level):** ✅ Pure math checks out — MPG_US (×0.42514) and MPG_UK (×0.35400) factors match miles/gallon→km/L exactly; KM_PER_L and L_PER_100KM agree on units-per-km for equivalent efficiencies; EV/kWh path and the EV/HYBRID/CNG CO₂ factors are correct; `costMinor` rounds to minor units and rejects negative prices. `MileageCalculatorTest` 8 → 14 cases (MPG_UK, unit equivalence, EV kWh, EV/HYBRID/CNG CO₂, EV end-to-end forSegment, negative-price guard). Vehicle auto-assignment/profiles are DAO-coupled → integration-test territory.

### W6.6 — Mileage rollups & PDF export · Status: [x] verified (code-level) 2026-06-12  · [x] improved 2026-06-12
**What it does:** Pre-aggregated mileage totals + IRS-style PDF.
**Key functions / files:** `domain/usecase/MileageDeduction.kt`, `platform/export/MileagePdfExporter.kt`, (`MileageSummaryEntity` — see rollup note).
**How to travel-test:** Export a month's mileage PDF; confirm totals match the log and business/personal split is right.
**Flagship bar:** Court/IRS-grade PDF with evidence; rollups match raw.
**Verification (2026-06-12, code-level):** ✅ The PDF (`MileagePdfExporter`) renders the tested `MileageLog` (W6.4) and is the live path the screen uses; it paginates the drive table and labels the deduction an estimate to verify.
> - **Improvement — deduction math extracted + locked:** the IRS deduction estimate (the legal/financial moat) was buried inside the Android rendering method, so the number a user *files with taxes* was untestable. Extracted to pure `MileageDeduction.estimate` (per-mile rates: BUSINESS 0.70 / MEDICAL 0.21 / CHARITABLE 0.14; personal/unclassified → no rate, $0). Renderer now calls it (identical output). Locked by `MileageDeductionTest` (6 cases: per-rate, multi-purpose sum, non-deductible $0, declaration-order/only-driven, empty log, custom-rate override e.g. HMRC).
> - **Rollup note (unwired scaffolding):** `MileageSummaryEntity`/`MileageSummaryDao` exist with a "rebuilt by the periodic mileage worker" doc, but **nothing writes or reads them** — the screen and PDF compute totals live from `BuildMileageLogUseCase`. Left as-is: the live path is correct at current scale; the summary table is a pre-built optimization to wire (worker + scheduling) only if profiling shows recompute cost. Tracked here rather than built speculatively.

### W6.7 — Day Story photo journal · Status: [x] verified (BuildDayStoryUseCaseTest) 2026-06-12  · improved: n/a (sound)
**What it does:** Photos placed on the day's timeline with location context.
**Key functions / files:** `presentation/screen/daystory/DayStoryScreen.kt`+VM, `domain/usecase/BuildDayStoryUseCase.kt`, `data/media/MediaStorePhotoLibrary`.
**How to travel-test:** Take photos during a day; open Day Story; confirm they map to the right places/times.
**Flagship bar:** Photo permission asked JIT; correlation is accurate; feels like a story.
**Verification (2026-06-12, code-level):** ✅ Correlation is sound — a photo pins to the visit whose `[arrival, departure]` window (open visit → end-of-day) contains its capture time; among overlapping visits a geotagged photo picks the spatially nearest place (`hasLocation` guards the EXIF `!!`, null centroids deprioritised) and an untagged one the tightest window. The story orders places by arrival and photos by capture time, omits photo-less visits, buckets out-of-window photos as `unplacedPhotos`, and `totalPhotoCount` counts all. Permission is reported via `hasPhotoPermission` (JIT on the screen). `BuildDayStoryUseCaseTest` 7 → 9 cases (added multi-photo/multi-place narrative ordering + photo-less-visit omission, and the "Unknown place" fallback). Photo enumeration is `MediaStorePhotoLibrary` (Android) → device-verify EXIF/`ACCESS_MEDIA_LOCATION`.

### W6.8 — Workout recording · Status: [ ] verified  [ ] improved
**What it does:** Live-records a run/walk/cycle route with stats.
**Key functions / files:** `domain/usecase/WorkoutRecorder.kt`, `WorkoutStatsCalculator`, `ActivityDao`.
**How to travel-test:** Record a short run; confirm distance/pace and the saved route.
**Flagship bar:** Strava-grade live stats; clean GPX-per-activity (ties to Part C D1 fitness subsystem).

## Wave 7 — Data portability

### W7.1 — Export (Voyager JSON / KML / CSV / GPX) · Status: [ ] verified  [ ] improved
**What it does:** Exports a day/range in multiple formats.
**Key functions / files:** `presentation/screen/export/ExportScreen.kt`+VM, `data/repository/ExportRepositoryImpl.kt`, `VoyagerJsonFormat`, `PolylineCodec`, `worker/ExportWorker`.
**How to travel-test:** Export a range as GPX and JSON; open the files; confirm completeness.
**Flagship bar:** Lossless round-trip; standards-valid GPX/KML.

### W7.2 — Import / restore · Status: [ ] verified  [ ] improved
**What it does:** Restores from a Voyager JSON backup.
**Key functions / files:** `ExportRepository.import`, `presentation/screen/onboarding/RestoreScreen.kt`.
**How to travel-test:** Export, wipe (pre-production OK), restore; confirm data returns intact.
**Flagship bar:** Round-trip identical; clear summary; handles signing-key change (see Part C K6).

### W7.3 — Google Timeline import · Status: [ ] verified  [ ] improved
**What it does:** Imports Google Location History JSON.
**Key functions / files:** `data/import/GoogleTimelineImporter`, `GoogleTimelineImportScreen`+VM.
**How to travel-test:** Import a real Takeout file; confirm places/segments populate sensibly.
**Flagship bar:** "Lost your Timeline? Bring it home" works on real exports; clear summary.

### W7.4 — Data retention & cleanup · Status: [ ] verified  [ ] improved
**What it does:** Deletes old raw samples per retention policy.
**Key functions / files:** `worker/DataRetentionWorker`.
**How to travel-test:** Set a short retention; confirm old raw samples prune while derived data stays.
**Flagship bar:** Respects policy exactly; never deletes derived history unexpectedly.

### W7.5 — Diagnostics snapshot · Status: [ ] verified  [ ] improved
**What it does:** Exports internal state for debugging.
**Key functions / files:** `domain/usecase/DiagnosticSnapshotUseCase.kt`.
**How to travel-test:** Generate a snapshot; confirm it captures candidate/pending/correction state.
**Flagship bar:** Enough to debug a field issue without a rebuild.

## Wave 8 — Onboarding & first-run

### W8.1 — Splash · Status: [ ] verified  [ ] improved
**What it does:** Animated intro (Orion's Belt).
**Key functions / files:** `presentation/screen/splash/AnimatedSplashContent.kt`.
**How to travel-test:** Cold start; confirm a quick, branded splash.
**Flagship bar:** Fast, not blocking cold start (see Part C E2).

### W8.2 — Permission onboarding (phased) · Status: [ ] verified  [ ] improved
**What it does:** Requests permissions in stages with rationale.
**Key functions / files:** `presentation/screen/onboarding/PermissionOnboardingScreen.kt`.
**How to travel-test:** First run; confirm foreground location first, background asked JIT, notification optional.
**Flagship bar:** Value before permissions; JIT background request feels self-evident.

### W8.3 — Persona pick (presets) · Status: [ ] verified  [ ] improved
**What it does:** Choose a persona that applies a settings preset.
**Key functions / files:** `presentation/screen/onboarding/PersonaPickScreen.kt`+VM, `domain/model/SettingsPresets.kt`.
**How to travel-test:** Pick each persona; confirm defaults change accordingly.
**Flagship bar:** One choice configures the app (ties to Part C B1/B2 5-persona model).

### W8.4 — Feature walkthrough · Status: [ ] verified  [ ] improved
**What it does:** One-time tour of core modules.
**Key functions / files:** `presentation/screen/onboarding/FeatureWalkthroughScreen.kt`.
**How to travel-test:** First run; confirm tour shows once and is skippable.
**Flagship bar:** Brief, skippable, never re-shown.

### W8.5 — App-phase flow orchestration · Status: [ ] verified  [ ] improved
**What it does:** Sequences Splash→Restore→Import→Onboarding→Persona→Walkthrough→Main.
**Key functions / files:** `MainActivity.kt`.
**How to travel-test:** Fresh install end-to-end; confirm phases run once and land on Main.
**Flagship bar:** No phase loops; resumes correctly if interrupted.

## Wave 9 — Settings & configuration

### W9.1 — Settings screen (all sections) · Status: [ ] verified  [ ] improved
**What it does:** Tracking, quality, detection, battery, sleep, notifications, geocoding, analytics, advanced + about.
**Key functions / files:** `presentation/screen/settings/SettingsScreen.kt`+`SettingsViewModel`, `domain/model/UserPreferences.kt` (150+ settings).
**How to travel-test:** Change representative settings in each section; confirm they persist and take effect.
**Flagship bar:** "Essentials + Everything" structure (see Part C B4); no setting is dead.

### W9.2 — Presets / profiles · Status: [ ] verified  [ ] improved
**What it does:** Apply preset bundles (battery-saver, commuter, traveler).
**Key functions / files:** `SettingsPresets`, `SettingsViewModel.applyPreset`.
**How to travel-test:** Apply a preset; confirm the bundle of settings changes coherently.
**Flagship bar:** Presets are meaningful and reversible.

### W9.3 — Data management · Status: [ ] verified  [ ] improved
**What it does:** Export/import settings, delete all data.
**Key functions / files:** `SettingsViewModel` (`exportData`, `exportSettings`, `importSettings`, `deleteAllData`).
**How to travel-test:** Export settings, change some, re-import; confirm restoration. Delete-all from a test profile.
**Flagship bar:** Destructive actions confirmed; settings round-trip cleanly.

### W9.4 — Per-section config behaviors · Status: [ ] verified  [ ] improved
**What it does:** The individual toggles actually change engine behavior.
**Key functions / files:** `SettingsRepository`, consumed across capture/pipeline/workers.
**How to travel-test:** Change GPS frequency / sleep schedule / dwell threshold; verify the engine respects each.
**Flagship bar:** Every toggle has an observable effect; no placebo settings.

## Wave 10 — Monetization & polish

### W10.1 — Paywall & Pro offerings · Status: [ ] verified  [ ] improved
**What it does:** Presents Pro products and starts purchase.
**Key functions / files:** `presentation/billing/PaywallScreen.kt`+`PaywallViewModel` (`purchase`, `restore`, `consumePurchaseState`), `ProCatalog`.
**How to travel-test:** Open the paywall from a gated feature; confirm products render and the flow starts (full transaction blocked on Part C J1/J2).
**Flagship bar:** Honest value framing; works on Play and degrades on F-Droid.

### W10.2 — Entitlements & feature gating · Status: [ ] verified  [ ] improved
**What it does:** Gates Pro features behind entitlement state.
**Key functions / files:** `EntitlementViewModel` (`isPro`), `ProEntitlementManager`, `BillingGateway`.
**How to travel-test:** Toggle entitlement (test path); confirm gated features lock/unlock consistently.
**Flagship bar:** No gate leaks; restore works; consistent across screens.

### W10.3 — Design system · Status: [ ] verified  [ ] improved
**What it does:** Colors, gradients, spacing, shapes, motion, surfaces, components.
**Key functions / files:** `presentation/theme/*` (`VoyagerColors`, `VoyagerGradients`, `VoyagerSpacing`, `VoyagerShapes`, `VoyagerMotion`, `VoyagerSurfaces`), `presentation/components/*`.
**How to travel-test:** Scan all screens for token consistency; check reduce-motion respect.
**Flagship bar:** No inline literals; light/dark parity (see Part C A1/A7); motion layer (A2).

### W10.4 — Notifications · Status: [ ] verified  [ ] improved
**What it does:** Channels + builders for tracking/alerts/insights/health.
**Key functions / files:** `platform/notification/VoyagerNotificationManager.kt`.
**How to travel-test:** Trigger each notification type; confirm correct channel/importance and actions work.
**Flagship bar:** Calm, persona-aware copy; correct channels; no spam.

### W10.5 — Feedback / Developer profile / Licenses · Status: [ ] verified  [ ] improved
**What it does:** Feedback form, about-developer, OSS licenses.
**Key functions / files:** `presentation/screen/feedback/FeedbackScreen.kt`, `developer/DeveloperProfileScreen.kt`, `developer/OpenSourceLicensesScreen.kt`.
**How to travel-test:** Send feedback (email intent); open licenses; confirm links work.
**Flagship bar:** Complete, accurate licenses; frictionless feedback.

---

# Part B — Trust & correctness gaps in built features

These are known defects in *already-built* features — the reason "implemented" ≠ "flagship."
Each cross-links to the Part A wave it degrades. Source: `~/.claude/plans/yes-finish-phase-4-radiant-rabin.md` (Wave 6 trust fixes). Mark status against that logbook as you confirm.

**Status: all 15 trust items (T1–T15) are ✅ cleared (as of 2026-06-12)** — each fixed (or verified already-correct) at code level and locked with unit tests; device travel-tests still confirm them end-to-end. The only remaining Part B row is the **I1** ship-blocker (3 `!!` in screen code), which lives in the in-progress redesign WIP and is owned there.

| ID | Symptom | Degrades | Status |
|----|---------|----------|--------|
| T1 | Long walks fragment into multiple segments (time-flush flaw) | W1.3 | ✅ — already fixed (no time-only flush for non-VISIT segments) + covered by existing test. |
| T2 | Place categories never auto-inferred from OSM tags | W2.3 | ✅ — already implemented: `PoiCategoryMapper` (OSM tag→category) applied at place discovery + geocode refresh (gated to UNKNOWN/no-override); comprehensive mapper + pattern fallback both tested. Verified, no change needed. |
| T3 | Visits don't close on app death | W0.6 / W1.4 | ✅ 2026-06-10 — already closed on cold start (`repairStrandedVisits`) + nightly worker; fixed the dwell-truncation bug (`closeStaleVisits` now closes at last-known-alive, not the 30-min selection cutoff). Tests added. |
| T4 | Slow traffic classified as cycling | W1.2 / W1.3 | ✅ 2026-06-11 — prior fix handled 3.0–4.5 m/s; added vehicle-context so the 4.5–6.5 m/s cycling band reads as slow traffic once driving, cleared by walking-steps/AR. Tests added. |
| T5 | Place fragmentation (same building → multiple places) | W2.5 / W2.6 | ✅ 2026-06-11 — both halves footprint-aware: discovery merges new clusters within an existing place's radius (W2.5 `mergeRadiusM`); the merge worker collapses same-named fragments within a confirmed venue's radius (W2.6 `mergeDistanceLimitM`). Tests added. |
| T6 | Quick-return continuation overeager | W1.4 | ✅ 2026-06-12 — extracted to pure `QuickReturnPolicy`; "moved away" now also breaks on TRANSIT and on a meaningful on-foot excursion (gap WALK ≥ max(2×radius, 100 m)), so walk-and-transit round trips become separate visits. PROCESS_DEAD bridging (no gap segments) is unaffected. Locked by `QuickReturnPolicyTest` (12) + 3 wiring cases. |
| T7 | Step-rate fusion misfires | W0.5 / W1.2 | ✅ 2026-06-12 — root cause was the cadence, not the thresholds: it was `totalSteps / bucketSpan`, so a 5-step / 2 s desk shuffle extrapolated to 150 spm → RUNNING. Extracted pure `StepRateCalculator` flooring the denominator at 30 s (burst → ~10 spm; real walk → ~108 spm; stillness → ~0). Locked by `StepRateCalculatorTest` (8). |
| T8 | Place-match search radius fixed at 200m | W1.5 | ✅ 2026-06-11 — search radius already adaptive on GPS accuracy; also made the reachability gate honor the place's own footprint (`max(searchRadius, place.radiusM+buffer)`) so large venues match in good GPS. Tests added. |
| T9 | Kalman reference doesn't reset on long travel | W1.1 | ✅ 2026-06-11 — already implemented (25km reanchor in `LocationKalmanFilter.filter()`); verified + locked with a re-anchor unit test. |
| T10 | Visit dwell uses wrong timestamp | W1.4 | ✅ 2026-06-11 — departure now recorded at the last in-place sample (`lastInsideSampleAt`), not the exit-confirming sample that inflated dwell by the exit-hysteresis + walk-out. Test added. |
| T11 | Day-boundary overnight stays double-count | W1.7 | ✅ 2026-06-11 — daily-rollup dwell now clamps each visit to the day window (`overlapMs` over overlapping visits) instead of dumping the full overnight dwell on the arrival day; rollup dayKey uses home tz. Tests added. |
| T12 | BatteryBudgetController computed but never applied | W0.2 | ✅ 2026-06-10 — worker applies + is scheduled (6h) + live re-apply on next motion transition; now also **surfaces** the downgrade to the user via `showTrackingAlert` (was silent, violating the controller's contract). UI to *enable* a budget = F2 (planned). |
| T13 | FLIGHT threshold 200 m/s misses takeoff/landing | W1.3 | ✅ 2026-06-11 — already fixed (sustained ≥80 m/s ×2 trigger alongside the 200 m/s single-sample bar); now locked with cruise + sustained tests. |
| T14 | No place-confidence decay | W2.7 | ✅ — already implemented: pure tested `PlaceConfidenceDecay` applied daily by `ConfidenceDecayWorker` (scheduled 03:30, wired into `scheduleAll`), revisits re-bump. Verified, no change needed. |
| T15 | isMock catches only API-flagged spoofers | W0.1 | ✅ 2026-06-12 — added pure `SpoofHeuristics` (teleport / physically-impossible-speed gate, > ~340 m/s within continuous tracking) wired into `PipelineConsumer` before Kalman; complements `isFromMockProvider` to catch non-API injectors. Conservative (gap-aware, 1 km jump floor) so real flights/jitter never trip. Locked by `SpoofHeuristicsTest` (8). |
| I1 | 3 remaining `!!` in screen code (ship-blocker) | W3.x / W9.1 | ☐ |

> Note: several T-items may already be resolved/merged in the source logbook — confirm
> current status there before working one.

---

# Part C — Planned-but-unbuilt features (opportunity backlog)

Headline names pulled inline; full detail (type/scope/effort/leverage/source/files) lives in
[`voyager-master-improvement-backlog.md`](voyager-master-improvement-backlog.md), grouped by
its own P0–P3 roadmap. IDs are 1:1 with that doc.

### P0 — Ship-blockers (~1–2 weeks)
- **J1** Create Play Console products (`pro_monthly`/`pro_yearly`/`pro_lifetime`) ⊘ _(user/Play Console)_
- **J2** End-to-end billing test on device ⊘ _(needs J1)_
- **J3** Play data-safety form ☐
- **J4** Public + linked privacy policy ☐ _(see `privacy-policy.md`)_
- **I1** Remove last 3 `!!` in screens ☐ _(also Part B)_
- **I2** 7-day crash-free dogfood across 4 OEMs ☐
- **G1** Measure real release AAB size ☐

### P1 — Polish & lightness (~4–6 weeks · biggest perceived-quality lever)
- **A1** Light theme · **A2** Motion layer · **A3** Dashboard redesign · **A4** Timeline ribbon redesign · **A5** Map-as-hero · **A6** Heatmap / Year-in-Review · **A9** Onboarding visual polish · **A10** First-hour "capturing now" pulse · **A11** Detail-sheet redesign
- **B1** Persona-scoped surfaces · **B2** 5-persona model (Keeper/Navigator/Professional/Athlete/Wanderer) · **B3** Feature Library screen · **B4** Settings restructure · **B7** Accessibility pass
- **F1** 5 tracking tiers · **F2** Battery-budget mode · **F3** Passive tier · **F5** Honest per-tier battery numbers
- **C6** Evidence prominence · **E1** Baseline Profiles · **E2** Lazy engine init · **E3** Recomposition audit · **L3** Microcopy pass · **L4** Notification copy & cadence

### P2 — Differentiators & depth (each its own plan)
- **D1** Fitness/workout subsystem (unblocks Athlete persona; extends W6.8)
- **C1** POI prior into confirmation · **C2** Accelerometer signature · **C3** Trip storytelling depth · **C5** Offline tiles + camera persistence · **C4** "On this day"
- **A8** Iconography & illustration set · **B5** Progressive Insights tabs · **B6** Privacy-first modifier · **B8** Internationalization · **L1** Geocoding quality · **L2** Category-inference quality
- **D4** OSM contribution loop · **D2** Family one-bit handshake ⊘ _(security review)_ · **D3** Duress mode / panic-wipe ⊘ _(security review)_

### P3 — Future-proofing (before cloud / iOS / B2B)
- **H1** ExportFormatPlugin · **H2** PipelineGateway interface · **H3** Typed-ID value classes · **H4** userId / multi-user column · **H5** SyncManager interface · **H6** DAO-import lint rule
- **G3** Dynamic Feature Modules · **G4** F-Droid lite build
- **K1–K7** Deeper test suites (synthetic pipeline, property tests, permission-revocation, worker-concurrency, encryption, backup-restore, CI)
- **I3** OEM matrix sign-off · **I4** Edge-case audit · **J5** Reproducible build · **J7** Background-location yearly re-justification

> Also: `blueprint.md` describes Wave 7–9 intelligence-layer items (e.g. pattern-based
> category inference, multi-day trip auto-detection) — most now map to shipped Part A
> features (W2.3, W6.2) or to C1/L2; check before adding new IDs.

---

# Part D — Competitor feature matrix

Source: [`research/competitor-analysis.md`](research/competitor-analysis.md). Voyager status:
✅ has · ◐ partial · ✗ missing (→ the Part A/C item that delivers/closes it).

### Parity matrix

| Competitor feature | Who ships it | Voyager status |
|--------------------|--------------|----------------|
| Auto place/drive detection | Google Timeline, Arc | ✅ W1.3–W1.5, W2.x |
| Photo correlation on timeline | Google Timeline, Polarsteps, PhotoMap | ◐ W6.7 (Day Story) |
| Monthly heatmap / Year-in-Review | Google Timeline, Strava | ✗ → **A6** |
| Trip stories + printed travel books | Polarsteps | ◐ W6.2/W6.3 → deepen with **C3** |
| Polished mode-detection timeline UX | Arc | ◐ W3.1 → **A4** ribbon redesign |
| Raw GPX / MQTT location stream | OwnTracks, GeoTracker | ✅ W7.1 (GPX export) |
| Family / safety location sharing | Life360 | ✗ → **D2** ⊘ (security review) |
| Workouts / segments / leaderboards | Strava | ◐ W6.8 → **D1** fitness subsystem |
| Auto-mileage classification + IRS PDF | MileIQ, Everlance, Hurdlr, Stride | ✅ W6.4–W6.6 (+ per-row GPS evidence) |
| Mood / journal with location pins | Daylio, Day One | ✗ (out of scope; nearest = W6.7) |
| Photo-by-location timeline | PhotoMap, Memories | ◐ W6.7 |
| Per-trip routes | Citymapper, Google Maps Trips | ✅ W3.2 / W6.2 |
| On-device-only privacy | Arc (iOS), OwnTracks | ✅ core architecture (Android-wide) |

### Voyager's 7 differentiators (whitespace nobody else owns)

Doubles as a moat-status tracker — tag each shipped / partial / planned.

1. **Three jobs in one app** (Memory + Proof + Habits) — ✅ shipped (unified engine).
2. **On-device + breadth on Android** — ✅ shipped (Arc is iOS-only; Timeline is cloud).
3. **Evidence + explainability** — ✅ W4.7 → press the moat via **C6**.
4. **Mileage log with court/IRS-grade GPS evidence per row** — ✅ W6.4–W6.6.
5. **Family one-bit handshake (no live stream)** — ✗ planned **D2** ⊘ (security review).
6. **Duress mode for journalists/activists** — ✗ planned **D3** ⊘ (security review).
7. **Local-first OSM contribution loop** — ✗ planned **D4**.

### One-line positioning (from the source doc)

- vs **Google Timeline**: "They killed your cloud sync. We never had one."
- vs **Polarsteps**: "They sell you back your own memories. We let you own them."
- vs **Life360**: "They sold your family's location to brokers. We don't even see it."
- vs **Strava**: "They leaked base locations from the heatmap. Ours stays on your phone."
- vs **MileIQ**: "$60/yr for one feature. We give mileage as one of ten."
- vs **Arc**: "Same privacy ethos, on the 70%+ of phones that aren't iPhones."
- vs **OwnTracks**: "All the privacy. None of the MQTT broker."

---

_Maintenance: when a Part C item ships, move its essence into a Part A card and tick it; when
a new gap is found, add it to `voyager-master-improvement-backlog.md` first, then reference it
here. This catalog is the working surface; the backlog and competitor docs remain the
source of truth._
