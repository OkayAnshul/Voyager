# Voyager — Deep Edge Cases, Hidden Bugs & Pre-Feature Acceptance Gate

_Date: 2026-05-15_
_Pass 3 of 3 — must read with: `docs/research/core-hardening-audit.md` (Pass 2) and `docs/research/competitor-analysis.md` (market context)._

## 0. Why a third pass

Passes 1 and 2 caught the obvious sins (force-unwraps, races, schema gaps, cloud-readiness debt). This pass simulates **what real users actually do** — travel across timezones, lose signal in tunnels, force-stop the app, restore a backup, install on a Xiaomi, hand the phone to a child, cross the International Date Line, run a VPN — and traces every failure mode through the codebase.

The premise: if Voyager's foundation can survive every scenario in §1–§13, then features in Parts 2–10 of the master plan ride a fortress. If even one of these scenarios silently corrupts a timeline, the *evidence card* (Voyager's biggest credibility moat) becomes a credibility *liability*.

This document is read in two ways:
- **Engineers**: a bug list to fix during Phase A.
- **Founder/PM**: a pre-feature acceptance gate (§14) — a single checklist that says "yes, the core is real."

---

## 1. Time, clock, and temporal hazards

The pipeline timestamps every sample. Every place visit is bounded by them. Every export, evidence row, IRS PDF, and court-quality timeline depends on them. They are the single most-trusted field in the database. They are also the field most likely to be wrong.

### T1. Wall-clock vs monotonic-clock mixing
- **Suspect**: `LocationCapture`, `Segmenter`, `PipelineConsumer`, `DetectVisitUseCase` — they likely use `System.currentTimeMillis()` for sample timestamps but may compare to `SystemClock.elapsedRealtime()` in places that measure durations.
- **Problem**: `System.currentTimeMillis()` is wall-clock (can jump backwards on NTP sync or user time change). `SystemClock.elapsedRealtime()` is monotonic. Mixing them in a single comparison causes negative durations and impossible visits.
- **Concrete failure**: user lands from a flight, phone NTP-syncs back 4 hours, the pipeline sees a sample whose timestamp is *earlier* than `lastProcessedTimestamp` and either silently drops it (`DedupSuppressor`) or creates a visit with negative dwell.
- **Fix**: every duration computation must use monotonic. Every persisted timestamp must use wall-clock + a stored timezone. Add a one-time audit grep for `currentTimeMillis()` vs `elapsedRealtime*()` and document the rule in a `Clock.kt` facade.

### T2. DST transitions splitting / doubling visits
- **Suspect**: `DetectVisitUseCase`, `VisitEntity.dayKey` derived from local timezone.
- **Spring forward (e.g., 02:00 → 03:00)**: a visit straddling 01:55–02:30 wall-clock either *appears to lose 30 minutes* (if using local-time math) or breaks rollups (`DailyRollupWorker` aggregates by `dayKey`).
- **Fall back (02:00 → 01:00)**: a visit at 01:30 could be assigned to the wrong day; `arrivalAt > departureAt` if they straddle the boundary.
- **Fix**: store every `VisitEntity` with **both** epoch-ms (UTC, monotonic-ish) **and** the IANA timezone ID at the time of the event (e.g., `"Europe/London"`). All UI presentation converts on render; all logic uses UTC math.

### T3. Timezone change mid-visit (international flight)
- **Scenario**: visit Tokyo → New York. The user's phone changes timezone in flight. Voyager's "visit" object started in JST, may "end" in EST. If `dayKey` is locked at insert-time, the visit references a stale day. If `dayKey` is recomputed on update, two days collide.
- **Fix**: snapshot the timezone at arrival; never recompute. Surface this in UI: "Arrived in Tokyo (JST), departed in New York (EST)".

### T4. International Date Line crossing
- **Scenario**: user flies LAX → SYD (crosses IDL westward, skips ~24h). Pipeline sees a sample at T, then next sample at T-20h (next day boundary by local clock, but later in real time).
- **Failure**: any code that does `if (sample.ts < lastAcceptedTs) drop` will discard the legitimate "later" sample. Conversely, code that trusts `currentTimeMillis` over GPS time could insert a backwards-segment.
- **Fix**: keep monotonic ordering by `elapsedRealtimeNanos` and absolute by wall-clock; never trust either alone.

### T5. User manually changes system time
- **Scenario**: a parent gives the device to a child; the child sets time to 2099. Pipeline accepts samples with bogus timestamps.
- **Fix**: a Kalman-like sanity gate — reject any sample whose `ts` is > +1 hour or < -10 minutes from `previousSample.ts + elapsedRealtimeDelta`. Log to `HealthLogEntity`.

### T6. Leap second / NTP step
- **Scenario**: Android's NTP correction shifts wall-clock by ±999 ms instantaneously.
- **Fix**: same as T1 — durations from monotonic, persisted timestamps from wall-clock with a "this was NTP-corrected at T" annotation if needed.

### T7. Sample-timestamp source is *Location.getTime()* vs *Location.getElapsedRealtimeNanos()*
- **Reality**: `Location.getTime()` is wall-clock-at-fix; `Location.getElapsedRealtimeNanos()` is monotonic-at-fix. The latter is far more reliable for ordering and dedup.
- **Fix**: dedup uses `getElapsedRealtimeNanos()`; persistence uses `getTime()`; both stored on the row.

### T8. Year 2038 / Long math
- **Status**: Long handles dates past 292 billion years. **But** any `Int` carrying a derived millis (e.g., a duration cast to Int) overflows at ~24.8 days. Grep for `.toInt()` on millis values.

---

## 2. Sensor & environment edge cases

### S1. Cold-start GPS giving 5 km accuracy
- **Scenario**: just after boot, FLP returns a fix with `accuracy=5000m`. `QualityScorer` likely scores this low, but does it actually reject? Or does it pass to Kalman where the variance smears a real place into a 5 km blob?
- **Fix**: hard threshold reject at `accuracy > 200m` in capture (before Kalman). Add a `HealthLogEntity` row when rejecting.

### S2. Stale last-known location replayed
- **Scenario**: `fusedLocationClient.getLastLocation()` returns a fix from yesterday. If `LocationCapture` ever calls this on start without checking `Location.getTime()`, you get a teleport.
- **Fix**: explicit `Location.getTime() > now - 30s` gate.

### S3. "Approximate" permission (Android 12+)
- **Reality**: user can grant "approximate" instead of "precise" location. Accuracy drops to ~10–20 km. Voyager's pipeline becomes nonsense (every place merges into one giant cluster).
- **Fix**: detect approximate-only at start; show a one-time explanation screen and switch to a "rough timeline" mode (no places, just cities). Never silently downgrade.

### S4. Permission revoked mid-session
- **Reality**: Android 14+ allows users to revoke a permission at any time without killing the app. The next FLP callback throws `SecurityException`.
- **Fix**: register a `PermissionMonitor` `BroadcastReceiver` (or use `ActivityCompat.shouldShowRequestPermissionRationale` polling) and pause the service gracefully, surface a notification.

### S5. Activity Recognition coalescing into ambiguous states
- **Reality**: AR can report `WALKING@90% + ON_FOOT@90%` (overlapping classes). Or `STILL@51% + IN_VEHICLE@49%` (truly ambiguous, e.g., in a car at a red light).
- **Fix**: `FuseActivityStateUseCase` needs explicit handling for this — use the highest-confidence non-overlapping pair, and lower visit-detection confidence accordingly. Tag the segment with "ambiguous activity" for evidence card.

### S6. Pedometer reset on reboot
- **Reality**: `Sensor.TYPE_STEP_COUNTER` resets to 0 on reboot. If Voyager stores raw counter values, a reboot causes a negative delta.
- **Fix**: monotonic-only delta math, with a reboot detector (compare `bootCount` from `Settings.Global` or `SystemClock.uptimeMillis()` regression).

### S7. WiFi off → fingerprint stage silently no-ops
- **Reality**: indoor place discrimination (L9 from competitor analysis) depends on WiFi scans. If user disables WiFi at home, fingerprinting silently produces zero data — the feature looks broken without explanation.
- **Fix**: surface "WiFi off — indoor accuracy reduced" in the Reliability dashboard when fingerprint count is zero for a confirmed place > 24h old.

### S8. Battery saver mode → AR / FLP throttled
- **Reality**: Doze-light and battery saver suppress location callbacks. The pipeline goes quiet without an obvious cause.
- **Fix**: `PowerManager.isPowerSaveMode()` polling + a HealthLog event "tracking degraded by battery saver" + Reliability screen surface.

### S9. Thermal throttling on cheap phones
- **Reality**: at >55°C SoC temp, Android suppresses background work. On a budget Xiaomi in summer in India, this is common.
- **Fix**: `PowerManager.getCurrentThermalStatus()` (API 29+) → if THROTTLING or higher, log and adapt sampling.

### S10. GPS spoofing apps (Lockito, Fake GPS) bypassing isMock
- **Reality**: `Location.isFromMockProvider()` is the official flag — but some spoofing apps patch the framework to suppress it. Some root-level spoofers can't be detected from userspace.
- **Fix**: add a heuristic "implausibility" check: if speed > 350 m/s (faster than any commercial flight), or if jumps > 100 km in < 60s, flag the segment. Don't try to defeat sophisticated attackers; just keep honest data honest.

---

## 3. Process & app lifecycle survival

### L1. App force-stopped via system Settings
- **Reality**: a Force Stop revokes the foreground service permission until the user explicitly relaunches the app. WorkManager is also blocked. Boot receivers don't fire either.
- **Fix**: nothing can override this at OS level. But: the Reliability dashboard should detect "no samples in >24h" on next launch and surface "Your tracking was paused (likely Force Stopped)".

### L2. App data cleared via system Settings → Keystore alias survives
- **Reality**: clearing app data wipes `/data/data/com.cosmiclaboratory.voyager/` including the DB. But the Android Keystore alias (the SQLCipher passphrase derivation key) is *not* always wiped — it lives in `androidkeystore`. On next install, a fresh DB is created but uses the *same* Keystore key. However, on a different device, the Keystore alias is missing and a fresh derivation is needed.
- **Fix**: on first run, attempt to open existing DB. If it fails with auth error AND a Keystore alias exists, surface "Existing data appears to be from a previous install with a different key — recover via `.voyager` export?" rather than silently overwriting.

### L3. ADB uninstall with `-k` flag (keeps data)
- **Reality**: `adb uninstall -k com.cosmiclaboratory.voyager` removes the APK but keeps `/data/data/...`. Reinstalling a different signing key (e.g., debug vs release) cannot read SQLCipher DB.
- **Fix**: signing-mismatch detection on first boot.

### L4. App migrated to SD card (older Androids)
- **Reality**: `installLocation="auto"` allows OS to move the APK. SQLCipher performance dies on slow SD. Foreground service permission may be lost.
- **Fix**: `installLocation="internalOnly"` in manifest (already check this).

### L5. Multi-user / Work Profile isolation
- **Reality**: a phone with a personal + work profile installs Voyager twice — once per profile. Each has its own DB, its own data dir, its own permissions. Surprising? Not really — that's Android. But two copies tracking the same person creates two timelines.
- **Fix**: detect "another Voyager profile is also running" via a content URI heartbeat; surface "Voyager is also running in your Work Profile". Or accept it and document.

### L6. Restore from auto-backup onto different signing identity
- **Reality**: covered in H5 of Pass 2 — `backup_rules.xml` must exclude DB. But also: Voyager's own `.voyager` encrypted export becomes the migration vector. **Test that export+import round-trips losslessly across signing keys.**
- **Fix**: write a round-trip integration test that exports, simulates device migration, imports, asserts byte-equality of rendered timeline.

### L7. Process death during a multi-step transaction
- **Reality**: WorkManager workers can be killed mid-execution. If `DiscoverPlacesWorker` is halfway through merging a cluster (writes one place, kills before the orphan visit reassignment), the DB is in a half-merged state.
- **Fix**: wrap multi-table writes in single Room `@Transaction`. Verify all worker entry points. Make worker idempotent — re-running should detect "already merged" state.

### L8. Recents-screen swipe vs back-button vs home-button
- **Reality**: swipe from Recents triggers `onTaskRemoved()` (different from `onDestroy()`). Many devs treat this as session-end; some apps stop the foreground service here. Voyager *must not* — tracking continues regardless of UI state.
- **Fix**: explicit override of `Service.onTaskRemoved()` that does NOT call `stopSelf()`. Test on Pixel + Xiaomi (different behaviors).

### L9. Predictive back gesture (Android 13+)
- **Reality**: in Compose, the predictive-back handler must be wired or the gesture animates out before the user commits. Confuses users navigating timeline → place detail → back.
- **Fix**: ensure `BackHandler` and `PredictiveBackHandler` are registered for stacked composables.

### L10. Configuration change during write
- **Reality**: user rotates phone while a "save place rename" coroutine is mid-flight. The ViewModel may be retained but the underlying coroutine scope could be cancelled if scoped to the wrong lifecycle.
- **Fix**: every "write that matters" runs in `viewModelScope` not `lifecycleScope`. Already an audit target.

---

## 4. UI / Compose edge cases not yet covered

### U1. Map state survives rotation but not process death
- **Reality**: `MapView` (MapLibre) state — camera position, selected markers — is lost on process death. The Map screen returns to default view, which feels like a bug to the user.
- **Fix**: persist last camera state (`lat`, `lng`, `zoom`, `bearing`, `tilt`) in DataStore on `onPause`; restore on `onResume`.

### U2. Compose previews crashing because of Hilt
- **Reality**: every screen depending on a `@HiltViewModel` cannot render in `@Preview` without a fake. Designers and engineers stop using previews; UI regressions go unnoticed.
- **Fix**: extract pure stateless composables for every screen — `DashboardContent(state: DashboardState, onAction: (DashboardAction) -> Unit)`. Previews target these.

### U3. Edge-to-edge handling on Android 15+
- **Reality**: API 35 enforces edge-to-edge. Compose `WindowInsets` handling must be correct or content goes under the status bar / nav gesture handle.
- **Fix**: audit `Scaffold(contentWindowInsets = ...)` everywhere. Visually verify on Android 14, 15, 16 (target SDK is 36).

### U4. Font scaling 200% breaking the activity rings
- **Reality**: users with low vision set system font scale to 200% or higher. Fixed-width Compose components with `width = 80.dp` overflow.
- **Fix**: layout audit at 130%, 180%, 200% font scale. Use intrinsic sizing or wrap-content for text-heavy components.

### U5. Right-to-left (Arabic / Hebrew / Urdu) layout flips
- **Reality**: the moment Voyager ships an Arabic locale (Persona 11 farmer cooperative scope might cross into Arabic markets), every horizontal layout must respect `LayoutDirection.Rtl`. The timeline's "arrival → departure" arrow flips direction.
- **Fix**: set `LocalLayoutDirection.current` correctly; test with `android:supportsRtl="true"` and a force-RTL pseudo-locale in dev.

### U6. Notification deep-link safety
- **Reality**: a tap on a daily-recap notification opens a specific day. If the deep link parser is sloppy and reads a malformed `dayKey`, the parser crashes the activity.
- **Fix**: defensive parsing in deep-link handler; on parse failure, open the dashboard. Never trust intent extras.

### U7. Accessibility on the map
- **Reality**: TalkBack has nothing to read on a MapView — it's a giant unlabeled surface. Markers have no semantics.
- **Fix**: render a list-based "accessibility map view" beside the visual map; ensure markers have content descriptions; or hide the map for TalkBack users and show the place list.

### U8. Color blindness on transport mode colors
- **Reality**: walk=green, cycle=blue, drive=purple, transit=orange — fine for most, but deuteranopia (~6% of men) struggles with green/orange.
- **Fix**: add icons on every colored chip; verify contrast in a Coblis simulation.

### U9. Compose recomposition storms from over-emitting Flows
- **Reality**: a Flow that emits 60 times/sec (active-time ticker) re-renders every composable observing it, even those that don't need 60Hz precision.
- **Fix**: gate with `derivedStateOf` and `Modifier.snapshotFlow`; clamp ticker to 1 Hz where possible.

### U10. App switcher snapshot leaking sensitive info
- **Reality**: when the user switches apps, Android takes a snapshot of the last visible screen — including the timeline with place names. A shoulder-surfer or stolen-phone-screenshot leaks data.
- **Fix**: optional `FLAG_SECURE` on sensitive screens (`TimelineScreen`, `PlaceDetailScreen`, `EvidenceCard`) — controllable in privacy settings.

---

## 5. Security threat-model gaps

The Pass 2 audit covered encryption-at-rest, backup exclusion, and isMock filtering. Several real-world attack surfaces remain:

### Sec1. Network security configuration
- **Reality**: Nominatim, Overpass, Photon — all over HTTPS, but is `cleartextTrafficPermitted=false` in `network_security_config.xml`? Without it, any code path that mistakenly uses `http://` succeeds.
- **Fix**: explicit `<base-config cleartextTrafficPermitted="false">`. Pin certificates for known providers (optional; can break if cert rotates).

### Sec2. Logcat leaking
- **Reality**: `Log.d("MapScreen", "lat=$lat, lng=$lng")` writes to logcat. On a rooted device or a connected developer machine, any app with `READ_LOGS` can read this.
- **Fix**: `Timber` with a `BuildConfig.DEBUG`-only tree; production never logs PII. Audit all `Log.*` calls.

### Sec3. Exported components
- **Reality**: `<receiver android:exported="true">` on any internal receiver lets other apps send it intents. `NotificationActionReceiver` likely needs to be exported=false unless it handles foreign intents.
- **Fix**: every component in `AndroidManifest.xml` declares `android:exported` explicitly (required API 31+). Default to `false`.

### Sec4. Intent injection through deep links
- **Reality**: Voyager probably has `voyager://...` deep links. Malicious app can send a crafted intent to navigate user into a hidden screen, or to trigger an export with a malicious file path.
- **Fix**: verify intent host/scheme; never pass a user-supplied path to a write operation without scoped-storage SAF mediation.

### Sec5. Tap-jacking on critical UI
- **Reality**: a malicious app draws an overlay over Voyager's "Delete all data" confirmation, fooling the user into tapping through.
- **Fix**: `setFilterTouchesWhenObscured(true)` on critical destructive buttons.

### Sec6. SQLCipher key lifetime in memory
- **Reality**: the derived passphrase sits in a `ByteArray`. Heap dumps via Studio profiler reveal it. On rooted devices, Frida can `dlopen` and dump.
- **Fix**: zero the byte array after use (`Arrays.fill(passphrase, 0.toByte())`); rotate Keystore alias periodically. Accept that a rooted attacker is out of scope; document it in threat model.

### Sec7. No root detection (and that's a choice)
- **Reality**: many privacy users run rooted phones. Adding `SafetyNet`/Play Integrity blocks them. Adding nothing means a compromised root environment can read everything.
- **Fix**: do *not* add root detection. Document threat model honestly: "Voyager protects against passive on-device threats. A rooted device with malware actively targeting Voyager will leak data; this is out of scope."

### Sec8. WebView (if any)
- **Reality**: any in-app WebView is an XSS surface. Voyager appears not to use one — verify.
- **Fix**: grep for `WebView` usage; if any, audit `javascriptEnabled`, `addJavascriptInterface`, file:// access.

### Sec9. Native library audit
- **Reality**: MapLibre, SQLCipher, possibly SQLDelight — all ship native libraries. CVEs against them affect Voyager.
- **Fix**: dependency-scanning in CI (`./gradlew dependencyCheckAnalyze` from OWASP Dependency-Check). Subscribe to security advisories.

### Sec10. Duress PIN (vision feature requires hardening)
- **Reality**: Persona 7 (journalist) needs a real duress PIN that *wipes* — not just hides. SQLCipher key destruction must be cryptographically complete.
- **Plan**: wipe Keystore alias + overwrite passphrase memory + delete DB file. **Test:** post-wipe, attempt forensic recovery with `sqlite3` directly on disk — confirm unrecoverable.

---

## 6. Network edge cases (geocoding & future sync)

### N1. Captive portal returns HTML masquerading as JSON
- **Reality**: hotel/airport WiFi returns a login HTML page for all requests. `NominatimGeocodingService` parses → JSON exception → caught silently → place stays unnamed forever.
- **Fix**: validate `Content-Type: application/json` before parsing; log captive portal as a HealthLog event.

### N2. DNS hijack / MITM on Nominatim
- **Reality**: an attacker on the same WiFi can return arbitrary JSON. If accepted, attacker controls place names — could inject home addresses or insults.
- **Fix**: HTTPS + optional cert pinning for known endpoints; signed responses ideally (not possible with public Nominatim).

### N3. VPN exit-IP rate limit sharing
- **Reality**: many Voyager users on Mullvad share an exit IP. Nominatim's public server rate-limits per IP at ~1 req/sec. All Voyager users on that exit collectively exceed the limit and get globally banned.
- **Fix**: aggressive client-side caching + exponential backoff + a per-install random delay (0–3s) before each request to spread the load.

### N4. IPv6-only network unreachable
- **Reality**: some carriers (T-Mobile US) deploy IPv6-only with NAT64. If Nominatim's IPv4-only endpoint isn't reachable, geocoding fails.
- **Fix**: catch network unreachable; fall through to Android Geocoder (offline-capable).

### N5. Slow / lossy network
- **Reality**: connection times out at 30s default. User on rural EDGE never gets a name.
- **Fix**: short timeouts (5s), retry with exponential backoff, queue for background completion when WiFi reappears.

### N6. Proxy environments (corporate, school)
- **Reality**: explicit HTTP proxy, sometimes with TLS MITM via installed root certificate. OkHttp respects system proxy by default — but if the user trusts a corporate CA, Voyager talks to the proxy.
- **Fix**: document that geocoding works in proxy environments only if the proxy is trusted; never bypass user-installed CAs (would break legitimate corporate use).

### N7. Offline cache invalidation
- **Reality**: cached Nominatim responses live forever. If a place is renamed in OSM, the cache stays stale.
- **Fix**: TTL of 90 days on geocode cache; background refresh.

---

## 7. Privacy / legal / compliance gaps

### LG1. GDPR Article 17 — right to erasure
- **Reality**: "Clear all data" must result in *immediate* erasure. With Pass 2's tombstone proposal (soft-delete + 30-day grace), a literal interpretation of Article 17 is violated.
- **Fix**: "Clear all data" performs a hard, immediate purge — bypasses the tombstone mechanism. Distinct from per-row delete (which uses tombstone). Document both in privacy policy.

### LG2. CCPA / "Do Not Sell My Data"
- **Reality**: Voyager doesn't sell data. The disclosure obligation is still real for California users.
- **Fix**: explicit "We do not sell, share, or transmit your data" statement in onboarding + Settings → Privacy.

### LG3. COPPA — children under 13
- **Reality**: a parent letting a 10-year-old use the app is technically subject to COPPA. Voyager collects location, a "personal information" category.
- **Fix**: age-gate in onboarding (just a "I am 13+" checkbox, COPPA-compliant minimum). Don't go beyond — adding identity verification kills privacy.

### LG4. Play Store data-safety form
- **Reality**: every field collected must be declared. Voyager collects location, activity, steps, photo metadata (via Day Story), WiFi BSSIDs. All "data not collected by us" because it stays on-device — but Play Store still wants disclosure.
- **Fix**: the data-safety form must say "Yes, the app handles X" + "No, it is not sent off-device". This nuance is allowed and is a marketing win.

### LG5. India DPDP 2023
- **Reality**: requires lawful purpose and consent. Voyager satisfies — but the consent text in onboarding must explicitly call out location/biometric (steps?) processing.
- **Fix**: localize consent text for IN locale.

### LG6. Korea PIPA (very strict location law)
- **Reality**: location-based service providers in Korea must register. Even on-device-only apps that touch location have notice obligations.
- **Fix**: defer Korea launch until legal review; do not localize for KR initially.

### LG7. Right to data portability (GDPR Article 20)
- **Reality**: user has the right to a machine-readable export. `.voyager`, GPX, GeoJSON, CSV all satisfy.
- **Fix**: already covered; document in privacy policy that any of these are a valid Article 20 response.

### LG8. Insurance / litigation discovery
- **Reality**: a user's Voyager `.voyager` export, in possession on their phone, is discoverable in US civil litigation. Auto-export to cloud (even encrypted) makes it *more* discoverable.
- **Fix**: educate users in the proof-tier README — exports are powerful evidence; a duress-PIN-style wipe before consenting to phone forensics is a legitimate user need. (Don't market this; document quietly.)

### LG9. Play Store background-location yearly re-justification
- **Reality**: Google requires annual re-attestation of background-location use. Easy to miss; results in app removal.
- **Fix**: calendar reminder to founder; pre-templated re-justification doc.

---

## 8. Storage & filesystem edge cases

### F1. /data partition full
- **Scenario**: cheap device with 16 GB storage, 95% full. Voyager's DB grows; eventually a write fails with `SQLiteFullException`.
- **Fix**: catch `SQLiteFullException`; pause tracking; surface "Your device is out of storage — Voyager paused".

### F2. SQLCipher journal file growth (WAL mode)
- **Reality**: WAL mode adds `-wal` and `-shm` companion files. Crash with WAL pending = recovery on next open.
- **Fix**: explicit `PRAGMA wal_checkpoint(TRUNCATE)` after `DataRetentionWorker` completes.

### F3. DB on encrypted file system (some Samsung)
- **Reality**: some OEMs encrypt user data with file-based encryption (FBE). Pre-login, Voyager can't even read its DB.
- **Fix**: foreground service must be `Direct Boot Aware = false` (default) so it waits for user unlock. Document.

### F4. Power-off mid-write corruption
- **Reality**: a hard power loss can corrupt SQLite WAL. Recovery is usually automatic; sometimes the DB is unrecoverable.
- **Fix**: defensive — on app start, attempt a `PRAGMA integrity_check`; if fails, route to a recovery screen (re-import from latest `.voyager` export).

### F5. Scoped storage for exports (Android 11+)
- **Reality**: writing exports to `Downloads/` requires SAF (Storage Access Framework). Direct file writes fail silently or throw.
- **Fix**: `ActivityResultContracts.CreateDocument` for all exports. Already partially correct — audit.

### F6. SD card / external storage removal
- **Reality**: if user ever pointed Voyager at external storage, removing the card breaks everything.
- **Fix**: never use external storage for the DB; document that exports to external storage are at user's risk.

### F7. Cache directory eviction by OS
- **Reality**: Android can purge `cacheDir` under storage pressure. Anything stored there is ephemeral.
- **Fix**: nothing critical lives in cacheDir; verify thumbnails, geocode cache live in `filesDir` or DB.

---

## 9. WorkManager & background job hazards

### W1. Worker scheduling collision
- **Reality**: scheduling the same worker via `enqueue()` instead of `enqueueUniqueWork()` creates duplicates.
- **Fix**: all periodic workers use `ExistingPeriodicWorkPolicy.KEEP` (or `UPDATE` deliberately).

### W2. Expedited work quota
- **Reality**: `setExpedited()` workers have a daily quota per app. Exceeding it falls back to regular scheduling — with no warning.
- **Fix**: log expedited fallbacks; reserve expedited for true user-initiated actions only (export now).

### W3. Worker outliving the app process
- **Reality**: a worker can run after the app's process is gone. Hilt-injected dependencies must survive — verify with `HiltWorker`.
- **Fix**: every worker uses `@HiltWorker` with `WorkerFactory`. Verify `WorkManagerInitializer` is correctly disabled in favor of Hilt's.

### W4. Constraint never satisfied
- **Reality**: a worker requiring `NetworkType.UNMETERED` on a metered-only device never runs. The user sees stale data forever.
- **Fix**: surface "Worker blocked by constraint" in Reliability dashboard after 7 days of non-execution.

### W5. Result.retry() infinite loop
- **Reality**: a worker that always retries on transient failure (e.g., Nominatim rate limit during peak) hammers the network.
- **Fix**: exponential backoff with a max attempt count; after N retries, give up and surface a HealthLog.

### W6. Inexact alarms (Android 12+)
- **Reality**: `AlarmManager.setExact` requires `SCHEDULE_EXACT_ALARM` permission, which is granted by default but user can revoke (Android 14+).
- **Fix**: prefer WorkManager for non-time-critical jobs; for exact (daily recap notification at 22:00 sharp), accept ±15 min imprecision.

### W7. Boot completed delivery flakiness
- **Reality**: `RECEIVE_BOOT_COMPLETED` is unreliable on some OEMs (Xiaomi). Service doesn't restart after reboot.
- **Fix**: document in Reliability dashboard ("Some devices need a manual restart of tracking after reboot — tap to enable autostart in OEM settings").

---

## 10. Build, supply chain, and reproducibility

### B1. Dependency CVE tracking
- **Reality**: every version of OkHttp, Retrofit, MapLibre, SQLCipher, Compose has had CVEs.
- **Fix**: `dependencyCheck` Gradle plugin in CI; monthly scan; auto-PR for security updates only.

### B2. Gradle wrapper integrity
- **Reality**: `gradle-wrapper.jar` is a binary in the repo. A malicious commit replacing it pwns every dev's machine and CI runner.
- **Fix**: `gradle-wrapper.jar` checksum verification in CI via `gradle/wrapper/gradle-wrapper.jar.sha256`.

### B3. Maven repo trust
- **Reality**: `mavenCentral()` and `google()` are trusted. Any custom repo added to `repositories { ... }` is a supply-chain risk.
- **Fix**: lockfile the dep graph (`gradle.lockfile`); audit any non-standard repo.

### B4. Signing key management
- **Reality**: the release signing key is *the* identity. Loss = orphaned users; theft = malicious updates.
- **Fix**: keystore in a password manager (1Password / Bitwarden), backed up offline, with a documented recovery procedure. Pre-document Google Play App Signing enrollment.

### B5. F-Droid reproducible build
- **Reality**: F-Droid requires reproducibility — bit-identical APK from same source. Embedded timestamps, randomized R8 dictionaries break it.
- **Fix**: pin R8 dictionary seed; use `SOURCE_DATE_EPOCH`; document the F-Droid build flow.

### B6. Min SDK & target SDK
- **Reality**: min SDK 24 (Android 7.0) is now ~99% of active devices. Target SDK 36 (Android 16) is correct. Verify all dependencies support both.
- **Fix**: Play Console pre-launch report; CI runs unit tests on min and target SDK.

### B7. ProGuard / R8 keep rules audit
- **Reality**: under-keep crashes at runtime; over-keep leaves dead code in the APK.
- **Fix**: explicit keep rules per package:
  - `-keep class com.cosmiclaboratory.voyager.storage.database.entity.** { *; }`
  - `-keep class com.cosmiclaboratory.voyager.domain.model.** { *; }`
  - kotlinx-serialization `@Keep` annotations.
  Test a release build before every launch; never test only debug.

### B8. Native ABI splits
- **Reality**: shipping all ABIs (`armeabi-v7a`, `arm64-v8a`, `x86_64`) bloats the APK. Splits via `splits.abi` reduce size.
- **Fix**: APK splits in `build.gradle.kts`; AAB upload includes all ABIs.

---

## 11. Internationalization & accessibility (deeper)

### I1. Text expansion in non-English locales
- **Reality**: German notifications are ~30% longer. "Tracking your journey" → "Verfolgen Ihrer Reise" — overflows truncation budgets.
- **Fix**: design notification copy with text expansion in mind; pseudo-localize in dev (Crowdin or `Locale.PSEUDO`).

### I2. Number / date formats
- **Reality**: 1,234.5 km in US vs 1.234,5 km in Germany. Date as MM/DD/YYYY vs DD.MM.YYYY.
- **Fix**: `NumberFormat.getInstance(locale)`, `DateTimeFormatter.ofLocalizedDate`. Never hand-format.

### I3. Right-to-left text + LTR map
- **Reality**: Arabic UI flips; but the map is inherently north-up. Buttons over the map must flip; map content does not.
- **Fix**: `LayoutDirection.Ltr` override only on the map composable.

### I4. Pluralization
- **Reality**: English has 2 plural forms; Arabic has 6; Polish has 4. Hard-coded `"$count places"` breaks everywhere.
- **Fix**: `<plurals>` resources in `strings.xml`; never string-interpolate counts.

### I5. TalkBack on map markers
- **Reality**: same as U7 — unspoken pain point for blind users.
- **Fix**: parallel list view + `contentDescription`s.

### I6. Screen reader linear order on Dashboard
- **Reality**: visual order (top-down) may not match accessibility traversal order if `Modifier.zIndex` is used.
- **Fix**: explicit `Modifier.semantics { traversalIndex = ... }` on stacked components.

### I7. Large-font / display-size combinations
- **Reality**: font scale 200% + display size large = some screens unusable.
- **Fix**: test in `Settings → Display → Display size and text`; layout audit on smallest supported width.

---

## 12. The "surprise user" matrix

A real-world founder thinks in stories, not specs. Each story below is an actual failure mode tied to actual code paths.

| # | The user did this... | What probably breaks | Fix |
|---|---------------------|----------------------|-----|
| US1 | Imports a corrupted Google Timeline JSON | Import parser crashes mid-stream; partial data inserted; rolled back? | Streaming parser with checkpoint; atomic batch import; rollback on error |
| US2 | Manually changes phone time to 2099 | Pipeline accepts; rollups confused; dayKey nonsensical | Sanity-check timestamps; reject implausible |
| US3 | Travels across IDL westward (LAX → SYD) | Dedup may drop legitimate samples | Use elapsedRealtime for ordering |
| US4 | Flies on a plane at 800 km/h | Speed-based mode classification: 222 m/s → DRIVE (wrong) | Cap speeds at 200 m/s; introduce FLIGHT class |
| US5 | Goes to the North Pole (lat=89.999) | Geohash precision degrades; clustering broken | Pole-aware clustering; rare but worth a unit test |
| US6 | Uses two phones with same Google account | Auto-backup conflict; two timelines | Backup excluded already (H5); document |
| US7 | Reinstalls and expects data back | Default behavior loses data; user blames app | First-launch flow: "Restore from .voyager export?" before creating fresh DB |
| US8 | Restores Pixel backup onto Xiaomi | Keystore alias different; DB unreadable | H5 fix; restore from `.voyager` |
| US9 | Side-loads both beta and release | Different signing keys; data isolation | Document; not fixable |
| US10 | Has Work Profile + Personal Voyager | Two timelines, same person | L5 fix |
| US11 | Disables Google Play Services | FLP doesn't work; activity recognition doesn't work | Fallback to LocationManager `GPS_PROVIDER`; document degraded mode |
| US12 | Runs MicroG (de-Googled Android) | Play Services shim; mostly works; some quirks | Test on LineageOS + MicroG; document supported state |
| US13 | Uses Voyager only on weekends | Long gaps between sessions; permission UI re-prompts; geofences expire | Session-restoration UX; refresh geofences on resume |
| US14 | Goes to a country with no street data in OSM | Geocoding returns nothing forever | Fallback to coordinates display; never show "unnamed place forever" |
| US15 | Has GPS off + WiFi off + cellular off (airplane) | No samples; pipeline idle | OK — but track session as "user-disabled radios" in HealthLog |
| US16 | Crosses a country border | Privacy mode auto-trigger? | Optional border-crossing detection → enter privacy mode (defer feature) |
| US17 | Has dyslexia / cognitive load issues | Settings overwhelm | The 2-tab settings simplification |
| US18 | Has shaky hands / motor disability | 48dp touch targets sometimes too small under stress | Honor `accessibility large touch targets` setting |
| US19 | Force-stops the app weekly to save battery | All workers blocked; reliability tanks | L1 fix — surface in Reliability |
| US20 | Drops phone in toilet, replaces it | Migration to new phone; backup useless (H5); needs `.voyager` | First-launch restore flow; document migration in onboarding |

---

## 13. Hidden bugs the previous audits did not name

These are subtler — not in any single file, but emergent from the interaction of files.

### X1. Pipeline restart on configuration change
- **Suspect**: ViewModels observing pipeline state. If a UI screen restarts the pipeline on rotation (because it sees state=NULL initially), tracking can briefly stop.
- **Verify**: check no ViewModel calls `startTracking()` or `stopTracking()` on init.

### X2. Coroutine `viewModelScope` cancelled mid-write to Room
- **Suspect**: a place rename initiated from `PlaceDetailViewModel` that the user backs out of mid-coroutine.
- **Fix**: either use `NonCancellable` for the critical write phase, or document that rename is best-effort.

### X3. Hilt + WorkManager initialization race
- **Reality**: WorkManager initializes early (App Startup library). Hilt may not yet be ready. A worker scheduled at boot can NPE.
- **Fix**: disable default `WorkManagerInitializer` in manifest; use Hilt's `@HiltWorkerFactory` registered in Application.onCreate.

### X4. `runCatching { ... }` swallowing structured-concurrency cancellation
- **Reality**: `runCatching` catches `CancellationException`, breaking coroutine cancellation.
- **Fix**: use `runCatching { ... }.onFailure { if (it is CancellationException) throw it }` — or just don't use `runCatching` in coroutine code.

### X5. `StateFlow.value` read before init
- **Reality**: reading `.value` of a `StateFlow` before its `MutableStateFlow` has been initialized (e.g., during ViewModel init order) yields garbage default.
- **Fix**: prefer `collectAsStateWithLifecycle()` with explicit initial value.

### X6. Compose `LaunchedEffect(Unit)` re-firing on key changes
- **Suspect**: a `LaunchedEffect(Unit)` that should fire once may fire repeatedly if hoisted into a higher composable that recomposes.
- **Fix**: prefer `rememberSaveable`/`remember`-keyed effects; pin keys explicitly.

### X7. Compose recomposition triggering Room query on every frame
- **Reality**: a Composable that reads a `remember { repository.observe() }` outside a flow operator can re-create the flow per recompose.
- **Fix**: `viewModel.state.collectAsStateWithLifecycle()` and never query Room directly from Composable.

### X8. Geofence callback running on Main with heavy work
- **Reality**: `GeofencingClient` callbacks fire on Main by default. If they do anything beyond an immediate enqueue, the UI jitters.
- **Fix**: callbacks enqueue a Worker or post to a known IO scope.

### X9. Pipeline tail samples lost on stopTracking
- **Reality**: a stop request flushes the Segmenter. If the last sample didn't trigger a flush, the trailing segment is incomplete.
- **Fix**: explicit `Segmenter.flushAll()` on stop, with a final write.

### X10. Notification re-built every minute → battery drain
- **Reality**: a foreground service that updates its notification every minute (active time display) wakes the SystemUI process and burns CPU.
- **Fix**: throttle notification update to once per 60 seconds OR only when state changes; never tick the timer in the notification.

---

## 14. PRE-FEATURE ACCEPTANCE GATE (single checklist)

This is the gate. Before Phase B (any new feature) ships, **every box below is ticked**. Anything left unchecked is a known accepted risk, signed by the founder.

### Stability & Crashes
- [ ] Zero `!!` in `presentation/screen/**` (grep clean).
- [ ] Zero `runBlocking` outside test sources.
- [ ] `LocalCrashHandler` writing to `HealthLogEntity` on every uncaught.
- [ ] Crash-free sessions ≥ 99.5% over a 7-day dogfood across 3 OEMs.
- [ ] StrictMode enabled in debug; zero violations on golden path.

### Data integrity
- [ ] All concurrent writes to `CurrentRuntimeStateEntity` serialized via actor (H1).
- [ ] `AtomicLong` dedup in LocationCapture (H2).
- [ ] Mutex-guarded visit insert; unique partial index (H4).
- [ ] `backup_rules.xml` excludes DB; restore-test passes (H5).
- [ ] Schema v2→v3 (lastModifiedAt + revision + deletedAt) migrated; Migration tests pass.
- [ ] FK declarations on PlaceEntity dependents with SET_NULL (A3).
- [ ] `PRAGMA journal_mode=WAL;` in Room callback.
- [ ] `Float → Double` migration for accuracyM, radiusM.

### Time correctness
- [ ] All durations use monotonic clock.
- [ ] All persisted timestamps include timezone ID.
- [ ] Sanity gate rejects time-travel samples.
- [ ] DST transition test passes (spring + fall).
- [ ] IDL crossing test passes.

### Concurrency hygiene
- [ ] `LocationCaptureService` double-start guarded.
- [ ] `PendingPlaceUpdateDao` atomic claim-and-consume.
- [ ] `ActivityTransitionReceiver` single-scope.
- [ ] `PlaceGeofenceManager` Mutex.
- [ ] Worker concurrency test passes.

### Architecture & extensibility
- [ ] `ExportFormatPlugin` interface live; ≥1 plugin registered via DI Set.
- [ ] `GeocodingProvider` interface live; existing providers refactored.
- [ ] `installationId` (UUID) generated, persisted in DataStore.
- [ ] `userId` column on syncable entities; defaults to installationId.
- [ ] Typed-ID value classes on at least repository public API.
- [ ] Lint rule blocks `Dao` imports outside `data/`.
- [ ] `PipelineGateway` interface in domain/; pipeline depends on it not DAO.
- [ ] All exported manifest components have explicit `android:exported`.

### Security
- [ ] `cleartextTrafficPermitted=false` in network_security_config.
- [ ] All production `Log.*` calls gated behind BuildConfig.DEBUG via Timber.
- [ ] `FLAG_SECURE` toggle in Privacy settings.
- [ ] No WebView in source tree (or audited if present).
- [ ] OWASP Dependency-Check passes with zero high/critical CVEs.

### Compliance
- [ ] GDPR Article 17 immediate-purge path documented and implemented.
- [ ] Play Store data-safety form drafted.
- [ ] Privacy policy public + linked from Settings.
- [ ] Onboarding consent text covers EU/IN/UK regions.
- [ ] Yearly Play Console re-justification calendared.

### Performance
- [ ] DB size > 100MB triggers user-visible info card.
- [ ] Notification updates throttled to ≥60s.
- [ ] Compose recomposition counts profiled; no hot path > 60 fps recompose.
- [ ] Battery cost on Pixel reference < 4%/day with default preset.

### UX failure modes
- [ ] Reliability Check screen detects OEM + 24h gap self-test.
- [ ] First-launch flow offers "Restore from .voyager export" before fresh DB.
- [ ] Force-Stop recovery banner on next launch if gap > 24h.
- [ ] Permission-revoked-mid-session graceful pause + surface.
- [ ] Captive portal detection in geocoder; HealthLog event.

### Tests in place
- [ ] Migration test (every schema bump).
- [ ] Concurrent 24h synthetic pipeline test.
- [ ] Property tests on Segmenter / Kalman / DetectVisit.
- [ ] Permission-revocation instrumented UI test.
- [ ] Backup-restore round-trip test.
- [ ] Worker concurrency test.
- [ ] Encryption round-trip across simulated OS upgrade.
- [ ] OEM matrix (manual, signed): Pixel, Xiaomi, Samsung, OnePlus.

### Distribution-ready
- [ ] Release-build smoke test green.
- [ ] ProGuard rules audited; no runtime ClassNotFound on release.
- [ ] F-Droid reproducible build verified.
- [ ] Signing key in offline password manager.
- [ ] Play Console pre-launch report all green.

---

## 15. Three-week hardening sprint (final form)

Re-stating Pass 2's sprint with Pass 3 additions. **3 weeks. Lock the gate. Then Phase B opens.**

| Week | Headline | New from Pass 3 |
|------|----------|-----------------|
| **A1 — Concurrency & Time** | H1–H4, H6–H9 fixes; force-unwraps; runBlocking removal | T1–T5 time correctness; X4–X9 hidden bugs; LocalCrashHandler; StrictMode |
| **A2 — Data Foundation** | Schema v2→v3 (cloud-ready columns); FK + cascade; backup_rules.xml | F1–F4 storage edge cases; sanity-gate; integrity_check on boot |
| **A3 — Identity & Boundaries** | userId/installationId; plugin interfaces; strings.xml; typed IDs | Sec1–Sec10 security; LG1–LG9 compliance; manifest exported audit |

At the end of A3: the Pre-Feature Acceptance Gate (§14) is ticked. Phase B (Real Names + Day Story) starts on the foundation Pass 1, 2, and 3 collectively secured.

---

## 16. The single-paragraph founder summary

> Voyager's pipeline is genuinely good — better than what most commercial location apps ship. But under load, across real-world edge cases, on real OEMs in real countries with real users doing real surprising things, **15 showstoppers, ~40 serious bugs, and ~50 future-blockers exist** that the engineering work to date has not addressed. Pass 3 of this audit catalogues them. Three weeks of disciplined hardening — concurrency safety, schema cloud-readiness, time correctness, security posture, compliance gates — converts Voyager from "a great prototype" to "a foundation features can compound on." The Pre-Feature Acceptance Gate (§14) is the contract. Ship features only after the gate is green. Anything else is shipping debt at the speed of marketing.

---

## 17. What's left after Pass 3 — known unknowns

We've now done three passes. Things still uncatalogued (real but lower priority):

- Full threat model write-up for journalists (Persona 7 + Sec10 + duress PIN) — needs security professional review.
- Multi-platform (KMP) extraction — deferred but the `PipelineGateway` and `domain/` interfaces are the seams when iOS comes.
- AI/LLM threat model when on-device LLM is added (v2.x) — prompt injection from place names? Yes, but later.
- Carbon emission factor accuracy review — needs domain expert (transport economist).
- Mileage log legal review — needs an accountant in 3 jurisdictions (US, UK, IN).
- Differential privacy review for any future opt-in aggregate contribution.

These are documented to prevent them being forgotten — not to be solved now. Phase B can ship without them.
