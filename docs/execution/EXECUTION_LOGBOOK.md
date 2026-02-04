# Voyager Redesign — Execution Logbook

> Derived from: `blueprint.md` v2 (2026-03-16)
> Purpose: Track every deliverable item from the blueprint. Nothing gets skipped without a documented reason.
> Rule: If a task status is not `DONE`, it must have either an active blocker or an explicit `SKIP_REASON`.

---

## How To Use This Logbook

- **Status values**: `TODO` | `IN_PROGRESS` | `DONE` | `BLOCKED` | `SKIPPED` | `DEFERRED_V2`
- **Priority**: `P0` (must ship) | `P1` (should ship) | `P2` (nice to have) | `P3` (future)
- **Each task row**: ID, description, blueprint ref, priority, status, depends-on, acceptance criteria, skip reason
- **Phase gate rule**: a phase is not complete until every P0 and P1 task in it is `DONE` or has an approved `SKIP_REASON`
- Update this doc as work progresses. This is the single source of truth for what's done and what's not.

---

## AUDIT UPDATE — 2026-03-18

> Source: local code audit + `./gradlew testDebugUnitTest`
> Result: build failed in `:app:compileDebugKotlin`. This audit reopens several previously marked `DONE` tasks where current source no longer satisfies acceptance criteria.

### Audit Findings

- **Build integrity failure**: current branch does not compile. Failures include screen/navigation signature mismatches, unresolved symbols in presentation, and repository interface/implementation drift.
- **Mixed legacy/new model surface**: duplicate active domain types remain for `PlaceCategory`, `ExportFormat`, `SegmentType`, and legacy timeline models, causing ambiguous imports and broken implementations.
- **Hot pipeline not wired**: `PipelineSerializer.sampleChannel` exists, but no consumer processes it. `ProcessLocationSampleUseCase` is not connected to live ingestion.
- **Serializer scope too narrow**: runtime locking only covers final sample-acceptance metadata write, not the full segment/visit/place mutation cycle required by the blueprint.
- **Lifecycle authority only partial**: `TrackingRuntimeCoordinator` exists, but service handoff is incomplete and permission degradation/watchdog recovery are not fully centralized through it.
- **Runtime state model incomplete**: `TimelineStateStore` persists only a subset of `current_runtime_state`; live place state and worker heartbeat fields are not mapped into the domain state model.
- **Worker/pipeline boundary violated**: place discovery and repair workers directly mutate semantic/derived tables instead of staging changes through `pending_place_updates` and active-day-safe repair paths.
- **Evidence-backed UI incomplete**: repository/viewmodel paths drop or synthesize evidence fields instead of surfacing the full explainability contract required by the blueprint.
- **Document drift**: Phase 1 still has unfinished DAO tests, all Phase 13 verification remains `TODO`, and `14.05` is still open. Prior `DONE` rows below were ahead of actual implementation.

### Audit Action

- Tasks reopened below where the mismatch is concrete in source.
- Any remaining `DONE` row should be treated as provisional until Phase 13 verification and `14.05` are completed.

---

## PHASE 0 — PROJECT SETUP & MIGRATION PREP

> Goal: Establish the foundation so that all subsequent phases build on clean infrastructure.
> Blueprint ref: Section 15.3 Phase 1, Section 1.1, 2.8

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 0.01 | Audit current codebase: list all existing entities, DAOs, repositories, ViewModels, services | 15.3 Phase 1 | P0 | DONE | — | Markdown file mapping every current class to its blueprint replacement or retirement decision | |
| 0.02 | Define Gradle module structure (if multi-module) or package structure for 6-layer architecture | 1.1 | P0 | DONE | — | Package tree document matching: platform, capture, pipeline, storage, domain, presentation | |
| 0.03 | Set up Room schema export directory and versioning strategy | 2.x | P0 | DONE | — | `app/schemas/` contains auto-exported JSON for each migration, CI validates schema | |
| 0.04 | Set up Jetpack DataStore dependency and create `UserSettingsDataStore` skeleton | 2.8, 10.1 | P0 | DONE | — | DataStore reads/writes a test key without crash | |
| 0.05 | Set up Hilt dependency injection modules skeleton: `DatabaseModule`, `DataStoreModule`, `PipelineModule`, `TrackingModule`, `GeocodingModule`, `WorkerModule` | 1.1 | P0 | DONE | — | App compiles with empty Hilt modules providing placeholder bindings | |
| 0.06 | Create `LegacyAdapter` interface for compatibility layer | 15.3 | P1 | SKIPPED | 0.01 | Interface defined; old screens can read through adapter while new repos are built | Clean redesign — no legacy adapter needed |
| 0.07 | Set up WorkManager dependency and `VoyagerWorkerFactory` | 5.5 | P0 | DONE | 0.05 | WorkManager initializes with custom factory, one no-op test worker runs successfully | |
| 0.08 | Configure SQLCipher dependency (optional, gated behind build flag) | 11.1 | P2 | DONE | — | App builds with and without SQLCipher; encryption toggle compiles but is non-functional yet | |
| 0.09 | Set up MapLibre SDK dependency and verify basic map renders | 1.5 | P1 | DONE | — | Empty map screen renders tiles from OSM/Carto | |
| 0.10 | Define code style, lint rules, and pre-commit checks for blueprint conventions | — | P1 | DEFERRED_V2 | — | CI rejects: mutable state writes outside TimelineStateStore, GlobalScope usage, non-UTC timestamps in entities | Custom lint rules deferred — architecture enforces patterns through DI and single-authority design |

---

## PHASE 1 — DATA MODEL & SCHEMA

> Goal: All Room entities, DAOs, indices, migrations, and the singleton runtime state table exist and pass unit tests.
> Blueprint ref: Section 2 (all subsections)

### 1A — Raw Tables (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.01 | Create `RawLocationSampleEntity` with all columns from blueprint | 2.2 | P0 | DONE | 0.03 | Entity compiles, all columns present: sampleId, capturedAt, receivedAt, lat, lng, accuracyM, verticalAccuracyM, speedMps, bearingDeg, altitudeM, provider, isMock, batteryPct, isCharging, deviceIdleMode, permissionSnapshot, trackingSessionId, localTimeZone | |
| 1.02 | Create `RawLocationSampleDao` with insert + index queries: (trackingSessionId, capturedAt), (capturedAt), geohash | 2.2 | P0 | DONE | 1.01 | DAO insert returns rowId; query by trackingSessionId returns sorted samples; geohash column populated | |
| 1.03 | Create `RawActivitySampleEntity` with all columns | 2.2 | P0 | DONE | 0.03 | Entity: activitySampleId, activityType enum, confidence, source enum, transition, capturedAt, trackingSessionId | |
| 1.04 | Create `RawActivitySampleDao` with insert + index queries: (trackingSessionId, capturedAt) | 2.2 | P0 | DONE | 1.03 | DAO compiles, queries return expected results | |
| 1.05 | Create `RawStepSampleEntity` with all columns | 2.2 | P0 | DONE | 0.03 | Entity: stepSampleId, periodStart, periodEnd, stepCount, source enum, confidence, trackingSessionId | |
| 1.06 | Create `RawStepSampleDao` with insert + index queries: (periodStart, periodEnd) | 2.2 | P0 | DONE | 1.05 | DAO compiles, range query by period returns correct step deltas | |
| 1.07 | Create `TrackingSessionEntity` with all columns | 2.2 | P0 | DONE | 0.03 | Entity: sessionId, startedAt, endedAt, startedBy enum, endedBy enum, interruptionReason, restartReason, localTimeZone, totalSamples, totalDistanceM | |
| 1.08 | Create `TrackingSessionDao` with insert, update (endedAt), query active session | 2.2 | P0 | DONE | 1.07 | getActiveSession() returns session where endedAt IS NULL; endSession() updates endedAt | |

### 1B — Derived Tables (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.09 | Create `MovementSegmentEntity` with all columns, dayKey constraint | 2.3 | P0 | DONE | 0.03 | Entity: segmentId, segmentType enum (VISIT/WALK/RUN/CYCLE/DRIVE/TRANSIT/GAP/UNKNOWN_MOTION), startAt, endAt, startSampleId, endSampleId, distanceM, confidence, routeId, placeId, gapReason enum, dayKey, isUserCorrected. Indices: (dayKey, startAt), (placeId), (routeId) | |
| 1.10 | Create `MovementSegmentDao` with insert, update, query by dayKey, overlap check query | 2.3 | P0 | DONE | 1.09 | getSegmentsForDay(dayKey) returns sorted segments; getOverlapping(dayKey, startAt, endAt) correctly finds conflicts | |
| 1.11 | Create `SegmentEvidenceEntity` with all columns | 2.3 | P0 | DONE | 0.03 | Entity: segmentId (PK+FK), avgSpeedMps, maxSpeedMps, speedStdDev, sampleCount, activityVotes JSON, stepCount, headingConsistency, dwellMarkers, providerMix JSON, decisionRuleVersion, explanationJson, counterEvidenceJson | |
| 1.12 | Create `SegmentEvidenceDao` with insert, get by segmentId | 2.3 | P0 | DONE | 1.11 | getEvidenceForSegment(segmentId) returns non-null for inserted evidence | |
| 1.13 | Create `RouteEntity` with all columns | 2.3 | P0 | DONE | 0.03 | Entity: routeId, segmentId (FK), encodedPolyline, simplifiedPolyline, totalDistanceM, totalDurationMs, avgSpeedMps, maxSpeedMps, transportMode, sampleCount, boundingBoxJson. Index: (segmentId) | |
| 1.14 | Create `RouteDao` with insert, get by segmentId, get by bounding box range | 2.3 | P0 | DONE | 1.13 | getRouteForSegment(segmentId) returns route with decoded polyline; bounding box query works | |

### 1C — Semantic Tables (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.15 | Create `PlaceEntity` with all columns, indices | 2.4 | P0 | DONE | 0.03 | Entity: placeId, centroidLat, centroidLng, radiusM, geohash, s2CellId, confidence, lifecycleStatus enum, userDisplayName, bestProviderName, bestProviderSource, category enum, categoryConfidence, userCategory, createdAt, lastVisitedAt, mergedIntoPlaceId. Indices: (geohash), (s2CellId), (lifecycleStatus), (category) | |
| 1.16 | Create `PlaceDao` with insert, update, query by geohash prefix, query by lifecycle status, rename, merge | 2.4 | P0 | DONE | 1.15 | getPlacesNear(geohashPrefix) returns matching places; updateDisplayName() persists; merge sets mergedIntoPlaceId | |
| 1.17 | Create `VisitEntity` with all columns, indices | 2.4 | P0 | DONE | 0.03 | Entity: visitId, placeId (FK), arrivalAt, departureAt, dwellMs, source enum, confidence, supersedesVisitId, isUserCorrected, dayKey. Indices: (placeId, arrivalAt), (dayKey, arrivalAt) | |
| 1.18 | Create `VisitDao` with insert (with non-overlap @Transaction), update departure, query by dayKey, query by placeId, delete | 2.4 | P0 | DONE | 1.17 | insertWithOverlapCheck() rejects/merges overlapping visits within same dayKey; test proves non-overlap invariant holds | |
| 1.19 | Create `VisitEvidenceEntity` with all columns | 2.4 | P0 | DONE | 0.03 | Entity: visitId (PK+FK), entrySampleIds JSON, exitSampleIds JSON, dwellCurveJson, insideCount, outsideCount, suppressionReasons JSON, geofenceHints, confirmationRuleUsed, arrivalConfidence, departureConfidence | |
| 1.20 | Create `VisitEvidenceDao` | 2.4 | P0 | DONE | 1.19 | Insert and query by visitId works | |
| 1.21 | Create `PlaceEvidenceEntity` with all columns | 2.4 | P0 | DONE | 0.03 | Entity: placeId (PK+FK), clusterDensity, totalVisitCount, visitCountLast7d, visitCountLast30d, avgDwellMs, repeatabilityScore, namingCandidatesJson, userConfirmationCount, categoryReasoningJson, lastClusterUpdateAt | |
| 1.22 | Create `PlaceEvidenceDao` | 2.4 | P0 | DONE | 1.21 | Insert and query by placeId works | |
| 1.23 | Create `GeocodeCandidateEntity` with all columns | 2.4 | P0 | DONE | 0.03 | Entity: geocodeId, placeId (FK), provider enum, rank, language, displayName, structuredPartsJson, confidence, licenseClass enum, cachedUntil, fetchedAt. Index: (placeId, rank) | |
| 1.24 | Create `GeocodeCandidateDao` with insert, query by placeId (ordered by rank), delete expired | 2.4 | P0 | DONE | 1.23 | getCandidatesForPlace(placeId) returns ranked list; deleteExpired() removes rows past cachedUntil | |

### 1D — Ops Tables (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.25 | Create `CurrentRuntimeStateEntity` (singleton) | 2.5 | P0 | DONE | 0.03 | Entity: id (always 1), activeSessionId, currentSegmentId, pendingVisitCandidateJson, lastAcceptedSampleId, lastAcceptedAt, livePlaceStateJson, lastWorkerHeartbeats JSON, stateVersion, lastPipelineLatencyMs | |
| 1.26 | Create `CurrentRuntimeStateDao` with get, upsert (atomic read-modify-write) | 2.5 | P0 | DONE | 1.25 | Upsert increments stateVersion; get returns singleton; concurrent upsert from test threads doesn't corrupt | |
| 1.27 | Create `PendingPlaceUpdateEntity` | 2.5 | P1 | DONE | 0.03 | Entity: updateId, placeId, updateType enum, payloadJson, createdAt, consumedAt | |
| 1.28 | Create `PendingPlaceUpdateDao` with insert, query unconsumed, mark consumed | 2.5 | P1 | DONE | 1.27 | getUnconsumed() returns rows where consumedAt IS NULL; markConsumed() sets timestamp | |
| 1.29 | Create `HealthLogEntity` | 2.5 | P1 | DONE | 0.03 | Entity: logId, eventType enum, eventAt, detailsJson, acknowledged | |
| 1.30 | Create `HealthLogDao` with insert, query recent, delete old | 2.5 | P1 | DONE | 1.29 | Insert and query by eventType works; deleteOlderThan() purges correctly | |

### 1E — Search Tables (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.31 | Create FTS4 virtual table `search_index` with Room @Fts4 annotation | 2.6 | P1 | DONE | 0.03 | FTS table created with unicode61 tokenizer, remove_diacritics=1. Content columns: placeDisplayName, placeCategory, dayKey, segmentType, geocodeDisplayName, userNotes | |
| 1.32 | Create `SearchMetadataEntity` | 2.6 | P1 | DONE | 0.03 | Entity: searchRowId, sourceTable enum (PLACE/VISIT/SEGMENT/DAY), sourceId, relevanceBoost | |
| 1.33 | Create `SearchDao` with FTS match query, join with metadata for boosted ranking | 2.6 | P1 | DONE | 1.31, 1.32 | search("cafe") returns matching places; results ordered by relevance * boost | |

### 1F — Analytics Tables (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.34 | Create `DailyRollupEntity` with all columns | 2.7 | P1 | DONE | 0.03 | Entity: dayKey (PK), totalDistanceM, totalSteps, totalDwellMs, totalTransitMs, totalWalkMs, totalDriveMs, placeVisitCount, uniquePlacesVisited, firstActivityAt, lastActivityAt, dominantTransportMode, anomalyFlags JSON, computedAt | |
| 1.35 | Create `DailyRollupDao` with upsert, query by dayKey, query range | 2.7 | P1 | DONE | 1.34 | Upsert for same dayKey updates; range query returns sorted rollups | |
| 1.36 | Create `WeeklyRollupEntity` with all columns | 2.7 | P1 | DONE | 0.03 | Entity: weekKey (PK, YYYY-Www), avgDailyDistanceM, avgDailySteps, totalDistanceM, totalSteps, activeDayCount, topPlacesJson, transportModeDistributionJson, comparisonToPrevWeekJson, computedAt | |
| 1.37 | Create `WeeklyRollupDao` | 2.7 | P1 | DONE | 1.36 | Upsert and query works | |
| 1.38 | Create `PlaceRollupEntity` with all columns | 2.7 | P1 | DONE | 0.03 | Entity: placeId (PK), totalVisitCount, totalDwellMs, avgDwellMs, lastVisitAt, firstVisitAt, visitCountLast7d, visitCountLast30d, visitCountLast90d, dominantDayOfWeek, dominantTimeOfDay, computedAt | |
| 1.39 | Create `PlaceRollupDao` | 2.7 | P1 | DONE | 1.38 | Upsert and query by placeId works | |

### 1G — Feedback Table (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.40 | Create `CorrectionFeedbackEntity` | 9.2 | P1 | DONE | 0.03 | Entity: feedbackId, correctionType enum, entityType enum, entityId, beforeValueJson, afterValueJson, createdAt, propagated | |
| 1.41 | Create `CorrectionFeedbackDao` with insert, query by type+window, mark propagated | 9.2 | P1 | DONE | 1.40 | getUnpropagatedByType(type, since) returns matching corrections; markPropagated() sets flag | |

### 1H — Database Assembly & Migration (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 1.42 | Assemble `VoyagerDatabase` with all entities, all DAOs | 2.x | P0 | DONE | 1.01-1.41 | Database compiles, version number incremented, schema JSON exported | |
| 1.43 | Write Room migration from current schema to new schema (dual tables during Phase 2) | 15.3 | P0 | SKIPPED | 1.42 | Migration runs on existing database without data loss; old tables preserved; new tables created empty | Pre-production — fresh database at version 1, no migration needed |
| 1.44 | Write unit tests for all DAOs: insert, query, edge cases (null departureAt, overlap check) | 2.x | P0 | TODO | 1.42 | All DAO tests pass; overlap invariant test for visits passes; geohash query returns correct results | |
| 1.45 | Write migration test: old DB -> new DB integrity | 15.3 | P0 | SKIPPED | 1.43 | MigrationTest from version N to N+1 passes with test data | Pre-production — fresh database, no migration to test |

---

## PHASE 2 — DOMAIN MODELS & CORE UTILITIES

> Goal: All Kotlin domain models, enums, type converters, and core utility classes exist.
> Blueprint ref: Sections 2.9, 3.10, 4.2, 9.4, 10.2.4

### 2A — Enums & Value Types (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 2.01 | Define all enums: SegmentType, ActivityType, ActivitySource, TransitionType, GapReason, PlaceLifecycleStatus, PlaceCategory, VisitSource, CorrectionType, StartReason, StopReason, PauseReason, ResumeReason, StepSource, LicenseClass, GeocodingProvider, PermissionState, BatterySaverAction, DayBoundaryMode, ExportFormat, RouteColorMode, RouteDetail, TimelineGrouping, InsightCategory, AnomalySeverity | 2.x, 3.x, 10.x | P0 | BLOCKED | — | All enums compile; each has Room @TypeConverter if used in entity | 2026-03-18 audit: duplicate legacy enums/models remain active (`PlaceCategory`, `ExportFormat`, legacy `SegmentType`), causing ambiguous imports and compile failures. |
| 2.02 | Define `InferenceExplanation` data class matching Section 3.10 contract | 3.10 | P0 | DONE | 2.01 | Data class: label, confidence, supportingMetrics, counterEvidence, ruleVersion, sourceSet, humanExplanation | |
| 2.03 | Define `EvidenceBlock` and `ConfidenceBlock` for ViewModel state models | 4.3 | P0 | DONE | — | Used by all detail sheet state models; contains evidence summary + confidence with source attribution | |
| 2.04 | Define `ComparisonResult`, `MetricDelta`, `ComparisonHighlight` models | 7.1 | P1 | DONE | — | Models match blueprint Section 7.1 exactly | |
| 2.05 | Define `Anomaly`, `AnomalySeverity` models | 7.2 | P1 | DONE | — | Models match blueprint Section 7.2 exactly | |
| 2.06 | Define `TravelerBehaviorProfile` data class | 10.2.4 | P1 | DONE | 2.01 | All fields from blueprint 10.2.4 present; defaults for each preset compile | |
| 2.07 | Define `UserCalibrationProfile` data class | 9.4 | P1 | DONE | — | Fields: arWeight, speedHeuristicWeight, stepRateWeight, minDwellMinutes, placeMatchRadiusBoostM, regionOverrides | |

### 2B — Core Utilities (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 2.08 | Implement `DayBoundaryResolver` with HOME_TIMEZONE and TRAVEL_AWARE modes | 2.9 | P0 | DONE | 2.01 | Unit tests: midnight split in IST, timezone change mid-day in TRAVEL_AWARE, DST spring-forward gap, DST fall-back overlap, day-crossing segment assignment | |
| 2.09 | Implement geohash encoding/decoding utility (precision 7) | 2.2, 2.4 | P0 | DONE | — | encode(lat, lng) returns 7-char geohash; decode returns center + bounding box; prefix queries work | |
| 2.10 | Implement Google Polyline encoding/decoding utility | 2.3 | P0 | DONE | — | encode(List<LatLng>) produces string; decode() round-trips within 1e-5 precision | |
| 2.11 | Implement Douglas-Peucker polyline simplification | 2.3 | P1 | DONE | — | simplify(polyline, epsilon) reduces point count; visual accuracy preserved at given epsilon | |
| 2.12 | Implement haversine distance utility | 3.6 | P0 | DONE | — | distanceM(lat1, lng1, lat2, lng2) returns meters; tested against known coordinate pairs | |
| 2.13 | Implement `EvidencePolicy` annotation/interface | 1.3 | P0 | BLOCKED | 2.02 | Policy enforced: UI models without EvidenceBlock fail compilation or lint check | 2026-03-18 audit: no compile/lint enforcement exists; UI/repository paths still surface inferred labels with partial evidence. |

### 2C — Room Type Converters (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 2.14 | Write Room @TypeConverters for all enums, Instant<->Long, JSON string<->Map/List, LatLng | 2.x | P0 | DONE | 2.01 | All converters tested: enum round-trips, Instant round-trips, JSON map round-trips | |

---

## PHASE 3 — SINGLE-AUTHORITY INFRASTRUCTURE

> Goal: TrackingRuntimeCoordinator, TimelineStateStore, and PipelineSerializer exist and are the sole authorities. Old split-ownership paths are redirected.
> Blueprint ref: Sections 1.2, 1.4, 5.1-5.4

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 3.01 | Implement `TimelineStateStore` — sole write authority for `current_runtime_state` | 1.2 | P0 | DONE | 1.25, 1.26 | Exposes `StateFlow<TrackingRuntimeState>`; atomic upsert with stateVersion increment; no other class writes to current_runtime_state | |
| 3.02 | Implement `PipelineSerializer` — single-writer channel + Mutex for hot pipeline | 1.4 | P0 | BLOCKED | 3.01 | `Channel<RawSample>(BUFFERED, 64)` with SUSPEND policy; Mutex guards state commits; unit test: 100 samples processed in order without race | 2026-03-18 audit: channel exists, but no consumer is wired to `ProcessLocationSampleUseCase`; mutex only wraps final acceptance metadata write. |
| 3.03 | Implement `TrackingRuntimeCoordinator` — sole lifecycle authority | 1.2 | P0 | DONE | 3.01, 3.02 | Methods: start(reason), stop(reason), pause(reason), resume(reason), restore(), restoreFromCrash(). Test: start->stop round-trip updates state; restore reads persisted state | |
| 3.04 | Implement structured concurrency scopes: application scope, service scope | 5.3 | P0 | DONE | 0.05 | Application scope: @Singleton SupervisorJob+Default. Service scope: tied to service lifecycle. Worker scope: per CoroutineWorker. Lint rule: no GlobalScope usage | |
| 3.05 | Implement permission state monitor and degradation chain | 5.4 | P0 | IN_PROGRESS | 3.03, 1.29 | PermissionMonitor observes permission state; coordinator transitions through FINE->COARSE->NO_LOCATION->BACKGROUND_RESTRICTED; each transition writes health_log and adjusts capture | 2026-03-18 audit: `PermissionMonitor` exists, but full coordinator-owned degradation chain is not verified in code or tests. |
| 3.06 | Implement `BootReceiver` routing through coordinator.restore() | 5.2 | P0 | DONE | 3.03 | BOOT_COMPLETED, MY_PACKAGE_REPLACED intents -> goAsync() -> coordinator.restore(). Test: mock boot -> coordinator called, service started only if session was active | |
| 3.07 | Implement crash restore: detect activeSessionId with null endedAt, create GAP segment | 5.2 | P0 | DONE | 3.03, 1.09 | On restoreFromCrash(): GAP segment created between lastAcceptedAt and now; session resumed with restartReason=CRASH_RESTORE | |
| 3.08 | Redirect existing service manager/orchestrator calls through coordinator | 15.3 Phase 1 | P0 | DONE | 3.03 | All start/stop/pause paths go through coordinator; old direct service starts removed or delegated | |
| 3.09 | Implement foreground service as thin capture-only wrapper | 5.1 | P0 | BLOCKED | 3.03 | Service creates location/activity callbacks, feeds channel, manages notification. No business logic. Null intent restart -> coordinator.restoreFromCrash() | 2026-03-18 audit: service starts capture directly without supplying required `sessionId` to capture classes; current source does not compile. |
| 3.10 | Implement health monitoring: lastSampleAt, lastForegroundNotificationAt, lastWorkerSuccessAt, lastStateCommitAt, lastPipelineLatencyMs | 5.6 | P1 | IN_PROGRESS | 3.01, 1.29 | Health monitor checks heartbeats on interval; writes health_log when thresholds exceeded; restart actions triggered | 2026-03-18 audit: runtime state mapping omits persisted worker heartbeat/live-state fields, so health surface is only partial. |

---

## PHASE 4 — CAPTURE LAYER

> Goal: Location, activity, and step data flows from Android APIs into raw tables through the pipeline channel.
> Blueprint ref: Sections 3.1-3.4, 3.7

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 4.01 | Implement `LocationCapture` — FusedLocationProvider callback -> raw_location_samples + pipeline channel | 3.1 | P0 | DONE | 3.02, 1.01 | Location updates arrive, get inserted into raw_location_samples, get sent to pipeline channel | |
| 4.02 | Implement `AdaptiveSamplingPolicy` — recalculates interval based on motion state, speed variance, battery, preset | 3.2 | P0 | DONE | 4.01 | Policy returns correct interval per motion state; battery saver multiplier applied; preset overrides work; recalculated on motion state change | |
| 4.03 | Implement `ActivityCapture` — Activity Recognition Transition API callback -> raw_activity_samples | 3.3 | P0 | DONE | 1.03 | AR transitions arrive and get stored; ENTER/EXIT events for STILL, WALKING, RUNNING, IN_VEHICLE, ON_BICYCLE | |
| 4.04 | Implement speed/heading heuristic fallback for motion model | 3.3 | P0 | DONE | 4.01 | When AR unavailable or confidence < 50%, speed heuristics produce motion state; tested with mock sample streams | |
| 4.05 | Implement `FuseActivityStateUseCase` — confidence fusion formula | 3.3 | P0 | DONE | 4.03, 4.04 | finalConfidence = AR*0.6 + speed*0.25 + stepRate*0.15; step-rate override (80 steps/min + STILL -> WALKING); weights configurable | |
| 4.06 | Implement `StepCapture` — Health Connect primary, sensor fallback, pedometer tertiary | 3.7 | P1 | IN_PROGRESS | 1.05 | Steps ingested into raw_step_samples; source field correctly set; polling at 15min active, 60min sleep | 2026-03-18 audit: sensor path exists, but Health Connect primary and tertiary fallback are not implemented/wired. |
| 4.07 | Implement `SleepDetector` — time+motion+charging signals | 3.4 | P1 | DONE | 4.05 | SLEEP state confirmed when: inside sleep window + STILL >30min; exits on motion/step/interaction; sleep state persisted in runtime state | |
| 4.08 | Implement `RescheduleTrackingPolicyUseCase` — recalculate all intervals from current state + settings | 4.2 | P0 | DONE | 4.02 | Settings change -> policy recalculated -> next sample uses new interval; no app restart needed | |

---

## PHASE 5 — PIPELINE (PROCESSING & DETECTION)

> Goal: The full sample-to-state-commit pipeline works. Segments, visits, places, gaps, and evidence are created.
> Blueprint ref: Sections 3.1, 3.5-3.6, 3.8-3.10

### 5A — Pipeline Core (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 5.01 | Implement sample normalization stage (coordinate rounding, unit conversion) | 3.1 | P0 | DONE | 3.02 | Coordinates rounded to 7 decimal places; units normalized to meters/seconds | |
| 5.02 | Implement quality scoring stage (accuracy filter, mock detection, staleness) | 3.1 | P0 | DONE | 5.01 | Samples with accuracyM > threshold rejected; isMock samples rejected (unless debug); stale samples (capturedAt > 60s behind receivedAt) flagged | |
| 5.03 | Implement dedup/jitter suppression (distance < 3m AND time < 2s = skip) | 3.1 | P0 | DONE | 5.02 | Consecutive samples within 3m and 2s deduplicated; counter incremented; test with rapid-fire mock samples | |
| 5.04 | Implement `Segmenter` — open/close/extend segments based on motion state transitions | 3.1 | P0 | DONE | 5.03, 4.05 | Motion state change creates new segment boundary; segment type derived from fused activity; segment persisted with correct startSampleId/endSampleId | |
| 5.05 | Implement `CorrelateStepsUseCase` — attach step deltas to closing segments | 3.7 | P1 | DONE | 5.04, 1.05 | When segment closes: query raw_step_samples in range, sum deltas, write to segment_evidence.stepCount | |
| 5.06 | Implement state commit stage — atomic write to current_runtime_state + tables | 3.1 | P0 | BLOCKED | 5.04, 3.01 | @Transaction: update current_runtime_state, insert/update segment, insert evidence. stateVersion increments. StateFlow emits new state | 2026-03-18 audit: current commit stage only records accepted-sample metadata; segment/visit/place mutation is not atomic under serializer lock. |
| 5.07 | Implement `ResolveDayBoundaryUseCase` — compute dayKey for samples and segments | 4.2 | P0 | DONE | 2.08 | dayKey computed using DayBoundaryResolver per user setting; tested with HOME_TZ and TRAVEL_AWARE | |

### 5B — Visit & Place Detection (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 5.08 | Implement `DetectVisitUseCase` Phase 1 — candidate accumulation | 3.5 | P0 | DONE | 5.04 | >3 samples within 80m over >3 min creates VISIT_CANDIDATE; candidate state persisted in pendingVisitCandidateJson | |
| 5.09 | Implement `DetectVisitUseCase` Phase 2 — confirmation with non-overlap check | 3.5 | P0 | DONE | 5.08, 1.18 | Candidate dwell > minDwell -> confirmed visit; @Transaction overlap check executes; merge/reject/supersede logic works | |
| 5.10 | Implement departure detection — exit radius or gap-based | 3.5 | P0 | DONE | 5.09 | >3 samples outside radius -> departureAt set; gap >10min with next sample outside -> departure; evidence written | |
| 5.11 | Implement `MatchPlaceLiveUseCase` — nearest cluster + hysteresis | 3.6 | P0 | DONE | 5.08, 1.15 | Geohash prefix query finds nearby places; hysteresis entry (2 consecutive) and exit (3 consecutive + 30m buffer); match/reject recorded in evidence | |
| 5.12 | Implement visit_evidence writing — entry/exit samples, dwell curve, inside/outside counts | 3.5, 2.4 | P0 | DONE | 5.09, 1.19 | Every confirmed visit has complete visit_evidence row; dwell curve populated | |
| 5.13 | Implement gap detection and GAP segment creation with reason determination | 3.8 | P0 | DONE | 5.04 | Gap > 3x expected interval creates GAP segment; gapReason correctly determined from: permission state, doze mode, crash detect, GPS loss, manual pause, unknown | |

### 5C — Batch Processing (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 5.14 | Implement `DiscoverPlacesBatchUseCase` with HDBSCAN | 3.6 | P1 | DONE | 1.15, 1.27 | HDBSCAN on unassigned visit centroids; new CANDIDATE places created; results written to pending_place_updates; params: min_cluster_size=3, min_samples=2, haversine metric | |
| 5.15 | Implement pending_place_updates consumption in pipeline | 1.4 | P1 | BLOCKED | 5.14, 3.02 | Pipeline checks pending_place_updates each cycle; applies under serializer lock; marks consumed | 2026-03-18 audit: `pending_place_updates` schema exists, but no runtime consumer applies staged updates in the hot pipeline. |
| 5.16 | Implement `BuildEvidenceSummaryUseCase` — human-readable evidence for any entity | 4.2, 3.10 | P1 | IN_PROGRESS | 1.11, 1.19, 1.21 | For segment/visit/place: assembles InferenceExplanation with all fields from Section 3.10 contract | 2026-03-18 audit: evidence summary currently drops or synthesizes fields such as `sourceSet`, `counterEvidence`, and full explanation provenance. |

### 5D — Semantic & Inference (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 5.17 | Implement HOME detection algorithm | 3.9 | P1 | DONE | 1.38 | Highest nighttime dwell (22:00-06:00) over 14-day window AND >5 overnight visits; evidence includes overnight visit count, avg dwell, consistency | |
| 5.18 | Implement WORK detection algorithm | 3.9 | P1 | DONE | 1.38 | Highest weekday daytime dwell (09:00-17:00) over 14-day window AND >3 weekday visits | |
| 5.19 | Implement category inference (GYM, RESTAURANT, etc.) | 3.9 | P1 | DONE | 5.17, 1.23 | From geocode provider category + time patterns + dwell patterns; never >0.7 confidence from time alone | |
| 5.20 | Implement stride calibration from walking/running segments | 3.7 | P2 | DONE | 5.05 | calibratedStrideLengthM = distance / steps; rolling average stored in UserCalibrationProfile | |

---

## PHASE 6 — REPOSITORIES & USE CASES

> Goal: All repository interfaces and implementations from blueprint Section 4.1 are complete.
> Blueprint ref: Section 4

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 6.01 | Implement `TrackingRepository` — all methods from Section 4.1 | 4.1 | P0 | DONE | 3.03 | start/stop/pause/resume delegate to coordinator; observeRuntimeState() returns StateFlow; observeHealth() returns health metrics | |
| 6.02 | Implement `TimelineRepository` — observeDay, observeRange, rebuildDay, getSegmentDetails, observeLiveTimeline | 4.1 | P0 | BLOCKED | 1.10, 1.18, 1.14 | observeDay(dayKey) returns Flow<TimelineDay> with all segments, visits, routes for that day; live timeline updates within 2s | 2026-03-18 audit: repository drops evidence detail, returns placeholder live timeline state, and cannot satisfy live-update acceptance while the hot pipeline is unwired. |
| 6.03 | Implement `PlaceRepository` — all CRUD + merge/split/rename/confirm/delete/adjustTimes | 4.1 | P0 | BLOCKED | 1.16, 1.18 | All methods from blueprint; merge updates mergedIntoPlaceId; split creates new place + reassigns visits; rename sets userDisplayName | 2026-03-18 audit: implementation is broken by legacy/new `PlaceCategory` collisions; current source does not compile. |
| 6.04 | Implement `EvidenceRepository` — get evidence for segment, visit, place | 4.1 | P0 | IN_PROGRESS | 1.12, 1.20, 1.22 | Returns complete evidence models with all fields populated | 2026-03-18 audit: visit/place evidence is reduced to confidence-only blocks and segment explanation data is partial. |
| 6.05 | Implement `SearchRepository` — FTS search with filters and ranking | 4.1 | P1 | DONE | 1.33 | search() returns Flow<SearchResults> with boosted ranking; filters applied; tested with mock FTS data | |
| 6.06 | Implement `AnalyticsRepository` — dashboard, comparisons, anomalies | 4.1 | P1 | DONE | 1.35, 1.37, 1.39 | observeDashboard returns aggregated metrics; observeComparisons returns period-over-period deltas | |
| 6.07 | Implement `GeocodingRepository` — reverseGeocode, getProviderStatus, refreshGeocodeForPlace | 4.1, 6.1 | P0 | DONE | 1.24 | Reverse geocode queries providers in priority order; result stored in geocode_candidates; conflict resolution per Section 6.3 | |
| 6.08 | Implement `StepsRepository` — daily/hourly steps, segment steps, stride calibration | 4.1 | P1 | DONE | 1.06, 5.05 | observeDailySteps returns Flow<StepsSummary>; hourly aggregation works; stride calibration reads from DataStore | |
| 6.09 | Implement `MapRepository` — route for segment, visit markers, bounding box, offline regions | 4.1 | P1 | DONE | 1.14 | getRouteForSegment decodes polyline and simplifies per zoom; visit markers ordered by arrivalAt | |
| 6.10 | Implement `ExportRepository` — export/import in GPX, GeoJSON, Voyager JSON, CSV | 4.1, 14 | P2 | BLOCKED | 6.02 | Export day produces valid GPX file; import merges without duplicates; privacy controls applied | 2026-03-18 audit: implementation is broken by `ExportFormat` model collisions/signature drift; current source does not compile. |
| 6.11 | Implement `SettingsRepository` — observe, update, applyPreset, export/import settings | 4.1, 10.3 | P0 | DONE | 0.04 | observeSettings() returns StateFlow<UserSettings>; applyPreset writes all settings atomically; triggers RescheduleTrackingPolicy | |
| 6.12 | Implement `GenerateTimelineDayUseCase` — assemble TimelineDay model | 4.2 | P0 | DONE | 6.02 | Combines segments + visits + routes + evidence for a day into single TimelineDay model; Map and Timeline consume same model | |
| 6.13 | Implement `ApplyUserCorrectionUseCase` — process all correction types | 4.2, 9.1 | P1 | DONE | 6.03, 1.40 | Handles: rename, recategorize, reclassify segment, merge places, split place, delete visit, adjust times, confirm. Records to correction_feedback | |
| 6.14 | Implement `PropagateUserFeedbackUseCase` — update calibration from corrections | 9.3 | P1 | DONE | 6.13, 2.07 | Aggregates corrections over 30-day window; adjusts confidence weights, HDBSCAN params, dwell thresholds per Section 9.3 rules | |
| 6.15 | Implement `GenerateInsightsUseCase` — routine, trend, anomaly, place, achievement insights | 7.3 | P2 | DONE | 6.06 | Produces insight list from rollups; tested with mock rollup data | |
| 6.16 | Implement `GenerateComparisonsUseCase` — period-over-period comparison | 7.1 | P2 | DONE | 6.06 | Computes metricDeltas for all compared metrics; highlights generated; confidence lower for gapped periods | |
| 6.17 | Implement `DetectAnomaliesUseCase` — rolling baseline + sigma deviation | 7.2 | P2 | DONE | 6.06 | 30-day rolling mean+stddev; flags >2 sigma; severity levels correct; human explanation generated | |
| 6.18 | Implement `SearchTimelineUseCase` — FTS query + filter + ranking | 4.2 | P1 | DONE | 6.05 | Delegates to SearchRepository; applies SearchFilters; returns ranked results | |

---

## PHASE 7 — GEOCODING PROVIDER STACK

> Goal: Multi-provider geocoding works with conflict resolution and caching.
> Blueprint ref: Section 6

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 7.01 | Define `GeocodingProvider` interface | 6.1 | P0 | DONE | — | Interface: providerId, priority, licenseClass, rateLimitPerMinute, reverseGeocode(), isAvailable() | |
| 7.02 | Implement `AndroidGeocoderProvider` (priority 1) | 6.2 | P0 | DONE | 7.01 | Uses android.location.Geocoder; isAvailable checks Geocoder.isPresent(); returns structured result | |
| 7.03 | Implement `PhotonProvider` (priority 2, self-hosted) | 6.2 | P0 | DONE | 7.01 | HTTP client calls Photon API; configurable server URL from settings; rate limiting; structured result parsing | |
| 7.04 | Implement `NominatimProvider` (priority 3, rate-limited fallback) | 6.2 | P1 | DONE | 7.01 | Enforces 1 req/sec max; User-Agent set per OSM policy; returns structured result | |
| 7.05 | Implement `GeocodingProviderChain` — priority-ordered fallthrough with conflict resolution | 6.3 | P0 | DONE | 7.02, 7.03 | Queries providers in order; scores results per formula (priority*0.4 + confidence*0.3 + specificity*0.2 + recency*0.1); stores all candidates; picks best | |
| 7.06 | Implement name resolution display priority logic | 6.4 | P0 | DONE | 7.05 | Resolution chain: userDisplayName -> userCategory -> bestProviderName -> semantic label -> coordinates; source indicator attached | |

---

## PHASE 8 — WORKMANAGER JOBS

> Goal: All background workers from blueprint Section 5.5 are implemented, idempotent, and scheduled.
> Blueprint ref: Section 5.5, 12.3

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 8.01 | Implement `DiscoverPlacesWorker` (every 6 hours) | 5.5 | P1 | BLOCKED | 5.14 | Enqueued uniquely by `discover_places_{date}`; idempotent; runs HDBSCAN; writes to pending_place_updates | 2026-03-18 audit: worker bypasses `pending_place_updates` and directly mutates visits/places; unassigned-centroid collection path is incomplete. |
| 8.02 | Implement `GeocodeBackfillWorker` (every 4 hours) | 5.5 | P0 | DONE | 7.05 | Finds places with null bestProviderName; reverse geocodes each; stores candidates; idempotent | |
| 8.03 | Implement `DailyRollupWorker` (daily 03:00) | 5.5 | P1 | DONE | 1.35 | Computes daily_rollups from segments/visits for previous day; upserts; idempotent by dayKey | |
| 8.04 | Implement `WeeklyRollupWorker` (Monday 04:00) | 5.5 | P1 | DONE | 1.37 | Computes weekly_rollups from daily_rollups; includes comparisonToPrevWeek; idempotent by weekKey | |
| 8.05 | Implement `SemanticLabelWorker` (weekly + on significant change) | 5.5 | P1 | DONE | 5.17, 5.18 | Runs HOME/WORK/category inference; updates place labels; idempotent | |
| 8.06 | Implement `DataRetentionWorker` (daily 04:00) | 5.5, 12.3 | P1 | DONE | 1.42 | Applies retention per tier (Section 12.1); batch deletes of 1000 rows; VACUUM if >10% deleted; logs stats | |
| 8.07 | Implement `IntegrityRepairWorker` (daily 05:00) | 5.5 | P1 | BLOCKED | 1.42 | Checks: visit non-overlap, orphaned segments, missing evidence, orphaned routes. Logs issues. Auto-repairs where safe | 2026-03-18 audit: worker directly rewrites visits/segments, including recent active-day data, violating the intended worker/pipeline boundary. |
| 8.08 | Implement `SearchIndexWorker` (after geocode/rename/correction) | 5.5 | P1 | DONE | 1.31 | Rebuilds FTS index for changed entities; triggered by geocode backfill, place rename, visit correction | |
| 8.09 | Implement `StepSyncWorker` (15 min active, 60 min sleep) | 5.5 | P1 | DONE | 4.06 | Fetches latest step data from Health Connect/sensor; inserts raw_step_samples; idempotent by period | |
| 8.10 | Implement `ExportWorker` (on-demand) | 5.5, 14 | P2 | DONE | 6.10 | Generates export file in requested format; progress reporting via WorkManager; handles privacy controls | |
| 8.11 | Implement `FeedbackCalibrationWorker` (after corrections accumulate) | 9.3 | P2 | DONE | 6.14 | Runs PropagateUserFeedbackUseCase; updates calibration profile; idempotent | |
| 8.12 | Register all workers in `WorkerModule` with correct schedules and constraints | 5.5 | P0 | DONE | 8.01-8.11 | All workers registered; unique names; correct constraints (network for geocode, charging-preferred for rollups) | |

---

## PHASE 9 — SETTINGS, PRESETS & NOTIFICATIONS

> Goal: Full settings system with all presets, notification channels, and notification triggers.
> Blueprint ref: Sections 10, 8

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 9.01 | Implement all settings keys in DataStore per Section 10.1 | 10.1 | P0 | DONE | 0.04 | All settings groups: Tracking, Battery, Sleep, Activity Inference, Place Detection, Timeline Behavior, Map, Geocoding, Privacy, Retention, Notifications, Debug. Read/write works | |
| 9.02 | Implement general preset definitions with concrete values per Section 10.2.1 | 10.2.1 | P0 | DONE | 9.01 | Battery Saver, Daily Commuter, Cyclist/Rider, Privacy Max, Precision Max. Each maps to all settings | |
| 9.03 | Implement traveler preset definitions with concrete values per Section 10.2.2 | 10.2.2 | P1 | DONE | 9.01 | City Explorer, Short Tripper, Long Traveler, Road Tripper, Transit Commuter, Backpacker. Each maps to all settings | |
| 9.04 | Implement `TravelerBehaviorProfile` instances for each traveler preset | 10.2.3, 10.2.4 | P1 | DONE | 2.06 | Each preset has concrete TravelerBehaviorProfile with all behavioral overrides | |
| 9.05 | Implement `applyPreset()` — atomic write all settings + trigger policy recalculation | 10.3 | P0 | DONE | 9.01, 6.11 | Preset switch writes atomically; emits on observeSettings(); triggers RescheduleTrackingPolicy; no restart | |
| 9.06 | Implement Custom preset tracking (base preset, individual overrides) | 10.2 | P1 | DONE | 9.05 | Changing any setting while on named preset switches to Custom; tracks customBasePreset for reset | |
| 9.07 | Register all notification channels per Section 8.1 | 8.1 | P0 | DONE | — | Channels: tracking_status (LOW), tracking_alerts (HIGH), insights_daily (DEFAULT), insights_weekly (DEFAULT), user_actions (DEFAULT), system_health (LOW), emergency_alerts (HIGH, unused v1) | |
| 9.08 | Implement foreground service notification with live transport mode updates | 8.2 | P0 | DONE | 9.07, 3.09 | Persistent notification shows "Tracking active — Walking/Driving/etc"; updates on motion state change; Pause/Stop quick actions | |
| 9.09 | Implement permission degradation notification with Fix action | 8.2, 8.3 | P0 | DONE | 9.07, 3.05 | "Location accuracy reduced — tap to restore"; Fix action opens system permission settings | |
| 9.10 | Implement `NotificationActionReceiver` for place confirmation and tracking actions | 8.3 | P1 | DONE | 9.07, 6.13 | Confirm/Rename/Dismiss actions on place notifications; Pause/Stop actions on tracking notification; delegates to correct use cases | |
| 9.11 | Implement daily insight notification trigger | 8.2 | P2 | DONE | 9.07, 8.03 | Next morning: "Yesterday: X km, Y steps, Z places"; only if dailyInsightsEnabled | |
| 9.12 | Implement weekly summary notification trigger | 8.2 | P2 | DONE | 9.07, 8.04 | Monday: "Last week: X km total, Y new places"; only if weeklyInsightsEnabled | |
| 9.13 | Implement anomaly notification trigger | 8.2 | P2 | DONE | 9.07, 6.17 | "Unusual: you traveled 3x your average distance"; only if anomalyAlertsEnabled | |
| 9.14 | Implement place confirmation notification trigger | 8.2 | P1 | DONE | 9.07, 5.11 | "Is this 'Starbucks on MG Road'?"; triggered after new place geocoded; only if placeConfirmationPromptsEnabled | |

---

## PHASE 10 — ENCRYPTION & DATA RETENTION

> Goal: Optional database encryption works. Retention tiers enforced.
> Blueprint ref: Sections 11, 12

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 10.01 | Implement SQLCipher database factory gated by setting | 11.1 | P2 | DONE | 0.08, 9.01 | When databaseEncryptionEnabled=true: Room uses SQLCipher SupportFactory; when false: standard Room | |
| 10.02 | Implement key generation and Android Keystore storage | 11.2 | P2 | DONE | 10.01 | 256-bit key via SecureRandom; stored under `voyager_db_key` alias; hardware-backed where available | |
| 10.03 | Implement enable-encryption flow: generate key, copy DB, verify, swap | 11.3 | P2 | DONE | 10.02 | Enable encryption: existing data preserved; encrypted DB opens correctly; unencrypted copy deleted | |
| 10.04 | Implement disable-encryption flow: decrypt, verify, swap | 11.3 | P2 | DONE | 10.03 | Disable encryption: data preserved; key removed from Keystore | |
| 10.05 | Implement orphaned encrypted DB detection and key-loss recovery | 11.3 | P2 | DONE | 10.02 | App startup: if encrypted DB exists but key missing -> prompt user: "Data unrecoverable, start fresh?" | |
| 10.06 | Implement EncryptedSharedPreferences for DataStore when privacy mode ON | 11.1 | P2 | DONE | 10.01 | When privacy mode: DataStore backed by EncryptedSharedPreferences | |
| 10.07 | Implement DataRetentionWorker with tiered cleanup per Section 12.1 | 12.1, 12.3 | P1 | DONE | 8.06 | Raw: 90d default; Derived: 365d; Semantic: forever; Rollups: forever; Ops: 30d; Feedback: 180d. Batch deletes, VACUUM, logged | |

---

## PHASE 11 — VIEWMODELS & PRESENTATION LAYER

> Goal: All ViewModels expose state+intents. TimelineDay shared model consumed by both Map and Timeline.
> Blueprint ref: Section 4.3, 4.4

### 11A — Core ViewModels (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 11.01 | Implement `DayNavigationStateHolder` — @ActivityRetainedScoped shared navigation state | 4.4 | P0 | DONE | 6.12 | focusedSegmentId, focusedDayKey; survives recomposition; Map and Timeline both observe | |
| 11.02 | Implement `TimelineViewModel` — observes TimelineDay, emits state+intents | 4.3 | P0 | BLOCKED | 6.02, 11.01 | State: TimelineScreenState with segments list, each with EvidenceBlock. Intents: SelectSegment, SelectDay, ScrollToSegment | 2026-03-18 audit: viewmodel contract is out of sync with the current `TimelineScreen` usage and compile is broken. |
| 11.03 | Implement `MapViewModel` — observes TimelineDay + routes, emits state+intents | 4.3 | P0 | DONE | 6.09, 11.01 | State: MapScreenState with markers, routes, focused segment. Intents: TapSegment, TapMarker, PanToSegment | |
| 11.04 | Implement `DashboardViewModel` — observes daily/weekly rollups, comparisons, anomalies, insights | 4.3 | P1 | DONE | 6.06 | State: DashboardScreenState with summary metrics, trend charts, anomaly cards, insight cards | |
| 11.05 | Implement `PlaceDetailViewModel` — single place with visits, evidence, analytics | 4.3 | P0 | DONE | 6.03, 6.04 | State includes: place info, visit history, evidence block, confidence block, geocode candidates | |
| 11.06 | Implement `SegmentDetailViewModel` — single segment with evidence, explanation | 4.3 | P0 | DONE | 6.04 | State includes: segment info, inference explanation, evidence block, counter-evidence | |
| 11.07 | Implement `SettingsViewModel` — observes settings, apply presets, update individual settings | 4.3 | P0 | DONE | 6.11 | State: SettingsScreenState with all groups. Intents: ApplyPreset, UpdateSetting, ResetToPreset | |
| 11.08 | Implement `SearchViewModel` — search query, filters, results | 4.3 | P1 | DONE | 6.18 | State: SearchScreenState with query, activeFilters, results (days, places, visits). Debounced query emission | |
| 11.09 | Implement `TrackingControlViewModel` — start/stop/pause tracking, health status | 4.3 | P0 | BLOCKED | 6.01 | State: tracking status, permission state, health indicators. Intents: Start, Stop, Pause, Resume | 2026-03-18 audit: compile output shows unresolved symbols in tracking-tier/manual-reason handling; build is currently broken. |

### 11B — Map-Timeline Sync (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 11.10 | Implement Map tap -> Timeline scroll synchronization | 4.4 | P0 | DONE | 11.01, 11.02, 11.03 | Tap marker on Map -> DayNavigationState.focusedSegmentId updates -> Timeline scrolls to card. Latency < 300ms | |
| 11.11 | Implement Timeline tap -> Map pan synchronization | 4.4 | P0 | DONE | 11.01, 11.02, 11.03 | Tap segment card in Timeline -> Map animates camera to bounding box, highlights route/marker. Latency < 300ms | |
| 11.12 | Implement live tracking updates in both screens simultaneously | 4.4 | P0 | BLOCKED | 11.02, 11.03, 6.02 | During active tracking: new sample -> pipeline -> TimelineDay update -> both screens update within 2s | 2026-03-18 audit: live-update acceptance cannot be met while the hot pipeline has no active channel consumer. |

---

## PHASE 12 — UI SCREENS (COMPOSE)

> Goal: All screens built in Jetpack Compose, consuming ViewModels from Phase 11.
> Blueprint ref: Section 1.1 (Presentation layer), 4.3, 4.4

### 12A — Core Screens (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 12.01 | Build Timeline Screen — day view with segment cards, visit cards, gap cards | 4.3 | P0 | BLOCKED | 11.02 | Each segment card shows: type icon, time range, duration, distance, place name (with source), confidence indicator. Tap opens detail sheet | 2026-03-18 audit: screen still mixes legacy and redesigned models/state paths and currently fails compile. |
| 12.02 | Build Map Screen — MapLibre map with route polylines, visit markers, transport mode colors | 1.5, 4.4 | P0 | DONE | 11.03, 0.09 | Routes color-coded by transport mode; visit markers numbered by order; cluster markers at low zoom; tap marker syncs to Timeline | |
| 12.03 | Build Segment Detail Sheet — evidence block, inference explanation, counter-evidence | 4.3 | P0 | DONE | 11.06 | Shows: segment type, duration, distance, avg speed, sample count, step count, activity votes, confidence, humanExplanation, counterEvidence, source set | |
| 12.04 | Build Visit Detail Sheet — place info, arrival/departure, dwell, evidence | 4.3 | P0 | DONE | 11.05 | Shows: place name (with source indicator), arrival, departure, dwell, confidence, visit evidence, correction actions (rename, adjust times, delete) | |
| 12.05 | Build Place Detail Screen — place info, visit history, evidence, analytics, corrections | 4.3 | P0 | DONE | 11.05 | Shows: place name, category, total visits, avg dwell, visit list, evidence block, geocode candidates list, rename/merge/split/categorize actions | |
| 12.06 | Build Settings Screen — all groups from Section 10.1, preset selector | 10.1 | P0 | BLOCKED | 11.07 | All setting groups as collapsible sections; preset dropdown at top; changes apply immediately; Custom tracking | 2026-03-18 audit: screen references missing `SettingsUiState`/permission APIs and currently fails compile. |
| 12.07 | Build Settings Preset Picker — general presets + traveler presets with descriptions | 10.2 | P0 | DONE | 9.02, 9.03 | Two-tier picker: General (Battery Saver, etc.) and Traveler (City Explorer, etc.); each shows brief description of behavior changes | |

### 12B — Secondary Screens (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 12.08 | Build Dashboard Screen — daily summary, step chart, distance chart, top places | 7.3 | P1 | BLOCKED | 11.04 | Shows: today's summary card (distance, steps, places, active time), hourly step chart, week comparison mini-card, anomaly alerts, insight cards | 2026-03-18 audit: screen references missing permission/state APIs and currently fails compile. |
| 12.09 | Build Search Screen — search bar, filter chips, results list (days, places, visits) | 4.1 | P1 | DONE | 11.08 | Search bar with debounce; filter chips: date range, category, transport mode; results grouped by type; tap navigates to Timeline day or Place detail | |
| 12.10 | Build Comparison Screen — period-over-period metrics with trends | 7.1 | P2 | DONE | 6.16 | Period selector; metric cards with arrows (UP/DOWN/STABLE); percentage deltas; highlight sentences | |
| 12.11 | Build Export Screen — format picker, date range, privacy options, progress | 14 | P2 | DONE | 6.10 | Format: GPX/GeoJSON/Voyager JSON/CSV; date range picker; privacy toggles; progress bar during export | |
| 12.12 | Build Tracking Control UI — status banner, permission degradation banner | 5.4, 4.3 | P0 | BLOCKED | 11.09 | Start/Stop/Pause button; current status indicator; permission degradation banner with Fix button; health warning banners | 2026-03-18 audit: UI depends on unresolved `TrackingControlViewModel` symbols and is not buildable. |
| 12.13 | Build Geocoding Provider Settings Sub-screen | 6.1, 10.1 | P1 | DONE | 9.01, 7.05 | Provider reordering (drag); Photon server URL input; test connection button; provider status indicators | |
| 12.14 | Build Privacy Settings Sub-screen — encryption toggle, exclude zones, export controls | 10.1, 11.1 | P2 | DONE | 9.01, 10.01 | Encryption enable/disable with warning dialog; exclude zone map picker; export privacy toggles | |

---

## PHASE 13 — INTEGRATION, TESTING & POLISH

> Goal: End-to-end flows work. All mandatory tests from blueprint Section 15.1 pass.
> Blueprint ref: Section 15

### 13A — Integration Tests (P0)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 13.01 | Test: process death during pending visit -> restore and resume accumulation | 15.1 | P0 | TODO | 5.08 | Kill process with active VISIT_CANDIDATE; restart; candidate restored from pendingVisitCandidateJson; visit eventually confirmed | |
| 13.02 | Test: boot restore -> service starts with correct state | 15.1 | P0 | TODO | 3.06 | Simulate BOOT_COMPLETED; coordinator.restore() called; service starts if session was active | |
| 13.03 | Test: null-intent service restart -> state restored | 15.1 | P0 | TODO | 3.07 | Service killed by system; restarted with null intent; state restored from current_runtime_state | |
| 13.04 | Test: permission downgrade mid-session -> graceful degradation | 15.1 | P0 | TODO | 3.05 | Revoke fine location during tracking; verify: coarse mode activated, health_log written, notification shown, pipeline continues | |
| 13.05 | Test: visit non-overlap invariant under concurrent inserts | 15.1 | P0 | TODO | 5.09 | Two visits with overlapping times for same dayKey; verify: one rejected or merged, never both stored | |
| 13.06 | Test: midnight crossing and DST changes | 15.1 | P0 | TODO | 5.07 | Segment spanning midnight: dayKey = startAt's day. DST spring-forward: no duplicate. DST fall-back: UTC ordering correct | |
| 13.07 | Test: route-gap reconstruction | 15.1 | P0 | TODO | 5.13 | 5-minute GPS loss mid-walk -> GAP segment created with correct reason and evidence | |
| 13.08 | Test: live UI reflection latency < 2 seconds | 15.2 | P0 | TODO | 11.12 | New sample -> pipeline -> state commit -> StateFlow -> UI update measured < 2000ms | |
| 13.09 | Test: settings change -> policy recalculation -> no restart | 15.2 | P0 | TODO | 9.05 | Change sampling preset; verify next sample uses new interval; no service restart | |

### 13B — Provider & Worker Tests (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 13.10 | Test: geocode provider fallback (Photon fail -> Nominatim) | 15.1 | P1 | TODO | 7.05 | Mock Photon failure; verify Nominatim called; result stored correctly | |
| 13.11 | Test: geocode conflict resolution scoring | 15.1 | P1 | TODO | 7.05 | Two providers return different names; verify scoring formula produces correct winner | |
| 13.12 | Test: Nominatim rate limiting (never > 1 req/sec) | 15.1 | P1 | TODO | 7.04 | 10 rapid geocode requests; verify Nominatim calls spaced >= 1 second | |
| 13.13 | Test: worker idempotency (run twice, same result) | 15.1 | P1 | TODO | 8.01-8.11 | Each worker: run with same input twice; verify database state identical after both runs | |
| 13.14 | Test: DataRetentionWorker respects tier boundaries | 15.1 | P1 | TODO | 8.06 | Raw data deleted at 90d; rollups preserved; semantic places preserved; cascade correct | |
| 13.15 | Test: IntegrityRepairWorker detects overlapping visits | 15.1 | P1 | TODO | 8.07 | Manually insert overlapping visits; run worker; verify logged and auto-repaired | |

### 13C — Evidence & Search Tests (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 13.16 | Test: every surfaced segment has non-null segment_evidence | 15.1 | P1 | TODO | 5.16 | Query all segments; verify each has segment_evidence with explanationJson populated | |
| 13.17 | Test: every surfaced visit has non-null visit_evidence | 15.1 | P1 | TODO | 5.12 | Query all visits; verify each has visit_evidence row | |
| 13.18 | Test: search returns matching results with correct ranking | 15.1 | P1 | TODO | 6.05 | Insert test data; search "Home"; verify Home place ranked highest due to user-rename boost | |
| 13.19 | Test: search index rebuild after rename reflects new name | 15.1 | P1 | TODO | 8.08 | Rename place; trigger SearchIndexWorker; verify search finds new name | |

### 13D — UI Tests (P1)

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 13.20 | Test: Map and Timeline show identical data for same dayKey | 15.1 | P1 | TODO | 11.12 | Both screens consume same TimelineDay; segment count, visit count, route count match | |
| 13.21 | Test: Map tap -> Timeline scroll within 300ms | 15.1 | P1 | TODO | 11.10 | Instrumented test: tap map marker, measure time to Timeline scroll animation start | |
| 13.22 | Test: permission degradation banner appears within 1 second | 15.1 | P1 | TODO | 12.12 | Revoke permission; verify banner visible within 1000ms | |

---

## PHASE 14 — LEGACY RETIREMENT

> Goal: All legacy tables, managers, and adapters removed. Blueprint schema is the sole schema.
> Blueprint ref: Section 15.3 Phase 4

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| 14.01 | Remove dual-write: pipeline writes only to new tables | 15.3 Phase 4 | P0 | DONE | 13.01-13.09 | Dual-write removed; all reads from new schema; no legacy table references in pipeline | |
| 14.02 | Remove LegacyAdapter and all compatibility layer code | 15.3 Phase 4 | P0 | DONE | 14.01 | No LegacyAdapter references remain; all screens use new repositories | |
| 14.03 | Drop legacy tables via Room migration | 15.3 Phase 4 | P0 | DONE | 14.02 | Migration drops old tables; schema JSON reflects only blueprint tables | |
| 14.04 | Remove old managers, orchestrators, legacy query paths | 15.3 Phase 4 | P0 | BLOCKED | 14.02 | No dead code; no unused imports; all coordinator/store/pipeline paths are the only paths | 2026-03-18 audit: duplicate legacy models/query paths remain in active source, so legacy retirement is not complete. |
| 14.05 | Final integrity check: all acceptance criteria from Section 15.2 verified | 15.2 | P0 | TODO | 14.01-14.04 | All 12 acceptance criteria pass; documented evidence for each | |

---

## DEFERRED — V2+ (Emergency & Safety)

> Blueprint ref: Section 13. Hook points exist from Phase 3 onward but no implementation in v1.

| ID | Task | Blueprint Ref | Priority | Status | Depends On | Acceptance Criteria | Skip Reason |
|----|------|--------------|----------|--------|------------|-------------------|-------------|
| V2.01 | Implement `EmergencySensor` interface (crash/fall detection) | 13.2 | P3 | DEFERRED_V2 | — | — | Per user decision: hook points designed, implementation deferred to v2 |
| V2.02 | Implement dead-man-switch | 13.2 | P3 | DEFERRED_V2 | — | — | Deferred to v2 |
| V2.03 | Implement SOS button in notification | 13.2 | P3 | DEFERRED_V2 | — | — | Deferred to v2 |
| V2.04 | Implement emergency contact management | 13.2 | P3 | DEFERRED_V2 | — | — | Deferred to v2 |
| V2.05 | Implement `exportEmergencySnapshot()` | 13.2 | P3 | DEFERRED_V2 | — | — | Deferred to v2 |

---

## SUMMARY — TASK COUNTS BY PHASE

| Phase | Description | P0 | P1 | P2 | P3 | Total |
|-------|-------------|----|----|----|----|-------|
| 0 | Project Setup | 6 | 3 | 1 | 0 | 10 |
| 1 | Data Model & Schema | 23 | 12 | 0 | 0 | 35 |
| 2 | Domain Models & Utilities | 6 | 4 | 0 | 0 | 10 |
| 3 | Single-Authority Infrastructure | 9 | 1 | 0 | 0 | 10 |
| 4 | Capture Layer | 5 | 2 | 1 | 0 | 8 |
| 5 | Pipeline | 11 | 6 | 1 | 0 | 18 |
| 6 | Repositories & Use Cases | 7 | 5 | 6 | 0 | 18 |
| 7 | Geocoding Provider Stack | 5 | 1 | 0 | 0 | 6 |
| 8 | WorkManager Jobs | 1 | 8 | 2 | 0 | 11 |
| 9 | Settings, Presets & Notifications | 4 | 4 | 6 | 0 | 14 |
| 10 | Encryption & Data Retention | 0 | 1 | 6 | 0 | 7 |
| 11 | ViewModels & Presentation | 9 | 2 | 0 | 0 | 11 |
| 12 | UI Screens | 7 | 3 | 4 | 0 | 14 |
| 13 | Integration & Testing | 9 | 13 | 0 | 0 | 22 |
| 14 | Legacy Retirement | 5 | 0 | 0 | 0 | 5 |
| V2 | Deferred (Emergency) | 0 | 0 | 0 | 5 | 5 |
| **TOTAL** | | **107** | **65** | **27** | **5** | **204** |

---

## PHASE GATE CHECKLIST

Before moving to the next phase, verify:

- [ ] All P0 tasks in current phase: `DONE`
- [ ] All P1 tasks in current phase: `DONE` or have approved `SKIP_REASON`
- [ ] All P2 tasks: `DONE`, `DEFERRED_V2`, or have `SKIP_REASON`
- [ ] No `BLOCKED` tasks without an active resolution plan
- [ ] Unit tests for completed tasks pass
- [ ] No regressions in previously completed phases

---

## SKIP REASON RULES

A task may be skipped ONLY if:

1. **Technical blocker**: dependency unavailable (e.g., Health Connect not on test device) — document workaround timeline
2. **Scope reduction**: explicit user decision to defer — reference conversation date
3. **Superseded**: another task covers this requirement — reference the superseding task ID
4. **Not applicable**: requirement doesn't apply to current state — explain why

A skip reason of "we'll do it later" is NOT valid unless it's accompanied by a specific phase or version target.
