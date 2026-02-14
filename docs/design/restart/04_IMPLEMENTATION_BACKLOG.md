# Voyager Restart Implementation Backlog

Execution note:
- Use `07_MASTER_PHASE_EXECUTION_PLAN.md` as the authoritative phase sequencing and gate policy document when this backlog overlaps with other restart docs.

## Phase 0 - Stabilize (Do First)

## 0.1 Restore test baseline
- Add missing unit-test dependency for Truth.
- Update `app/src/test/java/com/cosmiclaboratory/voyager/fixtures/TestDataFactory.kt` to current domain models:
- replace obsolete `PlaceCategory.FOOD`
- remove obsolete place constructor fields
- fix `Visit` construction to current signature (`_duration`/factory usage)
- ensure type correctness (`Float` vs `Double` where required)

Acceptance criteria:
- `./gradlew testDebugUnitTest` compiles and runs.

## 0.2 Resolve critical compile warnings in core runtime
- Fix nullability/type mismatch warnings in `NominatimGeocodingService`.
- Remove always-true/false state-condition code in high-risk state/orchestration paths.

Acceptance criteria:
- No high-risk runtime warnings remain in service/state/synchronizer/geocoding paths.

## 0.3 Add smoke checks
- Add smoke/integration checks for:
- tracking start/stop state transition
- worker enqueue result path
- current-state initialization path

Acceptance criteria:
- CI/local has at least one automated check per path above.

## Phase 1 - State Write Authority

## 1.1 Introduce `StateWriteGateway`
- New interface to own all state writes:
- tracking status
- current place/visit
- daily stats
- Implement atomic write flow across app state + DB.

Acceptance criteria:
- direct state writes from service/manager/synchronizer/repository are migrated to gateway.

## 1.2 Remove duplicate write channels
- Refactor:
- `LocationTrackingService`
- `LocationServiceManager`
- `CurrentStateRepositoryImpl`
- `StateSynchronizer`
- to avoid overlapping state ownership.

Acceptance criteria:
- one canonical mutation path per state concern.

## Phase 2 - Runtime Orchestration

## 2.1 Introduce `TrackingOrchestrator`
- Own start/stop/pause lifecycle.
- Activity and manager call orchestrator, not service/control logic directly.

Acceptance criteria:
- no lifecycle business logic in activity/service-manager beyond adapters.

## 2.2 Introduce `PlaceDetectionScheduler`
- Consolidate all worker enqueue/cancel/status paths.
- ViewModels and services use scheduler facade only.

Acceptance criteria:
- single scheduling boundary for detection workers.

## Phase 3 - Decompose Hotspots

## 3.1 Split `LocationTrackingService`
- Keep Android service as adapter.
- Move ingestion/processing logic into dedicated pipeline components.

## 3.2 Split `AppStateManager`
- Separate transition policy, validation, recovery, and event emission modules.

## 3.3 Split `PlaceDetectionUseCases`
- Separate clustering/detection, enrichment, and review orchestration.

Acceptance criteria:
- no single runtime class >700 lines in these modules (target).

## Phase 4 - Wiring and DI Hardening

## 4.1 Minimize `EntryPointAccessors`
- Keep only where Android framework boundaries force manual access.

## 4.2 Event contract hardening
- Event bus becomes notification-only.
- Prevent hidden mutation loops triggered by event handling.

Acceptance criteria:
- producer/consumer matrix implemented per wiring doc.

## Phase 5 - Feature Parity Validation

## 5.1 Feature matrix verification
- Validate all existing features remain functional:
- dashboard, map, timeline, settings, categories, insights, review, debug, workers, geofence, boot restore.

## 5.2 Regression pack
- Add targeted tests for critical end-to-end chains from `03_WIRING_AND_FUNCTION_MAPPING.md`.

Acceptance criteria:
- full parity checklist signed off.

## Phase 6 - Optimization and Reliability Extension (from `05`)

## 6.1 Insight system modernization
- Introduce canonical insight schema:
- `InsightFact`, `InsightDistribution`, `InsightConfidence`, `InsightProvenance`.
- Move heavy insight generation to a dedicated `InsightEngine`.
- Keep UI mapping as adapters only.

Acceptance criteria:
- Insight cards across screens use one canonical model and include confidence + timeframe.

## 6.2 Data-grain contract enforcement
- Implement canonical aggregates:
- `DayAggregate`, `PlaceAggregate`, `VisitAggregate`, `MovementSegment`.
- Ensure map/timeline/analytics consume shared grain contracts.

Acceptance criteria:
- For a selected date, counts and durations match across all screens.

## 6.3 Restart and shutdown resilience
- Introduce `TrackingLifecycleOrchestrator`.
- Make boot/process-death reconciliation idempotent and deterministic.
- Remove multi-authority restart decisions.

Acceptance criteria:
- Reboot/process death restores intended tracking state without orphan visits.

## 6.4 Place name suggestion ranking upgrade
- Add candidate normalization + dedupe.
- Replace static confidence with feature-based scoring and feedback loop.

Acceptance criteria:
- Suggestions are ranked deterministically and improve with user correction history.

## 6.5 Timeline duplicate/0s visit elimination
- Add transactional visit close/open flow.
- Enforce active-visit invariants.
- Add zero-duration dedupe/merge guardrails.

Acceptance criteria:
- No consecutive same-place `0s` duplicate segments in timeline.

## 6.6 Full location display consistency
- Introduce `LocationDisplayPolicy` (`COMPACT`/`STANDARD`/`FULL`).
- Apply shared formatter across map/timeline/insights components.

Acceptance criteria:
- Address rendering is consistent and full location is available in detail contexts.

## 6.7 Supporting optimization track
- DataStore migration for preferences (with SharedPreferences migration).
- Consolidate WorkManager operations behind a single scheduler boundary.
- Enforce export anonymization behavior when configured.
- Expand test coverage for startup, transitions, and insight output stability.

Acceptance criteria:
- Core optimization track has automated regression checks and documented rollout.

## Phase 7 - Next-Gen UX and Smart Tracking (from `06`)

## 7.1 Navigation and product shell redesign
- Introduce `Track` primary tab for workout capture.
- Move settings/control into a top-right control menu hierarchy.
- Align route definitions, nav host behavior, and menu/bottom-tab semantics.

Acceptance criteria:
- Navigation model is internally consistent and all primary tasks are discoverable in <=2 taps.

## 7.2 SmartTrackingEngine (running/walking)
- Implement workout session state machine:
- `Idle -> Warmup -> Active -> AutoPaused -> Cooldown -> Completed`
- Add adaptive sampling modes (`Eco`, `Balanced`, `Performance`).
- Add live metrics stream (pace, distance, time, cadence proxy where available).

Acceptance criteria:
- Workouts can be started/paused/resumed/finished reliably with valid metrics.

## 7.3 Workout data contracts and storage
- Add domain/storage models:
- `WorkoutSession`, `WorkoutPoint`, `Split`, `Lap`, `SegmentEffort`.
- Add repository/use cases for retrieval and summary generation.

Acceptance criteria:
- Workout detail, timeline, and insights consume the same canonical workout data.

## 7.4 Advanced insight features for workouts
- Add training load and readiness trends.
- Add context-aware recommendations from historical patterns.
- Add cross-link between place intelligence and workout performance context.

Acceptance criteria:
- Insight cards expose confidence/source context and actionable recommendations.

## 7.5 Post and sharing layer (privacy-first)
- Implement `ActivityPost` drafting and publishing workflow.
- Add privacy controls (private/followers/public + hidden start/end zones).

Acceptance criteria:
- Posts respect privacy settings across all rendered share surfaces.

## 7.6 Segment and challenge framework
- Add segment detection and PR comparison groundwork.
- Add challenge primitives (streaks, weekly goals, group challenges).

Acceptance criteria:
- Segment and challenge summaries are generated deterministically from session data.

## 7.7 Smart tracking hardening and rollout
- Add integrity scoring (GPS anomaly/spike checks).
- Add restart/process-death recovery for active workout sessions.
- Add stress/perf test pack for high-volume location streams.

Acceptance criteria:
- Smart tracking flows meet reliability/performance thresholds before broad rollout.

## Delivery Tracking Template

Use this checklist per item:
- Owner:
- Status: `todo` | `in_progress` | `done`
- Risk:
- PR/Commit:
- Test evidence:
- Notes:
