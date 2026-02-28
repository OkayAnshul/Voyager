# Voyager Master Findings and Research
**Date:** 2026-03-12  
**Authoring mode:** Senior architecture findings and research (no implementation execution in this document)  
**Scope:** End-to-end debugging, pipeline checks, architecture flaws, schema quality, scalability, and future-readiness

---

## 1) Executive Summary

Voyager has strong functional foundations, but the current architecture is not yet decision-safe for long-term scale and deterministic behavior across timeline, place identity, and user-facing diagnostics.

Most important conclusion:
1. **Correctness risk:** gap semantics and overlap reconciliation are not fully canonicalized across all paths.
2. **Architecture risk:** boundary leaks (`presentation -> data`, `domain -> data/utils`) and oversized multi-role classes reduce maintainability.
3. **Observability gap:** failure and filter reasons are not consistently persisted as product-grade explanations.
4. **Future-readiness gap:** feedback-learning and routine intelligence are feasible but need explicit contracts and event lineage.

---

## 2) Core Feature Readiness Assessment

## 2.1 Timeline: no gap, best feedback, naming quality
**Status:** Partial, high-risk

Findings:
- Timeline generation exists and is advanced, but user-visible explanations can still be generic.
- Missing durable truth separation for:
  - tracking disabled,
  - enabled but callback missing,
  - callback present but filtered/rejected.
- Same-time conflict prevention is not guaranteed as one universal pre-render invariant.

Impact:
- `TRACKING OFF`, `NO LOCATION DATA`, and conflicting entries can appear in ways that reduce trust.

## 2.2 Integrated map: route/visit visualization consistency
**Status:** Partial, medium-high risk

Findings:
- Map view model logic is feature-rich (date sync, place selection, visits, current-place rendering).
- Data consistency with timeline can drift when contracts are not sourced from one canonical interval stream.
- Presentation layer currently reaches into data-layer internals in several locations.

Impact:
- Potential date-window mismatch between map and timeline narratives.

## 2.3 Plugin-based insights
**Status:** Partial, medium risk

Findings:
- Insight orchestration exists and already aggregates multiple pipelines.
- Plugin governance contracts are incomplete:
  - timeout budget,
  - fail isolation,
  - ranking determinism,
  - evidence/provenance guarantees.

Impact:
- Growth path is clear, but future plugin count can destabilize output quality without strict contracts.

## 2.4 Future routines and learning
**Status:** Foundational only

Findings:
- Review/correction seeds exist, but immutable feedback event lineage is not yet first-class.
- Routine/habit domains are not fully modeled with confidence lifecycle and alert policy semantics.

Impact:
- Routine intelligence is possible, but not yet architecture-ready for safe scale.

---

## 3) Root-Cause Findings for Reported Product Issues

## Issue A: `Tracking Off (No tracking data for this period)` even without user interference
Finding:
- Segmentation path can classify full range as not-tracking when both visits and locations are absent.
- Tracking truth lacks complete state taxonomy for disabled vs enabled-but-empty vs filtered-empty.

Result:
- Misclassification and misleading user feedback.

## Issue B: Inconsistent/inaccurate place names and place coincidences
Finding:
- Cluster-to-place and naming is multi-stage and can be ambiguous under dense POI data.
- Fallback naming can yield generic or code-like labels without stricter quality gates.

Result:
- Place identity appears unstable and less user-friendly.

## Issue C: `No Location Data`
Finding:
- Ingestion has multiple skip/drop branches, but reason persistence is not consistently surfaced for timeline diagnosis.

Result:
- Users and QA cannot quickly differentiate permission/system interruptions from quality filtering.

## Issue D: Same timing with different entries
Finding:
- Cross-place overlap cleanup is not guaranteed as a single mandatory preprocessing stage for every timeline consumer.

Result:
- Contradictory intervals and inconsistent analytics narratives.

---

## 4) Systematic Codebase Debugging Protocol

The following protocol is the canonical way to debug Voyager pipeline behavior.

## 4.1 Stage 1: Ingestion (`LocationTrackingService`)
Checks:
1. Callback received count per period.
2. Save decision count vs reject count.
3. Reject reason distribution by branch.

Expected invariant:
- `accepted + rejected == callbacks received`.

Failure signature:
- callback count present but persisted points near zero without reason telemetry.

Likely causes:
- strict filtering thresholds, sleep/motion suppression, activity-moving suppression, timing thresholds.

## 4.2 Stage 2: Processing and state (`SmartDataProcessor`, `AppStateManager`)
Checks:
1. Accepted location to persisted location ratio.
2. Current-place transitions with visit start/end consistency.
3. State version progression monotonicity.

Expected invariant:
- no visit activation without valid place context.

Failure signature:
- state transitions logged but not reflected in canonical persisted tables.

Likely causes:
- multi-writer path complexity, recovery paths racing with normal path.

## 4.3 Stage 3: Segmentation (`MovementSegmentationUseCase`)
Checks:
1. Segment continuity over selected date range.
2. Gap classification reasons.
3. Visit-derived segments vs point-derived segments consistency.

Expected invariant:
- no unexplained gap segment; reason must exist.

Failure signature:
- `NOT_TRACKING` emitted for windows where tracking was active but data absent due filtering.

## 4.4 Stage 4: Place detection and naming (`PlaceDetectionUseCases`)
Checks:
1. Cluster count vs candidate place count.
2. Duplicate/overlap checks and place merge decisions.
3. Name source and confidence lineage.

Expected invariant:
- chosen display name is traceable to a ranked candidate source.

Failure signature:
- high frequency of `UNKNOWN`/code-like names in stable areas.

## 4.5 Stage 5: Map projection (`MapViewModel`)
Checks:
1. Selected-date location/visit/place dataset consistency with timeline date.
2. Selected-place details and visit list time alignment.

Expected invariant:
- map and timeline reflect same canonical interval truth.

Failure signature:
- same day showing different visit narrative between map and timeline.

## 4.6 Stage 6: Insight orchestration (`InsightEngine`)
Checks:
1. Plugin output completeness and provenance.
2. Failed plugin isolation and aggregate behavior.

Expected invariant:
- one failing pipeline does not break full insight generation.

Failure signature:
- missing/duplicated insights without provenance tags.

---

## 5) Pipeline Check Matrix (Pass/Fail Criteria)

| Subsystem | Check | Evidence Required | Failure Pattern | Severity | Recommended Direction |
|---|---|---|---|---|---|
| Tracking ingestion | Callback/save/reject accounting | callback logs + saved row count + reject counters | callbacks exist but no persistence trace | Critical | reason-coded reject taxonomy |
| Tracking truth | Disabled vs enabled-empty distinction | state snapshots + segment outputs | not-tracking shown for active session | Critical | explicit tracking truth model |
| Visit lifecycle | Single active visit invariant | visit table active rows + transitions | overlapping active visits | High | canonical interval resolver |
| Timeline segmentation | Gap reason completeness | segment metadata | generic/no reason gaps | Critical | mandatory gap reason schema |
| Overlap cleanup | Cross-place overlap elimination | final interval set | same-time conflicting entries | Critical | universal pre-render reconciliation |
| Place naming | Candidate ranking quality | name source/confidence lineage | unknown/code-like name spikes | High | ranked naming pipeline + normalization |
| Map consistency | Timeline-map parity | same-date comparison report | map and timeline mismatch | High | shared canonical interval contract |
| Insight reliability | Plugin isolation/provenance | plugin health + output provenance | aggregate failure on one plugin | Medium | plugin contract + timeout/isolation |
| Provider resilience | fallback and quota behavior | provider health/quota metrics | outage causes naming collapse | Medium | orchestrator + cached fallback |

Baseline acceptance targets:
1. Zero unresolved cross-place overlaps in canonical output.
2. 100% gap segments with explicit reason code.
3. `TRACKING OFF` only for truly disabled windows.

---

## 6) Architecture Flaw Register

## 6.1 Boundary violations
Observed:
- `presentation -> data` imports in view models and debug screen paths.
- `domain -> data` type coupling for geocoding contracts.
- strong `domain -> utils` coupling for infrastructure helpers.

Risk:
- architecture erosion and reduced modular testability.

Priority:
- High.

## 6.2 Oversized multi-responsibility components
Observed hotspots:
- `LocationTrackingService`
- `AppStateManager`
- `PlaceDetectionUseCases`
- `SmartDataProcessor`
- `PreferencesRepositoryImpl`

Risk:
- single-change blast radius, harder onboarding, hidden coupling.

Priority:
- High.

## 6.3 Multi-path orchestration complexity
Observed:
- service + orchestrator + state gateway + worker fallbacks + recovery mechanisms all interact in critical paths.

Risk:
- race windows and ambiguous source of truth under stress/failure.

Priority:
- High.

## 6.4 Decision architecture inconsistencies
Observed:
- overlap dedup and segmentation behaviors are not consistently enforced as one shared precondition for every consumer.

Risk:
- contradictory timeline and analytics narratives.

Priority:
- Critical.

---

## 7) Schema and Decision-Architecture Audit

## 7.1 Current schema strengths
1. Room entities and migration mechanisms are present and reasonably mature.
2. Core tables cover locations, places, visits, state, reviews, corrections, geocode cache.

## 7.2 Semantic contract gaps
Missing first-class product semantics:
1. tracking truth taxonomy for enabled-but-empty scenarios.
2. explicit persisted drop/gap reason models.
3. canonical non-overlap interval stream contract.
4. naming provenance/confidence as mandatory display contract.

## 7.3 Required contract additions (findings)
1. `TrackingStateTruth`
2. `LocationDropReason`
3. `TimelineGapReason`
4. canonical `VisitIntervalResolver` output
5. `PlaceNameCandidate` and `PlaceNamingMetadata`
6. `InsightEvidence`/provenance contract
7. immutable feedback event schema for future learning

## 7.4 Decision architecture rule
- Every user-facing timeline/map/insight result must be traceable to one canonical interval truth set and one provenance chain.

---

## 8) Reliability and Catching Policy (Best-Practice Findings)

## 8.1 Error handling policy by layer
1. Presentation: user-safe fallback states only; no business retries.
2. Domain: typed errors and deterministic rule outcomes; no silent catches.
3. Data/infra: bounded retry for transient external failures only.

## 8.2 Mandatory controls
1. typed error taxonomy (`retryable`, `integrity`, `provider`, `fatal`).
2. correlation ID across service -> processor -> repository -> worker.
3. idempotency keys for worker/service write-triggered operations.
4. dead-letter lane for failed enrichment/learning operations.
5. explicit fail-open/fail-closed policy per feature.

## 8.3 Anti-patterns to avoid
1. catch-log-continue in core truth decisions without reason persistence.
2. retries in domain logic that hide deterministic rule violations.
3. duplicate fallback paths that produce non-canonical outcomes.

---

## 9) Provider Research and Decision Framework (Map/Geocode)

## 9.1 Tiered provider posture
1. Free-first baseline: OSM stack.
2. Balanced tier: OpenCage/Geoapify/LocationIQ/MapTiler class providers.
3. Premium precision tier: Google/Mapbox/TomTom/HERE class providers.

## 9.2 Runtime orchestration requirements
1. policy-driven provider order and per-region override.
2. health and quota aware fallback.
3. kill switch per provider.
4. cached best-known-name fallback when network/provider unavailable.

## 9.3 Re-geocode strategy findings
1. resumable batch processing with checkpoints.
2. idempotency to avoid duplicate charging.
3. strict budget and daily quota guardrails.

## 9.4 Caching findings
1. L1 memory coordinate-cell cache.
2. L2 persistent geocode cache.
3. L3 place-level resolved naming cache.
4. stale-while-revalidate to reduce latency/cost.

---

## 10) Future Readiness: Feedback, Learning, Habits, Routines

## 10.1 Readiness matrix
| Capability | Current State | Blocker | Readiness |
|---|---|---|---|
| rename feedback capture | partial | lineage/provenance normalization | Partial |
| suggestion accept/reject loop | partial | immutable event contract missing | Partial |
| place-name learning reuse | low | candidate/evidence model not canonical | Low |
| routine detection | low | routine domain contract incomplete | Low |
| routine alerts | low | policy + confidence lifecycle not formalized | Low |
| explainable decisions | low | no shared evidence schema | Low |

## 10.2 Required future contracts (findings)
1. immutable `UserFeedbackEvent`.
2. derived `LearningSignal`.
3. `RoutineDefinition`, `RoutineOccurrence`, `RoutineAlertPolicy`.
4. explainability metadata for every learned recommendation.

## 10.3 Product safety requirements
1. user override as hard constraint.
2. privacy retention and consent policy for learned features.
3. deterministic replay of feedback-to-decision chain.

---

## 11) Prioritized Improvement Blueprint (Findings-Only Roadmap)

## Phase A: Correctness observability
Focus:
- tracking truth model,
- reason-coded gaps,
- reject reason telemetry.

Outcome:
- clear diagnosis of `tracking off` vs `no data` vs `filtered`.

## Phase B: Canonical interval correctness
Focus:
- universal overlap reconciliation,
- single non-overlap timeline contract.

Outcome:
- eliminate same-time conflicting entries.

## Phase C: Naming and provider quality
Focus:
- candidate ranking and provenance,
- orchestrated multi-provider fallback,
- cache-budget optimization.

Outcome:
- better place naming quality and stable cost profile.

## Phase D: Boundary hardening and plugin readiness
Focus:
- remove cross-layer leaks,
- plugin contract formalization,
- insight isolation/provenance.

Outcome:
- scalable and testable system architecture.

## Phase E: Feedback-learning and routine intelligence foundation
Focus:
- immutable feedback events,
- learning signal derivation,
- routine contracts and explainable outputs.

Outcome:
- safe path to personalized habits/routines features.

---

## 12) Verification Scenarios (Must Pass Before “Architecture Ready”)

1. Tracking enabled + filtered samples never appears as tracking disabled.
2. Every timeline gap has explicit reason code and provenance.
3. Final interval stream has zero unresolved overlaps.
4. Map and timeline show same narrative for same date/time window.
5. Place names are traceable to ranked candidates with confidence.
6. Provider outages degrade gracefully to fallback/cached outputs.
7. Insight failures are isolated and do not collapse aggregate result.
8. Feedback replay deterministically updates naming/routine confidence.

---

## 13) Final Senior Assessment

### Is architecture currently scalable for all requested core and future features?
**Not fully.**

### Is there heavy mixing and inconsistency?
**Yes, in critical control paths and layer boundaries.**

### Can Voyager be hardened without full rewrite?
**Yes.**  
A phased modular-monolith hardening approach is sufficient if correctness and boundary contracts are made strict.

### Final recommendation
Adopt canonical truth contracts first (tracking/gaps/intervals), then enforce architecture boundaries, then improve provider+naming quality, then expand into learning/routines on immutable feedback lineage.

This document is the final expanded findings and research artifact for debugging, architecture review, and strategic technical decisions.
