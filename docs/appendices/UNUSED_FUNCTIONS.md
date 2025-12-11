# Appendix C: Unused Functions Analysis

**Last Updated:** December 11, 2025

Complete analysis of unused and dead code in the Voyager codebase with recommendations.

---

## Summary

| Category | Lines | Files | Status | Action |
|----------|-------|-------|--------|--------|
| Incomplete Features | 742 | 3 | Not wired in DI | Archive |
| Disabled Code | ~500 | 1 | Commented out | Document & keep |
| Possibly Obsolete | ~150 | 1 | Unclear usage | Investigate |
| Working But Hidden | 270 | 1 | Not exposed in UI | Keep |

**Total Dead/Unused:** ~1,662 lines (excluding working-but-hidden)

---

## Category 1: Incomplete Features (Archive)

### StatisticalAnalyticsUseCase

**File:** `domain/usecase/StatisticalAnalyticsUseCase.kt`
**Lines:** 354
**Status:** EXISTS but NOT wired in Hilt DI

**Functions:**
- `invoke()` → List<StatisticalInsight>
- `computePlaceStatistics()` - Visit frequency, patterns
- `computeTemporalTrends()` - Weekday vs weekend analysis
- `computeCorrelations()` - Which places visited together
- `computeDistributions()` - Time distribution stats
- `computeFrequencyAnalysis()` - Visit frequency patterns
- `computePredictions()` - Future visit predictions

**Why Not Wired:**
- Performance concerns (O(n²) algorithms)
- UI not designed
- Prioritized core features

**Used By (Attempts to Use):**
```kotlin
@HiltViewModel
class SocialHealthAnalyticsViewModel @Inject constructor(
    private val statisticalAnalyticsUseCase: StatisticalAnalyticsUseCase // ❌ Will crash - not provided
)
```

**Recommendation:** Archive to `/archive/incomplete-features/statistical-analytics/`

**Restoration Steps:**
1. Add to UseCasesModule.kt:
```kotlin
@Provides
@Singleton
fun provideStatisticalAnalyticsUseCase(
    visitRepository: VisitRepository,
    placeRepository: PlaceRepository
): StatisticalAnalyticsUseCase = StatisticalAnalyticsUseCase(visitRepository, placeRepository)
```
2. Create AdvancedAnalyticsScreen
3. Wire to navigation
4. Optimize performance

---

### PersonalizedInsightsGenerator

**File:** `domain/usecase/PersonalizedInsightsGenerator.kt`
**Lines:** 388
**Status:** Same as StatisticalAnalyticsUseCase

**Functions:**
- `generateMessages()` → List<PersonalizedMessage>
- `generateHomeInsights()` - "You spent 8h at home today"
- `generateWorkInsights()` - "Typical work day: 7h 15m"
- `generateMovementInsights()` - Movement patterns
- `generateRoutineInsights()` - Routine detection
- `generateExplorationInsights()` - New places discovered

**Example Output (Not Currently Displayed):**
```
📊 "You spent 8h 30m at home today, 15% more than usual"
💼 "Your typical work day is 7h 15m, ending at 5:30 PM"
🏋️ "You've visited the gym 3 times this week - great!"
```

**Recommendation:** Archive with StatisticalAnalyticsUseCase

---

## Category 2: Disabled Code (Document & Keep)

### Category Score Calculations

**File:** `domain/usecase/PlaceDetectionUseCases.kt`
**Lines:** ~500 (commented/disabled)
**Reason:** ISSUE #3 - Poor accuracy (50%)

**Disabled Functions:**
```kotlin
// private fun calculateHomeScore(...) { ... }
// private fun calculateWorkScore(...) { ... }
// private fun calculateGymScore(...) { ... }
// private fun calculateEducationScore(...) { ... }
// private fun calculateShoppingScore(...) { ... }
// private fun calculateRestaurantScore(...) { ... }
```

**Why Disabled:**
- Only 50% accuracy
- Failed for shift workers, freelancers, students
- Too many assumptions about schedules

**Current Solution:** User review + CategoryLearningEngine (95% accuracy)

**Recommendation:**
- KEEP in place (already disabled with comments)
- Add documentation block explaining why disabled
- Reference ISSUE #3

**Documentation to Add:**
```kotlin
/**
 * DISABLED: Category score calculations (ISSUE #3)
 *
 * These functions attempted to categorize places based on time patterns
 * (e.g., night hours = home, weekday 9-5 = work). Achieved only 50% accuracy.
 *
 * Failed for:
 * - Shift workers (work at night)
 * - Freelancers (work from home)
 * - Students (variable schedules)
 *
 * Replaced with user review system + CategoryLearningEngine (95% accuracy).
 * Kept for historical reference and potential future ML training data.
 */
```

---

## Category 3: Possibly Obsolete (Investigate)

### FallbackPlaceDetectionWorker

**File:** `data/worker/FallbackPlaceDetectionWorker.kt`
**Lines:** ~150
**Purpose:** Backup worker when HiltWorker fails
**Status:** May be obsolete now that Hilt is stable

**Investigation Needed:**
1. Check WorkManager logs for usage: `adb logcat | grep FallbackPlaceDetectionWorker`
2. Test if HiltWorker is stable on all devices
3. Monitor for 30 days

**If Never Used:** Archive to `/archive/obsolete-code/`

**If Still Triggered:** Keep and improve error handling

---

### backup_dashboard_end.kt

**File:** `/backup_dashboard_end.kt` (root directory)
**Lines:** ~100
**Purpose:** Old dashboard component
**Status:** Clearly obsolete

**Contents:**
- Old `CurrentVisitCard` composable
- Superseded by integrated dashboard

**Recommendation:** DELETE (after confirming in git history)

---

## Category 4: Working But Hidden (Keep)

### DetectAnomaliesUseCase

**File:** `domain/usecase/DetectAnomaliesUseCase.kt`
**Lines:** 270
**Status:** WORKING, wired in DI, used by PlacePatternsViewModel

**Functions:**
- `invoke()` → List<Anomaly>
- `detectMissedPlaces()` - Locations that should be places
- `detectUnusualDurations()` - Abnormally long/short visits
- `detectUnusualTimes()` - Visits at unusual times
- `detectUnusualDays()` - Visits on unusual days of week

**Why Not Visible:** UI shows patterns but not anomaly alerts

**Example Detections (Not Shown to User):**
```
⚠️ "50 locations near 123 Main St aren't a place yet"
⚠️ "You spent 12 hours at work on Saturday - unusual"
⚠️ "You visited the gym at 2 AM - is this correct?"
```

**Recommendation:**
- KEEP (working code)
- Add AnomalyAlertsScreen to display results
- Add to UI enhancement roadmap

**Effort to Expose:** 1.5 hours

---

## Archive Strategy

### Directory Structure

```
/archive/
├── incomplete-features/
│   └── statistical-analytics/
│       ├── StatisticalAnalyticsUseCase.kt
│       ├── PersonalizedInsightsGenerator.kt
│       ├── SocialHealthAnalyticsViewModel.kt
│       └── EXPLANATION.md
└── obsolete-code/
    ├── FallbackPlaceDetectionWorker.kt (if confirmed unused)
    └── EXPLANATION.md
```

### EXPLANATION.md Template

```markdown
# Statistical Analytics Feature - Incomplete

## Status
NOT WIRED IN DI - Will crash if ViewModels try to inject

## Files
1. StatisticalAnalyticsUseCase.kt (354 lines)
2. PersonalizedInsightsGenerator.kt (388 lines)  
3. SocialHealthAnalyticsViewModel.kt

## Why Incomplete
- Feature started in Week 8-9
- UI design not finalized
- Performance concerns
- Priorities shifted to stability

## What It Does
Computes advanced analytics:
- Place visit frequency patterns
- Temporal trends (weekday/weekend)
- Correlations between places
- Time distribution analysis
- Predictive analytics

## Future Plan
Complete in v2.0 after:
- Performance optimization
- UI design finalized
- Comprehensive testing

## To Restore
1. Move files back to original locations
2. Add providers to UseCasesModule.kt
3. Create AdvancedAnalyticsScreen
4. Add to navigation
5. Test performance with 10K+ records
```

---

## Cleanup Checklist

- [ ] Archive StatisticalAnalyticsUseCase.kt
- [ ] Archive PersonalizedInsightsGenerator.kt
- [ ] Archive SocialHealthAnalyticsViewModel.kt
- [ ] Create EXPLANATION.md in archive
- [ ] Document disabled category calculations
- [ ] Monitor FallbackPlaceDetectionWorker for 30 days
- [ ] Delete backup_dashboard_end.kt
- [ ] Keep DetectAnomaliesUseCase (working code)
- [ ] Add AnomalyAlertsScreen to UI roadmap

---

## Summary

**Archive:** 742 lines (3 files)
**Document:** 500 lines (already disabled)
**Investigate:** 150 lines (1 file)
**Keep:** 270 lines (working but not exposed)
**Delete:** 100 lines (backup file)

**Total Cleanup:** ~1,492 lines to archive/delete
