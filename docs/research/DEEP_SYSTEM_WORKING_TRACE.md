# Voyager Deep System Working Trace

This document is a permanent, code-grounded technical walkthrough of how Voyager works.

It is written for implementation-level understanding of:
- visits
- lat/long ingestion
- batch processing
- place detection
- timeline and daily route generation
- place naming and geocoding
- OpenStreetMap behavior
- state/event consistency

## How to Read This

- `What`: the exact behavior implemented in code.
- `Why`: the engineering decision behind it.
- `Impact`: what this line/block changes in app behavior.

The request asked for significance of every line. For maintainability, this document uses **line-range granularity** over contiguous logic blocks in the critical path files. That still gives effectively line-level significance for all code that materially affects runtime behavior.

---

## 1) End-to-End Runtime Pipeline

## 1.1 Tracking bootstrap and lifecycle

### File: `data/orchestration/TrackingOrchestratorImpl.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 49-57 | Permission gate before start. | Avoid starting service in invalid permission state. | Prevents silent tracking failures and bad state. |
| 59-70 | Writes tracking active via `StateWriteGateway`. | Single write authority and event/state sync. | DB + in-memory state become consistent before service start. |
| 72-77 | Starts foreground service with start action. | Actual sensor capture begins only in service layer. | Kicks off location callbacks. |
| 78-80 | Persists `location_tracking_enabled=true`. | Boot restore uses this flag. | Tracking can auto-resume after reboot/update. |
| 93-106 | Stop path: service stop intent + state write inactive. | Symmetric lifecycle consistency. | Ensures tracking UI/state turn off when user stops. |
| 124-149 | Service callbacks (`onServiceStarted/onServiceStopped`) sync gateway state. | Recover from start/stop edges and system behavior. | Keeps state aligned with actual service status. |

### File: `data/receiver/BootReceiver.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 22-33 | Handles boot/package-replaced events with `goAsync()`. | Receiver execution can outlive `onReceive` return safely. | Prevents process kill mid-restore. |
| 34-46 | Reads persisted tracking flag and restarts via orchestrator. | Respect user’s prior tracking choice. | Automatic continuity across reboot/app update. |

---

## 1.2 Location ingestion and filtering

### File: `data/service/LocationTrackingService.kt`

#### Startup and request configuration

| Lines | What | Why | Impact |
|---|---|---|---|
| 260-341 | Service starts foreground notification, loads preferences, requests fused updates. | Android requires foreground + stable request config. | If success, callbacks start delivering raw GPS samples. |
| 300-314 | Marks service started, monitors permissions, optional motion detection start. | Runtime resilience and power-aware behavior. | Immediate observability + sleep-mode integration. |
| 344-374 | Stop path removes callbacks, updates state, cancels monitoring, stops foreground/self. | Deterministic shutdown sequence. | Prevents phantom tracking and stale updates. |

#### Per-location processing

| Lines | What | Why | Impact |
|---|---|---|---|
| 376-390 | Skip while paused; sleep-window suppression unless motion. | Battery/privacy friendly sleep behavior. | Fewer points overnight unless movement indicates relevance. |
| 392-406 | Update motion inference from speed; skip when activity says user moving/driving. | Reduce false place detections from transit. | Cleaner stationary/place data quality. |
| 412-416 | `shouldSaveLocation` gate. | Drop drift/noise before DB write. | Protects storage and downstream clustering quality. |
| 422-438 | Constructs domain `Location` with accuracy/speed/activity/semantic context. | Preserve full context for analytics and detection. | Later models can classify transit/context better. |
| 442-445 | Calls `SmartDataProcessor.processNewLocation`. | Single downstream intelligence hub. | All visit/state/stat logic runs in one path. |
| 447-448 | Dispatches location event after DB path. | Event consumers should see persisted data. | UI/event readers avoid reading stale state. |
| 450-468 | Increments counters, stationary mode update, auto-detection trigger check. | Adaptive tracking + deferred heavy processing. | Controls when place detection jobs run. |

#### Save-filter decision logic

| Lines | What | Why | Impact |
|---|---|---|---|
| 500-518 | Accuracy threshold gate (adaptive in stationary mode). | Filter low-confidence GPS noise. | Prevents bad geometry entering place logic. |
| 520-531 | Movement threshold gate. | Require meaningful displacement unless forced by time. | Cuts jitter-induced writes. |
| 533-538 | Minimum time between saves (longer in stationary mode). | Battery/perf tradeoff. | Lower write frequency when idle. |
| 540-547 | Impossible speed rejection vs user max speed. | Reject teleport-like GPS spikes. | Improves route and visit integrity. |
| 552-557 | Force-save when gap too long. | Avoid data holes. | Ensures continuity for timelines/routes. |
| 559+ | Save on significant movement or time+small movement. | Balanced precision vs data density. | Stable point stream for analytics and map path. |

#### Automatic place-detection trigger logic

| Lines | What | Why | Impact |
|---|---|---|---|
| 892-910 | Trigger by location count OR elapsed time OR first-run bootstrap rule. | Ensure place detection runs often enough. | New places appear without manual action. |
| 932-943 | Enqueue one-time detection worker via scheduler/helper. | Async off-main heavy compute. | Scalable detection execution. |
| 944-959 | If enqueue fails, fallback to direct detection call. | Robustness under WorkManager issues. | Detection still proceeds in degraded conditions. |

---

## 1.3 Smart processing, visit FSM, and daily stats

### File: `data/processor/SmartDataProcessor.kt`

#### Core orchestrated steps

| Lines | What | Why | Impact |
|---|---|---|---|
| 154-185 | Main ordered pipeline: restore pending visit, ensure state, validate/store location, update time, visit FSM, stats, triggers. | Deterministic sequencing for correctness. | Prevents racey partial updates. |
| 206-249 | State initialization with retries and validation. | Recover from null/corrupted singleton state. | App remains functional after process death or DB edge cases. |
| 273-289 | Persists location through repository. | Canonical source of location history. | Downstream analytics and clustering have source data. |
| 294-308 | Updates last location time in state (non-fatal if fails). | Freshness indicators + health checks. | UI freshness and diagnostics remain mostly accurate. |

#### Visit detection FSM and hysteresis

| Lines | What | Why | Impact |
|---|---|---|---|
| 314-330 | Loads cached places and dwell-time preference. | Minimize DB churn + configurable confirmation. | Efficient and user-tunable visit creation. |
| 333-341 | Case 1: near place, start pending dwell (no visit yet). | Avoid false positives on pass-through. | Visit only after sustained presence. |
| 344-359 | Case 2: same place, dwell accumulates, confirm after threshold. | Debounce arrivals. | Reliable visit starts. |
| 363-377 | Case 3: moved to another place while pending/confirmed. | Handle transitions and avoid double active visits. | Cleaner chain of visits. |
| 380-389 | Case 4: moved away; abandon unconfirmed or end confirmed visit. | Correct exit behavior. | Proper visit end times and no ghost presence. |
| 405-423 | Hysteresis threshold selection (entry vs exit + radius + accuracy). | Prevent ping-pong around boundaries. | Stable current-place state under noisy GPS. |

#### Visit persistence behavior

| Lines | What | Why | Impact |
|---|---|---|---|
| 355 + 445-473 | Start visit via repo then gateway current-place write. | Keep visit table and singleton state aligned. | UI sees correct active place/visit. |
| 481-517 | End visit, prune too-short visits by preference min duration, clear current place via gateway. | Avoid polluting history with micro-visits. | Better timeline quality and stats. |
| 526-567 | Daily stats recomputation: locations/day, distinct places/day, total tracked time including active visits. | Real-time summary correctness. | Dashboard/timeline analytics stay current. |

---

## 1.4 Visit repository and durability rules

### File: `data/repository/VisitRepositoryImpl.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 71-79 | `endVisit`: delete if duration < 30s else persist end+duration. | Hard floor against noise visits. | Short dwell artifacts removed. |
| 93-114 | `startVisit`: transactionally closes all active visits, then inserts new active visit. | Enforce single active visit invariant. | Prevents overlapping active visits across places. |
| 141-154 | Optional overlap-aware insert method. | Defensive dedupe path. | Avoid duplicate visits in backfill/import scenarios. |

### File: `data/database/dao/VisitDao.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 31-37 | `getVisitsBetween` includes overlap with period start (`entry<start && exit>start`). | Correctly includes overnight/ongoing visits. | Daily analytics/timeline not truncated at midnight. |
| 87-93 | `getOverlappingVisitsForPlace`. | Duplicate-prevention primitive. | Supports clean visit history. |

### File: `domain/model/Visit.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 17-23 | `duration` precedence: explicit exit > stored > live now. | Correct semantics for completed vs active. | Accurate duration everywhere. |
| 58-69 | `complete(exitTime)` computes immutable completed record. | Avoid stale/implicit duration errors. | Stable persisted visit durations. |
| 75-79 | `getCurrentDuration(now)` for active visits. | Real-time display and analytics. | Ongoing visit timers stay correct. |

---

## 1.5 Batch place detection internals

### File: `domain/usecase/PlaceDetectionUseCases.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 121-123 | Pulls recent locations with cap. | Memory safety and bounded processing. | Works on low-end devices. |
| 138-146 | Batch quality filtering. | Stream-like memory control and traceability. | Better throughput on large histories. |
| 160-165 | Optional cap to 2000 points before clustering. | DBSCAN cost control. | Keeps worker latency bounded. |
| 167-172 | Runs DBSCAN with preference params. | Tunable spatial clustering. | Directly controls place granularity. |
| 201-232 | Duplicate checks: nearest-distance and overlap-ratio. | Prevent duplicate place creation for same venue/area. | Cleaner place set. |
| 272-274 | New places forced to `UNKNOWN` category. | User-driven category confirmation policy. | Reduces wrong auto-categorization. |
| 303-307 | Enriches place with geocoding before insert (best effort). | Improves initial UX quality. | Better names/addresses on first appearance. |
| 320-337 | Creates initial visits from cluster sessions, then review decisions. | Backfills historical significance. | New place appears with visit history immediately. |

### File: `data/worker/PlaceDetectionWorker.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 24-31 | Worker entry detects places. | Background heavy task boundary. | Detection independent from UI/service lifecycle. |
| 31-47 | Auto-creates geofences for newly detected places. | Immediate transition notifications/analytics path. | Place events become available without manual setup. |
| 91-106 | Periodic work constraints + backoff. | Battery and retry policy controls. | Detection cadence adapts to device conditions. |
| 109-125 | One-time work constraints. | Controlled ad-hoc detection runs. | Fast response to trigger events. |

### File: `utils/WorkManagerHelper.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 88-146 | Robust enqueue with retries and unique-work policy for one-time runs. | Avoid duplicate jobs and transient initialization failures. | More reliable detection scheduling. |
| 151-180 | Fallback non-Hilt worker enqueue. | Survive DI/worker factory failures. | Degraded but functional detection path. |

### File: `data/orchestration/PlaceDetectionSchedulerImpl.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 27-35 | One-time and periodic scheduling wrappers. | Central scheduling abstraction. | Easier orchestration and consistent policy. |
| 42-53 | Reinitialize based on preferences enable flag. | Keep worker topology synced to settings. | Avoid wasted background work when disabled. |

---

## 1.6 Timeline generation and daily route calculation

### File: `domain/usecase/GenerateTimelineSegmentsUseCase.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 43-58 | Movement timeline delegates to movement segmentation use case, with today-end cap at now. | Prevent future-time artifacts. | Today timeline ends at real current time. |
| 74-80 | Legacy timeline uses configurable grouping window and date window. | User-tunable segment merging. | Coarser/finer timeline grouping. |
| 93-129 | Builds per-group segment with distance/time to next. | Adds route-like context in timeline cards. | Better movement comprehension. |
| 142-197 | Fills untracked gaps >= 5 min. | Explicit data absence visibility. | Users can see where tracking had holes. |
| 202-231 | Grouping rule: same place + gap <= window. | Merge fragmented repeat checks-ins. | Cleaner daily narrative. |

### File: `domain/usecase/MovementSegmentationUseCase.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 61-64 | Loads and sorts locations and visits in range. | Deterministic temporal processing. | Stable segment order. |
| 73-94 | Uses tracking start/current session for pre-tracking classification or full-day not-tracking fallback. | Distinguish “tracking off” vs “tracking on but no points.” | More truthful gap semantics. |
| 97-109 | Creates confirmed visit segments from visit records. | Visit table is source of truth for known places. | High-confidence place segments. |
| 111-149 | Classifies inter-visit gaps with raw points. | Build complete movement narrative. | Adds transit/transient/untracked detail. |
| 156-172 | Empty-point gap => `UNTRACKED_WHILE_TRACKING`. | Data loss/coverage explicit. | Improves diagnostics and interpretation. |
| 176-177 | Stationary threshold derives from preference with floor. | User-tunable but safe lower bound. | Stop detection adapts to user profile. |
| 262-277 | Movement decision based on speed from GPS or distance/time fallback. | Robust classification when speed missing. | Fewer misclassified runs. |
| 292-337 | Converts runs to `TRANSIT` and `TRANSIENT_STOP` segments with metadata. | Rich UI and analytics signal. | Shows speed, distance, route traces, pause windows. |

### File: `presentation/screen/timeline/TimelineViewModel.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 141-197 | Loads day data and builds both legacy and movement timelines off-thread. | Responsiveness and backward compatibility. | UI can render advanced or fallback timeline mode. |
| 162-175 | Caches day analytics by date+category hash with timeout. | Reduce repeated expensive calculations. | Faster refresh and smoother UI. |
| 203-213 | Applies category visibility filters and commits final state. | User control over noise. | Timeline output can hide categories by preference. |

---

## 1.7 Daily route and map rendering

### File: `data/database/dao/PlaceDao.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 76-82 | Daily places query groups by place, includes overlap visits, orders by first visit time ASC. | Correct date-specific chronology and midnight overlap handling. | Intended source order for route timeline path. |

### File: `presentation/screen/map/MapViewModel.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 152-155 | Loads all day locations by timestamp filter. | Route/raw path tied to selected day. | Date-specific map context. |
| 157 | Loads `getPlacesVisitedOnDate(...).reversed()`. | Current implementation choice (newest-first lists). | Affects route polyline order and marker numbering. |
| 167-175 | Stores places and map center in UI state. | Single source for map composable rendering. | Controls route line, markers, and camera defaults. |

### File: `presentation/components/OpenStreetMapView.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 44-73 | OSMDroid config: persistent prefs, UA, cache dir, tile threads. | OSM compliance + performance/offline cache behavior. | Stable map tile loading and fewer repeated downloads. |
| 88 | Uses MAPNIK tile source. | Standard OSM tiles. | Map visual baseline. |
| 137-145 | Clears overlays each update and re-adds my-location overlay. | Avoid duplicate overlays and stale draws. | Accurate map rendering per state tick. |
| 152-155 | If `showRoute`, draws place-route polyline from incoming place order. | Visualize day progression across places. | Route is straight-line sequence of place centroids. |
| 217-245 | Route renderer uses incoming order directly; no resort and no pathfinding. | Keep chronology externalized and lightweight. | Not turn-by-turn route; pure connection line. |
| 157-160 + 252-287 | Marker numbering based on list index and optional numbered icon. | Tie map pins to timeline order mental model. | Wrong list order => wrong timeline numbering. |

### File: `presentation/screen/map/MapScreen.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 176-195 | Passes `uiState.visiblePlaces` + day locations into `OpenStreetMapView`. | Single integration point for map rendering. | Category filters and ordering flow directly to map. |

---

## 1.8 Place naming and geocoding

### File: `domain/usecase/EnrichPlaceWithDetailsUseCase.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 43-47 | If user renamed place, skip enrichment. | Respect explicit user intent. | User label remains authoritative. |
| 50-60 | Pull reverse address + place details from geocoding repository. | Separate address and POI-name channels. | Better naming quality potential. |
| 62-70 | Maps OSM type/value to suggested category but does not auto-apply. | Prevent wrong automatic category changes. | Category remains user-controlled. |
| 113-150 | Name priority chain: user name -> POI name -> street -> locality -> coordinates. | Maximize human-readable names with safe fallback. | Most places avoid generic labels. |

### File: `data/repository/GeocodingRepositoryImpl.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 56-67 | Rounded-coordinate cache lookup and TTL validation. | High hit rate for revisited places. | Faster geocoding and fewer network calls. |
| 75-89 | Android Geocoder first fallback tier. | Fast/free, potentially offline-capable. | Good low-latency address retrieval. |
| 93-106 | Nominatim second tier if Android geocoder fails. | Better coverage/quality in many cases. | Improves address quality when available. |
| 119-135 | Overpass-first POI details (business names/types). | Better POI names than reverse-only APIs. | Higher quality place naming. |
| 137-149 | Nominatim details fallback. | Secondary source when Overpass misses/fails. | Keeps naming pipeline resilient. |

### File: `data/api/NominatimGeocodingService.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 35 + 43 | 1 req/sec rate limiter. | OSM policy compliance. | Prevents API abuse and potential blocking. |
| 45-49 + 110-114 | Adds user-agent header. | Required by Nominatim usage policy. | Requests are policy-compliant. |
| 80-93 | Extracts and normalizes address fields from JSON. | Structured address persistence for UI/model use. | Better place subtitle/context displays. |
| 137-154 | Optional name/type extraction for place details. | Enrichment signal for naming and suggestions. | POI-aware naming when available. |

### File: `data/api/OverpassApiService.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 98-106 | Posts Overpass QL with timeout. | Controlled latency and parser scope. | Prevent long hangs in naming flow. |
| 123-129 | Chooses nearest valid named POI. | Most likely relevant business at coordinate. | Better user-facing place names. |
| 157-165 | Query targets nearby named nodes/ways. | Capture business-like entities. | Name enrichment coverage depends on OSM tagging density. |

### File: `data/api/AndroidGeocoderService.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 33-37 | Checks geocoder availability at runtime. | Not all devices/services expose backend reliably. | Safe null fallback path. |
| 49-67 | Reverse geocode and structured conversion to `AddressResult`. | Standardized address model for cache/repo. | Unified behavior with Nominatim outputs. |
| 73-79 | No place-details support. | Android Geocoder lacks rich POI metadata. | Business naming depends on Overpass/Nominatim details. |

---

## 1.9 State write authority and event propagation

### File: `data/state/StateWriteGatewayImpl.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 24-33 | Defines gateway as single write authority contract. | Eliminate multi-writer race conditions. | State transitions are serialized and predictable. |
| 47 | `Mutex` for serialized writes. | Prevent concurrent state mutations. | Avoid split-brain in DB vs in-memory state. |
| 66-105 | Tracking status write: DB transaction -> app state -> tracking event. | Ordered consistency pipeline. | Readers get coherent state + events. |
| 108-170 | Current place write validates refs, writes DB, updates app state, emits place-entered event. | Referential integrity + observability. | Reduces orphan links and inconsistent current-place UI. |
| 172-197 | Daily stats write through gateway. | Keep summaries aligned with singleton state. | Dashboard/timeline counters remain consistent. |
| 199-210 | Last-location-time write (state freshness). | Health/freshness checks depend on this. | Better stale-data detection behavior. |

### File: `data/event/StateEventDispatcher.kt`

| Lines | What | Why | Impact |
|---|---|---|---|
| 28-38 | Separate shared flows for location/place/visit/tracking. | Typed event channels and selective subscribers. | UI/modules can subscribe narrowly. |
| 46-113 | Dispatch methods emit and notify listeners under mutex. | Atomic per-event emission + callback fanout. | Reduces interleaving anomalies in listeners. |
| 118-129 | Listener registration via thread-safe map. | Safe dynamic listener lifecycle. | Prevents concurrent modification failures. |

---

## 2) Data Model Significance

### `LocationEntity`
- `latitude/longitude/timestamp/accuracy` are the core track signal.
- `speed`, `userActivity`, `activityConfidence`, `semanticContext` influence segmentation and quality filtering.
- Indexes on timestamp and activity/context make day and behavior queries fast.

### `VisitEntity`
- `entryTime/exitTime/duration` form authoritative stay intervals.
- `exitTime == null` means active visit.
- FK to `PlaceEntity` enforces visit-place integrity.

### `PlaceEntity`
- `latitude/longitude/radius` define proximity identity.
- `name`, `isUserRenamed`, address fields, OSM suggestion fields drive naming UX.
- `visitCount/totalTimeSpent/lastVisit` support ranking and analytics.

### `CurrentStateEntity`
- Singleton `id=1` holds live app truth for tracking status and current place.
- `pendingVisit...` fields persist unconfirmed dwell across process death.
- `stateVersion` supports change detection semantics.

---

## 3) Decision Log: Why these design choices matter

1. **Dwell confirmation before visit creation**
- Decision: pending visit + min dwell threshold.
- Sense of impact: reduces false place arrivals caused by drive-by or brief GPS overlap.

2. **Hysteresis entry vs exit thresholds**
- Decision: harder to enter than to remain (or vice versa via threshold logic with context).
- Sense of impact: reduces boundary oscillation and frequent enter/exit flapping.

3. **Single state writer (gateway) + mutex**
- Decision: no direct arbitrary writes to `current_state` from all modules.
- Sense of impact: prevents race-induced UI inconsistencies and impossible states.

4. **Worker-based place detection with fallback**
- Decision: background heavy compute with robust enqueue retry and degraded fallback.
- Sense of impact: detection remains available even under WorkManager/DI failures.

5. **Movement-segmentation truth model**
- Decision: infer transit/transient/untracked around confirmed visits.
- Sense of impact: timeline becomes a narrative of the day instead of sparse visit list only.

6. **Geocoding multi-tier + cache**
- Decision: cache -> Android Geocoder -> Nominatim for address; Overpass-first for POI details.
- Sense of impact: better names with lower latency/cost and reduced network dependency.

7. **OSM map route as ordered place polyline (not routing engine)**
- Decision: display chronology through straight centroid links.
- Sense of impact: simple and fast, but not road-accurate navigation.

---

## 4) Known Behavior-Risk Findings (Code-Grounded)

1. **Route chronology mismatch risk**
- `PlaceDao` gives ascending first-visit-time order, then `MapViewModel` reverses.
- Route renderer assumes incoming order is chronology.
- Impact: route path and marker numbers can show reverse day order.

2. **Overpass parsing fragility for `way` rows**
- Query asks for `way`, parser expects `lat/lon` on each element.
- Some Overpass way outputs don’t provide direct `lat/lon` in this form.
- Impact: POI enrichment may fail more than expected; names fall back to weaker sources.

3. **Movement “gap-free” narrative can still have micro-unrepresented intervals**
- Certain tiny windows return empty classification.
- Impact: mostly harmless but technically violates strict no-gap claim in edge cases.

4. **Blocking preference reads in geocoding repo properties**
- `runBlocking` in cache config getters.
- Impact: avoidable thread blocking in geocoding hot path.

---

## 5) Practical Debugging Checklist by Pipeline Stage

1. **No points saved**
- Check service start path and permission monitoring.
- Inspect `shouldSaveLocation` gates and activity-recognition suppression.

2. **Places not detected**
- Verify trigger counters and worker enqueue status.
- Inspect quality filter output counts and clustering parameters.

3. **Visits missing or too many short visits**
- Inspect dwell threshold and min visit duration settings.
- Validate hysteresis thresholds against radius/accuracy.

4. **Timeline looks wrong**
- Compare movement segments vs legacy segments.
- Check day window overlap queries and gap thresholds.

5. **Map route order wrong**
- Verify place list ordering from DAO through ViewModel to OSM view.

6. **Poor place names**
- Check geocoding cache hits/misses.
- Verify Overpass and Nominatim request health/rate limiting.

---

## 6) Source Files Covered

- `data/orchestration/TrackingOrchestratorImpl.kt`
- `data/receiver/BootReceiver.kt`
- `data/service/LocationTrackingService.kt`
- `data/processor/SmartDataProcessor.kt`
- `data/repository/VisitRepositoryImpl.kt`
- `data/database/dao/VisitDao.kt`
- `domain/model/Visit.kt`
- `domain/usecase/PlaceDetectionUseCases.kt`
- `data/worker/PlaceDetectionWorker.kt`
- `utils/WorkManagerHelper.kt`
- `data/orchestration/PlaceDetectionSchedulerImpl.kt`
- `domain/usecase/GenerateTimelineSegmentsUseCase.kt`
- `domain/usecase/MovementSegmentationUseCase.kt`
- `presentation/screen/timeline/TimelineViewModel.kt`
- `data/database/dao/PlaceDao.kt`
- `presentation/screen/map/MapViewModel.kt`
- `presentation/components/OpenStreetMapView.kt`
- `presentation/screen/map/MapScreen.kt`
- `domain/usecase/EnrichPlaceWithDetailsUseCase.kt`
- `data/repository/GeocodingRepositoryImpl.kt`
- `data/api/NominatimGeocodingService.kt`
- `data/api/OverpassApiService.kt`
- `data/api/AndroidGeocoderService.kt`
- `data/state/StateWriteGatewayImpl.kt`
- `data/event/StateEventDispatcher.kt`

