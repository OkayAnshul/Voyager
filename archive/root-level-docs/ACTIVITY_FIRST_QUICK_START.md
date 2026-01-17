# Activity-First: Quick Start Guide

## 🎯 What We're Building

Transform Voyager from **"Where did I go?"** to **"What did I do?"**

### Before → After

| Before | After |
|--------|-------|
| "You were at (12.9716, 77.5946)" | "You worked out at Gold's Gym for 45 minutes" |
| "Restaurant" | "Starbucks Coffee, MG Road" |
| "You visited 5 places" | "You worked 8h, commuted 1h 20m, worked out 45m" |
| Generic categories | Real business names + user custom names |

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    ACTIVITY-FIRST FLOW                      │
└─────────────────────────────────────────────────────────────┘

1. GPS TRACKING (Existing)
   ├─ FusedLocationProvider
   ├─ LocationTrackingService
   └─ Saves: lat, lng, timestamp, accuracy

2. ACTIVITY RECOGNITION (Phase 1) ⭐ NEW
   ├─ HybridActivityRecognitionManager (already exists!)
   ├─ Google Activity Recognition API
   └─ Detects: WALKING, DRIVING, STATIONARY, CYCLING, UNKNOWN

3. ENHANCED LOCATION (Phase 1) ⭐ NEW
   ├─ Location + UserActivity + Confidence
   ├─ SemanticContext inference (WORKING_OUT, EATING, etc.)
   └─ Filter out "driving-through" locations

4. SMART PLACE DETECTION (Phase 2) ⭐ NEW
   ├─ DBSCAN clustering (existing)
   ├─ Activity analysis per cluster
   ├─ Activity-based category inference
   └─ Better accuracy: Gym vs Restaurant vs Shopping

5. REAL PLACE NAMES (Phase 3) ⭐ NEW
   ├─ OSM Nominatim geocoding (already exists!)
   ├─ Android Geocoder fallback
   ├─ User custom naming
   └─ Shows: "Starbucks Coffee" not "Restaurant"

6. ACTIVITY INSIGHTS (Phase 4) ⭐ NEW
   ├─ Workout session detection
   ├─ Commute analysis
   ├─ Time by activity (working, exercising, eating, etc.)
   └─ UI: "You worked out 3 times this week"

```

---

## 📦 What's Already Built (Leverage Existing Code)

### ✅ Activity Recognition Manager
**File:** `utils/HybridActivityRecognitionManager.kt`

```kotlin
// Already implemented!
- Detects: DRIVING, WALKING, STATIONARY, CYCLING, UNKNOWN
- Falls back to motion sensors if Google Play unavailable
- Provides confidence scores (0.0 - 1.0)
- Already injected into LocationTrackingService
```

**Action:** Just need to USE it in location tracking (currently unused)

### ✅ Geocoding Infrastructure
**File:** `domain/usecase/EnrichPlaceWithDetailsUseCase.kt`

```kotlin
// Already implemented!
- Queries OSM Nominatim for place details
- Falls back to Android Geocoder
- Returns: name, address, category, place type
- Caching via GeocodingRepository
```

**Action:** Call it during place detection (add 1 line of code)

### ✅ Place Name Fields
**File:** `domain/model/Place.kt`

```kotlin
// Already in the model!
val osmSuggestedName: String? = null    // Ready for OSM data
val osmSuggestedCategory: PlaceCategory? = null
val isUserRenamed: Boolean = false       // Track custom names
val needsUserNaming: Boolean = false     // Needs user input
```

**Action:** Just populate these fields (they're currently null)

---

## 🚀 Implementation Phases

### Phase 1: Store Activity with Locations (3 days)

**Goal:** Every GPS point knows what activity was happening

**Changes:**
1. Add 3 fields to Location model:
   ```kotlin
   val userActivity: UserActivity = UNKNOWN
   val activityConfidence: Float = 0f
   val semanticContext: SemanticContext? = null
   ```

2. Database migration (add 3 columns to `locations` table)

3. Modify `LocationTrackingService.saveLocation()`:
   ```kotlin
   val activityDetection = activityRecognitionManager.getCurrentActivity()

   // Skip locations while driving (prevent false places)
   if (activityDetection.isMoving(0.75f)) return

   val location = Location(
       // ... existing fields ...
       userActivity = activityDetection.activity,
       activityConfidence = activityDetection.confidence,
       semanticContext = inferSemanticContext(activity, time)
   )
   ```

**Result:** Database now has activity data for every location

---

### Phase 2: Activity-Aware Place Detection (4 days)

**Goal:** Better place categorization using activity data

**Changes:**
1. Filter moving locations before DBSCAN:
   ```kotlin
   val stationaryLocations = recentLocations.filter {
       it.userActivity != DRIVING ||
       it.activityConfidence < 0.75f
   }
   ```

2. Analyze activity distribution per cluster:
   ```kotlin
   val activityCounts = clusterLocations.groupBy { it.userActivity }
   val dominantActivity = activityCounts.maxByOrNull { it.value }?.key
   ```

3. Activity-based category inference:
   ```kotlin
   // If 80% stationary + workout times → GYM
   // If 60% walking + shopping hours → SHOPPING
   // If stationary + meal times → RESTAURANT
   ```

**Result:** Places categorized by activity, not just time patterns

---

### Phase 3: Real Place Names (3 days)

**Goal:** Show "Starbucks Coffee" instead of "Restaurant"

**Changes:**
1. Call geocoding during place detection:
   ```kotlin
   val placeDetails = enrichPlaceWithDetailsUseCase(lat, lng, category)

   val placeName = placeDetails?.osmSuggestedName
       ?: placeDetails?.address?.split(",")?.first()
       ?: generatePlaceName(category)
   ```

2. Add UI for custom naming:
   ```kotlin
   @Composable
   fun PlaceNameDialog(place: Place, onSave: (String) -> Unit) {
       // Show OSM suggestion
       // Allow user to enter custom name
       // Update place.isUserRenamed = true
   }
   ```

3. Show "Name This" badge for unnamed places

**Result:** Places have real names, users can customize

---

### Phase 4: Activity Insights (4 days)

**Goal:** Show meaningful insights based on activities

**Changes:**
1. Create `ActivityAnalyticsUseCases`:
   ```kotlin
   - getActivityTimeSummary() → Map<UserActivity, Duration>
   - detectWorkoutSessions() → List<WorkoutSession>
   - analyzeCommutePatterns() → CommuteAnalysis
   ```

2. Create `ActivityInsightsScreen`:
   ```kotlin
   - "This Week's Activities" (pie chart)
   - "Workouts This Week" (3 sessions, 2h 15m)
   - "Commute Analysis" (avg 35 min)
   ```

**Result:** Rich insights - "You worked out 3 times this week"

---

## 🎯 Quick Win Implementation (1 Day Proof of Concept)

Want to see results fast? Start here:

### Day 1: Activity-Aware Location Saving

**3 Files to Modify:**

1. **Location.kt** - Add activity fields
2. **LocationTrackingService.kt** - Capture activity
3. **LocationDao.kt** - Query by activity

**Code Changes (~50 lines):**

```kotlin
// 1. Location.kt (add 2 fields)
data class Location(
    // ... existing ...
    val userActivity: UserActivity = UserActivity.UNKNOWN,
    val activityConfidence: Float = 0f
)

// 2. LocationTrackingService.kt (modify saveLocation)
private suspend fun saveLocation(location: AndroidLocation) {
    val activity = activityRecognitionManager.getCurrentActivity()

    // Skip if driving with high confidence
    if (activity.activity == UserActivity.DRIVING &&
        activity.confidence > 0.75f) {
        Log.i(TAG, "Skipping location - user is driving")
        return
    }

    val locationData = Location(
        latitude = location.latitude,
        longitude = location.longitude,
        timestamp = LocalDateTime.now(),
        accuracy = location.accuracy,
        userActivity = activity.activity,
        activityConfidence = activity.confidence
    )

    locationRepository.saveLocation(locationData)
}

// 3. LocationDao.kt (add query)
@Query("SELECT * FROM locations WHERE userActivity = :activity")
fun getLocationsByActivity(activity: String): Flow<List<LocationEntity>>
```

**Test It:**
1. Build and run
2. Track locations for 1 hour (walk, drive, sit)
3. Query database: `SELECT userActivity, COUNT(*) FROM locations GROUP BY userActivity`
4. You should see: WALKING: 50, STATIONARY: 30, DRIVING: 5

**Impact:** This alone will dramatically improve place detection!

---

## 📊 Expected Results

### Accuracy Improvements

| Metric | Before | After |
|--------|--------|-------|
| False places (while driving) | 20-30% | <5% |
| Correct place categories | 60% | 85% |
| Places with real names | 0% | 70%+ |
| User satisfaction | Unknown | "This is amazing!" |

### New Insights Unlocked

```
Before:
"You have 258 locations and 5 places"

After:
"This Week:
 🏃 3 workouts (2h 15m)
 💼 Working: 42h 30m
 🚗 Commuting: 5h 20m (avg 35 min)
 🍽️ Dining out: 3 visits, 4h total
 🏠 Home: 68h

 Places: Home, Tech Corp Office, Gold's Gym,
         Starbucks Coffee, Whole Foods Market"
```

---

## 🔧 Technical Details

### Database Schema Changes

```sql
-- Migration v1 → v2
ALTER TABLE locations ADD COLUMN userActivity TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE locations ADD COLUMN activityConfidence REAL NOT NULL DEFAULT 0.0;
ALTER TABLE locations ADD COLUMN semanticContext TEXT;

CREATE INDEX idx_locations_activity ON locations(userActivity);
CREATE INDEX idx_locations_context ON locations(semanticContext);
```

### Performance Impact

- **Activity Recognition:** 1 API call per 30 seconds = 2,880 calls/day
- **Battery Impact:** ~3-5% additional drain (Google's estimate)
- **Storage:** +12 bytes per location (3 new fields)
- **Geocoding:** 1 API call per new place = ~1-5 calls/day with caching

### Privacy Considerations

- Activity data stored locally (encrypted database)
- No data sent to servers
- OSM queries are anonymous (no auth required)
- User can disable activity recognition in settings

---

## 🎓 Key Concepts

### UserActivity (What your body is doing)
- `WALKING` - On foot
- `DRIVING` - In vehicle
- `STATIONARY` - Not moving
- `CYCLING` - On bicycle
- `UNKNOWN` - Can't determine

### SemanticContext (What you're actually doing)
- `WORKING` - At work, stationary, work hours
- `WORKING_OUT` - Gym, high movement, workout hours
- `EATING` - Restaurant, meal times
- `COMMUTING` - Driving between home-work
- `SHOPPING` - Walking, browsing stores
- `SOCIALIZING` - Restaurant/cafe, evening/weekend
- `RELAXING_HOME` - Home, stationary, evening

### Place Naming Priority
1. **User custom name** (user manually named it)
2. **OSM suggested name** (from OpenStreetMap)
3. **Geocoded address** (street address from Android Geocoder)
4. **Category fallback** ("Restaurant", "Gym", etc.)

---

## 🚦 Getting Started Checklist

- [ ] Read full plan: `ACTIVITY_FIRST_IMPLEMENTATION_PLAN.md`
- [ ] Review existing code:
  - [ ] `HybridActivityRecognitionManager.kt` (activity recognition)
  - [ ] `EnrichPlaceWithDetailsUseCase.kt` (geocoding)
  - [ ] `PlaceDetectionUseCases.kt` (DBSCAN clustering)
- [ ] Start Phase 1: Add activity fields to Location model
- [ ] Database migration: Add 3 columns to locations table
- [ ] Modify LocationTrackingService to capture activity
- [ ] Test: Run for 1 hour, verify activity data saved
- [ ] Proceed to Phase 2...

---

## 📚 Resources

**Documentation:**
- Full Implementation Plan: `ACTIVITY_FIRST_IMPLEMENTATION_PLAN.md`
- Project Status: `docs/VOYAGER_PROJECT_STATUS.md`
- Gap Analysis: `docs/COMPREHENSIVE_GAP_ANALYSIS.md`

**Key Files:**
- Activity Recognition: `utils/HybridActivityRecognitionManager.kt`
- Location Service: `data/service/LocationTrackingService.kt`
- Place Detection: `domain/usecase/PlaceDetectionUseCases.kt`
- Geocoding: `domain/usecase/EnrichPlaceWithDetailsUseCase.kt`

**API References:**
- [Google Activity Recognition API](https://developers.google.com/location-context/activity-recognition)
- [OSM Nominatim API](https://nominatim.org/release-docs/latest/api/Overview/)
- [Android Geocoder](https://developer.android.com/reference/android/location/Geocoder)

---

## 💡 Pro Tips

1. **Start Small:** Implement Phase 1 first, test thoroughly before moving on
2. **Use Existing Code:** Don't rebuild what's already there (activity recognition, geocoding)
3. **Test in Real World:** Walk around, drive, sit at cafe - verify accuracy
4. **Battery Monitor:** Check battery stats after each phase
5. **User Feedback:** Show to real users early, get their input

---

## ❓ Common Questions

**Q: Will this drain battery?**
A: Activity recognition uses ~3-5% additional battery. We mitigate this with 30-second intervals and sleep schedule pausing.

**Q: What if Google Play Services isn't available?**
A: `HybridActivityRecognitionManager` automatically falls back to motion sensors.

**Q: What if OSM doesn't have the place name?**
A: We fall back to Android Geocoder, then show "Name This Place" badge for user input.

**Q: Can I disable activity recognition?**
A: Yes! Add `enableActivityRecognition` setting in UserPreferences.

**Q: Will this break existing data?**
A: No! Database migration adds new columns with default values. Old locations will show `UNKNOWN` activity.

---

## 🎉 Success Criteria

You'll know it's working when:

1. ✅ Dashboard shows: "You worked out for 45 minutes at Gold's Gym"
2. ✅ Timeline shows: "Commuting (driving)" instead of random GPS points
3. ✅ No false places created while driving on highway
4. ✅ Places have real names: "Starbucks Coffee" not "Restaurant"
5. ✅ Activity insights show: "3 workouts this week (2h 15m)"
6. ✅ Users can customize place names
7. ✅ Battery impact <5%

---

**Ready to start? Go to Phase 1 in the full implementation plan!** 🚀
