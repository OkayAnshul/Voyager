# Voyager Phase Work Breakdown (Sprint-Ready)

This document converts `07_MASTER_PHASE_EXECUTION_PLAN.md` into execution slices.

Estimation scale:
- Effort: `S` (1-2 days), `M` (3-5 days), `L` (1-2 weeks), `XL` (2+ weeks)
- Risk: `Low`, `Medium`, `High`, `Critical`

## Phase 0 - Baseline Stabilization

## Slice 0.1 - Test baseline restoration
- Objective: make tests compile and run deterministically.
- Tasks:
- fix outdated fixtures and model construction in test code
- add missing test dependencies
- resolve obvious test-model drift
- Owned files/modules:
- `app/src/test/java/com/cosmiclaboratory/voyager/fixtures/TestDataFactory.kt`
- `app/src/test/java/com/cosmiclaboratory/voyager/domain/TimeCalculationTest.kt`
- `app/build.gradle*` test dependencies
- Effort: `M`
- Risk: `Medium`
- Dependencies: none
- Deliverables:
- passing test baseline report

## Slice 0.2 - Runtime warning cleanup
- Objective: remove high-risk compile warnings in runtime-critical paths.
- Tasks:
- geocoding nullability/type fixes
- remove dead/always-true state branches in core flow
- Owned files/modules:
- geocoding services/repositories
- state/orchestration runtime paths
- Effort: `S`
- Risk: `Medium`
- Dependencies: 0.1 preferred
- Deliverables:
- warning cleanup changelog

## Slice 0.3 - Smoke harness
- Objective: establish minimal safety checks for refactor phases.
- Tasks:
- add start/stop tracking smoke check
- add worker enqueue smoke check
- add state bootstrap smoke check
- Owned files/modules:
- integration/smoke test package
- Effort: `M`
- Risk: `Low`
- Dependencies: 0.1
- Deliverables:
- smoke test suite

## Phase 1 - State Write Authority

## Slice 1.1 - Introduce gateway contracts
- Objective: introduce and wire `StateWriteGateway` interface and baseline implementation.
- Tasks:
- define write API (tracking status, current place/visit, daily stats)
- implement transaction-safe write flow
- add adapter layer for legacy callsites
- Owned files/modules:
- state domain contracts
- `data/state/*`
- `domain/repository/CurrentState*`
- Effort: `L`
- Risk: `High`
- Dependencies: phase 0 complete
- Deliverables:
- gateway contract + implementation + migration guide

## Slice 1.2 - Migrate mutation callsites
- Objective: remove direct state writes outside gateway.
- Tasks:
- migrate service/manager/synchronizer/repository writes
- enforce mutation boundary with lint/checklist
- Owned files/modules:
- `LocationTrackingService`
- `LocationServiceManager`
- `StateSynchronizer`
- `CurrentStateRepositoryImpl`
- Effort: `L`
- Risk: `High`
- Dependencies: 1.1
- Deliverables:
- callsite migration matrix

## Phase 2 - Runtime Orchestration Boundary

## Slice 2.1 - Tracking orchestration
- Objective: centralize lifecycle transitions in `TrackingOrchestrator`.
- Tasks:
- define lifecycle commands and state model
- move start/stop/pause/resume logic from service/manager boundaries
- add idempotent transition handling
- Owned files/modules:
- lifecycle orchestration package
- `LocationTrackingService`
- `LocationServiceManager`
- Effort: `L`
- Risk: `High`
- Dependencies: phase 1
- Deliverables:
- orchestrator API + transition diagram + tests

## Slice 2.2 - Worker scheduling boundary
- Objective: centralize detection work operations in `PlaceDetectionScheduler`.
- Tasks:
- abstract enqueue/cancel/status APIs
- remove direct WorkManager references from VMs/services
- enforce unique work policy by worker type
- Owned files/modules:
- worker orchestration modules
- `WorkManagerHelper`
- detection-related VMs/services
- Effort: `M`
- Risk: `High`
- Dependencies: 2.1
- Deliverables:
- scheduler facade + policy table

## Phase 3 - Visit Integrity and Timeline Correctness

## Slice 3.1 - Transactional visit transitions
- Objective: prevent race-induced visit artifacts.
- Tasks:
- add DAO/database transaction to close/open active visit atomically
- enforce single-active-visit invariant
- Owned files/modules:
- `VisitDao`, `VisitRepositoryImpl`
- `SmartDataProcessor`
- database transaction helpers
- Effort: `M`
- Risk: `High`
- Dependencies: phase 2
- Deliverables:
- invariant test suite

## Slice 3.2 - Timeline dedupe/compaction
- Objective: eliminate duplicate same-place 0s segments.
- Tasks:
- add compaction guardrails in segment generation
- add anomaly detector for suspicious timeline rows
- Owned files/modules:
- `GenerateTimelineSegmentsUseCase`
- `TimelineViewModel`
- timeline UI model mapping
- Effort: `M`
- Risk: `Medium`
- Dependencies: 3.1
- Deliverables:
- duplicate/0s regression tests

## Phase 4 - Persistence and UI Policy Consistency

## Slice 4.1 - UiPolicyRepository
- Objective: persist and centralize UI policy state.
- Tasks:
- introduce policy contracts
- persist category visibility and display modes
- wire shared state consumers
- Owned files/modules:
- policy repository package
- map/timeline/categories VMs
- shared UI state utilities
- Effort: `M`
- Risk: `Medium`
- Dependencies: phase 3
- Deliverables:
- policy schema + persistence implementation

## Slice 4.2 - Location display normalization
- Objective: consistent address rendering across screens.
- Tasks:
- implement `LocationDisplayPolicy`
- shared formatter and fallback rules
- remove ad-hoc split/truncation patterns
- Owned files/modules:
- map/timeline/components displaying addresses
- shared formatting utils
- Effort: `S`
- Risk: `Low`
- Dependencies: 4.1
- Deliverables:
- cross-screen snapshot checks

## Phase 5 - Insight Modernization

## Slice 5.1 - Canonical insight contracts
- Objective: unify insight output schema.
- Tasks:
- define canonical insight types
- implement `InsightEngine`
- adapter layer for existing screens
- Owned files/modules:
- insight domain/usecase layer
- insight/analytics viewmodels
- Effort: `L`
- Risk: `Medium`
- Dependencies: phase 4
- Deliverables:
- insight schema spec + adapter map

## Slice 5.2 - Explainability and confidence UX
- Objective: add confidence/provenance fields to insight UI.
- Tasks:
- update cards with confidence and source window
- fallback behavior for low-confidence insights
- Owned files/modules:
- insights screens/components
- Effort: `M`
- Risk: `Low`
- Dependencies: 5.1
- Deliverables:
- UI acceptance checklist

## Phase 6 - Hotspot Decomposition and Hardening

## Slice 6.1 - Service/state/usecase decomposition
- Objective: reduce risk from oversized core classes.
- Tasks:
- split service adapter vs pipeline logic
- split app state transition/validation/recovery concerns
- split place detection clustering/enrichment/review concerns
- Owned files/modules:
- `LocationTrackingService`
- `AppStateManager`
- `PlaceDetectionUseCases`
- Effort: `XL`
- Risk: `Critical`
- Dependencies: phase 5
- Deliverables:
- decomposition map + parity report

## Slice 6.2 - Preferences migration and cleanup
- Objective: migrate from monolithic SharedPreferences-heavy flow.
- Tasks:
- progressive DataStore migration with compatibility bridge
- split preference concerns by domain
- Owned files/modules:
- `PreferencesRepositoryImpl`
- preference models/contracts
- Effort: `L`
- Risk: `High`
- Dependencies: 6.1 can run partially parallel after interface freeze
- Deliverables:
- migration test plan + migration completion report

## Slice 6.3 - Performance and privacy hardening
- Objective: improve runtime efficiency and policy compliance.
- Tasks:
- aggregate caching and repeated compute elimination
- export anonymization enforcement when enabled
- Owned files/modules:
- analytics/timeline heavy VMs/usecases
- export use case and related settings
- Effort: `M`
- Risk: `Medium`
- Dependencies: 6.2
- Deliverables:
- performance benchmark summary

## Phase 7 - Navigation and UX Structural Alignment

## Slice 7.1 - Route model normalization
- Objective: remove route placement drift and duplication.
- Tasks:
- align destination definitions with nav host behavior
- remove duplicate semantic routes and dead paths
- Owned files/modules:
- `VoyagerDestination`
- `VoyagerNavHost`
- `MainActivity` shell navigation
- Effort: `M`
- Risk: `Medium`
- Dependencies: phase 6
- Deliverables:
- route matrix (before/after)

## Slice 7.2 - Cross-screen context stabilization
- Objective: stabilize date/place cross-navigation context.
- Tasks:
- formalize selected-date and selected-place contracts
- add navigation-context consumption guards
- Owned files/modules:
- `SharedUiState`
- timeline/map interaction points
- Effort: `S`
- Risk: `Low`
- Dependencies: 7.1
- Deliverables:
- context-flow tests

## Phase 8 - Smart Tracking Foundation

## Slice 8.1 - Engine skeleton and session lifecycle
- Objective: introduce run/walk session foundation.
- Tasks:
- add `SmartTrackingEngine` state machine
- start/pause/resume/finish session lifecycle
- Owned files/modules:
- smart tracking domain/data modules
- session lifecycle orchestration
- Effort: `L`
- Risk: `High`
- Dependencies: phase 7
- Deliverables:
- session lifecycle tests

## Slice 8.2 - Workout storage and live metrics
- Objective: persist and expose workout telemetry.
- Tasks:
- add workout tables/entities/repositories
- add live metrics stream and UI adapters
- Owned files/modules:
- workout repository and storage
- track/workout UI module
- Effort: `L`
- Risk: `High`
- Dependencies: 8.1
- Deliverables:
- schema + API + ingestion tests

## Phase 9 - Advanced Social and Competitive Extensions (Optional)

## Slice 9.1 - Segment/challenge primitives
- Objective: add performance-comparison scaffolding.
- Tasks:
- segment effort model
- challenge and streak primitives
- Owned files/modules:
- segment/challenge domain modules
- Effort: `M`
- Risk: `Medium`
- Dependencies: phase 8
- Deliverables:
- deterministic scoring tests

## Slice 9.2 - Posting and privacy controls
- Objective: support shareable workout posts with strict privacy defaults.
- Tasks:
- `ActivityPost` model and composer flow
- visibility controls and hidden start/end zones
- Owned files/modules:
- post/share modules and privacy policy handlers
- Effort: `M`
- Risk: `High`
- Dependencies: 9.1
- Deliverables:
- privacy enforcement tests

## 10) Suggested Sprint Grouping
- Sprint A: Phase 0 + Phase 1.1
- Sprint B: Phase 1.2 + Phase 2
- Sprint C: Phase 3 + Phase 4
- Sprint D: Phase 5 + Phase 6.1 (start)
- Sprint E: Phase 6.1 (finish) + 6.2
- Sprint F: Phase 6.3 + Phase 7
- Sprint G: Phase 8.1 + 8.2
- Sprint H (optional): Phase 9

## 11) Tracking Template (Per Slice)
- Slice ID:
- Owner:
- Planned sprint:
- Dependencies cleared: yes/no
- Effort (`S/M/L/XL`):
- Risk (`Low/Medium/High/Critical`):
- Files/modules touched:
- Feature flags:
- Tests added/updated:
- Exit gate evidence:
- Rollback note:

Evidence linkage:
- Baseline and risk context: `09_DEEP_AUDIT_FINDINGS.md`
- Gate checklist: `10_PIPELINE_VERIFICATION_MATRIX.md`
- Gap ownership mapping: `11_GAP_TO_PHASE_TRACEABILITY.md`
- Debugging standards: `12_DEBUGGING_AND_OBSERVABILITY_PLAYBOOK.md`
