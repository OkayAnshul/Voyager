# Voyager Wiring and Function Mapping (Code-Truth)

## 1. Wiring Index
This document maps runtime triggers to concrete handlers and side effects.

Legend:
- `T` trigger
- `H` handler
- `W` write side effect
- `E` event emission

## 2. End-to-End Runtime Flows

## 2.1 Tracking start flow
1. T: Permission state reaches full grant in `MainActivity`.
2. H: `LocationUseCases.startLocationTracking()`.
3. H: `LocationServiceManager.startLocationTracking()`.
4. W: `AppStateManager.updateTrackingStatus(true)`.
5. H: start foreground `LocationTrackingService` via intent.
6. H: service `ACTION_START_TRACKING` -> `startLocationTracking()`.
7. W: request fused location updates; set service running state.
8. W/E: service may update app state directly and/or dispatch tracking event fallback.
9. W: `CurrentStateRepository.updateTrackingStatus(true, startTime)` called in service/manager sync paths.

## 2.2 Location ingestion flow
1. T: `LocationCallback.onLocationResult` in `LocationTrackingService`.
2. H: `saveLocation(...)` in service.
3. H: `SmartDataProcessor.processNewLocation(location)`.
4. W: location validation/store (`LocationRepository.insertLocation`).
5. W: current-state timestamp update.
6. H: proximity + visit management.
7. W: place/visit updates via repository and app state manager.
8. W: daily stats update.
9. E: location events for real-time consumers.

## 2.3 Place detection worker flow
1. T: worker initialization from startup or manual trigger.
2. H: `WorkerManagementUseCases.initializeBackgroundWorkers()` or `DashboardViewModel.triggerPlaceDetection()`.
3. H: `WorkManagerHelper.enqueuePlaceDetectionWork(...)`.
4. H: `PlaceDetectionWorker.doWork()`.
5. H: `PlaceDetectionUseCases.detectNewPlaces()`.
6. W: `PlaceRepository.insertPlace(...)` for new places.
7. W: visit creation and review handling.
8. H/W: `PlaceUseCases.createGeofenceForPlace(...)` for detected places.

Fallback path:
- on enqueue/worker failure -> `FallbackPlaceDetectionWorker` using `EntryPointAccessors`.

## 2.4 Geofence transition flow
1. T: geofence enter/exit broadcast in `GeofenceReceiver`.
2. H: parse triggered place IDs.
3. H/W (enter): `VisitRepository.startVisit(...)` + `AppStateManager.updateCurrentPlace(...)`.
4. H/W (exit): complete active visit + clear current place in app state.
5. H: enqueue `GeofenceTransitionWorker` for secondary processing.

## 2.5 Boot restore flow
1. T: boot/package replaced broadcast.
2. H: `BootReceiver.onReceive(...)`.
3. H: reads `location_tracking_enabled` preference.
4. H: `LocationServiceManager.startLocationTracking()` if previously enabled.

## 3. Screen -> ViewModel -> Domain Wiring

| Screen | ViewModel | Primary Calls | Data/Effects |
|---|---|---|---|
| Dashboard | `DashboardViewModel` | `LocationUseCases`, `AnalyticsUseCases`, `PlaceDetectionUseCases`, `WorkManagerHelper` | Tracking status, counts, manual detection trigger |
| Map | `MapViewModel` | `LocationUseCases`, `PlaceUseCases`, `PlaceRepository`, `VisitRepository` | Date-filtered locations/places, place selection |
| Timeline | `TimelineViewModel` | `VisitRepository`, `PlaceRepository`, `LocationRepository`, `GenerateTimelineSegmentsUseCase`, `AnalyticsUseCases` | Timeline entries/segments |
| Categories | `CategoriesViewModel` | `PreferencesRepository`, `PlaceRepository` | Category visibility state (currently partially in-memory) |
| Settings | `SettingsViewModel` | `PreferencesRepository`, worker/scheduler-related use cases | runtime config writes |
| Place Review | `PlaceReviewViewModel` | review repositories/use cases | review workflow |

## 4. Repository and Storage Mapping

| Domain Repository | Implementation | Primary DAO(s) |
|---|---|---|
| `LocationRepository` | `LocationRepositoryImpl` | `LocationDao` |
| `PlaceRepository` | `PlaceRepositoryImpl` | `PlaceDao`, `VisitDao` |
| `VisitRepository` | `VisitRepositoryImpl` | `VisitDao`, `PlaceDao` |
| `CurrentStateRepository` | `CurrentStateRepositoryImpl` | `CurrentStateDao`, `PlaceDao`, `VisitDao` |
| `PreferencesRepository` | `PreferencesRepositoryImpl` | SharedPreferences |
| `GeocodingRepository` | `GeocodingRepositoryImpl` | `GeocodingCacheDao` + geocoding services |

## 5. State Write Authority Mapping (As-Is)

| State Concern | Writers Seen | Risk |
|---|---|---|
| Tracking active/start time | `LocationServiceManager`, `LocationTrackingService`, `CurrentStateRepositoryImpl`, `StateSynchronizer`, `AppStateManager` | multi-writer race risk |
| Current place/visit | `SmartDataProcessor`, `GeofenceReceiver`, `CurrentStateRepositoryImpl`, `StateSynchronizer`, `AppStateManager` | conflicting transitions |
| Daily stats | `SmartDataProcessor`, `CurrentStateRepositoryImpl`, `AppStateManager` | duplication potential |
| Service running status | service static flag + manager flow + prefs | split authority |

## 6. Event Wiring Map

Producer candidates:
- `LocationTrackingService`
- `AppStateManager`

Consumer candidates:
- `DashboardViewModel`
- `StateSynchronizer`
- any registered listener via `StateEventDispatcher`

Event classes:
- `TrackingEvent`
- `PlaceEvent`
- `VisitEvent`
- `LocationEvent`

Observed pattern:
- events are both reactive notifications and indirect synchronization triggers, increasing coupling.

## 7. DI Wiring Map

Hilt modules:
- `DatabaseModule`: DB + DAOs
- `RepositoryModule`: repository bindings
- `UseCasesModule`: use-case providers
- `OrchestratorModule`: data orchestrator + processor
- `StateModule`: event dispatcher + app state manager + synchronizer
- `NetworkModule`: geocoder/overpass/http clients
- `ValidationModule`, `UtilsModule`, `LocationModule`

Manual entry-point usage:
- `StateEntryPoint` used by fallback worker and service fallback paths.
- Some screens use `EntryPointAccessors` for shared state/developer mode entry points.

## 8. Function Mapping: Critical Use Cases

## 8.1 `PlaceDetectionUseCases.detectNewPlaces`
Input:
- preferences + recent locations.

Pipeline:
1. Load recent locations with cap.
2. Quality filter using preference thresholds.
3. Cluster locations.
4. Check duplicates/overlap against existing places.
5. Create `Place` (UNKNOWN by default category policy) + enrich details.
6. Persist place.
7. Create visits and review records.

Outputs:
- list of created places, side effects on geofence/visit/review paths.

## 8.2 `SmartDataProcessor.processNewLocation`
Input:
- one location.

Pipeline:
1. ensure current state initialized.
2. validate/store location.
3. update last location time.
4. run proximity + dwell/visit transitions.
5. update stats and triggers.

Outputs:
- persisted location + possible place/visit/state changes.

## 9. Failure and Recovery Wiring

| Failure Point | Current Recovery |
|---|---|
| WorkManager init/worker failure | verification + retries + fallback worker |
| State inconsistency | validation + recovery actions in `AppStateManager` and `StateSynchronizer` |
| Missing current state row | repository/service initialization paths create/reinitialize state |
| geocoding API failures | cache + Android geocoder + Nominatim/Overpass fallback |

Risk note:
- multiple independent recovery systems can conflict when they mutate same state.

## 10. Known Wiring Mismatches and Drift
1. Unit test fixtures do not match current domain constructors and enums.
2. Category settings persistence intended but not fully wired in categories/timeline/map paths.
3. Worker orchestration is wired from too many layers.
4. Some flow-based migration/validation paths are not one-shot bounded.

## 11. Target Wiring Rules (For Implementation)
1. All `CurrentState` writes route through a single `StateWriteGateway`.
2. Only `TrackingOrchestrator` can transition tracking lifecycle.
3. Only `PlaceDetectionScheduler` can enqueue/cancel detection workers.
4. Event bus is notification-only; no hidden corrective writes without explicit policy path.
5. UI layers cannot directly invoke infrastructure fallback wiring.

## 12. Verification Checklist for Wiring Refactor
1. Exactly one write path per state concern.
2. No screen/viewmodel directly controls WorkManager internals.
3. Service does not manually resolve unrelated dependencies except designated adapters.
4. State reconciliation jobs cannot trigger recursive mutation loops.
5. Each core trigger path has one primary orchestrator owner.
