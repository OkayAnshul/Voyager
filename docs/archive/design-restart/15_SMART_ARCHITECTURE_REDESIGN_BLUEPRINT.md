# Voyager Smart Architecture Redesign Blueprint

Date: 2026-03-09  
Status: Planning and architectural redesign only (no code changes in this phase)

## 1. Executive Summary

Voyager already has strong foundations: local-first tracking, state gateway, background orchestration, place detection, timeline/map/insights, and personalization settings.  
However, smartness is uneven across the stack. Some features are smart (quality filtering, dwell logic, clustering, caching), while core user truth still degrades in edge cases (movement between places, short transient stops, dual lifecycle control, worker fallback behavior).

This blueprint redesigns the architecture for:
1. Whole-system smartness (not timeline-only).
2. Truthful representation of user movement and stops.
3. Full edge-case resilience across startup/shutdown/restart/permission/process/device constraints.
4. Strict alignment between settings, runtime behavior, and UI outputs.

---

## 2. Current System Pipeline (As-Is)

### 2.1 Lifecycle and startup pipeline
1. App launch initializes migrations, state sync, orchestrator checks, WorkManager verification.
2. MainActivity initializes workers and may auto-start tracking when permissions transition to full granted.
3. BootReceiver can restart tracking based on persisted tracking flag.

### 2.2 Tracking pipeline
1. `LocationServiceManager` starts/stops foreground `LocationTrackingService`.
2. Service receives fused location updates.
3. Service applies filtering (accuracy, speed, movement/time gating, sleep/activity rules).
4. Accepted location is sent to `SmartDataProcessor`.
5. `SmartDataProcessor` stores location, updates state timestamps, manages proximity/dwell/visit lifecycle.
6. Service triggers place-detection scheduling by count/time rules.

### 2.3 Place/visit pipeline
1. Place detection use case performs quality filtering and DBSCAN clustering.
2. New places are created when cluster uniqueness checks pass.
3. Initial visits are generated from clustered historical points.
4. Enrichment resolves address/business details via geocoding + cache.

### 2.4 UI truth pipeline
1. Timeline is generated primarily from visits, grouped and gap-filled.
2. Map renders date-filtered locations and visited places.
3. Insights aggregate analytics and pattern outputs.
4. Settings update preferences with debounced persistence.

### 2.5 State and sync pipeline
1. `StateWriteGateway` is intended as single write authority.
2. `AppStateManager` maintains in-memory state with mutex/debounce/circuit-breaker.
3. Event dispatcher propagates state events.
4. `StateSynchronizer` monitors consistency (notification-only mode).

### 2.6 Background orchestration pipeline
1. WorkManagerHelper enqueues periodic and one-time place detection.
2. Worker monitor can invoke fallback worker under failure-like states.
3. Daily summary/review workers are scheduled by settings flow.

---

## 3. Core Architecture Findings (Generalized)

## 3.1 Smartness is fragmented by layer
1. Detection logic is smart for stable places; timeline logic is not equally smart for transit and temporary street stops.
2. Insights smartness depends on visit/place truth; when timeline truth is reduced to visits-only, derived intelligence is degraded.
3. Settings expose many parameters, but some runtime decisions still use hardcoded thresholds.

## 3.2 Truth model mismatch
1. Current user truth is mostly “confirmed place visits + synthetic untracked gaps.”
2. Real mobility truth includes at least five states:
3. Not tracking.
4. Untracked while tracking (missing samples/quality rejected).
5. Transit movement.
6. Transient stop (street/temporary).
7. Confirmed place visit.
8. Existing architecture does not model these states as first-class domain events.

## 3.3 Lifecycle control conflict risk
1. Tracking lifecycle is controlled by both manager and orchestrator flows.
2. Boot path and manual path do not fully converge on a single lifecycle authority.
3. Service-running inference can rely on assumed sources (state/prefs), not only service truth.

## 3.4 Background scheduling conflict risk
1. One-time detection can be non-unique under frequent triggers.
2. Fallback behavior can activate while primary work is still validly queued/blocked.
3. This can duplicate logical execution paths and distort state timing.

## 3.5 Personalization contract drift
1. Some thresholds are configurable in preferences but not consistently consumed in runtime logic.
2. Some UI/worker scheduling paths use fixed values despite preference fields existing.

## 3.6 Timeline-specific structural limitations
1. Segment grouping uses fixed gap constant rather than preference-driven window.
2. Timeline generation is visit-first, so movement segments between visits are not first-class.
3. Gap filling from midnight can misrepresent “pre-tracking” time as generic unknown/untracked.

## 3.7 Deep Debug Findings Table (Second-Pass Audit)

| ID | Severity | Area | Finding | Evidence (code) | Improvement Direction |
|---|---|---|---|---|---|
| F-01 | Critical | Timeline truth | Timeline grouping window is hardcoded and ignores personalization setting. | `GenerateTimelineSegmentsUseCase`: hardcoded `30L` grouping and unused `preferences` read. | Use `timelineTimeWindowMinutes` from preferences in grouping logic. |
| F-02 | Critical | Timeline truth | Timeline is visits-only; movement and transient street stops are not first-class segments. | `GenerateTimelineSegmentsUseCase` builds from `visits` then fills synthetic gaps. | Introduce unified movement segment model (`TRANSIT`, `TRANSIENT_STOP`, etc.). |
| F-03 | High | Timeline semantics | Gaps always start from midnight, not from tracking session start. | `fillGaps(startOfDay, firstStart)` in timeline use case. | Split `NOT_TRACKING` vs `UNTRACKED_WHILE_TRACKING` and anchor to session start. |
| F-04 | High | Timeline UI safety | Gap segments can be treated like real places for long-press rename / “View on Map”. | `TimelineScreen` passes all segments to actions without `isGap` guard. | Disable rename/map actions for non-place/gap segments. |
| F-05 | Critical | Worker orchestration | One-time place detection path is non-unique and can overlap. | `WorkManagerHelper.enqueuePlaceDetectionWork` uses `enqueue()` for one-time work. | Use `enqueueUniqueWork` with explicit policy for one-time detection intent. |
| F-06 | Critical | Worker orchestration | Fallback may trigger on valid `ENQUEUED/BLOCKED` states, duplicating runs. | Worker monitor fallback logic on non-terminal states. | Restrict fallback to terminal failure classes. |
| F-07 | High | Lifecycle authority | Dual control paths exist (`LocationServiceManager` and `TrackingOrchestrator`). | UI path uses manager; boot path uses orchestrator. | Converge all start/stop/restart through single orchestrator authority. |
| F-08 | High | Boot reliability | Boot receiver launches coroutine without `goAsync` lifecycle guard. | `BootReceiver.onReceive` with `scope.launch` only. | Use `goAsync` or enqueue resilient boot task via WorkManager. |
| F-09 | High | State/session integrity | DataFlow monitoring uses `currentSessionStartTime`, but tracking status update does not refresh it. | `CurrentStateDao.updateTrackingStatus` updates only `trackingStartTime`; `DataFlowOrchestrator` reads `currentSessionStartTime`. | Update session start consistently when tracking starts. |
| F-10 | Medium | Personalization drift | Geocoding cache settings exist but runtime uses fixed precision/duration values. | `GeocodingRepositoryImpl`: fixed `cacheDurationDays = 30`, rounding at 3 decimals. | Wire geocoding cache settings into repository runtime behavior. |
| F-11 | Medium | Map correctness | Date-place query uses `DISTINCT place` and loses revisit sequence semantics. | `PlaceDao.getPlacesVisitedOnDate` uses `SELECT DISTINCT p.*`. | For route/timeline truth, derive ordered segments from visit/segment stream, not distinct places. |
| F-12 | Medium | Map chronology | Route overlay sorts by `place.lastVisit` (global), not selected-date sequence. | `OpenStreetMapView.addPlaceRoute`: `sortedBy { it.lastVisit }`. | Route should consume day-scoped ordered segment/visit timeline. |
| F-13 | Medium | Overnight edge case | Date-place query filters by `entryTime` only and can miss cross-boundary visits. | `PlaceDao.getPlacesVisitedOnDate`: `v.entryTime >= start AND < end`. | Use overlap logic similar to visit query (`entry before day and exit during day`). |
| F-14 | Medium | Insights fidelity | Insights period selection does not fully scope insight engine output by selected period. | `InsightsViewModel` calls `insightEngine.generateInsights()` without explicit period bounds. | Add period-aware insight generation path. |
| F-15 | Medium | Service truth | Service running status may be “assumed running” from state/prefs fallbacks. | `LocationServiceManager.isServiceActuallyRunning` fallbacks to app state/prefs. | Separate “confirmed service running” vs “expected running” states in UI/domain. |
| F-16 | Low | Explainability | Untracked gaps do not include reason codes (no permission, low accuracy, paused, etc.). | Gap segments are synthetic placeholder only. | Add structured gap reason metadata for user trust. |
| F-17 | Low | Config coherence | Validation/runtime bounds still have inconsistency risk across components. | Existing multi-layer preference validation paths. | Define single source-of-truth constants for ranges/thresholds. |
| F-18 | Low | Performance | Repeated repository fetches in analytics paths increase unnecessary work. | Multiple places loaded repeatedly in stats flows. | Cache per-pass maps and centralize projection inputs. |

Notes:
1. Findings above include earlier observations plus additional second-pass discoveries not previously captured in this blueprint.
2. Evidence references are intentionally subsystem-level; formal implementation tasks should pin exact line ranges during execution phase.

---

## 4. Edge-Case Coverage Matrix (Architecture-Level)

Each row: expected smart behavior vs current risk.

## 4.1 Lifecycle edges
1. Cold start with delayed dependency readiness:
- Expected: deterministic initialization order and safe degraded mode.
- Risk: startup paths are robust but complex and potentially divergent.

2. Permission granted mid-session:
- Expected: explicit user-intended start policy and predictable state.
- Risk: auto-start may violate user expectation in some flows.

3. Service killed by system:
- Expected: rapid detection, clear state transition, no stale “active” illusion.
- Risk: inferred running state can over-report active tracking.

4. Reboot/app update restore:
- Expected: single authority restore path with reconciliation.
- Risk: restore starts tracking but may not fully align with other lifecycle abstractions.

## 4.2 Mobility truth edges
1. A→B→A with walking/rest/transit in between:
- Expected: explicit A visit, transit/stop/B state, return A visit.
- Risk: intermediate states can collapse or vanish under visit-only timeline model.

2. Short random street stop:
- Expected: timeline should capture a transient stop even if no permanent place created.
- Risk: currently likely becomes unknown/untracked or folded into adjacent visits.

3. High-motion commute with sparse fixes:
- Expected: transit inference with confidence and approximate road/locality labels.
- Risk: no dedicated transit segment model.

4. Overnight session crossing day boundary:
- Expected: correct day clamping with truth-preserving semantics.
- Risk: mixed behavior across analytics/timeline computations.

## 4.3 Data-quality edges
1. Poor GPS drift while stationary:
- Expected: reject noise but avoid false unknown blocks.
- Risk: filtered samples can create timeline gaps without inferred stop context.

2. Clock skew/future timestamps:
- Expected: bounded correction/rejection and explicit telemetry.
- Risk: validator coverage exists but end-to-end truth impact not fully unified.

3. Long gap while tracking active:
- Expected: explicit “tracking active but no quality samples” segment classification.
- Risk: generic untracked gap lacks reason semantics.

## 4.4 Concurrency edges
1. Rapid start/stop toggles:
- Expected: serialized lifecycle transitions, no conflicting state.
- Risk: multiple controllers increase conflict probability.

2. Overlapping worker enqueue attempts:
- Expected: one logical detection run per trigger window.
- Risk: one-time non-unique path can overlap.

3. Event storms and debounce:
- Expected: bounded suppression without hiding legitimate transitions.
- Risk: broad debounce/circuit logic may hide or delay meaningful events if thresholds mismatch context.

## 4.5 Personalization edges
1. Extreme setting combinations:
- Expected: coherent behavior and validated bounds with same interpretation everywhere.
- Risk: validation/runtime drift for some parameters.

2. Profile switching mid-day:
- Expected: graceful transition and traceable effects.
- Risk: partial application in some subsystems.

---

## 5. Target Smart Architecture (To-Be)

## 5.1 Unified Movement Truth Model
Introduce a first-class movement event model consumed by timeline/map/insights:
1. `TrackingStateSegment` with type:
- `NOT_TRACKING`
- `UNTRACKED_WHILE_TRACKING`
- `TRANSIT`
- `TRANSIENT_STOP`
- `CONFIRMED_PLACE_VISIT`
2. Each segment carries:
- Time range
- Coordinates/geometry summary
- Address label(s)
- Confidence
- Source/provenance (`observed`, `inferred`, `geocoded`, `user_confirmed`)
- Explanation metadata for UI transparency

## 5.2 Smartness layering principle
1. Layer A: Signal normalization (location/activity/quality/time).
2. Layer B: Segment inference (transit/stop/place candidate).
3. Layer C: Place persistence (stable place detection and visit management).
4. Layer D: Presentation projection (timeline/map/insights from same segment truth).

This decouples “timeline truthfulness” from “permanent place creation.”

## 5.3 Single lifecycle authority principle
1. `TrackingOrchestrator` becomes only start/stop/restart decision authority.
2. `LocationServiceManager` acts as adapter/health monitor only.
3. Boot/manual/UI/permission-driven starts all route through one orchestration contract.

## 5.4 Scheduler reliability principle
1. Unique one-time detection semantics.
2. Fallback only for terminal failure modes.
3. Shared “detection in progress” guard to prevent duplicate logical runs.

## 5.5 Settings fidelity principle
1. Every settings field maps to explicit runtime consumers.
2. No hidden hardcoded constants when equivalent preference exists.
3. Settings observability: every applied change logged as effective config snapshot.

## 5.6 Address intelligence principle
1. For non-place segments, resolve best-available label:
- POI/business if high confidence.
- Street name/locality if POI unavailable.
- Coordinate fallback with clear confidence.
2. Maintain cache and privacy boundaries.

---

## 6. Architectural Mismatch Inventory

## 6.1 Dual/overlapping responsibilities
1. Tracking lifecycle decision logic duplicated across orchestrator/manager paths.
2. Service-running truth inferred from multiple fallbacks, causing possible stale active state.

## 6.2 Domain-level coupling gaps
1. Timeline depends on visits as primary truth, but mobility truth includes non-visit segments.
2. Map/Insights consume partial truth projections, not unified movement truth model.

## 6.3 Validation vs execution mismatch
1. Preference bounds and business-rule bounds should be single-sourced.
2. Runtime path must consume validated values consistently.

## 6.4 Observability mismatch
1. Existing logs are extensive but not always tied to architecture-level KPIs.
2. Need explicit metrics for: segment inference quality, timeline truth drift, fallback rate, duplicate-detection suppression rate, configuration drift.

---

## 7. Redesign Blueprint by Subsystem

## 7.1 Sensing and ingestion subsystem
1. Normalize incoming location/activity events into a canonical stream.
2. Attach quality annotations (`accepted`, `rejected_reason`).
3. Preserve rejected sample stats for explainability, not just discard.

## 7.2 Movement segmentation subsystem
1. Infer transit and transient stops from canonical stream.
2. Use dwell + speed + acceleration + heading stability + temporal continuity.
3. Emit inferred segments independent of place creation.

## 7.3 Place intelligence subsystem
1. Keep DBSCAN/stable place detection for persistent places.
2. Create/merge stable places only after recurrence and confidence thresholds.
3. Never suppress movement truth because stable place criteria not met.

## 7.4 Timeline projection subsystem
1. Build timeline from unified segment stream, not visits-only.
2. Maintain explicit labels for each segment type.
3. Preserve intermediate segments between same-place revisits.

## 7.5 Map projection subsystem
1. Render segment-aware overlays:
- Confirmed places
- Transit polylines
- Transient stops
2. Keep date filtering consistent with timeline source data.

## 7.6 Insights subsystem
1. Compute insights from unified segments + visits.
2. Distinguish certainty levels in insight narratives.
3. Use hidden category settings with mode-aware filtering (timeline vs insights semantics separated).

## 7.7 Lifecycle and sync subsystem
1. Single tracking orchestrator path.
2. State gateway remains single write authority.
3. Reconciliation routine at startup: service truth + DB state + in-memory state + pending workers.

## 7.8 Worker orchestration subsystem
1. Unique work names/policies by intent class.
2. Strict failure classification for retries/fallback.
3. Telemetry per enqueue lifecycle.

## 7.9 Settings and personalization subsystem
1. Formal settings-to-runtime mapping table.
2. Per-setting “effective value” and “applied-at” timestamp.
3. Profile-aware conflict resolution (battery vs fidelity vs strictness).

---

## 8. Definition of Smartness (Architecture KPI Model)

Smartness is architecture-wide if all are true:
1. Timeline truthfulness:
- User can see meaningful state for entire day (not only confirmed places).
2. Continuity:
- Intermediate movement/stops are preserved between revisits.
3. Explainability:
- Inferred labels include confidence and reason.
4. Consistency:
- Timeline/Map/Insights show same underlying movement truth.
5. Personalization fidelity:
- Settings change behavior predictably and traceably.
6. Resilience:
- Startup/shutdown/restart/process death does not corrupt movement narrative.

---

## 9. Required Verification Strategy for Redesign

## 9.1 Scenario classes
1. Daily commuter (A↔B repeated transitions).
2. Errand chain (A→street stop→C→D→A).
3. Sparse traveler (long transit with intermittent fixes).
4. Home-bound stationary with drift.
5. Overnight and cross-day sessions.
6. Permission revocation/restoration mid-day.
7. Reboot and post-update restore.

## 9.2 Assertions
1. No segment disappearance when transitions occur.
2. No forced collapse of distinct movement intervals.
3. No contradictory tracking state across service/state/UI.
4. No duplicate detection run for same trigger window.
5. No setting drift between UI value and runtime effect.

---

## 10. Phased Redesign Roadmap (Planning)

### Phase P0: Architecture Correctness and Authority
1. Unify tracking lifecycle authority.
2. Fix worker uniqueness/fallback semantics.
3. Align validation-runtime preference contract.

### Phase P1: Movement Truth Model
1. Introduce unified segment model.
2. Build segmentation pipeline (transit/transient/visit/untracked/not-tracking).
3. Timeline projection from segment model.

### Phase P2: Smart Projection and Intelligence
1. Map and insights consume same segment truth.
2. Confidence-aware address labeling and UI explainability.
3. Personalization observability and profile governance.

### Phase P3: Robustness and Optimization
1. Stress and edge-case verification automation.
2. Performance tuning across geocoding, analytics, and projection.
3. Smartness KPI dashboard and production monitoring.

---

## 11. Risks and Mitigations

1. Risk: Increased complexity from richer segment model.
- Mitigation: strict layering and source-of-truth contracts.

2. Risk: Over-inference may reduce trust.
- Mitigation: explicit confidence/provenance and user controls.

3. Risk: Battery impact from richer inference.
- Mitigation: profile-aware adaptive inference intensity.

4. Risk: Migration from visits-only timeline.
- Mitigation: backward-compatible projection adapter and staged rollout.

---

## 12. Final Conclusions

Voyager is already a capable architecture but not yet a complete “smart mobility truth” architecture.  
Current smartness is strongest in filtering, place detection, and state protection, but weakest where users feel it most: continuity and correctness of movement narrative across timeline/map/insights.

The redesign direction is clear:
1. Treat movement truth as a first-class domain stream.
2. Keep stable place detection as one downstream consumer, not the only truth producer.
3. Enforce single authority for lifecycle and scheduling decisions.
4. Guarantee settings-to-runtime alignment and edge-case determinism.

This document is the architectural blueprint to redesign Voyager into a truly smart, edge-case-aware, personalized mobility assistant.

---

## 13. Real-World Problems This Smart Architecture Can Solve Efficiently

## 13.1 Daily commuting and mobility journaling
1. Problem: Users need truthful “where/when/how moved” records, not only destination snapshots.
2. Smart architecture solution: Unified movement segments preserve transit + transient stops + confirmed visits.
3. Efficiency impact: Reduced manual correction workload and higher trust in day reconstruction.

## 13.2 Safety and accountability trails
1. Problem: People need interpretable mobility history for safety checks and incident recall.
2. Smart architecture solution: Confidence + provenance on each segment and richer gap reasoning.
3. Efficiency impact: Faster retrieval and more reliable narratives under sparse/noisy data.

## 13.3 Lifestyle and habit intelligence
1. Problem: Users want actionable insights (routine drift, sedentary patterns, unusual movement).
2. Smart architecture solution: Insights computed from full movement truth model, not only place visits.
3. Efficiency impact: Better anomaly/pattern detection quality with fewer false inferences.

## 13.4 Field work and logistics tracking
1. Problem: Workers with multi-stop routes need accurate intermediate stop and transit accounting.
2. Smart architecture solution: Segment-aware route model with ordered stop/transit chain per day.
3. Efficiency impact: Better operational traceability and route optimization opportunities.

## 13.5 Battery-aware continuous awareness
1. Problem: Continuous smart tracking must stay efficient on battery/network.
2. Smart architecture solution: Adaptive inference intensity + robust worker uniqueness/failure handling.
3. Efficiency impact: Lower duplicate processing, lower API churn, predictable power behavior.

## 13.6 Personalized accessibility and control
1. Problem: Different users need different fidelity, privacy, and notification styles.
2. Smart architecture solution: Settings-to-runtime contract with profile-aware policy and explainable effects.
3. Efficiency impact: Fewer misconfiguration surprises and better “fit-for-user” behavior.

## 14. Practical Redesign Readiness Checklist

1. Lifecycle authority unified and documented.
2. One-time/periodic worker intent semantics made deterministic.
3. Movement segment domain model finalized.
4. Timeline, map, and insights switched to common segment source.
5. Settings-runtime mapping table completed and validated.
6. Startup/shutdown/restart reconciliation scenarios pass.
7. Observability metrics added for truth drift, fallback rate, and segment confidence quality.
