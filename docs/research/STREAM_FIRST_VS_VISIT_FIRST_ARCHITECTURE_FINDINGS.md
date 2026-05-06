# Stream-First vs Visit-First Architecture Findings

Date: 2026-04-13  
Scope: Native Android tracking, visit detection, place naming, timeline correctness, real-time monitoring

## 1. Executive Summary

The rebuilt Voyager app currently behaves like a **stream-first system**:

- live samples are processed continuously,
- movement state and visit state are inferred in real time,
- timeline-visible segments are formed during streaming,
- UI then merges and filters those results.

The older/reference design behaved closer to a **visit-first system**:

- locations were filtered and persisted,
- a dwell/visit state machine created durable visit records,
- places and geocoding enriched those visits,
- timeline was derived from visits plus movement/gaps.

For the reported flaws, the correct target is:

- **visit-first truth for confirmed stops and place duration**
- **segment-first truth for movement and gaps**
- **runtime-state truth for live temporary UI**
- **asynchronous geocoding for name enrichment**

This is the most correct architecture for real-time tracking without sacrificing timeline integrity.

## 2. Problem Statement

The application must:

- track user location continuously,
- classify movement using location evidence, speed, and activity signals,
- detect when the user stops at a place,
- calculate duration at known or unknown places,
- show those visits in timeline order,
- reflect stop/place updates in the UI in real time,
- preserve correct place naming and geocoding behavior.

The rebuilt app currently has correctness risks because stop truth is spread across multiple layers instead of being owned by one canonical model.

## 3. The Two Architectural Designs

### A. Rebuilt Design: Stream-First

Current runtime path in the rebuilt app:

1. Raw sample enters the pipeline.
2. Quality filtering and de-duplication run.
3. Activity/motion is fused.
4. Live place matching runs.
5. Visit state is inferred during streaming.
6. Segmenter writes timeline-style movement state.
7. Timeline repository and UI merge live and persisted state.

Main code references:

- `app/src/main/java/com/cosmiclaboratory/voyager/pipeline/PipelineConsumer.kt`
- `app/src/main/java/com/cosmiclaboratory/voyager/pipeline/stage/Segmenter.kt`
- `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/DetectVisitUseCase.kt`
- `app/src/main/java/com/cosmiclaboratory/voyager/data/repository/TimelineRepositoryImpl.kt`
- `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineViewModel.kt`

Strengths:

- strong live responsiveness,
- fast visual feedback,
- simple real-time processing loop.

Weaknesses:

- stop/place truth is formed in more than one layer,
- timeline correctness depends on render-time repair,
- place identity may be recovered indirectly instead of being stamped canonically,
- duplicate and overlapping entries become easier to create,
- duration correctness is vulnerable when stream state becomes durable truth.

### B. Old/Reference Design: Visit-First

Reference behavior from archived docs and logs:

1. Location service receives and filters samples.
2. Accepted samples are persisted.
3. Smart processor or visit FSM detects dwell.
4. Confirmed stop creates a durable visit row.
5. Place detection and geocoding enrich visit/place truth.
6. Timeline is composed from visits plus movement/gap intervals.

Main evidence sources:

- `docs/research/DEEP_SYSTEM_WORKING_TRACE.md`
- `docs/research/PER_LINE_TRACE_CORE_PIPELINE.md`
- `docs/research/TRACKING_PLACE_ACCURACY_MASTER_PLAN.md`
- `docs/design/restart/15_SMART_ARCHITECTURE_REDESIGN_BLUEPRINT.md`
- `archive/session-logs/week-5-6/DETECTION_IMPROVEMENT_LOGBOOK.md`
- `archive/session-logs/week-1-2/SESSION_4_FINAL_SUMMARY.md`

Strengths:

- correct duration and stop intervals,
- easier single-active-visit enforcement,
- better overlap control,
- stronger midnight/day-boundary behavior,
- one source of truth for timeline/map/detail screens.

Weaknesses:

- live UI can feel delayed if there is no provisional runtime state,
- geocoding may lag behind confirmation unless placeholder UX is designed.

## 4. Side-by-Side Comparison

| Concern | Stream-First Rebuild | Visit-First Reference | Correct Target |
|---|---|---|---|
| Confirmed stop truth | Mixed across stream, visit state, and UI | Visit row | Visit row |
| Real-time display | Strong | Weaker by default | Runtime state overlay |
| Duration integrity | Fragile when stream artifacts persist | Strong | Visit-based |
| Duplicate overlap control | Harder | Easier | Canonical visit reconciliation |
| Place naming | Late and inconsistent in some paths | Layered on durable place truth | Placeholder then upgrade |
| Timeline composition | Partly repaired in UI | Derived from truth | Derived from canonical intervals |
| Movement explanation | Good | Good when segmented separately | Segment-based |
| Midnight handling | Risky if tied to stream artifacts | Stronger with overlap queries | Visit overlap queries |

## 5. Findings

### 5.1 The main flaw is split ownership of stop truth

The rebuilt system mixes:

- pending visit candidate state,
- active visit state,
- persisted segment rows,
- UI merge/filter logic.

This is the core architectural reason the timeline can become inconsistent.

### 5.2 Timeline repair is happening too late

`TimelineViewModel` and `TimelineRepositoryImpl` already contain logic to hide duplicates and merge artifacts. That indicates the persistent model is not sufficiently canonical.

### 5.3 Place identity and duration are not tightly coupled enough

A stop should be one durable interval with:

- arrival,
- departure,
- duration,
- place link or provisional unknown-place identity.

In the stream-first approach, these pieces can be inferred separately and drift.

### 5.4 Archived evidence repeatedly points to canonical interval truth

The archived design work consistently emphasizes:

- single active visit invariant,
- overlap-safe visit handling,
- timeline from visits + movement,
- geocoding layered on top,
- deterministic gap semantics.

### 5.5 The rebuild simplified the old system, but removed some correctness guarantees

The newer pipeline is more direct, but it reduced the old discipline of:

- durable visit truth first,
- rendering second,
- canonical reconciliation before display.

## 6. Recommended Architecture

Adopt a **hybrid architecture with visit-first stop truth**.

### Canonical truth by concern

- **Stops / places / duration**: visit-first
- **Travel / movement / gaps**: segment-first
- **Live temporary UI**: runtime-state-first
- **Place labels**: async enrichment with immediate placeholder

### What this means

- A confirmed stop must always become a durable visit.
- Movement segments must describe travel between visits, not replace visit truth.
- UI may show pending dwell and active visit state immediately, but those are temporary views, not final history.
- Geocoding must enrich place names after confirmation, not create timeline truth by itself.

## 7. Correct Way To Solve and Fix

### 7.1 Re-center stop truth on visits

- Make the visit record the only durable answer to:
  - where the user stopped,
  - when they arrived,
  - when they left,
  - how long they stayed.
- Do not use streamed timeline segments as the permanent source of stop/place truth.

### 7.2 Keep movement logic separate

- Keep the current movement pipeline for:
  - walking,
  - cycling,
  - driving,
  - unknown movement,
  - data gaps.
- Use those segments only between canonical visits.

### 7.3 Add canonical reconciliation before timeline rendering

Before any day timeline is shown:

- merge same-place overlapping or adjacent visits,
- resolve cross-place overlaps deterministically,
- produce a non-overlapping interval set,
- subtract those visit intervals from movement intervals.

This must happen in the repository/domain layer, not in Compose UI.

### 7.4 Use placeholder-then-upgrade place naming

When a visit is confirmed but geocoding is still pending:

- show the stop immediately,
- use a provisional label such as coordinates or area fallback,
- replace it with the resolved place name when enrichment completes.

Recommended naming priority:

1. user custom name
2. trusted provider POI name
3. structured address
4. locality/area fallback
5. coordinates placeholder

### 7.5 Preserve live UX without corrupting truth

The correct live UI model is:

- pending dwell card from runtime state,
- active visit card from active visit row,
- in-progress movement card from segment snapshot,
- historical timeline from canonical persisted intervals.

### 7.6 Add reason-coded observability

To debug “tracking off”, “no location data”, and conflicting entries:

- persist why samples were dropped,
- persist why a gap exists,
- distinguish:
  - tracking disabled,
  - tracking enabled but no callbacks,
  - callbacks received but all samples filtered,
  - service interruption.

## 8. Implementation Direction

### Phase 1: Core correctness

- make visits canonical for stop history,
- stop using stream-generated visit segments as final truth,
- add visit overlap reconciliation,
- fix departure/duration semantics,
- keep timeline ordered from canonical intervals.

### Phase 2: Real-time UX stabilization

- show active visit and pending dwell immediately,
- use provisional naming for unknown places,
- upgrade labels when geocoding returns.

### Phase 3: Observability and diagnostics

- add drop reasons,
- add gap reasons,
- add overlap audits,
- add invariant checks for visit integrity.

## 9. Acceptance Criteria

The architecture is considered fixed when:

- only one active visit can exist at a time,
- no overlapping timeline entries remain in canonical output,
- visit durations are correct for active and completed stops,
- unknown places appear immediately with provisional labels,
- names upgrade after geocoding without changing stop duration truth,
- day and overnight timelines are consistent,
- movement segments explain travel only, not confirmed place-stay truth,
- UI no longer needs to repair duplicate visit artifacts.

## 10. Final Recommendation

Do **not** keep the current stream-first model as the canonical history source.  
Do **not** fully revert to the old architecture either.

The correct solution is:

- **visit-first for confirmed stop/place history**
- **segment-first for movement and gaps**
- **runtime-state-first for live temporary rendering**
- **async geocoding for name enrichment**

This is the clean architectural boundary that fixes the current flaws while keeping the rebuilt app efficient, real-time, and maintainable.

## 11. Evidence Index

Primary evidence used for these findings:

- `docs/research/DEEP_SYSTEM_WORKING_TRACE.md`
- `docs/research/PER_LINE_TRACE_CORE_PIPELINE.md`
- `docs/research/TRACKING_PLACE_ACCURACY_MASTER_PLAN.md`
- `docs/design/restart/13_FEATURE_RATIONALIZATION_PLAN.md`
- `docs/design/restart/15_SMART_ARCHITECTURE_REDESIGN_BLUEPRINT.md`
- `docs/design/restart/14_DEEP_DEBUG_MASTER_REPORT.md`
- `archive/session-logs/week-5-6/DETECTION_IMPROVEMENT_LOGBOOK.md`
- `archive/session-logs/week-5-6/ML_UX_IMPLEMENTATION_LOGBOOK.md`
- `archive/session-logs/week-1-2/SESSION_4_FINAL_SUMMARY.md`
