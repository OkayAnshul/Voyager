# Voyager — Core Hardening Audit & Cloud-Ready Foundation Plan

_Date: 2026-05-15_
_Companion docs: `docs/research/competitor-analysis.md`, `~/.claude/plans/debug-test-and-bright-deer.md` (full strategic plan)_

## 0. Why this matters

The engine is genuinely good — clean 6-layer architecture, well-modeled pipeline, sensible Room schema. But three deep-dive audits (pipeline correctness, architecture/extensibility, DB integrity) surfaced **15 issues that will quietly break the app at scale**, and **7 architectural decisions that will lock out future cloud / plugins / iOS / B2B unless fixed now while the surface area is small.**

This document is the deep engineering review that must happen before any feature work continues. The previous audit covered shallow issues — force-unwraps, runBlocking, encryption stubs. This part covers deep structural risks that compound as features pile on, and cloud-readiness gaps that would force a painful refactor if optional cloud sync is ever added.

---

## 1. Showstoppers — data-corruption-class bugs

### H1. `CurrentRuntimeStateDao.atomicUpdate()` — Read-Modify-Write race
- **File**: `storage/database/dao/CurrentRuntimeStateDao.kt:19-23`
- **What's wrong**: Reads entity, transforms, writes back inside a `@Transaction`. Room's `@Transaction` does NOT prevent concurrent application-level callers from interleaving read/transform/write across two threads — it only batches the write into one DB transaction. Two coroutines hitting `atomicUpdate()` simultaneously (PipelineConsumer setting `lastMotionState` while TrackingRuntimeCoordinator updates `pendingVisitCandidate`) will silently lose one update.
- **Impact**: Data loss on the single most critical row. Tracking state drifts; visits resurrect; resumed sessions reference a deleted visit.
- **Fix**: Replace with a single SQL `UPDATE ... WHERE` per field, OR funnel all writes through a Mutex-guarded singleton, OR use a Channel-backed actor that serializes intents. Recommended: actor pattern in a new `RuntimeStateActor` class.

### H2. `LocationCapture` duplicate-sample window
- **File**: `capture/LocationCapture.kt:35, 63-64` (in addition to the runBlocking issue at :45)
- **What's wrong**: `@Volatile lastProcessedTimestamp` is checked-then-set across Main and IO threads. Volatile is visibility, not atomicity. Two FLP batches landing within microseconds can both observe the same `lastProcessedTimestamp`, both pass the dedup gate, both enter the pipeline.
- **Impact**: Duplicate samples inflate visit dwell, fragment segments, double-count distance.
- **Fix**: Replace volatile + branch with `AtomicLong.updateAndGet { ... }` or move the dedup into the same coroutine context (single-threaded confinement).

### H3. `Segmenter` unbounded sample buffer for long VISIT segments
- **File**: `pipeline/stage/Segmenter.kt:100-115`
- **What's wrong**: VISIT/DWELL segments trim to first+last 50 samples only when `MAX_SEGMENT_SAMPLES` is reached, but no time-based flush. A 24-hour dwell with 1 sample/sec = ~86,400 samples × ~400 bytes ≈ **34 MB in memory per stationary day**.
- **Impact**: OOM on long stays (overnight sleep, office workday with no displacement).
- **Fix**: Time-based flush at 15-min intervals during VISIT (write partial segment, keep tail), OR hard cap buffer at 500 samples regardless of segment type.

### H4. `VisitDao.insertIfNotOverlapping` race
- **Files**: `storage/database/dao/VisitDao.kt`, integrity proven only by `VisitIntegrityTest`
- **What's wrong**: SELECT-overlap-then-INSERT is two statements. Room serializes inside one `@Transaction` but the *invariant* is enforced in code, not the schema. Multiple workers writing visits (SemanticLabelWorker, DiscoverPlacesWorker, pipeline) can race.
- **Impact**: Overlapping visits — breaks every downstream rollup, breaks evidence cards, breaks mileage exports.
- **Fix**: Add a SQL CHECK constraint or partial unique index `(placeId, arrivalAt)` AND wrap insert in a write Mutex held across the whole pipeline write phase.

### H5. Database **auto-backed-up by Google Drive** (silent data-loss landmine)
- **File**: `app/src/main/AndroidManifest.xml` + missing `backup_rules.xml`
- **What's wrong**: `android:allowBackup="true"` (default) backs up the SQLCipher DB to Google Drive. On device migration, the encrypted blob restores but the Keystore-derived key on the new device is different → the DB will never open → restore wipes the fresh install's DB and replaces with an unreadable one.
- **Impact**: User changes phone, opens Voyager, all data unreadable. **Critical credibility bomb.**
- **Fix**: Either set `android:allowBackup="false"`, or write a proper `backup_rules.xml` that excludes `databases/voyager_database*` and explicitly handles migration via Voyager's own encrypted export (`.voyager` file) instead.

---

## 2. Serious — pipeline & concurrency hazards

### H6. `LocationCaptureService` double-start race
- **File**: `platform/service/LocationCaptureService.kt:56-82`
- **Issue**: `intent == null` triggers async `restoreFromCrash()`; a near-simultaneous user-initiated startCommand fires the explicit start. Both can call `locationCapture.start()` concurrently → duplicate FLP callbacks.
- **Fix**: Guard with an `AtomicBoolean isStarting` and `Mutex` around startup.

### H7. `PendingPlaceUpdateDao` consumption race
- **File**: `storage/database/dao/PendingPlaceUpdateDao.kt:12-18`
- **Issue**: `getUnconsumed()` → process → `markConsumed()` is non-atomic. Two workers + main thread can each consume the same staged update.
- **Fix**: Use `UPDATE pending_place_updates SET consumed_at = NOW() WHERE id IN (...) AND consumed_at IS NULL RETURNING *` (single statement). If RETURNING isn't supported in the SQLCipher build, use a transaction with optimistic version check.

### H8. `ActivityTransitionReceiver` scope leak
- **File**: `capture/ActivityTransitionReceiver.kt:26`
- **Issue**: Creates a new `CoroutineScope(Dispatchers.IO)` per broadcast. Never cancelled. Activity transitions can fire at high rate; scopes accumulate.
- **Fix**: Singleton injected `CoroutineScope`, OR `goAsync()` + a `PendingResult` pattern.

### H9. `PlaceGeofenceManager.syncGeofences()` unsynchronized
- **File**: `capture/PlaceGeofenceManager.kt:40-78`
- **Issue**: Mutable `activeGeofenceIds` updated from two callers (boot restore + auto-promote).
- **Fix**: Wrap with `Mutex`.

### H10. `TimelineStateStore.update()` silent deserialization failure
- **File**: `storage/TimelineStateStore.kt:103-105`
- **Issue**: Corrupt JSON → catches → falls back to `EMPTY_STATE`. State silently resets, mid-pipeline.
- **Fix**: Distinguish "first run" from "corruption" → on corruption, log a HealthLogEntity event + preserve last good state from backup column.

### H11. `DetectVisitUseCase` return-window timezone edge cases
- **File**: `domain/usecase/DetectVisitUseCase.kt:179-213`
- **Issue**: 30-min return window uses UTC delta but `dayKey` uses local timezone → midnight-straddling visits can fire return logic on wrong visit.
- **Fix**: Use a consistent timezone for both, OR carry both timezone and UTC offset on `VisitEntity` so the comparison is unambiguous.

### H12. JSON columns growing unbounded
- **Entities**: `SegmentEvidenceEntity.activityVotesJson`, `PlaceEvidenceEntity.namingCandidatesJson`, `CurrentRuntimeStateEntity.livePlaceStateJson`
- **Issue**: No size cap; pathological device (jittery activity recognition) can balloon a row to MB-scale.
- **Fix**: Cap arrays at N entries on insert (e.g. top-50 votes by weight) + periodic GC worker.

---

## 3. Architecture — make it cloud/plugin/multi-user-ready

### A1. **No `lastModifiedAt`, no soft-delete, no version column on any entity** — *the single biggest cloud-readiness blocker*
- **Affected**: all 20 entities
- **Why it matters**: cloud sync requires three things — tombstones (to know when a row was deleted on device A so device B can apply that delete), revision numbers (to detect conflicts), and modification timestamps (for last-write-wins or merge resolution). Voyager has zero of these.
- **Plan**: add three columns to every entity that could ever sync (`places`, `visits`, `movement_segments`, `routes`, `geocode_candidates`, `correction_feedback`, settings):
  - `lastModifiedAt: Long` (epoch ms, default `strftime('%s','now') * 1000`)
  - `revision: Long` (default 1, increment on every UPDATE via Room TypeConverter or trigger)
  - `deletedAt: Long?` (NULL = live, non-null = tombstone, hard-delete after grace period via `DataRetentionWorker`)
- Add a single Room migration v2→v3 that adds these columns + backfills `lastModifiedAt = createdAt` or `arrivalAt` or current time.
- **All DAOs** add `WHERE deletedAt IS NULL` to read queries; delete becomes UPDATE `SET deletedAt = NOW()`.
- This is *the* migration that future-proofs Voyager. Doing it now while there are zero users in production is cheap. Doing it after 100k users is a quarter-long refactor.

### A2. **No `userId` field anywhere — single-user assumption baked into schema**
- **Affected**: all entities
- **Why it matters**: any future sync, family-pairing, or B2B fleet mode requires multi-tenant scoping. Today every query is `SELECT * FROM places` implicitly meaning "this device's data".
- **Plan**: add `userId: String` (UUID) to every syncable entity. For single-user devices, populate with a generated install-UUID stored in DataStore (`installationId`). For B2B/multi-user variant, populate with the active profile. All repository methods take a `userId` parameter (default to the install UUID).
- Migrate v3→v4 to add this column.

### A3. **No FOREIGN KEY CASCADE** — orphan visits/segments/geocodes
- **Affected**: `VisitEntity.placeId`, `MovementSegmentEntity.placeId`, `GeocodeCandidateEntity.placeId`, `SearchMetadataEntity.sourceId`
- **Why**: deleting a Place (merge, archive) leaves orphan rows that survive forever.
- **Plan**: declare proper `@ForeignKey(parentColumns = ["placeId"], childColumns = ["placeId"], onDelete = SET_NULL)` (not CASCADE — we want to preserve visit history with `placeId = null` so the user sees "Unknown place" instead of losing the visit).
- Add indices on FK columns.

### A4. **`ExportRepositoryImpl` and `GeocodingRepositoryImpl` are hardcoded — no plugin interface**
- **Files**: `data/repository/ExportRepositoryImpl.kt`, `data/repository/GeocodingRepositoryImpl.kt`
- **Why it matters**: every new export format (PDF, ICS, OPML) and every new geocoder (Pelias, Mapbox, self-hosted Photon) is a source edit. The plugin marketplace vision can't ship.
- **Plan**:
  - Define `interface ExportFormatPlugin { val id: String; val mime: String; fun supports(scope: ExportScope): Boolean; suspend fun write(data: ExportPayload, out: OutputStream) }`.
  - Define `interface GeocodingProvider { val priority: Int; suspend fun resolve(loc: GeoPoint, hint: CategoryHint?): List<GeocodeCandidate> }`.
  - Hilt module supplies a `Set<@JvmSuppressWildcards ExportFormatPlugin>` and the repo iterates.
  - Bonus: the `GeocodingProvider` interface lives in `domain/` so it can be implemented in a future plugin module without touching `:app`.

### A5. **Notification copy hardcoded in `VoyagerNotificationManager.kt`** — i18n & white-label blocker
- **File**: `platform/notification/VoyagerNotificationManager.kt`
- **Why**: "Voyager is tracking your journey" lives in code, not `strings.xml`. Localization, white-label rebrand, A/B copy testing all blocked.
- **Plan**: extract every user-visible string into `strings.xml`, with `@StringRes` IDs passed through the manager. Channel display names also.

### A6. **Pipeline layering violation — `PipelineConsumer` imports DAOs directly**
- **File**: `pipeline/PipelineConsumer.kt`
- **Issue**: pipeline depends on storage DAOs *and* domain UseCases *and* capture. Cleaner: pipeline orchestrates use cases that internally use repositories. No DAO imports outside `data/`.
- **Why it matters**: making `pipeline` a Kotlin Multiplatform module (for iOS port) requires it to depend only on `domain` interfaces, not Room.
- **Plan**: introduce `PipelineGateway` interface in `domain/` with the 4-5 methods the pipeline currently calls on DAOs (`recordSample`, `recordSegment`, `upsertVisit`, etc.). Implement it in `data/` as a thin facade. Pipeline depends on the interface.

### A7. **Singleton-scoped repositories with internal state — blocks multi-user / testing**
- **Files**: every `@Singleton` repository, especially `TimelineRepositoryImpl` (Overpass cache `ConcurrentHashMap`), `PipelineConsumer.started: AtomicBoolean`
- **Plan**: move per-instance caches into an injected `Cache` interface (so tests can pass a noop, B2B can pass a per-user cache). Pipeline's `started` flag moves into the actor pattern from H1.

### A8. **Long-typed IDs throughout — no value objects**
- **Why**: every public API takes `placeId: Long, segmentId: Long`. Easy to swap. No compile-time safety.
- **Plan**: introduce `value class PlaceId(val raw: Long)` / `VisitId(val raw: Long)` / `SegmentId(val raw: Long)` in `domain/model/ids/`. Migrate gradually starting with repository interfaces. Zero runtime cost (Kotlin value classes inline to Long at JVM level).

---

## 4. Performance & schema fit

### P1. SQLCipher journal mode
- Not explicitly set to WAL. Default `DELETE` mode locks the whole DB on writes.
- **Fix**: explicit `PRAGMA journal_mode=WAL;` in `VoyagerDatabase.kt` create callback.

### P2. Missing indices
- `VisitEntity` has `(placeId, arrivalAt)` but no `(placeId)` alone — `COUNT(*) WHERE placeId=?` still scans the composite index prefix; acceptable but verify Room query plans.
- `PlaceEntity.mergedIntoPlaceId` has no index — merge-chain traversal is O(n).
- `MovementSegmentEntity.isUserCorrected` has no index — common UI filter.
- **Fix**: add three single-column indices.

### P3. `SELECT *` in DAOs
- Many DAOs fetch full rows when projection would do. Specific offenders: `VisitDao.countByPlaceId()`, `PlaceDao.observeActivePlaces()` for the dashboard top-3.
- **Fix**: introduce projection POJOs (`PlaceSummaryProjection(placeId, name, visitCount)`) and Room-query them.

### P4. FTS index drift
- `SearchIndexEntity` (FTS4) has no triggers; when `PlaceEntity.userDisplayName` is renamed, FTS goes stale silently.
- **Fix**: in `PlaceRepositoryImpl.rename()`, explicitly call `SearchIndexDao.upsertPlace(placeId, newName)`. Or: switch to FTS4 with content table (`CREATE VIRTUAL TABLE search USING fts4(content="places", name)`) which auto-syncs.

### P5. No VACUUM
- `DataRetentionWorker` deletes rows but never `VACUUM`s. SQLCipher fragmentation grows.
- **Fix**: monthly `VACUUM` worker scheduled when device is idle + charging. Single statement.

### P6. Float vs Double precision
- `RawLocationSampleEntity.accuracyM: Float` and `PlaceEntity.radiusM: Float` — Float has ~7 significant digits, loses precision past 100m.
- **Fix**: change to Double (one migration, no semantic change).

---

## 5. Operational hardening

### O1. Crash sink
- No `Thread.UncaughtExceptionHandler` writing to `HealthLogEntity`. Crashes are invisible until reviewed in Play Console (and only on opted-in devices).
- **Fix**: install a `LocalCrashHandler` that writes a row to HealthLog with stacktrace + service state, then chains to the default handler.

### O2. Strict-mode in debug
- No `StrictMode` policy. Disk reads on main, network on main, leaked closeables — all invisible.
- **Fix**: in `VoyagerApplication.onCreate()` when `BuildConfig.DEBUG`, enable `StrictMode` for thread + VM policy.

### O3. ProGuard / R8
- `proguard-rules.pro` exists. Verify Room entities, kotlinx-serialization classes, and pipeline reflection-using classes are kept.
- **Fix**: explicit `-keep class com.cosmiclaboratory.voyager.storage.database.entity.** { *; }` and serializer keep rules.

### O4. Doze / app standby behaviour
- Foreground service survives Doze but WorkManager workers can be deferred indefinitely.
- **Fix**: critical workers (DataRetentionWorker is safe deferred; GeocodeBackfillWorker not critical) — none need `setExpedited`. But: surface deferral in `HealthLogEntity` so the Reliability Check screen can show it.

### O5. Compose-side performance hygiene
- No `Modifier.derivedStateOf` audit. Many Compose screens recompute on every state emission.
- **Fix**: profile with Layout Inspector recomposition counts; gate hot paths with `derivedStateOf`/`remember` where flagged. Defer to Phase B/C — not a launch blocker.

---

## 6. Testing — non-negotiable foundation tests

Before any new feature ships, the following test suites must exist:

| Suite | Target | Why |
|-------|--------|-----|
| **Migration tests** | Room `MigrationTestHelper` for every schema bump including the proposed v2→v3 (lastModifiedAt/revision/deletedAt) and v3→v4 (userId) | Schema bumps are the single highest data-loss risk |
| **Concurrent-pipeline integration test** | Synthetic 24-hour timeline replayed at 100× speed into the pipeline, asserting visit count, segment continuity, and zero overlap | Catches H1–H4 regressions automatically |
| **Property tests on `Segmenter`, `Kalman`, `DetectVisitUseCase`** | Random sample sequences, assert invariants (monotonic timestamps, no negative durations, no NaN coordinates) | Catches edge cases manual tests miss |
| **Permission-revocation UI test** | Instrumented: deny background location mid-tracking, assert service pauses, notification updates, no crash | Most common production crash scenario |
| **Backup-restore test** | Trigger Android auto-backup, restore on a different signing identity / Keystore, assert app boots cleanly (with empty DB, not corrupt) | Validates H5 fix |
| **Worker concurrency test** | Schedule `DataRetentionWorker`, `DiscoverPlacesWorker`, `GeocodeBackfillWorker` concurrently with a live pipeline; assert DB invariants hold | Catches H6–H9 |
| **OEM matrix** (manual) | One full day each on Pixel + Xiaomi + Samsung + OnePlus; document gaps | Doze and OEM killers are the #1 review killer |
| **Encryption round-trip** | Open SQLCipher DB with stored Keystore key, write+read 1000 rows, kill process, reopen, assert all rows readable; do this across OS upgrade | Validates encryption story honestly |

---

## 7. Proposed Hardening Sprint — slots into Phase A (now 3 weeks)

This re-scopes the previously-planned 2-week Phase A to 3 weeks. Phases B/C/D shift right by 1 week, total programme stays 90 days.

| Week | Theme | Deliverables |
|------|-------|--------------|
| **A1** | Concurrency safety | Fix H1 (actor for runtime state), H2 (atomic dedup), H4 (Mutex around visit write), H6 (double-start guard), H7 (atomic consume), H8 (single scope for receiver), H9 (Mutex for geofence sync). Plus shallow items: force-unwraps, runBlocking, encryption-always. |
| **A2** | Data foundation | Schema migration v2→v3 (`lastModifiedAt`, `revision`, `deletedAt` on 7 syncable entities); soft-delete refactor in DAOs and repositories; FK declarations + cascade rules (A3); `backup_rules.xml` (H5). |
| **A3** | Identity & extensibility | Add `installationId` (UUID) + `userId` columns (A2); introduce `ExportFormatPlugin` + `GeocodingProvider` interfaces (A4); extract notification copy to `strings.xml` (A5); typed-ID value classes (A8). |

After A3, the foundation is cloud-ready and plugin-ready without any features being built yet. Phase B (real names + Day Story) then layers cleanly.

---

## 8. The cloud-ready contract (frozen now, optional later)

Even though Voyager will never ship cloud as a default, **freezing the cloud-ready contract now** is what gives optionality. The contract:

1. **Every syncable entity has**: `id` (local Long PK), `clientId` (UUID created on insert), `userId` (UUID), `lastModifiedAt`, `revision`, `deletedAt`.
2. **All deletes are soft** (set `deletedAt`); a background worker hard-purges after a grace period (default 30 days, configurable).
3. **All mutations route through repositories**, never DAO direct (enforced by lint rule in `:app/lint.xml` — fail the build on `import ...Dao` outside `data/`).
4. **A `SyncManager` interface in `domain/`** with three methods: `pendingChanges(since: Long): Flow<ChangeBatch>`, `apply(batch: ChangeBatch): Result<Unit>`, `resolveConflict(local, remote): Resolution`. Default `NoOpSyncManager` ships; users can later install a `LocalNetworkSyncManager` (WiFi-direct) or — if ethics ever permit — an `E2EECloudSyncManager`.
5. **Conflict resolution is last-write-wins per field**, with `CorrectionFeedbackEntity` preserving the pre-conflict value (so users can revert in UI).
6. **Tombstones replicate**; hard deletes only happen post-replication.

With (1) and (4) in place from day 1, adding cloud later is an opt-in plugin, not a refactor.

---

## 9. What we are NOT hardening (defer with reason)

- Kotlin Multiplatform extraction of `pipeline` to support iOS — defer until iOS spec is real. The `PipelineGateway` interface (A6) is the seam; the extraction itself is later.
- WAL mode tuning beyond enabling it — defer; default is fine for current load.
- VACUUM scheduling beyond a monthly worker — defer; instrument first, optimize later.
- Compose recomposition profiling — defer to Phase C polish.
- StrongBox enforcement — defer; current Keystore-derived passphrase is acceptable.

---

## 10. Verification — how we know hardening worked

End of week A3:
- `grep -rn '!!' app/src/main/java/com/cosmiclaboratory/voyager/presentation/` returns 0 matches in screens.
- `grep -rn 'runBlocking' app/src/main/java/com/cosmiclaboratory/voyager/` returns 0 matches outside test/.
- A concurrent-pipeline integration test runs 24h of synthetic samples in <60s, asserts: zero overlapping visits, zero negative durations, zero null `placeId` for non-merged places, total distance within ±0.5% of ground truth, peak heap < 80 MB.
- A migration test from a v1 asset DB to v3 schema preserves all rows + populates `lastModifiedAt` / `revision` / `deletedAt` correctly.
- A backup-restore instrumented test boots cleanly post-restore with empty DB (no corrupt-DB crash).
- All export formats are loaded via the `ExportFormatPlugin` registry; adding a no-op format in tests requires zero source changes outside the test module.
- `lint` rule blocks any new `Dao` import outside `data/`.
- All foreground service starts route through a single `Mutex`; logs show no double-start in a 100-run stress test.

If all eight pass, the foundation is hardened. Build features on it without fear.

---

## 11. Closing — the philosophical point

The work in this hardening pass isn't glamorous and doesn't ship a single user-visible feature. But every feature in the broader plan — Day Story, mileage log, Family handshake, plugin marketplace, eventually-maybe cloud — *assumes a foundation that does not lose data, does not corrupt state, and does not lock the future in.*

A startup that ships features on a fragile core ships *technical debt at the speed of marketing.* A startup that hardens its core for three weeks before sprinting ships *features that compound.*

The Voyager engine is two PRs away from being genuinely production-grade. Do those PRs first.
