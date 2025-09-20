# Voyager Next-Gen Architecture Blueprint v2

## Summary

Voyager should be rebuilt as a local-first, privacy-first mobility intelligence system with one runtime authority for tracking, one persisted source of truth for state, and one derived timeline pipeline that powers both Map and Timeline in real time. The product target is Google Maps Timeline parity for day reconstruction, visits, routes, and corrections, plus power-user controls, stronger privacy, richer semantics, and provider abstraction for free-first operation.

A hard requirement is evidence-backed UI: every user-visible claim must be traceable to recorded evidence, derived metrics, confidence, source, and reasoning. If the UI says Walking, it must also be able to show the supporting speed range, duration window, sample count, activity-recognition confidence, and why competing labels were rejected.

---

## 1. Core Architecture

### 1.1 Layer Design

Use a 6-layer design:

| Layer | Responsibility | Key Components |
|-------|---------------|----------------|
| Platform | Android services, sensors, APIs | ForegroundService, ActivityRecognition, HealthConnect, Geofencing |
| Capture | Location/activity/steps ingestion | LocationCapture, ActivityCapture, StepCapture, SensorFusion |
| Pipeline | Sample filtering, segmentation, visit/place inference | SampleProcessor, Segmenter, VisitDetector, PlaceMatcher |
| Storage | Persistence | Room/SQLCipher, DataStore, encrypted preferences |
| Domain | Business logic | Repositories, UseCases, Models |
| Presentation | UI | Compose screens, ViewModels, state holders |

### 1.2 Single-Authority Principles

- **TrackingRuntimeCoordinator** is the only lifecycle authority for start, stop, pause, resume, boot restore, crash restore, permission degradation, and watchdog recovery. Remove split ownership across service manager, orchestrator, and UI.
- **TimelineStateStore** is the only write authority for current runtime state. It persists state atomically and exposes hot StateFlow for UI. No direct mutable writes outside this store.
- **PipelineSerializer** is the single-writer gate for the hot ingestion pipeline. All incoming samples enter through one serial channel (Kotlin Channel with capacity CONFLATED for backpressure) so that visit detection, segmentation, and state commits never race. Workers that touch places or visits acquire row-level locks via database transactions, never global mutexes.

### 1.3 Execution Model

- Keep the foreground service as the primary continuous tracking mechanism because Android background limits still require foreground execution for reliable continuous location tracking.
- Use event-driven internal flows, not ad hoc polling loops. Only three long-lived streams are allowed: tracking runtime state, latest user motion/activity, and derived live timeline state.
- Default to on-device only. Cloud sync is out of scope for v1 architecture, but export/import and a future encrypted sync boundary should be designed as separate adapters.
- Add an **EvidencePolicy**: no UI model may expose inferred labels without attached evidence metadata and explainability fields.

### 1.4 Concurrency Model

- The hot pipeline (sample arrival -> state commit) runs on a single dedicated coroutine fed by a `Channel<RawSample>(BUFFERED, capacity=64)`. If the buffer fills (device was asleep, burst on wake), the oldest unprocessed samples are retained (SUSPEND policy, not DROP) so no data is lost.
- Visit detection and segment mutation are always serialized through PipelineSerializer. The serializer owns a `Mutex` that guards the read-modify-write cycle on `current_runtime_state`.
- Background workers (DiscoverPlacesWorker, GeocodeBackfillWorker, DailyRollupWorker) operate on derived/aggregate tables only and use Room `@Transaction` with explicit row-level WHERE clauses. They never write to `current_runtime_state` or `movement_segments` for the active day.
- Worker-to-pipeline conflict resolution: if a DiscoverPlaces batch reclassifies a place that has an active visit candidate, the worker writes to a `pending_place_updates` staging table. The pipeline picks up pending updates on its next cycle and applies them under its own serializer lock.

### 1.5 Map Architecture

- Default to MapLibre-first behind a **MapEngine** abstraction. Keep tile/style providers swappable so the free stack can use open providers now and paid providers later without UI rewrite.
- Mapping stack separates: MapEngine, TileProvider, StyleProvider, RouteOverlayProvider, OfflineRegionProvider.

---

## 2. Data Model And Schema

### 2.1 Storage Domains

Split storage into five domains: **raw**, **derived**, **semantic**, **ops**, and **settings**.

### 2.2 Raw Tables

**raw_location_samples** (append-only):
- sampleId (PK, auto), capturedAt (UTC instant), receivedAt (UTC instant), lat, lng, accuracyM, verticalAccuracyM, speedMps, bearingDeg, altitudeM, provider (gps/network/fused/passive), isMock, batteryPct, isCharging, deviceIdleMode, permissionSnapshot (fine/coarse/none), trackingSessionId (FK), localTimeZone (IANA zone ID).
- Index: (trackingSessionId, capturedAt), (capturedAt), geohash column for spatial queries.

**raw_activity_samples** (append-only):
- activitySampleId (PK), activityType (STILL/WALKING/RUNNING/CYCLING/IN_VEHICLE/ON_BICYCLE/TILTING/UNKNOWN), confidence (0-100), source (TRANSITION_API/CLASSIFIER/SPEED_HEURISTIC/ACCELEROMETER), transition (ENTER/EXIT/null), capturedAt, trackingSessionId (FK).
- Index: (trackingSessionId, capturedAt).

**raw_step_samples** (append-only):
- stepSampleId (PK), periodStart (UTC), periodEnd (UTC), stepCount (delta for period), source (HEALTH_CONNECT/STEP_SENSOR/PEDOMETER), confidence, trackingSessionId (FK).
- Index: (periodStart, periodEnd).

**tracking_sessions**:
- sessionId (PK), startedAt, endedAt (null if active), startedBy (USER/BOOT/CRASH_RESTORE/SCHEDULE/PERMISSION_REGAINED), endedBy (USER/PERMISSION_LOST/BATTERY_CRITICAL/SYSTEM_KILL/ERROR), interruptionReason, restartReason, localTimeZone, totalSamples, totalDistanceM.

### 2.3 Derived Tables

**movement_segments** (canonical timeline units):
- segmentId (PK), segmentType (VISIT/WALK/RUN/CYCLE/DRIVE/TRANSIT/GAP/UNKNOWN_MOTION), startAt, endAt, startSampleId (FK), endSampleId (FK), distanceM, confidence (0.0-1.0), routeId (FK, nullable), placeId (FK, nullable for non-visit), gapReason (PERMISSION/DOZE/PROCESS_DEAD/GPS_LOSS/MANUAL_PAUSE/UNKNOWN, null for non-gap), dayKey (TEXT, YYYY-MM-DD in resolved local zone), isUserCorrected.
- Index: (dayKey, startAt), (placeId), (routeId).
- Constraint: for any given dayKey, segments must not have overlapping [startAt, endAt) intervals.

**segment_evidence** (per-segment backing facts):
- segmentId (FK, PK), avgSpeedMps, maxSpeedMps, speedStdDev, sampleCount, activityVotes (JSON map of activityType -> voteCount), stepCount, headingConsistency (0.0-1.0), dwellMarkers, providerMix (JSON map of provider -> count), decisionRuleVersion, explanationJson (human-readable justification), counterEvidenceJson (why competing labels were rejected).

**routes** (route storage):
- routeId (PK), segmentId (FK), encodedPolyline (Google encoded polyline string for compact storage), simplifiedPolyline (Douglas-Peucker simplified for map rendering at low zoom), totalDistanceM, totalDurationMs, avgSpeedMps, maxSpeedMps, transportMode, sampleCount, boundingBoxJson (NE/SW corners for spatial queries).
- Index: (segmentId).
- Polyline encoding: raw sample points -> time-ordered coordinates -> Google Polyline Algorithm for storage. Simplification at read time using Douglas-Peucker with epsilon adaptive to zoom level.

### 2.4 Semantic Tables

**places** (stable place clusters):
- placeId (PK), centroidLat, centroidLng, radiusM, geohash (precision 7), s2CellId (level 16), confidence, lifecycleStatus (CANDIDATE/CONFIRMED/ARCHIVED/MERGED), userDisplayName (nullable, always wins), bestProviderName, bestProviderSource, category (HOME/WORK/GYM/RESTAURANT/SHOPPING/TRANSIT_HUB/CUSTOM/UNKNOWN), categoryConfidence, userCategory (nullable, user override), createdAt, lastVisitedAt, mergedIntoPlaceId (nullable).
- Index: (geohash), (s2CellId), (lifecycleStatus), (category).

**visits** (immutable visit intervals):
- visitId (PK), placeId (FK), arrivalAt, departureAt (null if ongoing), dwellMs, source (LIVE_DETECTION/BATCH_DISCOVERY/USER_CREATED), confidence, supersedesVisitId (nullable, for corrections), isUserCorrected, dayKey.
- Index: (placeId, arrivalAt), (dayKey, arrivalAt).
- **Non-overlap invariant**: enforced by `@Transaction` insert that queries for any existing active visit overlapping [arrivalAt, departureAt) and rejects or merges before insert. Additional CHECK via database trigger or integrity worker.

**visit_evidence**:
- visitId (FK, PK), entrySampleIds (JSON array), exitSampleIds (JSON array), dwellCurveJson (time vs distance-from-centroid series), insideCount, outsideCount, suppressionReasons (JSON), geofenceHints, confirmationRuleUsed, arrivalConfidence, departureConfidence.

**place_evidence**:
- placeId (FK, PK), clusterDensity, totalVisitCount, visitCountLast7d, visitCountLast30d, avgDwellMs, repeatabilityScore (0.0-1.0), namingCandidatesJson (ranked list from all providers), userConfirmationCount, categoryReasoningJson, lastClusterUpdateAt.

**geocode_candidates**:
- geocodeId (PK), placeId (FK), provider (ANDROID_GEOCODER/PHOTON/NOMINATIM/GOOGLE/CUSTOM), rank, language, displayName, structuredPartsJson (street, city, state, country, postal), confidence, licenseClass (FREE/ATTRIBUTION/PAID), cachedUntil, fetchedAt.
- Index: (placeId, rank).

### 2.5 Ops Tables

**current_runtime_state** (singleton):
- id (PK, always 1), activeSessionId, currentSegmentId, pendingVisitCandidateJson (serialized in-progress dwell state), lastAcceptedSampleId, lastAcceptedAt, livePlaceStateJson, lastWorkerHeartbeats (JSON map of workerName -> lastSuccessAt), stateVersion (monotonic counter), lastPipelineLatencyMs.

**pending_place_updates** (staging for worker -> pipeline handoff):
- updateId (PK), placeId, updateType (RECLASSIFY/MERGE/RENAME/CATEGORY_CHANGE), payloadJson, createdAt, consumedAt (null until pipeline processes).

**health_log**:
- logId (PK), eventType (SAMPLE_GAP/WORKER_FAILURE/PERMISSION_CHANGE/BATTERY_CRITICAL/CRASH_RESTORE/WATCHDOG_TRIGGER), eventAt, detailsJson, acknowledged.

### 2.6 Search Tables (FTS)

**search_index** (Room FTS4 virtual table):
- rowid mapping to source table + source ID.
- Indexed content: placeDisplayName, placeCategory, dayKey, segmentType, geocodeDisplayName, userNotes.
- Tokenizer: unicode61 with remove_diacritics=1.
- Rebuild trigger: after place rename, geocode backfill, or visit correction.

**search_metadata**:
- searchRowId (FK), sourceTable (PLACE/VISIT/SEGMENT/DAY), sourceId, relevanceBoost (user-renamed places get 2x, confirmed places get 1.5x).

### 2.7 Analytics Tables

**daily_rollups**:
- dayKey (PK), totalDistanceM, totalSteps, totalDwellMs, totalTransitMs, totalWalkMs, totalDriveMs, placeVisitCount, uniquePlacesVisited, firstActivityAt, lastActivityAt, dominantTransportMode, anomalyFlags (JSON), computedAt.

**weekly_rollups**:
- weekKey (PK, ISO week YYYY-Www), avgDailyDistanceM, avgDailySteps, totalDistanceM, totalSteps, activeDayCount, topPlacesJson (top 5 by visit count), transportModeDistributionJson, comparisonToPrevWeekJson, computedAt.

**place_rollups**:
- placeId (PK), totalVisitCount, totalDwellMs, avgDwellMs, lastVisitAt, firstVisitAt, visitCountLast7d, visitCountLast30d, visitCountLast90d, dominantDayOfWeek, dominantTimeOfDay, computedAt.

### 2.8 Settings Storage

Use Jetpack DataStore (Preferences) for user settings, NOT Room. Settings are key-value pairs grouped logically. See Section 8 for the full settings schema.

### 2.9 Time And Timezone Strategy

- All persistence uses UTC instants (`Instant` / epoch millis).
- Every `raw_location_sample` and `tracking_session` stores `localTimeZone` (IANA zone ID, e.g., "Asia/Kolkata").
- **DayBoundaryResolver**: a domain utility that produces canonical day boundaries given a user preference:
  - `HOME_TIMEZONE` mode: all days split at midnight in the user's configured home timezone. Simple, consistent, no surprises. Default.
  - `TRAVEL_AWARE` mode: day boundary follows the timezone of the most recent sample. A flight from IST to JST mid-day produces two partial days. Power-user option.
- `dayKey` on segments, visits, and rollups is always computed by DayBoundaryResolver and stored as TEXT `YYYY-MM-DD`.
- DST handling: spring-forward gap (e.g., 2:00 AM doesn't exist) — samples in the gap get assigned to the next valid instant. Fall-back overlap (e.g., 2:00 AM happens twice) — use UTC ordering, no duplication.
- Day-crossing segments (start Tuesday, end Wednesday) are assigned to the day of their startAt.

---

## 3. Detection, Inference, And Explainability Pipeline

### 3.1 Pipeline Stages

Ingestion pipeline order (single-threaded through PipelineSerializer):

```
permission check
  -> sample normalization (coordinate rounding, unit conversion)
  -> quality scoring (accuracy filter, mock detection, staleness check)
  -> dedup/jitter suppression (distance < 3m AND time < 2s = skip)
  -> activity fusion (merge AR transitions + speed heuristics + step rate)
  -> sleep/power policy (reduce processing during detected sleep)
  -> segmenter (open/close/extend segments based on motion state transitions)
  -> visit detector (dwell accumulation -> candidate -> confirmed)
  -> place matcher (nearest cluster + hysteresis + confidence)
  -> step correlation (attach step deltas to active segment)
  -> state commit (atomic write to current_runtime_state + segment tables)
  -> UI emission (StateFlow push to presentation layer)
```

### 3.2 Adaptive Sampling Policy

Not one fixed interval. Policy inputs and outputs:

| Motion State | Base Interval | Accuracy | Battery Saver Modifier |
|-------------|--------------|----------|----------------------|
| STILL/DWELLING | 60-120s | BALANCED | x2 interval |
| WALKING | 10-15s | HIGH | x1.5 interval |
| RUNNING | 5-8s | HIGH | x1.5 interval |
| CYCLING | 8-12s | HIGH | x1.5 interval |
| DRIVING | 5-10s | HIGH | x2 interval |
| SLEEP_WINDOW | 300-600s | LOW | x3 interval |
| CHARGING | Use motion state default | HIGH | no modifier |

Additional inputs: recent speed variance (high variance = shorter interval), dwell likelihood (high = longer interval), user preset multiplier.

Policy is recalculated on every motion state change and every 5 minutes during stable states.

### 3.3 Hybrid Motion Model

- **Primary**: Android Activity Recognition Transition API — ENTER/EXIT events for STILL, WALKING, RUNNING, IN_VEHICLE, ON_BICYCLE.
- **Fallback**: speed/heading heuristics from raw samples when AR is unavailable or confidence < 50%.
- **Step-rate fusion**: if step sensor reports > 80 steps/min and AR says STILL, override to WALKING. If step rate is 0 for > 2 min and AR says WALKING, flag low confidence.
- **Confidence fusion formula**: `finalConfidence = (AR_confidence * 0.6) + (speed_heuristic_confidence * 0.25) + (step_rate_confidence * 0.15)`. Weights are configurable per preset.

### 3.4 Sleep Detection

Sleep detection gates the sampling policy to minimize battery drain during sleep hours.

- **Time-based signal**: user-configured sleep window (default 23:00-07:00 local time). Enters SLEEP_CANDIDATE state when inside window.
- **Motion signal**: if STILL for > 30 minutes continuously AND inside sleep window, confirm SLEEP state.
- **Charging signal**: if charging + STILL + inside sleep window, confirm SLEEP with high confidence.
- **Exit**: any motion event OR step rate > 0 for > 1 min OR user interaction with app.
- **During SLEEP**: sampling interval extends to 5-10 minutes, accuracy drops to LOW, activity recognition polling disabled. Samples still recorded for continuity, just at reduced rate.
- Sleep state is persisted in `current_runtime_state.livePlaceStateJson` so process death during sleep restores correctly.

### 3.5 Visit Detection

Two-phase detection:

**Phase 1 — Candidate accumulation**:
- Trigger: > 3 consecutive samples within 80m radius over > 3 minutes (configurable thresholds).
- State: `VISIT_CANDIDATE` with centroid, accumulation start time, sample count, max distance from centroid.
- Persisted in `current_runtime_state.pendingVisitCandidateJson` so process death never loses in-progress dwell.

**Phase 2 — Confirmation**:
- Trigger: candidate dwell time exceeds minimum (default 5 minutes, configurable per preset).
- Action: create `visit` row with `source=LIVE_DETECTION`, match or create place, write `visit_evidence`.
- Non-overlap check: `@Transaction` query checks if any existing visit on same dayKey overlaps [arrivalAt, departureAt). If overlap found: (a) if new visit is contained within existing, discard. (b) if partial overlap, adjust boundaries of shorter visit. (c) if new visit supersedes, set `supersedesVisitId` on old visit.

**Departure detection**:
- Trigger: > 3 consecutive samples outside the place radius OR gap > 10 minutes with next sample outside radius.
- Action: set `departureAt` on visit, close segment, write departure evidence.

### 3.6 Place Discovery And Matching

**Live matching** (during pipeline):
- Query places within 200m of sample using geohash prefix.
- If nearest confirmed place centroid is within `place.radiusM + 30m` (hysteresis buffer): match.
- If matched, increment `insideCount` on visit_evidence. If not matched but in dwell, increment `outsideCount`.
- Entry hysteresis: must be inside radius for > 2 consecutive samples to enter.
- Exit hysteresis: must be outside radius + 30m buffer for > 3 consecutive samples to exit.
- Every match/reject decision records justification in visit_evidence.

**Batch discovery** (DiscoverPlacesWorker, periodic):
- Runs HDBSCAN on unassigned visit centroids from the last 7 days.
- Parameters: min_cluster_size=3, min_samples=2, metric=haversine.
- Outputs new CANDIDATE places. Does not promote to CONFIRMED — that requires either user confirmation or 3+ visits.
- Writes results to `pending_place_updates` staging table for pipeline consumption.

### 3.7 Step Count Pipeline

Steps are a first-class data source, not optional.

**Ingestion**:
- Primary: Health Connect StepsRecord API — query hourly deltas via `readRecords()`.
- Fallback: `TYPE_STEP_COUNTER` sensor (Sensor.TYPE_STEP_COUNTER) — continuous hardware step counter, compute deltas between reads.
- Tertiary: `TYPE_STEP_DETECTOR` events if counter unavailable.
- Polling: every 15 minutes during active tracking, every 60 minutes during sleep.

**Correlation with segments**:
- When a segment closes, query `raw_step_samples` where `periodStart >= segment.startAt AND periodEnd <= segment.endAt`.
- Sum step deltas and write to `segment_evidence.stepCount`.
- Step-to-distance calibration: `calibratedStrideLengthM = segment.distanceM / segment.stepCount` computed per walking/running segment. Rolling average stored in user calibration profile (DataStore). Used for distance estimation when GPS is poor.

**Surfacing**:
- Daily rollup includes `totalSteps`.
- Walking/running segments show step count in evidence block.
- Dashboard shows daily step chart derived from `raw_step_samples` aggregated by hour.

### 3.8 Gap Handling

Gaps are explicit timeline segments, never silently hidden.

- Gap created when: time between consecutive accepted samples exceeds the expected interval by > 3x.
- `gapReason` determination:
  - `PERMISSION`: last known permission state was degraded.
  - `DOZE`: device was in idle/doze mode (from `deviceIdleMode` flag).
  - `PROCESS_DEAD`: no heartbeat in `current_runtime_state` for > expected interval AND crash restore detected on next start.
  - `GPS_LOSS`: permission was fine, device was active, but no location fix (indoor, tunnel).
  - `MANUAL_PAUSE`: user explicitly paused tracking.
  - `UNKNOWN`: none of the above matched.
- Gap segments have no route, no place, no distance. They carry `segment_evidence` with: lastKnownSampleId, firstPostGapSampleId, gapDurationMs, gapReasonConfidence.

### 3.9 Semantic Inference

Additive and revisable — labels are evidence-derived, not hardcoded.

- **HOME**: place with highest nighttime dwell (22:00-06:00) over 14-day window AND > 5 overnight visits. Evidence: overnight visit count, avg nightly dwell, consistency score.
- **WORK**: place with highest weekday daytime dwell (09:00-17:00) over 14-day window AND > 3 weekday visits. Evidence: weekday visit count, avg work-hours dwell.
- **Categories** (GYM, RESTAURANT, etc.): from geocode provider category data + visit time patterns + dwell duration patterns. Never assigned with confidence > 0.7 from time patterns alone — requires provider category confirmation OR user confirmation.
- Labels are recomputed by `SemanticLabelWorker` weekly and on significant visit changes.

### 3.10 Inference Explainability Contract

Every inference emits:
```
label: String,
confidence: Float (0.0-1.0),
supportingMetrics: Map<String, Any>,  // e.g., avgSpeed, sampleCount, stepRate
counterEvidence: List<String>,        // e.g., "AR reported STILL for 20% of duration"
ruleVersion: String,                  // e.g., "walk-classifier-v3"
sourceSet: Set<String>,               // e.g., ["AR_TRANSITION", "SPEED_HEURISTIC", "STEP_SENSOR"]
humanExplanation: String              // e.g., "Classified as Walking because avg speed was 4.8 km/h with 1,240 steps over 12 minutes"
```

---

## 4. Repository, Use Case, And ViewModel Contracts

### 4.1 Repositories

Capability-based, not screen-based:

**TrackingRepository**:
- `startTracking(reason: StartReason): Result<TrackingSession>`
- `stopTracking(reason: StopReason): Result<Unit>`
- `pauseTracking(reason: PauseReason): Result<Unit>`
- `resumeTracking(reason: ResumeReason): Result<Unit>`
- `observeRuntimeState(): StateFlow<TrackingRuntimeState>`
- `observeHealth(): StateFlow<TrackingHealth>`

**TimelineRepository**:
- `observeDay(dayKey: String): Flow<TimelineDay>`
- `observeRange(startDay: String, endDay: String): Flow<List<TimelineDay>>`
- `rebuildDay(dayKey: String): Result<Unit>`
- `getSegmentDetails(segmentId: Long): SegmentDetail`
- `observeLiveTimeline(): StateFlow<LiveTimelineState>` (for real-time map/timeline sync)

**PlaceRepository**:
- `observePlaces(filter: PlaceFilter): Flow<List<Place>>`
- `observePlace(placeId: Long): Flow<PlaceDetail>`
- `renamePlace(placeId: Long, name: String): Result<Unit>`
- `mergePlaces(sourceIds: List<Long>, targetId: Long): Result<Unit>`
- `splitPlace(placeId: Long, visitIds: List<Long>): Result<Place>`
- `setPlaceCategory(placeId: Long, category: PlaceCategory): Result<Unit>`
- `confirmVisit(visitId: Long): Result<Unit>`
- `deleteVisit(visitId: Long): Result<Unit>`
- `adjustVisitTimes(visitId: Long, arrival: Instant?, departure: Instant?): Result<Unit>`

**SearchRepository**:
- `search(query: String, filters: SearchFilters): Flow<SearchResults>`
- `searchDays(query: String): Flow<List<DaySearchResult>>`
- `searchPlaces(query: String): Flow<List<PlaceSearchResult>>`
- `searchVisits(query: String, filters: VisitSearchFilters): Flow<List<VisitSearchResult>>`
- `rebuildSearchIndex(): Result<Unit>`
- SearchFilters include: dateRange, placeCategories, transportModes, minDwellMs, maxDistanceM.
- Results ranked by: exact match > prefix match > fuzzy match, boosted by `search_metadata.relevanceBoost`.

**AnalyticsRepository**:
- `observeDashboard(range: DateRange): Flow<DashboardState>`
- `observeComparisons(config: ComparisonConfig): Flow<ComparisonResult>`
- `observeAnomalies(range: DateRange): Flow<List<Anomaly>>`
- `getPlaceAnalytics(placeId: Long, range: DateRange): PlaceAnalytics`

**EvidenceRepository**:
- `getSegmentEvidence(segmentId: Long): SegmentEvidence`
- `getVisitEvidence(visitId: Long): VisitEvidence`
- `getPlaceEvidence(placeId: Long): PlaceEvidence`
- `getInferenceExplanation(segmentId: Long): InferenceExplanation`

**GeocodingRepository**:
- `reverseGeocode(lat: Double, lng: Double): Flow<GeocodingResult>`
- `getProviderStatus(): List<ProviderStatus>`
- `refreshGeocodeForPlace(placeId: Long): Result<Unit>`

**StepsRepository**:
- `observeDailySteps(dayKey: String): Flow<StepsSummary>`
- `observeHourlySteps(dayKey: String): Flow<List<HourlySteps>>`
- `getStepsForSegment(segmentId: Long): StepCount`
- `getUserStrideCalibration(): StrideCalibration`

**MapRepository**:
- `getRouteForSegment(segmentId: Long, zoomLevel: Int): MapRoute`
- `getVisitMarkers(dayKey: String): List<VisitMarker>`
- `getDayBoundingBox(dayKey: String): LatLngBounds`
- `getOfflineRegions(): List<OfflineRegion>`

**ExportRepository**:
- `exportDay(dayKey: String, format: ExportFormat): Result<Uri>`
- `exportRange(range: DateRange, format: ExportFormat): Result<Uri>`
- `importData(uri: Uri, format: ExportFormat): Result<ImportSummary>`

**SettingsRepository**:
- `observeSettings(): StateFlow<UserSettings>`
- `updateSetting(key: SettingKey, value: Any): Result<Unit>`
- `applyPreset(preset: Preset): Result<Unit>`
- `exportSettings(): Result<String>`
- `importSettings(json: String): Result<Unit>`

### 4.2 Central Use Cases

| Use Case | Responsibility |
|----------|---------------|
| ProcessLocationSampleUseCase | Run single sample through full pipeline |
| FuseActivityStateUseCase | Merge AR + speed + steps into motion state |
| DetectVisitUseCase | Two-phase visit candidate -> confirmation |
| DiscoverPlacesBatchUseCase | HDBSCAN on unassigned visit centroids |
| MatchPlaceLiveUseCase | Nearest-cluster + hysteresis for incoming sample |
| CorrelateStepsUseCase | Attach step deltas to closing segments |
| GenerateTimelineDayUseCase | Assemble all segments/visits/routes for a day |
| SearchTimelineUseCase | FTS query + ranking + filter application |
| GenerateInsightsUseCase | Compute dashboard metrics from rollups |
| GenerateComparisonsUseCase | Week-over-week / month-over-month deltas |
| DetectAnomaliesUseCase | Statistical deviation from baseline |
| ApplyUserCorrectionUseCase | Process rename/merge/split/reclassify with feedback propagation |
| PropagateUserFeedbackUseCase | Update calibration profiles from corrections |
| RescheduleTrackingPolicyUseCase | Recalculate sampling intervals from current state + settings |
| BuildEvidenceSummaryUseCase | Assemble human-readable evidence for any entity |
| ResolveDayBoundaryUseCase | Compute dayKey for a given instant using user timezone preference |

### 4.3 ViewModel Contracts

- ViewModels expose only `state: StateFlow<ScreenState>` + `fun onIntent(intent: ScreenIntent)`.
- Every detail sheet state model must include an `evidence: EvidenceBlock` and `confidence: ConfidenceBlock`.
- **Timeline and Map must consume the same derived day model** (`TimelineDay`) so claims stay synchronized. When Map pans to a segment, Timeline scrolls to it, and vice versa.

### 4.4 Map-Timeline Synchronization Protocol

Both screens observe `TimelineRepository.observeDay(dayKey)` which emits a single `TimelineDay` model containing all segments, visits, and routes.

- **Map tap -> Timeline scroll**: Map emits `MapIntent.SegmentTapped(segmentId)`. Shared `DayNavigationState` (in a shared ViewModel or NavArgs) updates `focusedSegmentId`. Timeline observes this and scrolls to the segment card.
- **Timeline tap -> Map pan**: Timeline emits `TimelineIntent.SegmentSelected(segmentId)`. Map observes `focusedSegmentId` and animates camera to the segment's bounding box, highlights the route/visit marker.
- **Live tracking sync**: during active tracking, both screens observe `TimelineRepository.observeLiveTimeline()` which appends the in-progress segment in real time.
- Coordination lives in `DayNavigationStateHolder` — a Hilt `@ActivityRetainedScoped` object, not a ViewModel, so it survives fragment/composable recomposition.

---

## 5. Android Lifecycle, Coroutines, And Work

### 5.1 Service Architecture

- Foreground service owns active capture only. It must not own business orchestration decisions beyond invoking the coordinator.
- Service creates location/activity callbacks, feeds samples to the pipeline channel, and manages the foreground notification. Nothing else.
- On null intent restart (system killed and restarted service): delegate to `TrackingRuntimeCoordinator.restoreFromCrash()` which reads `current_runtime_state` and resumes.

### 5.2 Boot And Crash Restore

- Boot, package replaced, and device reboot all route through the same `TrackingRuntimeCoordinator.restore()` path.
- BootReceiver launches a one-shot coroutine via `goAsync()` + application scope. It does NOT start the service directly — it calls `coordinator.restore()` which checks `current_runtime_state` and decides whether to start.
- Crash restore: on next service start after unexpected death, coordinator detects `activeSessionId != null AND endedAt == null`, creates a GAP segment for the dead period, and resumes tracking with `restartReason = CRASH_RESTORE`.

### 5.3 Structured Concurrency

- **Application scope** (`@Singleton`, `SupervisorJob() + Dispatchers.Default`): for coordinator, state store, health monitors. Lives for app process lifetime.
- **Service scope** (`SupervisorJob() + Dispatchers.Default`): for location callbacks, activity callbacks, pipeline channel. Cancelled on service destroy.
- **Worker scope**: each WorkManager worker gets its own `coroutineScope` via `CoroutineWorker`. No shared state with application scope except through Room/DataStore.
- No unmanaged `GlobalScope` or standalone `CoroutineScope()` anywhere.

### 5.4 Permission Degradation Chain

Defined fallback chain when permissions degrade:

| State | Available Data | Action |
|-------|---------------|--------|
| FINE_LOCATION | Full GPS/fused | Normal operation |
| COARSE_LOCATION | ~100m accuracy network location | Continue tracking, mark samples as coarse, widen place matching radius to 150m, disable route polyline generation |
| NO_LOCATION + ACTIVITY_RECOGNITION | Activity transitions only | Pause location capture, continue activity logging, create GAP segments for location timeline, notify user |
| NO_LOCATION + NO_ACTIVITY | Nothing useful | Pause all capture, persist state, show prominent notification asking user to restore permissions |
| BACKGROUND_RESTRICTED | Cannot run background | Stop foreground service, schedule periodic WorkManager check (15 min minimum), notify user that continuous tracking is unavailable |

- Each degradation writes to `health_log` with `eventType = PERMISSION_CHANGE`.
- Re-acquisition: when permissions are restored, coordinator detects via `ActivityCompat.OnRequestPermissionsResultCallback` or periodic check, and resumes from appropriate state.
- UI shows a persistent banner in degraded states with a one-tap action to open system permission settings.

### 5.5 WorkManager Jobs

| Worker | Schedule | Idempotency Key | Description |
|--------|----------|-----------------|-------------|
| DiscoverPlacesWorker | Every 6 hours | `discover_places_{date}` | HDBSCAN on recent unassigned visits |
| GeocodeBackfillWorker | Every 4 hours | `geocode_backfill_{date}` | Reverse geocode places missing names |
| DailyRollupWorker | Daily at 03:00 local | `daily_rollup_{dayKey}` | Compute daily_rollups for previous day |
| WeeklyRollupWorker | Monday 04:00 local | `weekly_rollup_{weekKey}` | Compute weekly_rollups |
| SemanticLabelWorker | Weekly + on significant change | `semantic_label_{weekKey}` | Recompute HOME/WORK/category labels |
| DataRetentionWorker | Daily at 04:00 local | `retention_{date}` | Apply retention policy per tier |
| IntegrityRepairWorker | Daily at 05:00 local | `integrity_{date}` | Check visit non-overlap, orphaned segments, missing evidence |
| SearchIndexWorker | After geocode/rename/correction | `search_index_{timestamp}` | Rebuild FTS index for changed entities |
| StepSyncWorker | Every 15 min active, 60 min sleep | `step_sync_{period}` | Fetch latest step data from Health Connect/sensor |
| ExportWorker | On-demand | `export_{timestamp}` | Generate export file |

Each worker: uniquely enqueued by name, idempotent by input key (re-execution produces same result), writes completion status to `health_log`.

### 5.6 Runtime Health Monitoring

Monitor these heartbeats:

| Metric | Expected Interval | Alert Threshold | Action |
|--------|-------------------|-----------------|--------|
| lastSampleAt | Per sampling policy | 3x expected interval | Log to health_log, bump notification |
| lastForegroundNotificationAt | 60s | 5 minutes | Restart foreground service |
| lastWorkerSuccessAt (per worker) | Per worker schedule | 2x schedule | Re-enqueue worker, log warning |
| lastStateCommitAt | Per sample processing | 3x sample interval | Pipeline may be stuck — restart pipeline coroutine |
| lastPipelineLatencyMs | Per sample | > 2000ms sustained | Log performance warning, consider dropping accuracy tier |

---

## 6. Geocoding And Provider Stack

### 6.1 Provider Architecture

Provider-ranked and normalized behind `GeocodingProvider` interface:

```
interface GeocodingProvider {
    val providerId: String
    val priority: Int  // lower = preferred
    val licenseClass: LicenseClass
    val rateLimitPerMinute: Int
    suspend fun reverseGeocode(lat: Double, lng: Double): ProviderGeoResult?
    fun isAvailable(): Boolean
}
```

### 6.2 Provider Priority (free-first default)

| Priority | Provider | Use Case | Rate Limit | License |
|----------|----------|----------|------------|---------|
| 1 | Android Geocoder | Local hints when device has Play Services | Device-local, unlimited | FREE |
| 2 | Self-hosted Photon | Primary bulk reverse geocoding | Self-hosted, unlimited | FREE (ODbL attribution) |
| 3 | Nominatim (OSM public) | Low-volume fallback only | 1 req/sec absolute max | FREE (restrictive policy) |
| 4+ | Paid providers (future) | Google, Mapbox, HERE | Per API key | PAID |

### 6.3 Conflict Resolution Algorithm

When multiple providers return results for the same coordinates:

1. If user has set `userDisplayName` on the place: use it. Done.
2. If user has confirmed a specific provider result: use that. Done.
3. Score remaining candidates: `score = provider_priority_weight * 0.4 + confidence * 0.3 + specificity * 0.2 + recency * 0.1`.
   - `specificity`: a result with street address + business name scores higher than just "neighborhood".
   - `recency`: more recent fetch scores slightly higher due to data updates.
4. Pick highest scoring candidate as `bestProviderName`.
5. Store ALL candidates in `geocode_candidates` for user review and future re-ranking.

### 6.4 Name Resolution Display Priority

Always show names in this order:
1. `place.userDisplayName` (user-set custom name) — **always wins**
2. `place.userCategory` label if set (e.g., "My Gym")
3. Best provider geocode result (`place.bestProviderName`)
4. Semantic label if confident > 0.8 (e.g., "Home", "Work")
5. Raw coordinates as fallback: "12.9716, 77.5946"

UI must indicate the source of the displayed name (icon or subtitle: "Custom name", "via Photon", "Inferred").

---

## 7. Comparison, Anomaly Detection, And Insights

### 7.1 Comparison Engine

`GenerateComparisonsUseCase` computes period-over-period deltas:

**Supported comparisons**:
- Week vs previous week (default dashboard view)
- Month vs previous month
- Weekday pattern vs weekend pattern
- Any custom date range vs another custom date range

**Compared metrics**:
- Total distance, total steps, active time, transit time
- Unique places visited, total visit count
- Transport mode distribution (% walk / drive / cycle / transit)
- Average daily movement start time, end time
- Top places by dwell time (position changes)

**Output model**:
```
ComparisonResult {
    periodA: DateRange,
    periodB: DateRange,
    metricDeltas: Map<MetricKey, MetricDelta>,  // each has: valueA, valueB, absoluteDelta, percentDelta, trend (UP/DOWN/STABLE)
    highlights: List<ComparisonHighlight>,        // e.g., "You walked 23% more this week"
    confidence: Float                             // lower if either period has significant gaps
}
```

### 7.2 Anomaly Detection

`DetectAnomaliesUseCase` uses statistical deviation from a rolling baseline.

**Algorithm**:
- Compute 30-day rolling mean and standard deviation for each tracked metric.
- Flag anomaly when current value deviates by > 2 standard deviations from the rolling mean.
- Severity: MILD (2-3 sigma), NOTABLE (3-4 sigma), SIGNIFICANT (> 4 sigma).

**Tracked metrics for anomalies**:
- Daily distance, daily steps, daily active time
- Visit count, unique places count
- Longest single dwell, longest single trip
- First activity time, last activity time (routine shift detection)
- Transport mode ratios (sudden car-to-walk shift, etc.)

**Output model**:
```
Anomaly {
    metricKey: String,
    observedValue: Double,
    baselineMean: Double,
    baselineStdDev: Double,
    deviationSigma: Float,
    severity: AnomalySeverity,
    baselinePeriod: DateRange,
    impactedDay: String,
    humanExplanation: String  // "You traveled 12.3 km today — 3.2x your 30-day average of 3.8 km"
}
```

### 7.3 Insights Generation

`GenerateInsightsUseCase` produces user-facing insights from rollups + comparisons + anomalies:

- **Routine insights**: "You typically arrive at Work around 9:15 AM" (from place_rollups + visit time patterns).
- **Trend insights**: "Your daily walking distance has increased 15% over the last month" (from weekly_rollups comparison).
- **Anomaly insights**: surfaced anomalies with human-readable explanations.
- **Place insights**: "You've visited Cafe X 12 times this month, up from 4 last month" (from place_rollups comparison).
- **Achievement insights**: "New personal record: 15,000 steps today!" (from daily_rollups).

Insights are computed by DailyRollupWorker and cached. Not recomputed on every UI load.

---

## 8. Notification Architecture

### 8.1 Notification Channels

| Channel ID | Name | Importance | Description |
|------------|------|------------|-------------|
| `tracking_status` | Tracking Status | LOW | Persistent foreground service notification |
| `tracking_alerts` | Tracking Alerts | HIGH | Permission loss, GPS issues, crash recovery |
| `insights_daily` | Daily Insights | DEFAULT | Daily summary, anomalies, achievements |
| `insights_weekly` | Weekly Summary | DEFAULT | Weekly comparison report |
| `user_actions` | Quick Actions | DEFAULT | Notifications that prompt user action (confirm place, review visit) |
| `system_health` | System Health | LOW | Worker failures, integrity issues (debug-visible only) |

### 8.2 Notification Triggers

| Trigger | Channel | Content |
|---------|---------|---------|
| Tracking started | tracking_status | "Tracking active — [transport mode]" with live updates |
| Permission degraded | tracking_alerts | "Location accuracy reduced — tap to restore" with action button |
| GPS lost > 5 min | tracking_alerts | "GPS signal lost — timeline may have gaps" |
| Crash restored | tracking_alerts | "Tracking resumed after interruption" |
| Day complete (next morning) | insights_daily | "Yesterday: 8.2 km, 12,400 steps, 4 places visited" |
| Anomaly detected | insights_daily | "Unusual: you traveled 3x your average distance today" |
| Weekly summary (Monday) | insights_weekly | "Last week: 45 km total, 3 new places discovered" |
| New place needs confirmation | user_actions | "Is this 'Starbucks on MG Road'? Tap to confirm or rename" |
| Worker failed 3x | system_health | "Background task failed — some features may be delayed" |

### 8.3 Notification Actions

Foreground service notification: "Pause tracking" / "Stop tracking" quick actions.
Place confirmation notification: "Confirm" / "Rename" / "Dismiss" actions — handled by `NotificationActionReceiver` which delegates to `ApplyUserCorrectionUseCase`.
Permission notification: "Fix" action opens app permission settings intent.

---

## 9. User Feedback Learning Loop

### 9.1 Correction Types And Their Signals

| User Action | What It Teaches | Where It Propagates |
|-------------|----------------|-------------------|
| Rename place | Provider geocode was wrong or insufficient | Boost user name priority, log provider miss |
| Set place category | Auto-category was wrong | Update category reasoning, adjust time-pattern weights |
| Correct segment type (Walk -> Bus) | Motion classifier failed | Log to `correction_feedback`, adjust confidence weights |
| Merge places | Clustering was too aggressive (split one real place into two) | Increase HDBSCAN min_cluster_size for that area |
| Split place | Clustering was too lax (merged two distinct places) | Decrease HDBSCAN cluster distance for that area |
| Delete visit | False positive visit detection | Log dwell threshold miss, tighten dwell minimum |
| Adjust visit times | Arrival/departure detection was imprecise | Log entry/exit sample timing error |
| Confirm visit | Detection was correct | Increase place confidence, reinforce detection parameters |

### 9.2 Feedback Storage

**correction_feedback** table:
- feedbackId (PK), correctionType (RENAME/RECATEGORIZE/RECLASSIFY_SEGMENT/MERGE_PLACE/SPLIT_PLACE/DELETE_VISIT/ADJUST_TIMES/CONFIRM), entityType (PLACE/VISIT/SEGMENT), entityId, beforeValueJson, afterValueJson, createdAt, propagated (boolean).

### 9.3 Propagation Pipeline

`PropagateUserFeedbackUseCase` runs after every correction:

1. Record correction in `correction_feedback`.
2. Immediate effects: update the entity directly (rename, reclassify, etc.).
3. Deferred calibration (runs in `FeedbackCalibrationWorker`):
   - Aggregate corrections by type over 30-day window.
   - If > 3 segment reclassifications of same type (e.g., DRIVE -> BUS): adjust `confidenceFusionWeights` in user calibration profile. Specifically, reduce AR weight for IN_VEHICLE and increase speed-heuristic weight for transit-speed ranges.
   - If > 2 place merges in same geohash region: increase HDBSCAN `min_cluster_size` by 1 for that region.
   - If > 2 place splits: decrease HDBSCAN `cluster_selection_epsilon` by 10% for that region.
   - If > 3 visit deletions with dwell < 10 min: increase `minDwellForConfirmation` by 1 minute (capped at 15 min).
4. Calibration changes stored in DataStore under `user_calibration_profile`. Pipeline reads these on every cycle.

### 9.4 Calibration Profile

Stored in DataStore (not Room) because it's a small, frequently-read config:

```
UserCalibrationProfile {
    arWeight: Float = 0.6,          // default, adjusted by segment corrections
    speedHeuristicWeight: Float = 0.25,
    stepRateWeight: Float = 0.15,
    minDwellMinutes: Int = 5,       // adjusted by visit deletion feedback
    placeMatchRadiusBoostM: Int = 0, // increased if users frequently merge nearby places
    regionOverrides: Map<String, RegionCalibration>  // geohash -> HDBSCAN param overrides
}
```

---

## 10. Settings, Presets, And Customization

### 10.1 Settings Groups

**Tracking**:
- trackingEnabled: Boolean
- samplingPreset: SamplingPreset (BATTERY_SAVER / BALANCED / HIGH_ACCURACY / CUSTOM)
- customSamplingIntervalMs: Long (only when CUSTOM)
- motionDetectionEnabled: Boolean
- activityRecognitionEnabled: Boolean
- stepCountingEnabled: Boolean
- stepCountSource: StepSource (HEALTH_CONNECT / SENSOR / AUTO)

**Battery**:
- batterySaverThresholdPct: Int (default 20)
- batterySaverAction: BatterySaverAction (REDUCE_ACCURACY / INCREASE_INTERVAL / PAUSE_TRACKING)
- chargingBoostEnabled: Boolean (use higher accuracy while charging)

**Sleep**:
- sleepDetectionEnabled: Boolean
- sleepWindowStart: LocalTime (default 23:00)
- sleepWindowEnd: LocalTime (default 07:00)
- sleepSamplingIntervalMs: Long (default 300000 = 5 min)

**Activity Inference**:
- arConfidenceThreshold: Int (default 50, range 20-90)
- speedHeuristicEnabled: Boolean
- stepRateFusionEnabled: Boolean
- confidenceWeights: ConfidenceWeights (AR, speed, step — advanced)

**Place Detection**:
- minDwellMinutes: Int (default 5, range 2-30)
- placeRadiusM: Int (default 80, range 30-200)
- entryHysteresisCount: Int (default 2)
- exitHysteresisCount: Int (default 3)
- exitBufferM: Int (default 30)
- autoDiscoveryEnabled: Boolean
- discoveryIntervalHours: Int (default 6)

**Timeline Behavior**:
- dayBoundaryMode: DayBoundaryMode (HOME_TIMEZONE / TRAVEL_AWARE)
- homeTimeZone: String (IANA zone ID)
- showGapSegments: Boolean (default true)
- showLowConfidenceSegments: Boolean (default true)
- minSegmentDurationMs: Long (default 60000 = 1 min, shorter segments merged)

**Map**:
- mapProvider: MapProvider (MAPLIBRE / future providers)
- tileProvider: TileProvider (OSM / CARTO / CUSTOM_URL)
- showRoutePolylines: Boolean
- routeColorByTransportMode: Boolean
- showVisitMarkers: Boolean
- visitMarkerNumbering: Boolean
- offlineMapsEnabled: Boolean
- clusterMarkersAtZoom: Int (zoom level below which markers cluster)

**Geocoding**:
- providerOrder: List<GeocodingProviderId>
- photonServerUrl: String (for self-hosted)
- autoGeocodeNewPlaces: Boolean
- geocodeLanguage: String (default device locale)

**Privacy**:
- databaseEncryptionEnabled: Boolean
- excludeZones: List<ExcludeZone> (geofenced areas where tracking auto-pauses)
- stripExactCoordinatesOnExport: Boolean (round to ~100m for sharing)
- exportIncludeRawSamples: Boolean (default false)

**Retention**:
- rawSampleRetentionDays: Int (default 90)
- derivedDataRetentionDays: Int (default 365)
- rollupRetentionDays: Int (default -1, forever)
- correctionFeedbackRetentionDays: Int (default 180)
- autoCleanupEnabled: Boolean

**Notifications**:
- trackingStatusNotificationEnabled: Boolean (always true for foreground service, but controls detail level)
- dailyInsightsEnabled: Boolean
- weeklyInsightsEnabled: Boolean
- anomalyAlertsEnabled: Boolean
- placeConfirmationPromptsEnabled: Boolean
- healthAlertsEnabled: Boolean

**Debug** (hidden behind developer toggle):
- showPipelineLatency: Boolean
- showSampleAccuracyOverlay: Boolean
- showConfidenceScores: Boolean
- logPipelineDecisions: Boolean
- forceProvider: GeocodingProviderId? (override provider selection)
- exportDiagnostics: Boolean

### 10.2 Preset Categories

Presets are organized into two tiers: **General presets** (optimized for a usage style) and **Traveler presets** (optimized for specific travel patterns). Each preset maps to concrete values for ALL configurable settings AND defines behavioral overrides for how timeline, place detection, and insights adapt.

#### 10.2.1 General Presets

| Setting | Battery Saver | Daily Commuter | Cyclist/Rider | Privacy Max | Precision Max |
|---------|--------------|----------------|---------------|-------------|---------------|
| Sampling (still) | 180s | 120s | 60s | 120s | 30s |
| Sampling (moving) | 30s | 15s | 5s | 15s | 3s |
| Accuracy | BALANCED | BALANCED | HIGH | BALANCED | HIGH |
| Min dwell | 10 min | 5 min | 5 min | 5 min | 2 min |
| Place radius | 120m | 80m | 60m | 100m | 40m |
| AR confidence | 40 | 50 | 60 | 50 | 70 |
| Sleep detection | ON | ON | ON | ON | ON |
| Step counting | OFF | ON | ON | OFF | ON |
| Raw retention | 30d | 90d | 90d | 30d | 365d |
| DB encryption | OFF | OFF | OFF | ON | OFF |
| Exclude zones | none | none | none | home+work | none |
| Route polylines | OFF | ON | ON | OFF | ON |
| Auto geocode | ON | ON | ON | OFF | ON |
| Offline maps | OFF | OFF | ON | OFF | ON |
| Battery threshold | 30% | 20% | 15% | 25% | 10% |
| Daily insights | OFF | ON | ON | OFF | ON |
| Place confirmations | OFF | ON | ON | OFF | ON |
| Day boundary mode | HOME_TZ | HOME_TZ | HOME_TZ | HOME_TZ | HOME_TZ |

#### 10.2.2 Traveler Presets

Traveler presets adapt detection parameters to match the user's travel pattern. The key differences are in how aggressively places are detected, how routes are captured, and what the timeline prioritizes.

| Setting | City Explorer | Short Tripper | Long Traveler | Road Tripper | Transit Commuter | Backpacker |
|---------|--------------|---------------|---------------|-------------|-----------------|------------|
| Sampling (still) | 45s | 60s | 90s | 60s | 60s | 120s |
| Sampling (moving) | 8s | 10s | 10s | 8s | 12s | 15s |
| Accuracy | HIGH | HIGH | HIGH | HIGH | BALANCED | BALANCED |
| Min dwell | 3 min | 5 min | 8 min | 10 min | 3 min | 10 min |
| Place radius | 40m | 60m | 80m | 120m | 50m | 100m |
| AR confidence | 60 | 50 | 50 | 50 | 60 | 40 |
| Sleep detection | ON | ON | OFF | ON | ON | ON |
| Step counting | ON | ON | ON | OFF | ON | ON |
| Raw retention | 90d | 120d | 365d | 180d | 90d | 365d |
| DB encryption | OFF | OFF | OFF | OFF | OFF | OFF |
| Route polylines | ON | ON | ON | ON | ON | ON |
| Auto geocode | ON | ON | ON | ON | ON | ON |
| Offline maps | ON | ON | ON | ON | OFF | ON |
| Battery threshold | 15% | 15% | 20% | 15% | 20% | 25% |
| Daily insights | ON | ON | ON | ON | ON | ON |
| Place confirmations | ON | ON | ON | ON | ON | ON |
| Day boundary mode | TRAVEL_AWARE | TRAVEL_AWARE | TRAVEL_AWARE | HOME_TZ | HOME_TZ | TRAVEL_AWARE |

#### 10.2.3 Traveler Preset Behavioral Overrides

Each traveler preset doesn't just change numbers — it changes **how the detection pipeline thinks**. These overrides are applied via `TravelerBehaviorProfile` which the pipeline reads alongside sampling settings.

**City Explorer** (walking-heavy urban discovery, many short stops):
- Timeline emphasis: maximize unique place discovery, show walking routes with POI density.
- Place detection: aggressive — low dwell threshold (3 min), tight place radius (40m) to distinguish nearby shops/cafes.
- Visit behavior: short visits are normal, not noise. Don't auto-merge visits < 5 min.
- Route behavior: capture detailed walking routes for "explore the city" replay.
- Insights: highlight "places discovered today", "neighborhoods explored", "walking distance".
- Geocoding: eager — geocode every detected place immediately (city areas have dense POI data).
- HDBSCAN params: min_cluster_size=2, cluster_selection_epsilon=30m (tight clustering for dense urban POIs).

**Short Tripper** (weekend getaway, 1-3 day trips, mix of driving and exploring):
- Timeline emphasis: balanced routes and places, trip-as-a-whole summary.
- Place detection: moderate — standard thresholds, focus on distinct stops (hotel, restaurant, attraction).
- Visit behavior: distinguish "passing through" (< 5 min, low confidence) from "intentional stop" (> 5 min, high confidence). Show both but visually differentiate.
- Route behavior: capture inter-city routes with distance/duration. Color-code by transport mode.
- Insights: highlight "trip summary" (total distance, places, transport modes), "time spent in transit vs exploring".
- Trip detection: auto-detect trip boundaries when user is > 30 km from home for > 4 hours. Group days into a "trip" in the timeline.

**Long Traveler** (multi-week/month travel, different cities/countries):
- Timeline emphasis: per-city/region grouping, accommodation detection, transit hub detection.
- Place detection: relaxed dwell threshold (8 min) to avoid false positives in transit hubs. Wider radius (80m) for large venues (airports, train stations).
- Visit behavior: distinguish "accommodation" (overnight dwell at non-home place) from "attraction" (daytime visit). Auto-suggest labeling hotels/hostels.
- Route behavior: capture intercity/international routes. Show timezone changes on timeline.
- Day boundary: TRAVEL_AWARE — timezone follows the traveler across borders.
- Sleep detection: OFF — sleep schedules are irregular during long travel. Use "accommodation detection" instead (longest nightly dwell = accommodation).
- Insights: highlight "countries/cities visited", "accommodation nights", "travel days vs rest days", "total trip distance".
- Geocoding: aggressive with caching — each new city needs fresh geocoding. Cache aggressively because the user will revisit nearby places.
- Data retention: 365 days — travelers want to keep trip memories long-term.

**Road Tripper** (long driving journeys, highway stops, campgrounds):
- Timeline emphasis: route is the story — continuous route with stop markers, distance milestones.
- Place detection: high dwell threshold (10 min) to avoid detecting every gas station or traffic stop. Wide radius (120m) for highway rest stops and campgrounds.
- Visit behavior: distinguish "fuel/rest stop" (10-20 min) from "destination stop" (> 1 hour). Visual differentiation on timeline.
- Route behavior: maximum detail on driving routes. Show distance markers every 50 km. Capture elevation changes for mountain passes.
- Insights: highlight "driving hours", "distance today", "stops today", "average speed", "longest stretch without stop".
- Day boundary: HOME_TZ — road trips usually stay in one or adjacent timezones. Simpler.
- Sleep detection: ON — road trippers have more regular sleep at campgrounds/motels.

**Transit Commuter** (daily public transit, train/bus/metro, urban):
- Timeline emphasis: commute optimization — show transit segments with boarding/alighting detection.
- Place detection: aggressive dwell (3 min), tight radius (50m) for transit stops/stations.
- Visit behavior: transit stops (< 5 min) are recorded but grouped as "transit" not "visits". Only stops where user remains > 5 min outside a vehicle count as visits.
- Route behavior: detect transit corridors. If same route taken daily, merge into "commute route" pattern.
- Insights: highlight "commute time today vs average", "transit time breakdown by mode", "on-time/delay detection based on travel time variance".
- Transport mode focus: distinguish bus/train/metro where possible from speed profiles and stop patterns.
- HDBSCAN params: min_cluster_size=5 (need repeated visits to confirm a transit stop as a real "place").

**Backpacker** (budget travel, mixed transport, hostels, long stays):
- Timeline emphasis: per-city stay duration, budget-friendly — minimize battery while capturing the journey.
- Place detection: relaxed (10 min dwell, 100m radius) — backpackers spend extended time at fewer places.
- Visit behavior: long stays (hostels, city exploration) are normal. Don't flag 8-hour visits as anomalies.
- Route behavior: capture transit between cities but not detailed intra-city walking (battery conservation).
- Sampling: battery-conservative moving interval (15s) because backpackers can't always charge.
- Battery threshold: high (25%) — charging opportunities are limited.
- Insights: highlight "cities visited", "average stay per city", "travel days", "rest days", "total trip duration".
- Data retention: 365 days — backpackers want to keep the full journey.
- Offline maps: ON — connectivity is unreliable.

#### 10.2.4 TravelerBehaviorProfile Schema

```
TravelerBehaviorProfile {
    presetId: String,

    // Place detection overrides
    minDwellForVisitMs: Long,
    minDwellForConfirmedVisitMs: Long,
    placeRadiusM: Int,
    hdbscanMinClusterSize: Int,
    hdbscanEpsilonM: Double,

    // Visit classification
    transitStopMaxDwellMs: Long,      // visits shorter than this near transit routes = transit stop
    shortStopMaxDwellMs: Long,        // visits shorter than this = short stop (visual differentiation)
    accommodationMinDwellMs: Long,    // overnight dwell threshold for accommodation detection

    // Route behavior
    routeDetailLevel: RouteDetail,    // MINIMAL / STANDARD / DETAILED
    showDistanceMilestones: Boolean,
    milestoneIntervalKm: Int,
    routeColorMode: RouteColorMode,   // BY_TRANSPORT / BY_SPEED / SOLID

    // Trip detection
    tripDetectionEnabled: Boolean,
    tripDistanceFromHomeKm: Int,      // how far from home to trigger "trip" grouping
    tripMinDurationHours: Int,        // how long away from home to confirm "trip"

    // Timeline emphasis
    timelineGrouping: TimelineGrouping, // BY_DAY / BY_CITY / BY_TRIP
    showTransitStopsOnTimeline: Boolean,
    showShortStopsOnTimeline: Boolean,

    // Insights focus
    insightsFocus: Set<InsightCategory> // PLACES_DISCOVERED, DISTANCE, COMMUTE, TRIP_SUMMARY, etc.
}
```

**Custom** preset: user modifies individual settings from any base. Any setting change while on a named preset switches to Custom with that preset as the base.

### 10.3 Preset Schema In DataStore

```
activePreset: String  // "BATTERY_SAVER" | "DAILY_COMMUTER" | ... | "CUSTOM"
customBasePreset: String?  // which preset Custom was derived from, for reset
// Then all individual setting keys as defined in 10.1
```

When `applyPreset(preset)` is called: write all settings atomically, then emit on `observeSettings()` flow, which triggers `RescheduleTrackingPolicyUseCase` to recalculate all sampling/detection parameters without app restart.

---

## 11. Encryption And Key Management

### 11.1 Database Encryption

- Use SQLCipher via `net.zetetic:android-database-sqlcipher` with Room.
- Encryption is opt-in (default OFF for performance, ON in Privacy Max preset).
- Encryption applies to the main Room database containing raw samples, derived data, and semantic data. DataStore preferences are NOT encrypted by SQLCipher (they use EncryptedSharedPreferences if privacy mode is ON).

### 11.2 Key Management

- Encryption key generated on first enable: 256-bit random key via `SecureRandom`.
- Key stored in Android Keystore (`AndroidKeyStore` provider) under alias `voyager_db_key`.
- Key is hardware-backed where available (StrongBox on supported devices, TEE otherwise).
- Key access requires device unlock (setUserAuthenticationRequired=false for background service access, but setUnlockedDeviceRequired=true).
- **No biometric binding** for v1 — the key is accessible whenever the device is unlocked. This allows the foreground service to access the database after boot without requiring user interaction.

### 11.3 Key Lifecycle

- **Enable encryption**: generate key, create encrypted copy of database, verify integrity, swap files, delete unencrypted copy.
- **Disable encryption**: decrypt database to plain copy, verify integrity, swap files, delete encrypted copy, remove key from Keystore.
- **Device migration**: key is NOT included in Android backup. On new device, database cannot be decrypted. User must export data before migration and import on new device. App detects orphaned encrypted database and prompts user.
- **Key loss**: if Keystore is wiped (factory reset, ROM flash), encrypted data is unrecoverable. App detects this on startup and offers to start fresh.

---

## 12. Data Retention Strategy

### 12.1 Retention Tiers

| Tier | Data | Default Retention | Configurable Range | Cleanup Action |
|------|------|-------------------|-------------------|----------------|
| Raw | raw_location_samples, raw_activity_samples, raw_step_samples | 90 days | 7-365 days | DELETE rows older than threshold |
| Derived | movement_segments, segment_evidence, routes, visits, visit_evidence | 365 days | 30-forever | DELETE segments + CASCADE to evidence, routes |
| Semantic | places, place_evidence, geocode_candidates | Forever | 90-forever | Only ARCHIVED places older than threshold (preserves name/category for reimport) |
| Rollups | daily_rollups, weekly_rollups, place_rollups | Forever | 365-forever | DELETE rollups older than threshold |
| Ops | current_runtime_state, health_log, pending_place_updates | 30 days (health_log), instant (pending after consumed) | Not configurable | DELETE consumed/old ops rows |
| Feedback | correction_feedback | 180 days | 30-365 days | DELETE old feedback (calibration profile persists independently) |
| Search | search_index, search_metadata | Mirrors source data | Automatic | Rebuilt when source data is cleaned |

### 12.2 Archive Tier

When derived data is deleted but rollups are kept, the rollups serve as an "archive" — you lose segment-level detail but retain daily summaries. This is the default behavior.

Optional "full archive" mode (power user): before deleting raw/derived data, compress and export to a local archive file (internal storage). Archive files can be reimported later to reconstruct detailed timeline for historical days.

### 12.3 DataRetentionWorker

Runs daily at 04:00 local time:
1. Read retention settings from DataStore.
2. For each tier, compute cutoff date.
3. Delete in batches of 1000 rows per transaction (avoid ANR on large deletes).
4. After raw/derived cleanup, trigger SearchIndexWorker to rebuild FTS.
5. Run `VACUUM` if deleted rows exceeded 10% of table size.
6. Log cleanup stats to health_log.

---

## 13. Emergency And Safety Hooks (Deferred — v2+)

### 13.1 Design Intent

Emergency features are deferred for v1 implementation but the architecture includes hook points so they can be added without structural changes.

### 13.2 Hook Points

**EmergencySensor interface** (in domain layer, no implementation in v1):
```
interface EmergencySensor {
    fun observeEmergencyTriggers(): Flow<EmergencyTrigger>
    fun getLastKnownSafeState(): SafeState
}
```

**EmergencyTrigger model** (in domain/model, defined but unused in v1):
- triggerType: CRASH_DETECTED / FALL_DETECTED / DEAD_MAN_SWITCH / SOS_MANUAL / ANOMALY_SEVERE
- detectedAt: Instant
- lastKnownLocation: LatLng
- confidence: Float
- supportingData: Map<String, Any>

**Architecture provisions**:
- `TrackingRuntimeCoordinator` has an `emergencySensorSlot: EmergencySensor?` field. When null (v1), emergency logic is skipped. When populated (v2+), coordinator subscribes to triggers.
- `NotificationChannel` for `emergency_alerts` is registered in v1 (HIGH importance) but never used — so it's ready when needed.
- `PlaceRepository` exposes `getFrequentPlaces(limit)` and `getHomePlace()` which are needed for dead-man-switch ("user hasn't been seen at any known place for X hours").
- `ExportRepository.exportEmergencySnapshot()` method signature defined — would produce a minimal JSON with last 24h of locations, current place, and emergency contacts. Not implemented in v1.

**Deferred features for v2+**:
- Crash/fall detection via accelerometer spike analysis.
- Dead-man-switch: configurable timer, resets on any motion or app interaction, triggers emergency contacts.
- SOS button in quick-access notification.
- Emergency contact management (stored locally, never synced).
- Integration with Android's Safety Hub APIs when available.

### 13.3 Why Defer

Crash/fall detection requires extensive sensor fusion tuning and false-positive reduction that is a standalone project. The hook points ensure zero architectural debt when the work begins.

---

## 14. Export And Import

### 14.1 Export Formats

| Format | Use Case | Content |
|--------|----------|---------|
| GPX | Standard GPS exchange, compatible with other apps | Tracks (routes), waypoints (places), time stamps |
| GeoJSON | Developer/analysis use | Full geometry with properties |
| Voyager JSON | Full backup/restore | All derived data, places, visits, settings, calibration |
| CSV | Spreadsheet analysis | Flat tables: one file per entity type |

### 14.2 Privacy Controls On Export

- `stripExactCoordinatesOnExport`: rounds lat/lng to 3 decimal places (~100m precision).
- `exportIncludeRawSamples`: if false (default), only export derived segments and visits.
- Exclude zones: any data within exclude zones is omitted from export.
- Export always includes a metadata header: export date, app version, coordinate precision, included date range.

### 14.3 Import

- Voyager JSON import: merge strategy — skip duplicates by (dayKey + startAt + segmentType) composite key, insert new, never overwrite existing user corrections.
- GPX import: create segments from tracks, attempt place matching for waypoints near existing places.
- Import runs in ExportWorker with progress reporting via WorkManager progress updates.

---

## 15. Test Plan And Migration

### 15.1 Mandatory Tests

**Pipeline tests**:
- Process death during pending visit candidate — restore and resume accumulation.
- Rapid sample burst after doze wake — all samples processed, none dropped.
- Concurrent worker + pipeline writes — no deadlock, no data corruption.
- Midnight crossing — segment and visit dayKey assigned correctly.
- DST spring-forward — no duplicate or missing segments in the gap.
- DST fall-back — UTC ordering prevents duplicate visits.
- Timezone change mid-day (travel) — DayBoundaryResolver produces correct split.

**Detection tests**:
- Visit non-overlap invariant — INSERT conflicting visit fails or auto-merges.
- Place matching hysteresis — rapid entry/exit near boundary does not create visit flicker.
- Gap creation — 5-minute GPS loss creates explicit GAP segment with correct reason.
- Step correlation — walking segment closing attaches correct step count.
- Sleep detection — entering sleep window + STILL correctly extends sampling interval.

**Lifecycle tests**:
- Boot restore — service starts after reboot with correct state.
- Null-intent service restart — state restored from current_runtime_state.
- Permission downgrade mid-session — graceful degradation to correct fallback tier.
- Battery saver toggle — sampling policy recalculated within 1 sample cycle.

**Provider tests**:
- Geocode provider fallback — Photon failure falls through to Nominatim.
- Geocode conflict resolution — multiple providers scored and ranked correctly.
- Provider rate limiting — Nominatim requests never exceed 1/sec.

**Search tests**:
- FTS query returns matching places, visits, days.
- Search with filters (date range + category) returns correct subset.
- Search index rebuild after rename reflects new name.

**Evidence tests**:
- Every surfaced segment has non-null segment_evidence.
- Every surfaced visit has non-null visit_evidence.
- Every place with bestProviderName has at least one geocode_candidate.
- InferenceExplanation contains human-readable explanationJson for all non-GAP segments.

**Worker tests**:
- Each worker is idempotent — running twice with same input produces same result.
- DataRetentionWorker respects tier boundaries — rollups survive raw cleanup.
- IntegrityRepairWorker detects and logs overlapping visits.

**UI tests**:
- Map and Timeline show identical data for same dayKey.
- Map tap -> Timeline scroll and vice versa within 300ms.
- Live tracking reflection: new sample appears in UI within 2 seconds.
- Settings change -> policy recalculation -> next sample uses new interval.
- Permission degradation banner appears within 1 second of permission loss.

### 15.2 Acceptance Criteria

1. Same-day Timeline and Map stay consistent — both consume identical `TimelineDay` model.
2. No silent visit overlap — enforced by transaction + integrity worker.
3. Explicit gap reasons — every gap has a non-null `gapReason`.
4. Under 2 seconds live UI reflection after accepted sample.
5. Settings changes reschedule relevant policies without app restart.
6. Every visible inference has supporting evidence and source attribution.
7. The app remains fully functional with only the free provider stack (no paid API keys).
8. Search returns relevant results within 500ms for databases up to 1 year of data.
9. Step counts are displayed on walking/running segments and daily summaries.
10. User corrections propagate to calibration profile within one FeedbackCalibrationWorker cycle.
11. Data retention cleanup runs without ANR on databases with 1M+ raw samples.
12. Export produces valid GPX/GeoJSON that imports correctly in third-party tools.

### 15.3 Migration Strategy

Four phases from current codebase to target architecture:

**Phase 1 — Stabilize single-writer path**:
- Introduce TrackingRuntimeCoordinator as sole authority.
- Introduce TimelineStateStore as sole state writer.
- Introduce PipelineSerializer channel.
- Redirect all existing start/stop/state paths through coordinator.
- Keep existing schema, existing screens. Behavior should be identical.

**Phase 2 — Introduce canonical schema**:
- Create new tables alongside legacy tables (movement_segments, segment_evidence, places, visits, visit_evidence, routes, etc.).
- Dual-write: pipeline writes to both legacy and new tables.
- New tables validated against legacy for correctness.
- Introduce FTS search_index.

**Phase 3 — Switch screens to new data**:
- Timeline, Map, Dashboard, Settings read from new repositories.
- Evidence blocks appear in detail sheets.
- Map-Timeline synchronization via shared TimelineDay model.
- Legacy tables become read-only fallback.
- Introduce all new workers.

**Phase 4 — Retire legacy**:
- Remove legacy tables, managers, orchestrators, query paths.
- Remove dual-write.
- Remove compatibility adapters.
- Full schema is the canonical schema defined in this blueprint.

**Compatibility layer**: during Phases 2-3, a `LegacyAdapter` reads from old tables and converts to new domain models. This allows screens to migrate incrementally — a screen can switch to the new repository while other screens still use the legacy adapter.

---

## 16. Assumptions And Research Anchors

- Defaults chosen here: on-device only, MapLibre-first, free-first providers, real-time UI reflection, evidence-backed UI for every displayed claim, SQLCipher encryption opt-in, Health Connect preferred for steps.
- Platform constraints and provider choices should align with current official guidance:
  - Android background location / foreground services: https://developer.android.com/develop/sensors-and-location/location/background
  - Foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
  - Activity Recognition transitions: https://developer.android.com/develop/sensors-and-location/location/transitions
  - WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager
  - Health Connect: https://developer.android.com/health-and-fitness/guides/health-connect/plan/introduction
  - MapLibre: https://maplibre.org/
  - Nominatim usage policy: https://operations.osmfoundation.org/policies/nominatim/
  - Photon geocoder: https://github.com/komoot/photon
  - SQLCipher for Android: https://www.zetetic.net/sqlcipher/sqlcipher-for-android/
  - Android Keystore: https://developer.android.com/privacy-and-security/keystore
  - Room FTS: https://developer.android.com/reference/androidx/room/Fts4
