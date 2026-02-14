# Voyager Master Phase Execution Plan (Conflict-Safe)

## 1) Purpose and Precedence
This is the execution control document for the restart effort.

Use existing docs as inputs:
- `01_ARCHITECTURE_AND_DESIGN.md`
- `02_CORE_FUNCTIONS_GAPS_AND_STRUCTURE.md`
- `03_WIRING_AND_FUNCTION_MAPPING.md`
- `04_IMPLEMENTATION_BACKLOG.md`
- `05_FUNCTION_SEPARATION_AND_OPTIMIZATION_RESEARCH.md`
- `06_NEXT_GEN_UI_AND_SMART_TRACKING_PLAN.md`

Execution precedence rule:
- If phase ordering or ownership conflicts appear across `01..06`, follow this `07` document.
- `01..06` remain design/reference sources; `07` is the implementation sequencing source.

## 2) Core Non-Conflict Rules
1. One migration seam per concern at a time.
2. New interfaces first, callsite migration second, legacy removal last.
3. Feature-flag new behavior until parity checks pass.
4. No parallel edits to the same ownership boundary in the same sprint.
5. No phase jump before phase gate is green.

## 3) Dependency DAG (High Level)
- Phase 0 -> Phase 1 -> Phase 2 -> Phase 3 -> Phase 4 -> Phase 5 -> Phase 6 -> Phase 7 -> Phase 8 -> Phase 9
- Phases 8 and 9 require stable completion of 0..7.
- Phase 9 is optional until phase-8 reliability is proven.

## 4) Master Phase Plan

## Phase 0 - Baseline Stabilization
### Objective
Create a deterministic baseline for refactor safety.

### In scope
- Restore test baseline.
- Fix critical compile warnings in runtime paths.
- Add smoke checks for tracking lifecycle, worker enqueue, state bootstrap.

### Out of scope
- Structural refactors.

### Owned modules
- test sources
- geocoding/runtime warning files
- smoke test harness

### Exit gate
- Baseline checks pass consistently in local/CI.

---

## Phase 1 - State Write Authority
### Objective
Establish a single state mutation authority.

### In scope
- Introduce `StateWriteGateway`.
- Route tracking/current-place/daily-state writes through gateway.
- Event system becomes notification-oriented.

### Out of scope
- Full lifecycle orchestration changes.

### Owned modules
- state management and repositories touching current state
- synchronizer mutation points
- service/manager direct state writes

### Exit gate
- No direct core state writes outside gateway.
- Integration tests prove single-write-path behavior.

---

## Phase 2 - Runtime Orchestration Boundary
### Objective
Consolidate lifecycle and worker control responsibilities.

### In scope
- Introduce `TrackingOrchestrator`.
- Introduce `PlaceDetectionScheduler`.
- Remove direct WorkManager control from viewmodels/services.

### Out of scope
- Timeline integrity changes.

### Owned modules
- `LocationTrackingService`, `LocationServiceManager`, worker management use cases, WorkManager helper callsites

### Exit gate
- Orchestrator owns tracking transitions.
- Scheduler owns detection worker lifecycle.

---

## Phase 3 - Visit Integrity and Timeline Correctness
### Objective
Eliminate duplicate/0s visit artifacts and transition races.

### In scope
- Transactional active-visit end/start transitions.
- Visit invariants and timeline dedupe/compaction policy.
- Guardrails against consecutive same-place zero-duration segments.

### Out of scope
- Major UI redesign.

### Owned modules
- visit repository/dao transitions
- smart processor transition logic
- timeline segment generation

### Exit gate
- Jitter/re-entry scenarios pass without duplicate 0s artifacts.

---

## Phase 4 - Persistence and UI Policy Consistency
### Objective
Make UI behavior persistent and cross-screen consistent.

### In scope
- Introduce `UiPolicyRepository`.
- Persist category visibility/date-display related policies.
- Implement `LocationDisplayPolicy` (`COMPACT`, `STANDARD`, `FULL`).
- Close existing UI persistence TODOs.

### Out of scope
- Insight model redesign.

### Owned modules
- map/timeline/categories policy handling
- shared UI policy storage and formatter paths

### Exit gate
- Policy values persist across restart and render consistently across screens.

---

## Phase 5 - Insight Model Modernization
### Objective
Move to canonical, explainable insight outputs.

### In scope
- Introduce canonical insight contracts:
- `InsightFact`, `InsightDistribution`, `InsightConfidence`, `InsightProvenance`, `InsightAction`.
- Introduce `InsightEngine`.
- Adapt insight screens to canonical contracts.

### Out of scope
- Smart tracking workout subsystem.

### Owned modules
- analytics/insight use cases
- insight viewmodels and adapters

### Exit gate
- Insight cards show confidence, timeframe, and provenance.

---

## Phase 6 - Hotspot Decomposition and Hardening
### Objective
Improve maintainability and performance predictability.

### In scope
- Split oversized classes by role.
- Start SharedPreferences -> DataStore migration with compatibility path.
- Apply query-keyed caches and remove repeated heavy VM computations.
- Align export/privacy behavior with configured settings.

### Out of scope
- Navigation/product expansion.

### Owned modules
- `LocationTrackingService`, `AppStateManager`, `PlaceDetectionUseCases`, `PreferencesRepositoryImpl`, heavy screens/viewmodels

### Exit gate
- Hotspot decomposition targets reached.
- DataStore migration tests pass.

---

## Phase 7 - Navigation and UX Structural Alignment
### Objective
Align IA/routes with actual task model and reduce screen-placement drift.

### In scope
- Normalize primary/secondary route semantics.
- Remove route duplication and destination drift.
- Stabilize cross-screen date/place context behavior.

### Out of scope
- Full social feature set.

### Owned modules
- navigation destinations/host
- main shell navigation behavior
- screen entry path cleanup

### Exit gate
- Navigation map is 1:1 consistent with implemented routes.
- Top user flows reachable in <=2 taps.

---

## Phase 8 - Smart Tracking Foundation (Running/Walking)
### Objective
Add workout-grade tracking without destabilizing life-tracking core.

### In scope
- Introduce `SmartTrackingEngine` state machine.
- Add workout domain/storage contracts:
- `WorkoutSession`, `WorkoutPoint`, `Split`, `Lap`.
- Implement adaptive sampling and live metrics stream.

### Out of scope
- Full social/challenge ecosystem.

### Owned modules
- smart tracking domain/data/presentation boundaries
- session lifecycle and restart recovery paths

### Exit gate
- Workout flow reliability passes movement/restart stress scenarios.

---

## Phase 9 - Advanced Social/Competitive Extensions (Optional)
### Objective
Layer optional value-add features after core stability.

### In scope
- Segment comparisons and challenges.
- Post/share workflow (`ActivityPost`, `SharePrivacyConfig`).
- Privacy-first sharing controls and hidden start/end zone rules.

### Out of scope
- Core lifecycle refactor (must already be complete).

### Owned modules
- social/post layer
- segment/challenge layer

### Exit gate
- Privacy and integrity tests pass for all share surfaces.

## 5) Locked Interface Set (Progressive Introduction)
1. `StateWriteGateway`
2. `TrackingOrchestrator`
3. `PlaceDetectionScheduler`
4. `UiPolicyRepository`
5. `LocationDisplayPolicy`
6. `InsightEngine` + canonical insight types
7. `SmartTrackingEngine` + workout types
8. `ActivityPost` + `SharePrivacyConfig`

## 6) Verification Matrix (Phase Gates)
1. Lifecycle reliability tests.
2. Single-writer state authority tests.
3. Worker idempotency and uniqueness tests.
4. Timeline integrity tests.
5. Policy persistence and cross-screen consistency tests.
6. Insight contract tests (confidence/provenance).
7. Performance tests under high data volume.
8. Migration tests for preferences storage.
9. Smart tracking stress tests (movement + restart).
10. Privacy/share enforcement tests.

## 7) Rollback and Release Policy
- Every phase lands behind controlled toggles when possible.
- Maintain rollback path to previous stable behavior until next phase gate is green.
- Do not remove legacy paths until parity and reliability criteria pass.

## 8) Delivery and Ownership Template (Per Phase)
- Owner:
- Scope commit range:
- Flags introduced:
- Risks:
- Test evidence:
- Rollback path:
- Gate status: `blocked` | `ready` | `passed`

Execution companion:
- Use `08_PHASE_WORK_BREAKDOWN.md` for sprint-level slice planning and workload assignment under each phase.
- Use `10_PIPELINE_VERIFICATION_MATRIX.md` for gate pass criteria.
- Use `11_GAP_TO_PHASE_TRACEABILITY.md` for ownership and closure mapping.
- Use `12_DEBUGGING_AND_OBSERVABILITY_PLAYBOOK.md` for incident/debug standards.

## 9) Assumptions
1. Existing stack is retained (Compose/Hilt/Room/WorkManager).
2. Stability and correctness are prioritized before expansion features.
3. Phases are sequential; overlapping ownership is disallowed.
4. Smart tracking and social expansion are gated behind core stabilization.
