# Voyager — Core Hardening Execution Plan

_Date: 2026-05-15_
_Reads with: `competitor-analysis.md`, `core-hardening-audit.md`, `edge-cases-and-hidden-bugs.md`._

## Verdict

**Current state of the codebase:**

| Dimension | Score | Reality |
|-----------|-------|---------|
| Architecture (layering) | 8/10 | Clean 6-layer; minor pipeline-DAO leak |
| Pipeline correctness | 6/10 | Strong design; 4 races + 1 OOM risk |
| Data integrity | 4/10 | No tombstones, no FKs, no versioning, backup landmine |
| Concurrency safety | 5/10 | Multiple RMW races, scope leaks |
| Edge-case coverage | 3/10 | Time, OEM, restore, captive portal all unhandled |
| Security posture | 6/10 | Encryption OK; missing surface hardening |
| Test coverage | 2/10 | ~3% file coverage; UI, workers, migrations untested |
| Extensibility for v2 (cloud, plugins) | 3/10 | Schema lacks foundation; hardcoded chains |

**Overall: NOT ready for public ship. Foundation work required.** Estimated 3 focused weeks (1 engineer full-time) to reach a green Pre-Feature Acceptance Gate.

---

## The plan: 21 days, four phases

This is the **canonical execution backlog**. Every item maps to a finding in one of the three audit docs. Items are sized in engineer-days (EngD) and ordered for minimum-blast-radius (early items fix what later items depend on).

### Phase 0 — Day 0 (1 day, ~6 hours)

Pre-work: cannot start without these.

| # | Item | EngD | Source | File(s) |
|---|------|------|--------|---------|
| 0.1 | Add `LocalCrashHandler` writing to `HealthLogEntity` and chain to default handler | 0.3 | Pass 2 O1 | new `platform/crash/LocalCrashHandler.kt`; wire in `VoyagerApplication.onCreate` |
| 0.2 | Enable `StrictMode` (thread + VM) for `BuildConfig.DEBUG` | 0.2 | Pass 2 O2 | `VoyagerApplication.kt` |
| 0.3 | Set up CI: unit tests on PR, OWASP Dependency-Check monthly | 0.3 | Pass 3 B1 | `.github/workflows/` |
| 0.4 | Add `dependencyCheck` Gradle plugin & baseline | 0.2 | Pass 3 B1 | root `build.gradle.kts` |

Now any further bug fires a HealthLog row; any StrictMode violation surfaces in debug.

---

### Phase A1 — Days 1–7: Concurrency, Time, & Pipeline Safety

**Goal:** the pipeline cannot lose, duplicate, corrupt, or misorder a sample under any concurrent workload.

| # | Item | EngD | Source | File(s) |
|---|------|------|--------|---------|
| A1.1 | **Replace `CurrentRuntimeStateDao.atomicUpdate()` with actor-pattern serializer** | 1.5 | H1 | new `domain/runtime/RuntimeStateActor.kt`; refactor all callers in `pipeline/`, `TrackingRuntimeCoordinator` |
| A1.2 | **`LocationCapture` dedup via `AtomicLong.updateAndGet`**; remove `@Volatile` pattern; remove `runBlocking` at :45 | 0.5 | H2 + Phase A | `capture/LocationCapture.kt` |
| A1.3 | **`Segmenter` time-based flush every 15 min during VISIT**; hard cap buffer at 500 samples | 0.5 | H3 | `pipeline/stage/Segmenter.kt` |
| A1.4 | **Mutex-wrapped `VisitDao.insertIfNotOverlapping`**; add SQL unique partial index `(placeId, arrivalAt) WHERE deletedAt IS NULL` | 0.5 | H4 | `storage/database/dao/VisitDao.kt`; migration v2→v3 |
| A1.5 | **`LocationCaptureService` double-start guard** (AtomicBoolean + Mutex) | 0.5 | H6 | `platform/service/LocationCaptureService.kt` |
| A1.6 | **`PendingPlaceUpdateDao` atomic claim-consume** (single UPDATE statement) | 0.5 | H7 | `storage/database/dao/PendingPlaceUpdateDao.kt` |
| A1.7 | **`ActivityTransitionReceiver` single singleton scope**; remove per-broadcast scope creation | 0.3 | H8 | `capture/ActivityTransitionReceiver.kt` |
| A1.8 | **`PlaceGeofenceManager.syncGeofences` Mutex** | 0.2 | H9 | `capture/PlaceGeofenceManager.kt` |
| A1.9 | **`TimelineStateStore` corruption-aware fallback**; log to HealthLog instead of silent reset | 0.3 | H10 | `storage/TimelineStateStore.kt` |
| A1.10 | **`DetectVisitUseCase` timezone-aware return window**; carry tz on VisitEntity | 0.5 | H11 + T2 + T3 | `domain/usecase/DetectVisitUseCase.kt`, `storage/database/entity/VisitEntity.kt` (schema bump consolidated in A2.1) |
| A1.11 | **JSON column size caps** on evidence/votes entities (top-50 by weight on insert) | 0.3 | H12 | `domain/usecase/*EvidenceWriter*`, JSON serializer |
| A1.12 | **Clock facade** `domain/time/Clock.kt`; enforce monotonic vs wall-clock separation | 0.5 | T1, T6, T7 | new `Clock.kt`; grep + replace `System.currentTimeMillis()` in pipeline |
| A1.13 | **Sample time-travel sanity gate** in `LocationCapture` (reject ±1h impossible jumps) | 0.3 | T5 | `capture/LocationCapture.kt` |
| A1.14 | **Speed clamp at 200 m/s** in `Segmenter`; introduce `FLIGHT` placeholder mode | 0.3 | US4 | `pipeline/stage/Segmenter.kt` |
| A1.15 | **Force-stop recovery banner** on next launch if last-sample gap > 24h | 0.5 | L1 + US19 | new `presentation/screen/reliability/ForceStopBanner.kt` |
| A1.16 | **Remove all `!!` from `presentation/screen/**`** (replace with safe nullable handling) | 0.5 | Pass 1 #1 | `MapScreen.kt`, `DashboardScreen.kt`, `CategoriesScreen.kt` |
| A1.17 | **Audit `runCatching { }`** in coroutine code; rethrow `CancellationException` | 0.3 | X4 | grep + fix |
| A1.18 | **`Segmenter.flushAll()` on tracking stop** | 0.2 | X9 | `pipeline/PipelineConsumer.kt` |
| A1.19 | **Throttle foreground notification updates to ≥60s** | 0.3 | X10 | `platform/notification/VoyagerNotificationManager.kt` |

**Phase A1 total: ~8 EngD (7 day-buffer accounting for review + integration testing).**

---

### Phase A2 — Days 8–14: Data Foundation & Cloud-Ready Schema

**Goal:** the schema can support unlimited future feature growth, soft deletes, multi-device, and (optional) cloud sync — without a destructive future migration.

| # | Item | EngD | Source | File(s) |
|---|------|------|--------|---------|
| A2.1 | **Schema migration v2→v3**: add `lastModifiedAt: Long`, `revision: Long`, `deletedAt: Long?` to 7 syncable entities (places, visits, movement_segments, routes, geocode_candidates, correction_feedback, user_preferences) | 2.0 | A1 + H4 | `storage/database/entity/*`, new `MIGRATION_2_3` |
| A2.2 | **Soft-delete refactor**: all DAO read queries add `WHERE deletedAt IS NULL`; delete methods become `UPDATE SET deletedAt = NOW()` | 1.0 | A1 | `storage/database/dao/*` |
| A2.3 | **`DataRetentionWorker` extended** to hard-purge tombstones after 30-day grace | 0.3 | A1 | `platform/worker/DataRetentionWorker.kt` |
| A2.4 | **Add FK declarations with `onDelete = SET_NULL`** on VisitEntity.placeId, MovementSegmentEntity.placeId, GeocodeCandidateEntity.placeId, SearchMetadataEntity.sourceId | 0.5 | A3 | entity files; migration |
| A2.5 | **Missing indices**: `mergedIntoPlaceId`, `isUserCorrected`, FK single-column | 0.3 | P2 | entities; migration |
| A2.6 | **Convert `accuracyM` and `radiusM` from Float to Double** | 0.5 | P6 | RawLocationSampleEntity, PlaceEntity; migration |
| A2.7 | **`PRAGMA journal_mode=WAL`** in VoyagerDatabase create callback | 0.1 | P1 | `storage/database/VoyagerDatabase.kt` |
| A2.8 | **`backup_rules.xml` excludes DB**; `dataExtractionRules.xml` for Android 12+ | 0.3 | H5 | new `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`; AndroidManifest |
| A2.9 | **First-launch restore flow**: detect existing keystore but missing/mismatched DB; offer `.voyager` import | 1.0 | L2 + L3 + US7 + US8 | new `presentation/screen/onboarding/RestoreScreen.kt`; logic in `VoyagerApplication` |
| A2.10 | **`PRAGMA integrity_check`** on app boot; route to recovery screen on failure | 0.3 | F4 | `storage/database/VoyagerDatabase.kt` |
| A2.11 | **Storage-full detection**: catch `SQLiteFullException`, pause tracking, notify user | 0.3 | F1 | wrap critical writes in pipeline |
| A2.12 | **Migration test suite** with `MigrationTestHelper` for v1→v2 and v2→v3 | 1.0 | Pass 1, Pass 2 §6 | `androidTest/.../MigrationTest.kt` |
| A2.13 | **WAL checkpoint after DataRetentionWorker** | 0.1 | F2 | `DataRetentionWorker.kt` |
| A2.14 | **GDPR Article 17 hard-purge path** (bypasses tombstone for "Clear all data") | 0.3 | LG1 | `data/repository/SettingsRepositoryImpl.kt` |

**Phase A2 total: ~8 EngD.**

---

### Phase A3 — Days 15–21: Identity, Boundaries, Tests, & Compliance

**Goal:** the codebase is now resilient AND extensible. Plugin marketplace, cloud sync, white-label, iOS port are all *opt-in plugins* against fixed seams — not refactors.

| # | Item | EngD | Source | File(s) |
|---|------|------|--------|---------|
| A3.1 | **`installationId` UUID generated on first launch**, persisted in DataStore | 0.3 | A2 | `domain/identity/InstallationIdProvider.kt` |
| A3.2 | **Schema migration v3→v4**: add `userId: String` to 7 syncable entities, backfill with installationId | 1.0 | A2 | entities; migration |
| A3.3 | **`ExportFormatPlugin` interface** + DI Set binding + refactor existing GPX/GeoJSON/CSV/JSON formats as plugins | 1.5 | A4 | new `domain/export/ExportFormatPlugin.kt`; refactor `data/repository/ExportRepositoryImpl.kt` |
| A3.4 | **`GeocodingProvider` interface** lifted to `domain/`; existing Nominatim/Photon/Android-Geocoder refactored as plugins | 1.0 | A4 | new `domain/geocoding/GeocodingProvider.kt`; refactor `data/api/*GeocodingService.kt` |
| A3.5 | **`PipelineGateway` interface in `domain/`**; pipeline depends on it not DAOs | 1.0 | A6 | new `domain/pipeline/PipelineGateway.kt`; refactor `pipeline/PipelineConsumer.kt`; implement in `data/` |
| A3.6 | **Typed-ID value classes**: `PlaceId`, `VisitId`, `SegmentId`, `RouteId` | 0.5 | A8 | new `domain/model/ids/*`; refactor repository interfaces |
| A3.7 | **All notification copy extracted to `strings.xml`** | 0.5 | A5 | `res/values/strings.xml`; refactor `VoyagerNotificationManager.kt` |
| A3.8 | **Manifest `android:exported` audit**: every component declares it explicitly | 0.3 | Sec3 | `AndroidManifest.xml` |
| A3.9 | **`network_security_config.xml`** with `cleartextTrafficPermitted=false` | 0.2 | Sec1 | `res/xml/network_security_config.xml` |
| A3.10 | **Replace all `Log.*` with `Timber.*`**; production Tree drops verbose | 0.5 | Sec2 | grep + replace |
| A3.11 | **`FLAG_SECURE` toggle** in Privacy settings | 0.3 | U10 | `presentation/screen/settings/PrivacyTab.kt`; per-screen activity flag |
| A3.12 | **Captive portal detection** in geocoding (Content-Type check, HealthLog event) | 0.3 | N1 | `data/api/NominatimGeocodingService.kt` |
| A3.13 | **Nominatim rate-limit jitter** (0–3s random delay) | 0.2 | N3 | same |
| A3.14 | **Reliability Check screen**: OEM detection, 24h gap self-test, deep-link to dontkillmyapp | 1.0 | Pass 1 #8 + S8 + W7 | new `presentation/screen/reliability/ReliabilityScreen.kt` |
| A3.15 | **Battery cost self-report card** (BatteryStatsManager) | 0.5 | Pass 1, Comp Analysis 3a | new `platform/battery/BatteryUsageReporter.kt` |
| A3.16 | **Approximate-permission detection** (Android 12+): show "rough timeline" mode banner | 0.3 | S3 | `platform/coordinator/PermissionMonitor.kt` |
| A3.17 | **Cap geocoding cache TTL at 90 days** + background refresh | 0.3 | N7 | `data/repository/GeocodingRepositoryImpl.kt` |
| A3.18 | **Lint rule: no `Dao` import outside `data/`** | 0.5 | A6 | `:app/lint.xml`, custom lint check |
| A3.19 | **Encryption-always (delete encryption toggle)**; remove `migrateToEncrypted` stubs | 0.5 | Pass 1 #3 | `DatabaseEncryptionManager.kt`; settings screen |
| A3.20 | **Encryption round-trip test** (write 1000 rows, kill process, reopen) | 0.5 | Pass 2 §6 | `androidTest/.../EncryptionTest.kt` |
| A3.21 | **Concurrent 24h synthetic pipeline test** at 100× speed | 1.0 | Pass 2 §6 | new `androidTest/.../ConcurrentPipelineTest.kt` |
| A3.22 | **Permission-revocation UI test** | 0.5 | Pass 2 §6 | new `androidTest/.../PermissionRevocationTest.kt` |
| A3.23 | **Property tests on Segmenter / Kalman / DetectVisit** | 0.5 | Pass 2 §6 | use `kotlin.test` + Kotest or hand-rolled |
| A3.24 | **Backup-restore round-trip test** | 0.5 | H5 + Pass 2 §6 | `androidTest/.../BackupRestoreTest.kt` |
| A3.25 | **Privacy policy public + linked** from Settings | 0.3 | LG2 | new repo `docs/privacy-policy.md` + Settings URL |

**Phase A3 total: ~13 EngD** (slightly over a week — the gate is dense).

---

## Total scope: 29 EngD ≈ 3 weeks + 1 day buffer

This is one engineer working focused. Two engineers in parallel could close in ~2 weeks if work is split: one owns A1+A3 concurrency/identity; other owns A2 data foundation.

## Pre-Feature Acceptance Gate — must be green before Phase B

(Reproduced from `edge-cases-and-hidden-bugs.md` §14, condensed here.)

- All A1 / A2 / A3 backlog items completed and merged.
- `grep '!!' presentation/screen/` returns 0.
- `grep 'runBlocking' src/main/` returns 0.
- Migration tests pass for v1→v2→v3→v4.
- Concurrent 24h pipeline test passes (zero overlapping visits, zero NaN, peak heap < 80MB).
- Backup-restore test passes.
- Encryption round-trip test passes.
- OEM matrix (manual): Pixel + Xiaomi + Samsung + OnePlus — each runs cleanly for 24h.
- Crash-free sessions ≥ 99.5% over 7-day internal dogfood across the 4 OEMs.
- Battery cost on Pixel < 4%/day with default preset.
- OWASP Dependency-Check: zero high/critical CVEs.
- Privacy policy public + Play Store data-safety form drafted.

If all green: ship the foundation. Build Phase B features on top with confidence.

If any red: do not ship features. Triage and clear.

---

## What this plan deliberately does NOT include

These are post-foundation work, deferred with reason:

- New user features (Day Story, mileage log, real names) — Phase B.
- iOS port — wait for $5k MRR; the `PipelineGateway` seam (A3.5) prepares it.
- Cloud sync — frozen contract from Pass 2 §8 makes this an opt-in plugin later; no work now.
- StrongBox enforcement — current Keystore is fine.
- Compose recomposition deep profiling — defer to Phase C polish.
- Differential privacy for opt-in aggregation — only if/when aggregate contribution feature ships.

---

## Risk register for this sprint

| Risk | Probability | Mitigation |
|------|-------------|------------|
| Schema v2→v3 migration corrupts dev DBs during sprint | Medium | Snapshot dev DB before merge; migration tests gate the PR |
| Plugin interface refactor (A3.3, A3.4) breaks export feature in subtle ways | Medium | End-to-end smoke test exports every format on every release build |
| Actor refactor (A1.1) introduces deadlock | Medium | Concurrent test (A3.21) catches; code review focus |
| Soft-delete rollout misses a DAO query → users see "ghost" data | Medium | Lint rule (A3.18) + `WHERE deletedAt IS NULL` test for every DAO method |
| Pre-existing technical-debt items found mid-sprint blow the timeline | High | Scope is locked; new finds go to a separate backlog, not this sprint |

---

## The honest answer the founder needs

If a teammate asks "is the code working fine?": yes, on a Pixel, with the original developer using it, on a clear-day path, it works. **But it would not survive 1000 users across 50 OEMs in 30 countries.** That gap is what this 3-week sprint closes.

The cost of *not* doing this sprint:
- Year 1 churn doubles because of restore-from-backup failures (H5).
- Mileage exports occasionally show overlapping visits (H4) → credibility-killer for the paid tier.
- Force-stop on Xiaomi looks like "the app is broken" → 1-star reviews.
- Adding cloud sync later costs a full quarter and risks every user's data.

The cost of *doing* this sprint:
- 3 weeks of no new features.
- Strict acceptance gate forces honest discipline.
- Foundation features compound on top, every release ships faster than the last.

This is the highest-leverage 3 weeks Voyager has in the next 12 months. Then — and only then — Phase B opens.
