# Voyager Restart: Function Separation and Optimization (Research + Codebase Grounding)

## 0) Purpose
This document extends the existing restart docs with:
- your 6 explicitly requested problem areas,
- additional core function optimization opportunities found from actual code,
- a practical fix approach that can be executed phase-wise.

Primary grounding files include:
- `data/service/LocationTrackingService.kt`
- `data/processor/SmartDataProcessor.kt`
- `data/state/AppStateManager.kt`
- `data/sync/StateSynchronizer.kt`
- `domain/usecase/GenerateTimelineSegmentsUseCase.kt`
- `domain/usecase/GatherPlaceNameSuggestionsUseCase.kt`
- `domain/usecase/AnalyticsUseCases.kt`
- `data/repository/PreferencesRepositoryImpl.kt`
- `data/repository/VisitRepositoryImpl.kt`

## 1) Current Insight System vs Smarter Insight System

### 1.1 Current system (as-is)
Current insight/analytics behavior is distributed across:
- `AnalyticsUseCases` (day analytics, movement patterns, state analytics)
- `StatisticalAnalyticsUseCase` / `PersonalizedInsightsGenerator` (additional insight generation)
- multiple analytics/insight viewmodels and screens.

Current characteristics:
- calculations are mostly aggregate-first and UI-tailored,
- confidence/explainability fields are limited,
- no single insight schema for distribution-heavy UI (histograms, trends, confidence, provenance),
- repeated mapping logic across screens.

### 1.2 Gaps
- Insight payload is not normalized for heavy UI.
- No explicit uncertainty/confidence contract for users.
- Weak source traceability (user cannot see what data window generated an insight).
- Computation and presentation concerns are mixed.

### 1.3 Target smarter insight model
Introduce canonical insight schema:
- `InsightFact`: metric key/value + timeframe.
- `InsightDistribution`: bins/percentiles/trend slices.
- `InsightNarrative`: human-readable explanation.
- `InsightConfidence`: score + reason codes.
- `InsightProvenance`: source tables, time window, sample size.
- `InsightAction`: optional recommended action.

### 1.4 Suggested implementation approach
1. Add a domain-level `InsightEngine` that outputs canonical insight models.
2. Keep existing `AnalyticsUseCases` as data provider only.
3. Build adapter mappers per screen from canonical insights to UI models.
4. Add confidence and sample-size threshold rules so low-quality insights are hidden or downgraded.

### 1.5 Success criteria
- All insights in UI come from a single schema.
- Every insight shows timeframe + confidence + source footprint.
- Same input dataset produces consistent output across analytics screens.

## 2) Proper Division of Data (Day-wise / Place-wise / Visit-wise / Map Reflection)

### 2.1 Current state
Data slicing is repeatedly done in ViewModels (`TimelineViewModel`, `MapViewModel`) and use cases with local filtering and partial caching.

### 2.2 Gaps
- No canonical "data grain" contract.
- Date boundary behavior can diverge across screens.
- In-memory filtering repeated and potentially expensive.
- Map/timeline/analytics can disagree for same day.

### 2.3 Target grain model
Define explicit aggregation grains:
- `DayAggregate`: day totals (time, movement, places count).
- `PlaceAggregate`: per-place totals (visits, total duration, last seen).
- `VisitAggregate`: canonical visit facts.
- `MovementSegment`: movement intervals between visits.

### 2.4 Function mapping policy
- Day screens use only `DayAggregate`.
- Place cards use only `PlaceAggregate`.
- Timeline rows use `VisitAggregate + MovementSegment`.
- Map route overlays consume the same timeline segment list (single source).

### 2.5 Success criteria
- For any selected date, map, timeline, and analytics show matching counts/durations.
- No screen performs custom date slicing outside a shared query boundary utility.

## 3) App Doesn’t Sustain on Shutdown/Restart

### 3.1 Current state
Recovery/startup behavior is spread across:
- `BootReceiver`
- `LocationServiceManager`
- `LocationTrackingService`
- `StateSynchronizer`
- state flags in preferences/current state.

### 3.2 Gaps
- Multiple state authorities can disagree during process death/restart.
- Service running flags and DB/app-state reconciliation are complex and race-prone.
- Foreground service restrictions on modern Android require stricter orchestration.

### 3.3 Target recovery design
Introduce `TrackingLifecycleOrchestrator` as the only lifecycle authority:
- reads persisted desired-tracking-state,
- validates runtime preconditions (permissions/background rules),
- starts/stops service through one controlled path,
- performs deterministic state reconciliation on app boot.

### 3.4 Reliability rules
- Single write authority for tracking + current place state.
- Idempotent boot restore (safe to execute multiple times).
- If start is disallowed by system, persist pending-intent state and retry only on valid trigger.

### 3.5 Success criteria
- Reboot restores intended tracking state correctly.
- Process death does not produce orphan active visits.
- State mismatch is detected and reconciled deterministically.

## 4) Better Place Name Suggestion

### 4.1 Current state
`GatherPlaceNameSuggestionsUseCase` merges candidates from OSM/Geocoder/history/nearby/category default, with mostly static confidence scores.

### 4.2 Gaps
- Static weights; limited learning from accepted/rejected user choices.
- Duplicate normalization is basic.
- Provider constraints/policies are not encoded as first-class strategy rules.

### 4.3 Target ranking pipeline
1. Candidate generation (OSM details, reverse geocode, nearby user-renamed places, history).
2. Normalization (case, punctuation, locale, token cleanup).
3. Feature extraction:
- distance,
- category fit,
- historical acceptance rate,
- freshness,
- source reliability.
4. Ranking score + explanation.
5. Feedback loop persists accepted suggestion outcome.

### 4.4 Success criteria
- Suggestions are ranked by dynamic score, not static constants.
- Top-3 quality improves over time from user corrections.
- UI can show why suggestion #1 was chosen.

## 5) Duplicate Timeline Place Entries with Separate 0s Visits

### 5.1 Current state
Visit transitions occur in `SmartDataProcessor` + `VisitRepositoryImpl` and segment grouping in `GenerateTimelineSegmentsUseCase`.

### 5.2 Likely causes
- Transition jitter around pending/confirmed visit edges.
- Non-transactional close/open sequence can generate tiny or zero-duration visits.
- Timeline grouping depends on raw visit integrity.

### 5.3 Fix approach
- Add atomic DAO transaction: end previous active visit + start new visit in one transaction.
- Add minimum effective visit duration guardrail (e.g., merge/drop 0–N second noise visits by policy).
- Add post-processing dedupe pass for immediate same-place zero-duration repeats.
- Enforce invariant: max 1 active visit globally.

### 5.4 Success criteria
- No duplicate same-place consecutive timeline segments with `0s` duration.
- At most one active visit after any transition.

## 6) Full Location Everywhere in UI

### 6.1 Current state
UI components mix full addresses and truncated/split address forms (e.g., `split(",").firstOrNull()`, one-line ellipsis).

### 6.2 Gaps
- Inconsistent UX: same place appears differently on Map, Timeline, and bottom sheets.
- Hard-coded truncation rules reduce trust and clarity.

### 6.3 Target display policy
Introduce shared `LocationDisplayPolicy`:
- `COMPACT`: short label (for dense lists)
- `STANDARD`: locality + street if available
- `FULL`: full formatted address

Shared formatter utility resolves fallback priority:
1. formatted address
2. street + locality
3. locality
4. coordinates

### 6.4 Success criteria
- Uniform address policy across all screens.
- Full location available wherever details are shown.

## 7) Additional Core Optimization Opportunities (Beyond the 6 Requested)

## 7.1 State authority duplication
Observed overlap among service, manager, repository, app state manager, and synchronizer. This remains the highest systemic risk.

Fix:
- Introduce `StateWriteGateway` (single mutation authority).
- Convert event bus usage to notification-only, not mutation path.

## 7.2 WorkManager complexity and fallback sprawl
WorkManager checks/retries/fallbacks are spread across app/service/helper/viewmodels.

Fix:
- Consolidate scheduling/canceling/status into `PlaceDetectionScheduler`.
- Enforce unique work policy (`KEEP`/`REPLACE`/`APPEND_OR_REPLACE`) by explicit contract per worker type.

## 7.3 Persistence fragmentation
`PreferencesRepositoryImpl` is large, SharedPreferences-heavy, and many feature flags are mixed in one class; some UI settings still TODO-persisted.

Fix:
- Migrate progressively to DataStore with SharedPreferences migration.
- Split preferences by bounded context (tracking, analytics, UI, privacy).

## 7.4 Transaction and integrity hardening
Visit and state transitions should be transactional and auditable.

Fix:
- Add transaction wrappers for multi-step transitions.
- Add invariant checks in CI smoke tests.

## 7.5 Oversized class decomposition
Major hotspots exceed maintainable size and mix multiple responsibilities.

Fix:
- Split by role (adapter/orchestrator/domain policy/IO boundary).
- Keep Android framework classes thin adapters.

## 7.6 Test coverage deficit
Current tests are too few for runtime-critical flows.

Fix:
- Add focused tests for:
- startup/restart recovery,
- visit transition invariants,
- timeline segment generation,
- insight schema output stability,
- migration and persistence behavior.

## 7.7 Export/privacy alignment gap
`anonymizeExports` exists in preferences but export path should enforce consistent anonymization policy.

Fix:
- Add explicit anonymization pipeline in `ExportDataUseCase`.
- Add per-format privacy guarantees.

## 8) Wiring and Function Mapping (Extended)

## 8.1 Proposed authority map
- Tracking lifecycle authority: `TrackingLifecycleOrchestrator`
- State mutation authority: `StateWriteGateway`
- Worker scheduling authority: `PlaceDetectionScheduler`
- Insight generation authority: `InsightEngine`
- Presentation formatting authority (address/name): shared formatter services

## 8.2 Data flow mapping (target)
1. `LocationTrackingService` (adapter) -> ingest location -> `SmartDataProcessor`.
2. `SmartDataProcessor` -> transactional visit/state mutations via `StateWriteGateway`.
3. Gateway persists to DB + updates in-memory state once.
4. Timeline/Map/Insights read canonical aggregates, not custom slices.
5. UI layers render with shared formatting policies.

## 9) Suggested Execution Phases (Adds to existing backlog)

### Phase 6.1: Data and state authority cleanup
- Implement `StateWriteGateway` + transition transactions.
- Remove duplicate mutation paths.

### Phase 6.2: Restart reliability
- Implement `TrackingLifecycleOrchestrator` and idempotent boot reconciliation.

### Phase 6.3: Timeline integrity
- Add zero-duration guardrails and dedupe compaction.

### Phase 6.4: Smarter insights
- Introduce canonical insight schema and adapters.

### Phase 6.5: Name ranking improvements
- Candidate normalization, feature scoring, feedback learning.

### Phase 6.6: Location display unification
- Implement `LocationDisplayPolicy` + shared formatter across screens.

### Phase 6.7: Broader optimization
- DataStore migration, WorkManager scheduler consolidation, export anonymization enforcement.

## 10) Acceptance Test Matrix (Minimum)
- Restart/boot matrix: reboot, process death, background start constraints, permission edge cases.
- Visit lifecycle matrix: jittery GPS, rapid boundary crossing, same-place reentry.
- Cross-screen parity: day/place/visit totals equal between map/timeline/analytics.
- Insight matrix: confidence/provenance present and valid for each insight card.
- Suggestion matrix: rank stability and quality with historical corrections.
- UI matrix: compact/standard/full address policy correctness.

## 11) Research References
- Android foreground service restrictions and launch guidance:
- https://developer.android.com/about/versions/12/foreground-services
- https://developer.android.com/develop/background-work/services/fgs/launch
- https://developer.android.com/develop/background-work/services/fgs/changes
- WorkManager unique work and conflict policies:
- https://developer.android.com/reference/androidx/work/WorkManager
- https://developer.android.com/reference/androidx/work/ExistingWorkPolicy
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
- Room transactions and migration testing:
- https://developer.android.com/reference/androidx/room/Transaction
- https://developer.android.com/training/data-storage/room/migrating-db-versions
- DataStore migration from SharedPreferences:
- https://developer.android.com/codelabs/android-preferences-datastore
- https://developer.android.com/reference/androidx/datastore/preferences/SharedPreferencesMigrationKt
- Nominatim usage policy:
- https://operations.osmfoundation.org/policies/nominatim/
- Mobility analysis/stay-point references:
- https://scikit-mobility.github.io/scikit-mobility/reference/preprocessing.html
- https://pubmed.ncbi.nlm.nih.gov/20167789/

## 12) Final Notes
This document is intentionally implementation-oriented for restart execution. It should be used with:
- `01_ARCHITECTURE_AND_DESIGN.md`
- `03_WIRING_AND_FUNCTION_MAPPING.md`
- `04_IMPLEMENTATION_BACKLOG.md`

for phased delivery and regression-safe modernization.
