# Voyager Restart Architecture and Design (Code-Truth)

## 1. Executive Snapshot
Voyager is a large Android/Kotlin app (Compose + Hilt + Room + WorkManager + Foreground Service) with strong feature breadth but weak architectural boundaries in critical runtime paths.

Current system shape:
- Primary runtime loop: `LocationTrackingService` -> `SmartDataProcessor` -> repositories/`CurrentStateRepository` -> `AppStateManager` + DB + events.
- Parallel background loop: `WorkerManagementUseCases` -> `WorkManagerHelper` -> `PlaceDetectionWorker` (or fallback worker) -> place detection/use cases.
- State is represented in multiple forms simultaneously:
- `CurrentState` in DB (`current_state` table)
- in-memory `AppStateManager` state flow
- service status flow in `LocationServiceManager`
- event stream in `StateEventDispatcher`

This multi-authority design is the core architectural risk.

## 2. Runtime Entrypoints and Lifecycle
### 2.1 Process/App startup
- `VoyagerApplication`:
- Initializes migrations (`DataMigrationHelper`), state bootstrap, `StateSynchronizer`, `DataFlowOrchestrator`, WorkManager verification.
- Custom `Configuration.Provider` supplies `HiltWorkerFactory`.

### 2.2 UI startup
- `MainActivity`:
- Handles permissions and launches Compose app.
- Calls `WorkerManagementUseCases.initializeBackgroundWorkers()` during startup.
- Starts location tracking when permission state transitions to fully granted.

### 2.3 Background services and receivers
- `LocationTrackingService`: foreground location collection + processing + event/state updates.
- `BootReceiver`: restarts tracking from shared preference flag.
- `GeofenceReceiver`: handles entry/exit events and visit/state updates.
- WorkManager workers for place detection and summaries.

## 3. Layer Map (As Implemented)
### 3.1 Presentation
- Compose screens and ViewModels under `presentation/screen/*`.
- ViewModels call use cases and repositories directly in some places.
- Some screens use `EntryPointAccessors` directly, bypassing normal DI flow.

### 3.2 Domain
- Large model surface (`Place`, `Visit`, analytics, reviews, settings).
- Use cases include:
- Tracking/location orchestration support
- place detection and enrichment
- analytics and timeline generation
- worker management

### 3.3 Data
- Room database (`VoyagerDatabase`, v3) with encrypted SQLCipher backend.
- Repository implementations map entities <-> domain models.
- Services, workers, receivers, migration helpers.

### 3.4 Cross-cutting infra
- `AppStateManager`, `StateSynchronizer`, `StateEventDispatcher`.
- `ErrorHandler`, `WorkManagerHelper`, `LocationServiceManager`.

## 4. Persistence Architecture
### 4.1 Storage
- Room entities include locations, places, visits, geofences, current state, geocoding cache, and review-system entities.
- `current_state` table is singleton-style (`id=1`) with FK references to places/visits.

### 4.2 Migration model
- Schema migrations exist (1->2, 2->3).
- App-level data migration helper also runs runtime migrations via flows.

### 4.3 Key design issue
- Runtime migration helper uses `collect {}` on flows in migration/validation paths, which can run indefinitely and is not bounded for one-shot migration semantics.

## 5. Background Execution Architecture
### 5.1 Foreground tracking
- `LocationTrackingService` is very large and handles:
- notification lifecycle
- permission handling
- location update filtering/logic
- tracking state synchronization
- work scheduling triggers
- direct entry-point retrieval fallback paths

### 5.2 Worker architecture
- Primary worker: `PlaceDetectionWorker` (`@HiltWorker`).
- Fallback worker: `FallbackPlaceDetectionWorker` manually resolves dependencies through `EntryPointAccessors`.
- `WorkManagerHelper` adds verification/retry/monitoring/fallback logic around enqueue.

### 5.3 Architectural issue
- Worker scheduling and fallback logic is spread across:
- `MainActivity`
- `WorkerManagementUseCases`
- `DashboardViewModel`
- `LocationTrackingService`
- `WorkManagerHelper`

## 6. State Architecture
### 6.1 State representations
- DB `CurrentState` (persistent truth candidate)
- `AppStateManager` in-memory state (runtime truth candidate)
- `LocationServiceManager.isServiceRunning` flow
- Event bus (`StateEventDispatcher`)

### 6.2 Synchronization
- `StateSynchronizer` subscribes to events and app state; also writes back to repositories.
- `CurrentStateRepositoryImpl` updates `AppStateManager` first, then DB.
- Service and manager also update state directly.

### 6.3 Resulting risk
- Feedback loops and double-writes are likely:
- app state -> synchronizer -> repository -> app state
- service -> app state + repository + event dispatcher
- manager -> app state + repository

## 7. External Integration Architecture
- Reverse geocoding strategy:
- cache -> Android Geocoder -> Nominatim fallback.
- Place detail enrichment:
- Overpass API primary for business names -> Nominatim fallback.

Strengths:
- Good fallback chain and cache use.

Observed issue:
- Kotlin/Java nullability warnings in `NominatimGeocodingService` indicate correctness debt.

## 8. Security and Privacy Architecture
- SQLCipher passphrase from `SecurityUtils`.
- Local-first storage model.
- Sensitive runtime behavior depends on permission gates in activity/service.

## 9. Architecture Health Assessment
### 9.1 Strengths
- Rich functionality and domain coverage.
- Modern Android stack and dependency management.
- Persistent state + event infrastructure exists.
- Background execution mechanisms are robust in intent.

### 9.2 Structural liabilities
- Multiple competing state authorities.
- God classes (`LocationTrackingService`, `AppStateManager`, `PlaceDetectionUseCases`, `PreferencesRepositoryImpl`).
- Cross-layer leakage (ViewModels and services doing infra orchestration).
- Extensive manual fallback wiring (`EntryPointAccessors`) signals DI boundary erosion.
- High warning density and weak automated test reliability.

## 10. Target Architecture (Incremental Refactor, Preserve Features)
### 10.1 Architectural principles
- Single writer boundary for state mutations.
- One orchestration boundary per runtime concern.
- Reduce side-effect fan-out from any one component.
- Keep all features, but normalize execution paths.

### 10.2 Target runtime modules
1. `TrackingOrchestrator`
- Owns start/stop/pause tracking lifecycle.
- Only component allowed to command service + tracking state transition.

2. `StateWriteGateway`
- Single gateway for state mutation (`tracking`, `currentPlace`, `dailyStats`).
- Internally coordinates in-memory + DB updates atomically with explicit policy.

3. `PlaceDetectionScheduler`
- Unified API for one-time/periodic/fallback scheduling.
- Hide WorkManager internals from ViewModels/services.

4. `LocationIngestionPipeline`
- Validation/filtering -> persistence -> proximity/visit handling -> analytics trigger.
- Split out of monolithic service and processor for testability.

5. `EventContractLayer`
- Strict producer/consumer matrix.
- No component both emits and mutates for same state transition without policy checks.

### 10.3 State authority policy
- Persistent state of record: DB `current_state`.
- In-memory state cache: derived and synchronized via gateway.
- Event stream: notification mechanism, not state authority.

### 10.4 Public interface additions (target)
- `interface TrackingOrchestrator`
- `interface StateWriteGateway`
- `interface PlaceDetectionScheduler`
- `data class StateTransitionPolicy`
- `sealed class SchedulerResult`
- `sealed class StateMutationResult`

## 11. Phased Migration Plan
### Phase 0: Stabilize
- Fix build warnings/errors that block trust (test compile, nullability hotspots).
- Add architecture probes/metrics around state transitions and worker enqueue outcomes.

### Phase 1: Decouple state writes
- Introduce `StateWriteGateway` and route all state writes through it.
- Stop direct mixed writes from service/manager/synchronizer.

### Phase 2: Decouple runtime orchestration
- Introduce `TrackingOrchestrator` and move lifecycle logic out of activity/service manager.
- Consolidate worker enqueue paths under `PlaceDetectionScheduler`.

### Phase 3: Shrink god classes
- Split `LocationTrackingService` into command adapter + ingestion pipeline delegates.
- Split `PlaceDetectionUseCases` into detection, classification, review orchestration components.
- Split settings persistence from settings policy/profile logic.

### Phase 4: Hardening
- Restore test baseline (unit + integration slices).
- Enforce event/state authority constraints with tests and lint checks.

## 12. Constraints and Compatibility
- Preserve all existing features and screens.
- Keep current Room schema compatible during phased rollout.
- Keep existing background behaviors (detection + summaries) active while refactoring internals.
- Avoid destructive migrations until functional parity tests pass.

## 13. Risks and Mitigations
1. Risk: State regression during authority consolidation.
- Mitigation: dual-write validation phase with diff logs and rollback switches.

2. Risk: Worker behavior changes under scheduler unification.
- Mitigation: keep existing worker request contracts and output data keys stable first.

3. Risk: Service lifecycle regressions.
- Mitigation: smoke tests for start/stop/pause/permission edge transitions.

4. Risk: Feature parity drift in large refactor.
- Mitigation: feature inventory matrix tied to migration phases.
