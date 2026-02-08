# Voyager Core Systems Fix Plan

Comprehensive fix plan addressing all bugs found in the full system audit.
Organized into 7 phases, ordered by dependency chain and severity.

---

## Phase 1: Visit Data Integrity (CRITICAL — Foundation for everything else)

All visit-related systems depend on correct duration storage and retrieval.
Fix these first because Phases 2-7 build on correct visit data.

### 1A: Fix Visit.duration priority order
**File:** `domain/model/Visit.kt` (lines 18-23)
**Bug:** `_duration > 0L` is checked first — a stale snapshot from a premature save
shadows the correct `exitTime`-based calculation even after the visit is completed.
**Fix:** Reorder the `when` branches:
```kotlin
val duration: Long
    get() = when {
        exitTime != null -> Duration.between(entryTime, exitTime).toMillis()
        _duration > 0L -> _duration
        else -> Duration.between(entryTime, LocalDateTime.now()).toMillis()
    }
```
**Also fix `isOngoing`:** Currently checks `exitTime == null && _duration == 0L`.
Should only check `exitTime == null` to be consistent with `isActive`.

### 1B: Fix VisitMapper.toEntity() — persist backing field, not computed property
**File:** `data/mapper/VisitMapper.kt` (line 49)
**Bug:** `duration = duration` calls the computed property `Visit.duration`, which for
active visits returns live wall-clock time. This stores a stale snapshot in the DB.
**Fix:** Change to `duration = _duration` (the backing field). The computed value
should only be persisted when `complete()` has been called.
**Note:** `_duration` must be made `internal` or a getter must be added for the mapper.

### 1C: Fix PlaceDetectionUseCases.createInitialVisits — use createWithDuration
**File:** `domain/usecase/PlaceDetectionUseCases.kt` (lines 635-651)
**Bug:** Uses raw `Visit(...)` constructor without setting `_duration`. After fix 1B,
these visits would be stored with `duration = 0L` instead of the calculated value.
**Fix:** Replace `Visit(placeId=..., entryTime=..., exitTime=...)` with
`Visit.createWithDuration(placeId, entryTime, exitTime, confidence)` which correctly
computes and stores `_duration`.

### 1D: Fix VisitDao.getVisitsBetween — include cross-boundary visits
**File:** `data/database/dao/VisitDao.kt` (line 31)
**Bug:** Query only filters on `entryTime BETWEEN start AND end`. Misses overnight
visits that started before the window but are still active during it.
**Fix:** Change query to:
```sql
SELECT * FROM visits
WHERE (entryTime BETWEEN :startTime AND :endTime)
   OR (entryTime < :startTime AND (exitTime > :startTime OR exitTime IS NULL))
ORDER BY entryTime
```

### 1E: Fix DurationCalculator usage — single source of truth
**Bug:** Duration is calculated 3 different ways across the codebase. `DurationCalculator`
exists as the intended single source but is never used.
**Fix:** Audit all `Duration.between(entryTime, exitTime).toMillis()` calls in:
- `VisitRepositoryImpl.endVisit` (line 73)
- `Visit.complete()` (line 61)
- `VisitMapper` (line 22)
Replace with `DurationCalculator.calculateDuration(entryTime, exitTime)`.

---

## Phase 2: Eliminate Duplicate Visit Creation (CRITICAL)

Two competing pipelines create visits without coordination, causing duplicates.

### 2A: Remove deprecated GeofenceTransitionService
**File:** `data/service/GeofenceTransitionService.kt`
**Bug:** Deprecated but still active. Creates visits via `visitRepository.insertVisit()`
directly — bypasses `startVisit()` transaction logic, never updates AppStateManager,
never closes prior active visits. Races with SmartDataProcessor.
**Fix:**
1. Verify `GeofenceTransitionWorker` handles all ENTER/EXIT transitions correctly
2. Remove `GeofenceTransitionService` class entirely
3. Remove its entry from `AndroidManifest.xml`
4. Remove the `GeofenceReceiver` reference to the service (if any)

### 2B: Consolidate place detection paths
**Bug:** Two parallel detection paths both write places:
- Path A: Workers -> `PlaceDetectionUseCases.detectAndSavePlaces()`
- Path B: `PlaceUseCases.detectPlacesFromLocations()` (called from SmartDataProcessor)
**Fix:**
1. Check if `PlaceUseCases.detectPlacesFromLocations()` is called anywhere
2. If so, route it through `PlaceDetectionUseCases` for consistency
3. If not, remove the duplicate method

### 2C: Fix SmartDataProcessor double state-write
**File:** `data/processor/SmartDataProcessor.kt` (lines 362-376)
**Bug:** `startVisitAtPlace()` calls `appStateManager.updateCurrentPlace()` directly
AND then calls `stateWriteGateway.updateCurrentPlace()` which internally calls
`appStateManager.updateCurrentPlace()` again. Same for `endCurrentVisit()`.
**Fix:** Remove the direct `appStateManager.updateCurrentPlace()` call at line 362.
Keep only the `stateWriteGateway` call — it is the single write authority.
Apply same fix to `endCurrentVisit()`.

---

## Phase 3: State Management Fixes (HIGH)

### 3A: Fix AnalyticsUseCases operator precedence
**File:** `domain/usecase/AnalyticsUseCases.kt` (line 98)
**Bug:** `currentState?.isLocationTrackingActive ?: false || serviceManager...`
parses as `?: (false || serviceManager...)` due to `||` having higher precedence.
If state is non-null and `isLocationTrackingActive` is `false`, the service manager
fallback is never consulted.
**Fix:** Add parentheses:
```kotlin
val isTrackingActive = (currentState?.isLocationTrackingActive ?: false) ||
        locationServiceManager.isLocationServiceRunning()
```

### 3B: Fix LocationServiceManager.stopLocationTracking — asymmetric state update
**File:** `utils/LocationServiceManager.kt` (lines 121-140)
**Bug:** `startLocationTracking()` updates state layer immediately, but
`stopLocationTracking()` only updates local StateFlow + SharedPrefs. If the service
never receives the stop intent (already dead), `CurrentState.isLocationTrackingActive`
stays `true` in the DB forever.
**Fix:** Add `syncTrackingStatusWithStateManager(false)` call in `stopLocationTracking()`,
matching the pattern used in `notifyServiceStopped()`.

### 3C: Fix StateCircuitBreaker thread safety
**File:** `data/state/StateCircuitBreaker.kt` (lines 42-51)
**Bug:** `stateChangeHistory`, `recentTrackingChanges`, `recentPlaceChanges` are
plain `mutableListOf` — not thread-safe. Concurrent coroutine access will throw
`ConcurrentModificationException`.
**Fix:** Wrap all list accesses in a `Mutex`:
```kotlin
private val mutex = Mutex()
// In shouldBlock(), recordChange(), etc:
mutex.withLock { stateChangeHistory.add(now) ... }
```

### 3D: Fix SmartDataProcessor.pendingVisit — survives process death
**File:** `data/processor/SmartDataProcessor.kt` (line 71)
**Bug:** `pendingVisit` is an in-memory `var` on a `@Singleton`. Lost on process kill.
If killed mid-dwell, dwell tracking restarts from zero even if user has been at the
same place for hours.
**Fix:** On startup, restore `pendingVisit` from current state:
```kotlin
// In init or a startup method:
private fun restorePendingVisit() {
    val currentPlace = appStateManager.appState.value?.currentPlace ?: return
    val activeVisit = visitRepository.getActiveVisitForPlace(currentPlace.placeId)
    if (activeVisit != null) {
        pendingVisit = PendingVisit(
            place = currentPlace,
            firstSeenTime = activeVisit.entryTime,
            isConfirmed = true
        )
    }
}
```
No new persistence needed — derive from existing active visit + state.

### 3E: Fix triple state-write on service start
**File:** `data/service/LocationTrackingService.kt` (lines 261-278)
**Bug:** `updateTrackingStatus` is called 3 times concurrently when service starts.
**Fix:** Consolidate to a single state update after the service has fully initialized.

---

## Phase 4: DBSCAN & Place Detection Fixes (HIGH)

### 4A: Fix DBSCAN noise flag — border points incorrectly excluded
**File:** `utils/LocationUtils.kt` (line 223 in expandCluster)
**Bug:** Points initially marked as noise are excluded from clusters via
`if (!noise[neighborIndex])`. Standard DBSCAN allows noise points to become border
points when absorbed into a cluster during expansion.
**Fix:** When adding a noise point to a cluster, clear its noise flag:
```kotlin
if (cluster.none { it == locations[neighborIndex] }) {
    noise[neighborIndex] = false  // Allow border points
    cluster.add(locations[neighborIndex])
}
```

### 4B: Fix DBSCAN O(n) list membership — use Set
**File:** `utils/LocationUtils.kt` (line 214-217 in expandCluster)
**Bug:** `newNeighbor !in neighbors` is O(n) on a MutableList. With 2000 points this
becomes O(n^3) worst case.
**Fix:** Change `neighbors` from `MutableList<Int>` to `LinkedHashSet<Int>`.
Also change `getNeighbors` return type to `MutableSet<Int>`.

### 4C: Fix post-cluster radius vs eps mismatch
**File:** `domain/usecase/PlaceDetectionUseCases.kt` (line 241)
**Bug:** DBSCAN uses `preferences.clusteringDistanceMeters` (eps = 100m) but
post-cluster verification uses `preferences.placeDetectionRadius` (150m).
This inflates confidence by pulling in points from adjacent places.
**Fix:** Use the same parameter for both:
```kotlin
LocationUtils.calculateDistance(...) <= preferences.clusteringDistanceMeters
```

### 4D: Wire PlaceCategoryScorer — places are permanently UNKNOWN
**Bug:** `PlaceCategoryScorer` is fully implemented but never called during place
creation. All detected places get category `UNKNOWN`.
**Fix:** In `PlaceDetectionUseCases.detectAndSavePlaces()`, after creating a new place,
call `PlaceCategoryScorer.scoreCategory(place, nearbyPois)` and update the place entity
before saving to DB.

### 4E: Remove improveExistingPlaces stub
**File:** `domain/usecase/PlaceDetectionUseCases.kt` (lines 377-409)
**Bug:** Fully disabled no-op. Called by workers but does nothing.
**Fix:** Remove the method body (keep as empty method or remove entirely + remove
calls from `PlaceDetectionWorker` and `FallbackPlaceDetectionWorker`).

---

## Phase 5: Timeline & Map Data Flow Fixes (MEDIUM)

### 5A: Fix GenerateTimelineSegmentsUseCase endOfDay inconsistency
**File:** `domain/usecase/GenerateTimelineSegmentsUseCase.kt` (line 48)
**Bug:** Uses `date.atTime(23, 59, 59)` while analytics uses `date.plusDays(1).atStartOfDay()`.
**Fix:** Change to `date.plusDays(1).atStartOfDay()` for consistency.
Remove unused `timeWindowMinutes` parameter.

### 5B: Fix PlaceRepositoryImpl.getPlaceWithVisits — always returns empty
**File:** `data/repository/PlaceRepositoryImpl.kt` (lines 145-151)
**Bug:** Fetches visits Flow but never collects it. Hardcoded `emptyList()`.
**Fix:**
```kotlin
override suspend fun getPlaceWithVisits(placeId: Long): PlaceWithVisits? {
    val place = placeDao.getPlaceById(placeId)?.toDomainModel() ?: return null
    val visits = visitDao.getVisitsForPlace(placeId).first().toDomainModels()
    return PlaceWithVisits(place, visits)
}
```

### 5C: Fix Map double-load race on init
**File:** `presentation/screen/map/MapViewModel.kt`
**Bug:** `loadMapData()` in `init` and `observeSharedDate()` both fire immediately,
racing to load the same data.
**Fix:** Remove the `loadMapData()` call from `init`. Let `observeSharedDate()` be the
single trigger. It fires immediately with the current date value.

### 5D: Fix Map selectedPlaceVisits — not date-filtered
**Bug:** When selecting a place on the map, all-time visits load instead of only
visits for the selected date.
**Fix:** Filter visits by the currently selected date in `selectPlace()`.

### 5E: Fix Map marker ordering — chronological, not DB insertion order
**Bug:** Numbered markers on map ordered by DB insertion, not by visit time for the day.
**Fix:** Sort places by their first visit's `entryTime` for the selected date before
assigning marker numbers.

### 5F: Fix active visit TimeRange.end frozen at construction
**Bug:** Active visit segment's end time is frozen when the segment is created.
Never updates while viewing the timeline.
**Fix:** In `TimelineScreen`, for active visit segments, show `LocalDateTime.now()`
as the end time and refresh periodically (or use a derived state).

### 5G: Fix travelTimeToNext negative duration
**Bug:** `travelTimeToNext` can be negative when `exitTime` is null.
**Fix:** Guard: `if (exitTime == null) null else Duration.between(exitTime, nextEntry)`.

---

## Phase 6: Location Pipeline Fixes (MEDIUM)

### 6A: Wire LocationValidator into save pipeline
**Bug:** `LocationValidator` is fully implemented but never called. No location
validation occurs — bad/inaccurate locations go straight to DB.
**Fix:** In `LocationTrackingService` (or `LocationRepositoryImpl.saveLocation`),
call `locationValidator.validate(location)` before persisting. Discard invalid locations.

### 6B: Fix location event dispatch order — DB write before event
**File:** `data/service/LocationTrackingService.kt` (line 413)
**Bug:** Event dispatched BEFORE DB write. Consumers that react to the event may
query the DB and not find the location yet.
**Fix:** Reorder: save to DB first, then dispatch the event.

### 6C: Fix pauseLocationTracking — doesn't remove GPS updates
**Bug:** `pauseLocationTracking()` pauses state but hardware GPS keeps running,
draining battery.
**Fix:** Call `fusedLocationClient.removeLocationUpdates(locationCallback)` in pause,
and re-register in resume.

### 6D: Fix stopLocationTracking uses startService instead of startForegroundService
**File:** `utils/LocationServiceManager.kt` (line 127)
**Bug:** On Android 8+, using `startService()` from background to send stop intent
can crash with `IllegalStateException`.
**Fix:** Use `context.startForegroundService(intent)` for the stop intent, or use
`stopService()` directly since we're stopping, not starting.

---

## Phase 7: Dead Code Removal & Cleanup (LOW)

### 7A: Remove dead code
| What | Where |
|------|-------|
| `queueStateUpdate()` / `processStateUpdateQueue()` | `AppStateManager.kt` |
| `stateChangeObservers` + `registerStateObserver()` | `AppStateManager.kt` |
| `StateEventDispatcher` SharedFlows (no consumers) | `StateEventDispatcher.kt` |
| `validateAnalyticsCalculations()` (always true) | `DataFlowOrchestrator.kt` |
| `DataFlowOrchestrator.clearEvents()` (stub) | `DataFlowOrchestrator.kt` |
| `StateSynchronizer` unused repository injections | `StateSynchronizer.kt` |
| `SharedUiState.navigationSource` (never read/set) | `SharedUiState.kt` |
| `fixFutureTimestamps()` (detects but never fixes) | `AppStateManager.kt` |
| `detectCommutePattern()` (hardcoded values) | `AnalyticsUseCases.kt` |

### 7B: Fix the one remaining TODO
**File:** `utils/NotificationHelper.kt` (line 222)
**TODO:** "Add approve/edit/dismiss action buttons when broadcast receivers are implemented"
**Fix:** Either implement the notification action buttons or remove the TODO and document
it as a future enhancement in the roadmap.

### 7C: Fix LocationServiceManager.isLocationServiceRunning side-effectful getter
**Fix:** Extract `updateServiceStatus()` out of the getter. Expose a pure read method
`val isRunning: Boolean get() = _isServiceRunning.value` and keep the health monitoring
as a separate scheduled check.

### 7D: Fix StateWriteGatewayImpl.updateLastLocationTime inconsistency
**Bug:** Does NOT update in-memory AppStateManager, unlike all other gateway methods.
**Fix:** Add `appStateManager.updateLastLocationTime(timestamp)` after the DAO write.

---

## Execution Order

```
Phase 1 (Visit integrity)     ─── foundation, fix first
    |
Phase 2 (Duplicate visits)    ─── depends on correct visit logic from Phase 1
    |
Phase 3 (State management)    ─── depends on single write path from Phase 2
    |
Phase 4 (DBSCAN/detection)    ─── independent of 1-3 but logically follows
    |
Phase 5 (Timeline/Map)        ─── depends on correct visits + detection
    |
Phase 6 (Location pipeline)   ─── independent, can parallel with Phase 5
    |
Phase 7 (Cleanup)             ─── last, after all functional fixes
```

## Verification After Each Phase

After each phase, run:
1. `./gradlew compileDebugKotlin` — must pass
2. `./gradlew testDebugUnitTest` — must pass
3. Manual smoke test on device for the affected screens
