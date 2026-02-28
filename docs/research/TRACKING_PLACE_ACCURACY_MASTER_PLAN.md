# Tracking and Place Accuracy Master Plan
**Date:** 2026-03-12  
**Status:** Implementation-ready planning and review document  
**Audience:** Engineering, QA, Product

---

## 1) Executive Summary

This document is the canonical plan to debug and improve four production-visible issues:

1. `TRACKING OFF (No tracking data for this period)` appears even when the user did not manually disable tracking.
2. Place names and place coincidences are inconsistent or inaccurate at the same time window.
3. `NO LOCATION DATA` appears in timeline gaps.
4. Two different entries can exist for overlapping/same timing.

The current system has strong building blocks (foreground tracking, DBSCAN place detection, visit FSM, timeline segmentation), but it lacks reason-coded observability and deterministic overlap reconciliation across all pipelines.  
The plan below adds those controls first, then improves naming/boundary quality and provider strategy without uncontrolled cost growth.

---

## 2) Current Pipeline (As Implemented)

### 2.1 End-to-end flow
1. `LocationTrackingService.saveLocation()` receives Android location callbacks.
2. `shouldSaveLocation()` filters samples (accuracy/time/movement/speed constraints).
3. Accepted points are passed to `SmartDataProcessor.processNewLocation()`.
4. Visit FSM starts/ends visits around known places.
5. Automatic and scheduled place detection (`PlaceDetectionUseCases`, `PlaceDetectionWorker`) runs DBSCAN and creates/updates places/initial visits.
6. Timeline is built from visits + raw points by `MovementSegmentationUseCase` and surfaced by timeline use cases/view models.
7. Place names are derived via enrichment and fallback naming (`EnrichPlaceWithDetailsUseCase`, `Place.getBestName()`).

### 2.2 Confirmed fault points in code
- `MovementSegmentationUseCase` creates full-period `NOT_TRACKING` when visits and locations are both empty; this can mask “tracking enabled but all samples dropped.”
- `LocationTrackingService.saveLocation()` has multiple early-return skip paths (sleep mode, moving filter, quality gating), but skip reasons are not persisted for segment explanation.
- `VisitDao.getOverlappingVisitsForPlace(...)` protects overlap only per place ID; cross-place overlaps can remain.
- `AnalyticsUseCases` has visit deduplication logic, but equivalent reconciliation is not guaranteed in movement timeline generation path.
- Place naming fallback can expose low-quality labels (`UNKNOWN`, locality-first strings, plus-code-like tokens) when detail confidence is weak.

---

## 3) Root-Cause Matrix for Reported Issues

## 3.1 `TRACKING OFF` while user did not interfere
**Likely current behavior**
- If no visits and no locations exist for range, segmentation emits `NOT_TRACKING` with `"No tracking data for this period"`.

**Why it happens**
- Missing distinction between:
  1. tracking disabled,
  2. tracking enabled but no callbacks,
  3. callbacks received but rejected by filters.

**Impact**
- Misleading user trust signal and incorrect operational diagnosis.

## 3.2 Inconsistent/inaccurate place coincidences and names
**Likely current behavior**
- DBSCAN clusters by spatial density; nearest existing places and overlap checks decide create/skip.
- Name generation prioritizes OSM/place details/address fields; quality varies by source density and token quality.

**Why it happens**
- Nearest-POI association can snap to nearby but wrong entity in dense areas.
- Name fallback lacks strict normalization and confidence gates.
- Unknown categories remain high by design, increasing generic labels.

**Impact**
- Place list feels unstable; same area may resolve to different names over time.

## 3.3 `NO LOCATION DATA`
**Likely current behavior**
- Gaps with no points in an interval become `UNTRACKED_WHILE_TRACKING` with generic explanation.

**Why it happens**
- Early-return filters drop points without durable reason metadata.
- Service/callback interruptions and constrained scheduling are indistinguishable from filter-based drops in timeline UI.

**Impact**
- User cannot tell if issue is permissions/system kill/quality filter logic.

## 3.4 Same timing but different entries
**Likely current behavior**
- Overlapping visits across different places can coexist in some paths.
- Timeline segmentation can render both when overlap cleanup is not globally applied.

**Why it happens**
- Cross-place overlap resolution is not enforced as a universal precondition before timeline composition.

**Impact**
- Conflicting timeline narrative and broken confidence in analytics.

---

## 4) Debug and Review Playbook (First Phase)

## 4.1 Observability upgrades (must do first)
Add reason-coded diagnostics before behavior changes:

1. `LocationDropReason` at ingestion decision points:
   - `PAUSED`
   - `SLEEP_WINDOW_SUPPRESSED`
   - `MOVING_ACTIVITY_SUPPRESSED`
   - `LOW_ACCURACY`
   - `INSUFFICIENT_MOVEMENT`
   - `MIN_INTERVAL_NOT_MET`
   - `IMPOSSIBLE_SPEED`
2. `TimelineGapReason` in segmentation:
   - `TRACKING_DISABLED`
   - `TRACKING_ENABLED_NO_CALLBACK`
   - `SAMPLES_REJECTED`
   - `SERVICE_INTERRUPTED`
   - `UNKNOWN_GAP`
3. Persist counters and recent samples for each reason to enable deterministic root-cause attribution.

## 4.2 Deterministic debug checklist
1. Validate service lifecycle events vs state flags for affected day.
2. Compare callback count vs saved count vs dropped count (with reason histogram).
3. Inspect timeline gap segments and map each gap to persisted reason.
4. Run overlap scan across all visits (cross-place and same-place).
5. Rebuild timeline from deduplicated interval set and verify single truth per time range.
6. Audit place naming quality:
   - rate of `UNKNOWN`
   - rate of plus-code-like labels
   - median confidence by provider/source.

## 4.3 Review gate definition
No timeline bug fix is accepted unless:
- every displayed gap has a reason code,
- overlap scan returns zero unresolved overlaps,
- and naming quality metric improves against baseline.

---

## 5) Implementation Changes (Decision Complete)

## 5.1 Tracking-state truth model
Introduce explicit state model used by segmentation:
- `TrackingStateTruth`:
  - `DISABLED`
  - `ENABLED_ACTIVE_STREAM`
  - `ENABLED_NO_STREAM`
  - `ENABLED_STREAM_FILTERED`

Segment mapping rule:
- `DISABLED` -> `NOT_TRACKING`
- enabled states with missing samples -> `UNTRACKED_WHILE_TRACKING` + specific reason

## 5.2 Global overlap reconciliation
Apply one shared reconciliation stage before any timeline/analytics rendering:
1. Merge same-place overlapping/adjacent visits.
2. Resolve cross-place overlaps by deterministic precedence:
   - higher place confidence,
   - then longer dwell,
   - then most recent high-quality sample proximity.
3. Emit non-overlapping interval set as canonical timeline input.

## 5.3 Place naming quality pipeline
Create normalized candidate ranking:
1. User custom name (hard override).
2. High-confidence provider venue name.
3. Street/landmark composite name.
4. Locality/admin fallback.
5. Coordinate fallback (last resort).

Normalization rules:
- remove plus-code-like raw names unless no alternative exists,
- reject too-short/noisy tokens,
- preserve meaningful local script while sanitizing machine-like strings.

Persist metadata:
- `displayNameSource`
- `nameQualityScore`
- `providerUsed`
- `lastGeocodedAt`

## 5.4 Boundary and geometry strategy
Current centroid+radius remains baseline, but enhance with:
1. Provider polygon boundary when available.
2. Confidence-weighted radius updates from observed stationary points.
3. Boundary-aware containment checks in dense POI regions.

## 5.5 Re-geocoding and regeneration workflow
Add controlled “regenerate place names” flow:
1. User can trigger full or filtered re-geocode.
2. Jobs run in resumable batches with checkpointing.
3. Idempotency key avoids duplicate charging.
4. Respect per-provider daily quota and budget caps.

---

## 6) Multi-Provider Geocode/Map Strategy

## 6.1 Provider abstraction
Define `GeocodeProvider` contract:
- `reverse(lat, lng, locale, intent)`
- returns normalized result:
  - name candidates
  - address parts
  - confidence
  - source/provider metadata
  - cost tier marker

Route requests through `GeocodeOrchestrator`:
- policy-based provider priority,
- runtime health/latency/quota checks,
- automatic fallback on timeout/low-confidence/limit reached.

## 6.2 Recommended provider tiers
Use a hybrid ladder so cost and quality are both controlled:

1. **Free-first baseline**
   - OSM-based stack for default reverse geocode.
2. **Low-cost improvement tier**
   - OpenCage / Geoapify / LocationIQ / MapTiler-style providers.
3. **Premium precision tier**
   - Google / Mapbox / TomTom / HERE when strict confidence thresholds are required.

Note: Pricing/features are dynamic. Validate current quotas, ToS, and attribution rules before enabling each provider in production.

## 6.3 Real-time provider switching
Support runtime provider policy changes without app reinstall:
- remote-config driven provider order,
- per-region overrides,
- emergency kill-switch per provider,
- offline fallback to cached best known name.

---

## 7) Caching and Cost Optimization

## 7.1 Three-layer cache
1. **L1 in-memory cache** keyed by rounded coordinate cell.
2. **L2 persistent geocode cache** with normalized coordinate hash + locale.
3. **L3 place-level resolved-name cache** storing final ranked display name + metadata.

## 7.2 Cache policy
- TTL by provider confidence class.
- Invalidate on user rename, provider-policy change, or quality downgrade trigger.
- Prefer stale-while-revalidate for UI responsiveness.

## 7.3 Cost control rules
1. Always check caches before network calls.
2. Bucket nearby coordinates to avoid duplicate reverse-geocode calls.
3. Defer low-priority enrichment to background windows.
4. Enforce monthly budget guardrails and automatic tier downgrade when exceeded.

---

## 8) APIs / Types to Add or Evolve

Required contract additions for implementation:

1. `LocationDropReason` enum.
2. `TimelineGapReason` enum.
3. `TrackingStateTruth` model for segmentation.
4. `PlaceNameCandidate` (text, source, confidence, normalization flags).
5. `GeocodeProviderResult` (provider, confidence, cost tier, normalized address/name payload).
6. `PlaceNamingMetadata` (selected source, quality score, provider, timestamps).

All timeline and analytics consumers must rely on canonical post-reconciliation interval stream.

---

## 9) Test Plan and Acceptance Criteria

## 9.1 Unit tests
1. Ingestion filter reason mapping for every drop path.
2. Gap classification mapping by tracking truth state.
3. Overlap reconciliation determinism under conflicting visits.
4. Name candidate normalization/ranking edge cases.

## 9.2 Integration tests
1. End-to-end day simulation: enabled tracking + filtered points + worker delays.
2. Timeline reconstruction from mixed visit/point data with intentional overlaps.
3. Multi-provider failover under quota and timeout conditions.
4. Re-geocode batch resume after interruption.

## 9.3 Acceptance criteria
1. `TRACKING OFF` shown only when tracking is truly disabled for that interval.
2. Every `NO LOCATION DATA`/untracked segment has explicit reason code.
3. No overlapping timeline entries in canonical output.
4. Significant reduction in `UNKNOWN`/code-like place names vs baseline.
5. Geocoding spend remains under configured budget ceilings.

---

## 10) Rollout Plan and Impact

## 10.1 Phases
1. **Phase A: Observability first**
   - ship reason codes, metrics, and overlap audit.
2. **Phase B: Truth and dedup correctness**
   - enforce canonical interval reconciliation and accurate gap classification.
3. **Phase C: Naming and provider orchestration**
   - deploy provider abstraction, ranking, caching, and re-geocode controls.
4. **Phase D: Boundary refinement**
   - polygon-aware matching in dense areas and confidence tuning.

## 10.2 Expected impact
- Higher trust: timeline explains gaps clearly.
- Higher data integrity: one canonical entry per time interval.
- Better UX: stable and user-friendly place names.
- Better economics: controlled provider costs with measurable quality lift.

## 10.3 Risks and mitigations
1. **Risk:** More metadata storage overhead  
   **Mitigation:** capped retention and aggregated counters.
2. **Risk:** Provider failover complexity  
   **Mitigation:** strict orchestrator contract + integration tests.
3. **Risk:** Over-normalization harms local naming nuance  
   **Mitigation:** locale-aware normalization and user-rename override.

---

## 11) Immediate Execution Backlog (Ordered)

1. Add reason-code instrumentation at all location drop and gap creation points.
2. Build overlap audit query/report and enforce canonical pre-render reconciliation.
3. Add naming metadata fields and candidate ranking pipeline.
4. Introduce geocode provider interface and orchestrator with one fallback provider.
5. Add three-layer cache with TTL/invalidation policy.
6. Implement resumable re-geocode batch workflow.
7. Complete regression suite and acceptance dashboard.

---

## 12) Scope Boundary

In scope:
- tracking/gap truthfulness,
- overlap correctness,
- place naming quality,
- provider orchestration,
- cache/cost optimization.

Out of scope (for this cycle):
- full map rendering engine replacement,
- unrelated UI redesign,
- historical backfill beyond controlled re-geocode workflow.

