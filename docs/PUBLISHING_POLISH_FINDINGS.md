# Publishing Polish — Findings & Changes

**Date:** 2026-07-25 · **Branch:** `fix-timeline-map-correctness`
**Scope (as chosen by the user):** deep correctness audit (primary), targeted UI/UX
polish on First-run / Core tabs / Proof (secondary, lighter), and one branding text change.
The Map dark-basemap work was explicitly out of scope.

---

## 1. Branding — splash byline

- `presentation/screen/splash/AnimatedSplashContent.kt` — splash signature changed
  `— Aravya by Anshul` → **`— Voyager by Anshul`** (rendered text + matching doc-comment).
- This is the **only** text change. No other "Voyager"/"Aravya" strings, the app name,
  the top-bar wordmark, or notifications were touched.

---

## 2. Correctness audit (primary)

### Method
The branch carries a large in-flight, uncommitted changeset (~90 files, the 2026-07-23
correctness pass + a screen redesign). I established a green baseline first
(`:app:testPlayDebugUnitTest` → BUILD SUCCESSFUL, tests pass), then traced the core
domain/data logic end-to-end looking for: nullability, rounding, timezone/day-boundary
math, divide-by-zero, off-by-one, unit conversion, and swallowed exceptions.

### Fixes applied
| # | File | Fix |
|---|------|-----|
| I1 | `presentation/screen/reliability/ReliabilityScreen.kt` | Removed both `state.hoursSinceLastSample!!` non-null assertions (the known ship-blocker) by hoisting the value into a local `val` so the null-check smart-casts across the `when` branches. **Zero user-facing `!!` remain in the presentation tree** (verified by grep). |
| — | `data/repository/TimelineRepositoryImpl.kt` (2 sites) | The place `nameSource` hint didn't match the actual name resolution chain: `userCategory` outranks `bestProviderName` in `resolveDisplayName`, but the label only checked `userDisplayName`/`bestProviderName`, so a place named from its user category was mislabelled "Nearby area"/"via provider". Rewrote the `when` to mirror the resolver exactly (added the `userCategory` branch, switched `!= null` → `isNullOrBlank()` so blank names don't mislabel). |
| — | `domain/usecase/WorkoutStatsCalculator.kt` | Removed the dead `splitStartAlt` variable (written twice, never read) in `splits()`. |
| — | `presentation/screen/analytics/StatisticsScreen.kt` | **Found on-device:** the Insights "Weekly Synthesis" headline rendered "You travelled 0% more this week — **this this week.**" — the tail hardcoded " this week" and *also* appended `"this ${periodLabel.lowercase()}"`, duplicating it (and wrong for any non-week period). Fixed: use `periodLabel` once, add a place tail only when there are places. |
| — | `presentation/screen/onboarding/PersonaPickScreen.kt` | **Found on-device:** the persona "Start" CTA (last item of an edge-to-edge `LazyColumn`) sat **behind the gesture-nav bar** — the label rendered under the home pill and was awkward/impossible to tap; the title also crowded the status bar. `contentPadding` was a flat `PaddingValues(20.dp)` with no system-bar insets. Fixed: inset top by `statusBars` and bottom by `navigationBars`. |

### Verified sound — no bug found (traced, not assumed)
- **Mileage / tax deduction** (`MileageDeduction`, `MileageRateConfig`, `MileageModels`):
  the refactor from a hard-coded `IRS_2025_RATES` map to a user `MileageRateConfig` is
  complete (all 4 call-sites updated, no lingering references). The per-unit rate
  conversion `perMile × (KM.meters / MILE.meters)` is correct and provably
  **unit-invariant** (money is identical whether shown in mi or km). The default
  `MileageRateConfig()` reproduces the old IRS-2025 behaviour exactly, so no regression.
  `deductibleMeters` and `rateFor` both gate on the same `MilagePurpose.deductible`, so
  "deductible distance" and "deductible amount" can't disagree.
- **Timeline reconciliation** (`TimelineReconciler.collapseGaps`): correct contiguous-gap
  coalescing; kept-reason precedence (PERMISSION, else longest leg) is sound; merged
  duration = span of the coalesced gaps.
- **Timeline read path** (`TimelineRepositoryImpl`): good fixes — timezone-aware day
  bounds via `DayBoundaryResolver` + `homeTimeZone` (fixes cross-timezone step-window
  drift), user-override coalescing (`userOverrideType ?: segmentType`), and
  `SharingStarted.WhileSubscribed(5s)` instead of `Eagerly` (stops the heavy live flow
  churning when nothing observes it).
- **Geocoding** (`GeocodingRepositoryImpl`, `GeocodingConflictResolver`): `resolveDisplayName`
  is **local-DB-only** — no network geocoding on the timeline read path (no hot-path
  stall). The `refreshGeocodeForPlace` change is itself a real bug-fix: it no longer
  stores the coordinate-placeholder as if it were a name, and no longer permanently
  excludes weak places from `GeocodeBackfillWorker` retry.
- **Workout stats** (`WorkoutStatsCalculator`): elevation gain/loss uses correct
  noise-hysteresis; the per-unit `splits()` boundary interpolation is subtle but correct
  (each boundary advances `legStartTime` while reusing the full-leg rate, which sums
  properly); altitude encode/decode (decimetres ↔ metres) round-trips.
- **DB schema/migrations** (`VoyagerDatabase` v13): every new entity column has a matching
  `ALTER TABLE` — `userOverrideType` (9→10), elevation + `encodedTimes`/`encodedAltitudes`
  (10→11), `sourceSegmentId` (11→12), `workout_segments` table (12→13). **No
  crash-on-upgrade schema mismatch.**
- **Export** (`ExportRepositoryImpl`, `MileagePdfExporter`): GPX/GeoJSON now export the
  *reconciled* timeline (matches what the user sees) and correctly skip GAP segments
  (no route); the mileage PDF is now unit/currency/jurisdiction-aware with
  preset-specific legal disclaimers (IRS / HMRC / custom).

### Notes (not bugs — watch on device)
- **Per-segment DB reads on the timeline flow** (`resolveDisplayName` +
  `visitDao.countByPlaceId` + `placeEvidenceDao.getByPlaceId`, once per segment per
  emission). Bounded (one day of segments) and mitigated by `WhileSubscribed`, but worth
  an eye on a very dense day during your device test.
- **`GeocodingRepositoryImpl.refreshGeocodeForPlace`** compares `safeDisplayName` against
  `coordinatePlaceholder(place.centroidLat, place.centroidLng)`. If a provider ever
  formats its coordinate fallback from the *candidate* point rather than the place
  centroid, the `!=` check could miss it. Confirm on device that weak-geocode places show
  "Near …", not raw coordinates.

---

## 3. UI/UX polish (secondary, lighter)

**Finding:** the three target surfaces (First-run, Core tabs, Proof) were **already
substantially redesigned** in the in-flight changeset. They are design-system-compliant:
`VoyagerColors`/`VoyagerSpacing`/`VoyagerSurfaces` tokens, `VoyagerCard`/`GlassCard`,
`staggeredEntrance` motion, honest copy, clear hierarchy. Objective checks came back
clean — **zero inline `Color(0x…)` literals and zero raw `MaterialTheme.colorScheme`
usages** across those surfaces (the A7 "design-system finish" is effectively done here).
The Proof hub (previously flagged "least-polished") now has a premium hero, live
deductible subtitle, and on-device reassurance footer.

**What I did:** verified compliance, made the targeted fixes above, and — after finding an
emulator AVD — **rendered every surface on-device** to review it for real (see next section).

---

## 3b. On-device review (emulator `Medium_Phone_API_36.1`, Android 16 / API 36)

Booted the AVD headless, installed the play-debug APK, and screenshotted the splash + all
five tabs. (First boot on the software `swiftshader` GPU threw a recurring *"System UI isn't
responding"* dialog — a **SystemUI** ANR, i.e. emulator jank, not Voyager; rebooting with
`-gpu host` on the machine's real GPU cleared it and everything rendered smoothly.)

**Looked great, on-brand, no issues:**
- **Splash** — after the OS launch-icon frame, the app's own dark splash: letter-spaced
  `VOYAGER` wordmark, a blue route line drawing itself with a glowing head dot. (The
  "— Voyager by Anshul" byline fades in a beat after this frame.)
- **Today** — greeting, "Tracking Stopped → Start" card, mono `0.0 km` / `0 stops` tiles,
  green "Private by Design" card with Kinetic-Engine / Ultra-low-power chips.
- **Timeline** — clean "Today" day-navigator + photo deep-link; tidy "Tracking Not Active"
  empty state.
- **Proof** — premium gold "Audit-ready by design" hero; Mileage(PRO) with the **live**
  "$0.00 deductible this month · 45 to classify" subtitle; Trips / Activities / Export
  cards; green on-device footer.
- **Insights** — mono stat tiles with trend arrows, locked-PRO tab chips. (Had the copy
  bug now fixed above.)

**Two real bugs found here and fixed (verified on-device after the fix):**
- Insights "Weekly Synthesis" now reads a clean "You travelled 0% more this week." (was
  "…— this this week.").
- The persona "Start" button now sits above the gesture-nav bar and is tappable (was
  occluded behind the home pill).

**Observations outside the chosen scope (flagged, not changed):**
- **Map basemap is light/cream** (the `openfreemap/liberty` style) — clashes with the dark
  UI, and the "NO TELEMETRY RECORDED FOR THIS DAY" overlay is low-contrast/hard to read on
  it. This is the Map dark-basemap work you deliberately left out of scope. A one-line
  swap in `platform/map/MapLibreMapEngine.kt` (`STYLE_URL`) to a dark style would fix it —
  say the word.
- **Launcher / OS-splash icon** is a playful purple-bubble "VOYAGER" with pink sparkles —
  off-brand versus the sophisticated dark app. Not in scope; worth a redraw before launch.
- **Insights empty-data trends** show alarming red "-100.0%" for Places / Time Away when
  both periods are zero. Minor; consider suppressing the delta when the baseline is 0.

## 4. Verification

- Baseline (pre-change): `:app:testPlayDebugUnitTest` → **BUILD SUCCESSFUL**, tests pass.
- After edits: `:app:compilePlayDebugKotlin` → **exit 0**.
- Final full run (all edits, incl. the two on-device UI fixes): `:app:testPlayDebugUnitTest`
  → **BUILD SUCCESSFUL**, unit tests pass. `:app:assemblePlayDebug` → clean.
- **On-device**: installed the play-debug APK on the emulator, walked the whole first-run
  flow + all five tabs, and confirmed both UI fixes render correctly (fixed synthesis
  sentence; Start button clears the nav bar).

## 6. Timeline current-location naming (2026-07-27)

**Goal (user):** review the timeline and, instead of "Unknown place", show something
accurate for the current location detected.

**Root cause.** The read path was sound, but every *unnamed* location fell back to raw
coordinates or the literal string "Unknown place":
- `CurrentLocationCard` (the live "Now" card) showed `ActiveVisitInfo.placeName`, which
  was `"%.4f, %.4f"` when the current visit wasn't yet a named place.
- Unlinked visits built a synthetic `TimelinePlace` whose `displayName` was raw coords
  (`TimelineRepositoryImpl.observeDay` + `getSegmentDetails`).
- `TimelineScreen` printed the literal "Unknown place" for a placeless segment.
- The Proof-hub day-story / trip-detail narratives did the same (`?: "Unknown place"`).

**Fix.** New `GeocodingRepository.resolveDisplayNameForCoordinates(lat, lng)`:
reverse-geocodes a *bare coordinate* (no place row) into an accuracy-gated provider name,
else the honest coarse **"Near [neighborhood/street/city]"** locator, falling back to raw
coordinates only when nothing is known. It runs the network off-thread
(`withContext(IO)`), and **caches per ~11 m coordinate bucket** with a 60 s retry on
failure — so the live flow's re-emissions don't re-geocode or hammer the network. Wired
into the current-location card, both synthetic-place sites, and the day-story/trip
narratives. `TimelineScreen`'s placeless fallback is now "Location unavailable" (honest;
only when there truly are no coordinates).

**Not a leak (left as-is):** `TrackingStateSegment.formatSummary()` still contains
"Unknown place"/"Unknown location" but the whole class is **dead code** (no references in
the app) — a candidate for the later "remove unnecessary" cleanup pass.

**Verification.** `:app:testPlayDebugUnitTest` → BUILD SUCCESSFUL (648 + 4 new tests:
HIGH-tier → provider name, weak → "Near …", null-island → "Location unavailable" + no
geocode, and cache → single geocode on repeat). Still owed: on-device travel-test, since
the live current-location label only appears with real GPS movement into a new,
un-named area.

## 7. Publishing readiness — profile / feedback / license / cleanup / v1 (2026-07-27)

**Build config was already Play-ready:** `versionCode 1` / `versionName "1.0.0"`, keystore-based
release signing (`keystore.properties`, gitignored) with debug fallback, `isMinifyEnabled` +
`isShrinkResources` + proguard on release, play/fdroid flavors, SQLCipher-encrypted DB.

**Developer profile (`DeveloperProfileScreen`)** — tone tightened for launch (per user):
kept the sincere "A note from Anshul" verbatim and the hidden 3-tap "Why Voyager?" easter
egg; rewrote the jokier header tagline/quote, the "About Voyager" card, and the "Developer
Manifesto" → "How I build" (privacy/honesty-led bullets); trimmed the "Built With" list.
The two CTAs now open the **in-app** Feedback composer instead of a raw mailto / GitHub
profile: "Send feedback" → General, "Report an issue" → Bug (fixes the dead
`github.com/OkayAnshul` link).

**Feedback routing** — `VoyagerDestination.Feedback` gained an optional `category` arg
(`feedback?category={category}`, mirrors the `DayStory` pattern); `FeedbackScreen` takes
`initialCategory` and preselects the tab. The Settings→Feedback entry was updated to
`createRoute()`.

**License screen** — removed the stale **Great Vibes font** entry (the `.ttf` was deleted
on this branch) and the test-only MockK/Turbine/Truth entry (never shipped). Inter +
JetBrains Mono remain (both still bundled in `res/font`).

**Dead code removed:** `domain/model/TrackingStateSegment.kt` (whole file — `TimeRange`,
`TrackingStateSegment`, `TrackingSegmentType` all unreferenced) and the dead map-settings
knobs `offlineMapsEnabled` (field only) + `clusterMarkersAtZoom` (UI slider whose write key
`"cluster_markers_at_zoom"` didn't even match the caller's `"clusterMarkersAtZoom"`, and
whose value nothing consumed) — pulled from `SettingsModels`, `SettingsRepositoryImpl`
(field/key/read/write), and the Map settings UI.

**"First version" in the database — collapsed schema to v1.** Voyager is pre-production
(no installed databases), so the throwaway `1→13` dev migration chain was cruft. Set
`@Database(version = 1)`, deleted all 12 `MIGRATION_x_y` objects + the `MIGRATIONS` array,
removed `.addMigrations(*MIGRATIONS)` (added `.fallbackToDestructiveMigrationOnDowngrade()`
so a dev build stepping *back* to v1 recreates cleanly instead of crashing), rewrote the
class KDoc as the v1 baseline + go-forward migration policy, cleaned the 9 entity
`see MIGRATION_2_3` comments, deleted schema JSONs `1…13.json` and let the build regenerate
a single **`1.json`** (verified: version 1, 30 tables incl. `workout_segments`/`vehicles`/
`activities`), and deleted the now-obsolete `VoyagerDatabaseMigrationTest` (nothing to
migrate at v1). **Requires clearing app data / reinstalling on any device that ran a
pre-release build** (the on-open `create()` recreate-on-failure path also handles this).

**Verification:** `:app:testPlayDebugUnitTest` → BUILD SUCCESSFUL (all unit tests pass);
`:app:compileFdroidDebugKotlin` → clean. One pre-existing icon deprecation warning
(`Icons.Filled.HelpOutline`), unrelated.

**Still open in phase 2 (not blocking):** the developer screen still uses raw
`MaterialTheme.colorScheme.*` in places (design-system inconsistency, renders fine); a
broader lint-driven dead-code sweep beyond the items above.

## 8. Onboarding cleanup (2026-07-27, phase 3)

Flow unchanged in shape (`SPLASH → INTRO → PERMISSIONS → PERSONA → MAIN`) but each surface
reworked per the user's direction. Decisions: typography = expressive Inter + JetBrains Mono
(no new decorative font); the "ask which transport modes you use" idea was **deferred**.

- **IntroScreen — rebuilt.** One clean, scannable screen: hero ("Your life, mapped." display
  + "Private by design · provable on demand"), a mono "WHAT VOYAGER DOES" kicker, and the full
  feature set grouped into the three pillars that also drive the persona pick —
  **Memory** (blue), **Proof** (amber/gold), **Habits** (purple) — each a `VoyagerCard` with a
  JetBrains-Mono pillar label + a checked feature list. Then an "Unlike Google Timeline" card
  (on-device, encrypted, no account/cloud/ads, evidence-grade) and a one-line about.
  The Google-Timeline import / backup restore moved from a big inline card to a **corner
  "Bring history" button** that opens a `ModalBottomSheet` (same two actions + progress/summary/
  error). Import still lives fully in Export/Settings.
- **PermissionOnboardingScreen — trust + no skip.** Background step (`step == 1`) now gives
  exact direction ("Tap Allow Background Location, then choose Allow all the time … without it
  your timeline will have gaps"). The **"Skip for now" button was removed** — the only forward
  action is to grant. (The OS dialog result still proceeds either way; we just don't offer an
  easy opt-out that quietly breaks capture.)
- **Persona — clearer self-identification.** Header → "Which of these sounds most like you?";
  each `Job` gained a `forWho` line ("You want a private diary…", "You need evidence…", "You
  want to understand your routines…"). CTA → **"Start capturing"** with a transparency note
  ("Tracking starts now — running privately on your device. You can pause it any time.").
- **Auto-start tracking.** `PersonaPickViewModel` now injects `TrackingRepository` +
  `PermissionMonitor`; after applying the preset + recording the job it calls
  `startTracking(StartReason.USER)` when foreground location is granted (fails safe otherwise).

**Deferred:** transport-mode question + any `Segmenter` classifier prior (user said leave it).
`Segmenter` reads no settings today, so a future prior would inject `SettingsRepository` into
the pipeline.

**Verification.** `:app:testPlayDebugUnitTest` → BUILD SUCCESSFUL; `:app:compileFdroidDebugKotlin`
→ clean. **On-device (AVD Medium_Phone_API_36.1, Android 16), full fresh first-run walked and
screenshotted:** Intro renders clean with the pillars + corner button (sheet opens, both import
actions present); permission background step shows the exact-direction copy and **no Skip**;
persona cards show the "who this is for" lines + "Start capturing"; and after tapping Start the
app lands on the dashboard with **"Tracking active"** and a running `LocationCaptureService`
foreground notification — auto-start confirmed. (Dashboard's "1 permission missing" banner is
the battery-optimization exemption, handled outside onboarding — not a regression.)

## 9. Timeline — review flow off the timeline, broken confirm fixed, tighter layout (2026-07-27)

**Root-cause bug (the "confirm doesn't work" report):** the review queue filter
(`PlaceReviewViewModel.pendingReviewPlaces`) and `TimelinePlace.needsReview` both flag a place
when `confidence < threshold || category == UNKNOWN`, but `PlaceRepository.confirmPlace` only
raises confidence (to 0.8) — it can't invent a category. So confirming an **uncategorised**
place left `category == UNKNOWN` true forever → it never left the queue and the Confirm button
looked dead. **Fix:** added `TimelinePlace.isConfirmed` (set from `lifecycleStatus == CONFIRMED`
in `PlaceRepositoryImpl.toTimelinePlace`); `needsReview` and `pendingReviewPlaces` now exclude
confirmed places. Locked in with a regression test.

**Reviews moved off the timeline, surfaced quietly on the dashboard (per the user):**
- Timeline no longer shows any prominent review UI — removed the day-header "N to review" +
  "Confirm all" block, and the per-row cues are gated off (`isReview = false`): no amber review
  ring, no per-row "Confirm"/"Name it" badges. Only the calm "Why?" (evidence) and movement
  "Reclassify" affordances remain. Renaming a place is still available via long-press.
- The dashboard top-bar **bell** stays the single review entry point (→ PlaceReview), but its
  badge is now a **small muted dot** (`Primary @ 0.7`) instead of a loud red count — quiet, not
  prominent. Confirming from there now actually clears the dot (the fix above).

**Tighter, more spacious layout:** timeline horizontal insets 16→8 dp (day nav, header,
current-location card, LazyColumn content padding), place-card padding 12→10 dp, and the left
timestamp rail 56→46 dp (HH:mm still fits) — cards span noticeably wider.

**Verification.** `:app:testPlayDebugUnitTest` → BUILD SUCCESSFUL (incl. the new
"confirmed place leaves the queue even when uncategorised" test). **On-device (debug build,
seeded 14-day history):** the timeline renders clean and edge-to-edgier with no review chrome —
day-arc header + stats, place cards (Home / Office — Civil Lines / El Chico Café) and movement
rows with only Why?/Reclassify; no amber rings, no confirm buttons. (Minor: the now-unused
per-row review-affordance + amber-ring code is left dormant behind `isReview = false` rather
than deleted — safe, a candidate for a later cleanup.)

## 10. Expressive typography rolled out app-wide (2026-07-27)

Brought the first-run Intro's "creative type" style (varied sizes/weights, mono kickers,
accent colours — using the two bundled families Inter + JetBrains Mono) to the main screens.

- **`SectionHeader` (VoyagerComponents) redesigned** — the single component behind ~50
  section headers across 11 screens. Was Title-Case Inter `titleSmall` in flat Primary blue;
  now a **JetBrains-Mono UPPERCASE letter-spaced kicker**. New `accent: Color?` param: when
  null it auto-picks from a curated 5-colour palette (`SectionAccents`:
  Primary/Purple/Amber/Green/Blue) deterministically by title, so every screen gets varied
  colours for free; pass an explicit accent to override.
- **Dashboard** — explicit section accents: Today's Places = Primary, Insights = Purple,
  Anomalies = Amber, Shortcuts = Green (on top of the existing display greeting + mono stat
  tiles + coloured chips/rings).
- **Timeline** — the day-header dominant-mode label ("Mostly driving") is now a mono
  UPPERCASE kicker coloured by its transport (`transportColor`), e.g. purple for drive.
- **Map** — its text surfaces (the "No Movement" / "NO TELEMETRY RECORDED FOR THIS DAY"
  empty overlay) already used the mono-kicker treatment; left as-is.

**Verification.** `:app:testPlayDebugUnitTest` green; `:app:assemblePlayRelease` → BUILD
SUCCESSFUL (minified APK installs + launches, no crash). **On-device (seeded data):** dashboard
"TODAY'S PLACES" kicker in blue; Insights "TOP PLACES" in gold + the "You wandered 0% farther"
Inter/mono headline; Timeline "MOSTLY DRIVING" in purple — the app now reads as a lively,
multi-size / multi-colour / two-font type system.

## 5. Needs your device travel-test
- Splash now reads "— Voyager by Anshul".
- Reliability screen renders correctly with and without a recent tracking gap (the `!!` fix).
- Timeline place labels: a user-categorised place shows its category name with the
  "Your label" source hint; an un-named place shows "Near …", never raw coordinates.
- Mileage PDF/CSV totals and the deductible figure in your locale/unit/currency.
- Workout splits + elevation on a real recorded route.
- Aesthetic review of First-run / Core tabs / Proof on-device (the part I couldn't render).
