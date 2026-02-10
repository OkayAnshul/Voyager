# Manual Testing Guide for Voyager
**Version:** 1.0
**Last Updated:** 2025-11-14

---

## Prerequisites Before Testing

### ⚠️ MUST FIX FIRST (App won't compile otherwise)

**Fix these CRITICAL issues before ANY testing:**

1. **Add missing DI providers** (see COMPREHENSIVE_GAP_ANALYSIS.md - CRITICAL #1)
2. **Add GeocodingCacheEntity to database** (see GAP_ANALYSIS - HIGH #2)
3. **Increment database version to 2**

Without these fixes, the app will crash on launch.

---

## Testing Environment Setup

### 1. Hardware Requirements
- Android device with GPS (not emulator for location testing)
- Android 8.0+ (API 26+)
- Battery > 50% (for extended tracking tests)

### 2. Permissions Setup
Before starting tests, grant all permissions:
```
Settings → Apps → Voyager → Permissions:
✅ Location: Allow all the time
✅ Physical activity: Allow
✅ Notifications: Allow (Android 13+)
```

### 3. Mock Data (Optional)
For faster testing without waiting for real visits:
- Run dashboard debug functions to trigger place detection
- Or use the PlaceDetectionWorker manual trigger

---

## Test Suite 1: Core Location Tracking

### Test 1.1: Start/Stop Tracking
**Objective:** Verify location tracking can start and stop

**Steps:**
1. Open app → Dashboard screen
2. Verify tracking is OFF (no green "Live" indicator)
3. Tap "Start Tracking" button
4. **Expected:** Green "Live" indicator appears
5. Wait 30 seconds
6. **Expected:** "Total Locations" count increases
7. Tap "Stop Tracking"
8. **Expected:** "Live" indicator disappears

**Pass Criteria:**
- ✅ Tracking starts without errors
- ✅ Live indicator shows correct state
- ✅ Location count increments (check every 30s)
- ✅ Tracking stops cleanly

**Known Issues:**
- If tracking doesn't start, check GPS is enabled
- Emulator won't collect real GPS points

---

### Test 1.2: Foreground Service Notification
**Objective:** Verify tracking continues in background

**Steps:**
1. Start tracking (from Test 1.1)
2. Press Home button (send app to background)
3. Pull down notification shade
4. **Expected:** See "Voyager Tracking Active" notification
5. Wait 2 minutes in background
6. Return to app
7. **Expected:** Location count has increased

**Pass Criteria:**
- ✅ Notification appears when tracking starts
- ✅ Notification shows current tracking status
- ✅ Locations collected while app in background
- ✅ Notification disappears when tracking stops

---

### Test 1.3: Real-time Dashboard Updates
**Objective:** Verify dashboard updates in real-time

**Setup:** Start tracking and stay in Dashboard screen

**Steps:**
1. Open Dashboard → Start tracking
2. Watch "Total Locations" for 2 minutes
3. **Expected:** Count increases periodically
4. **Expected:** "Time Tracked" updates
5. Walk to a new location
6. Wait 5 minutes at the new location
7. **Expected:** Place detection triggers (if configured)

**Pass Criteria:**
- ✅ Location count updates without manual refresh
- ✅ Time tracked increases continuously
- ✅ Dashboard doesn't freeze or lag
- ✅ UI remains responsive during updates

---

## Test Suite 2: Place Detection

### Test 2.1: Manual Place Detection Trigger
**Objective:** Trigger place detection manually

**Steps:**
1. Ensure you have tracked locations (run Test 1.1 first)
2. Open Dashboard
3. Scroll down to find "Detect Places" button (or debug menu)
4. Tap "Detect Places"
5. **Expected:** Loading indicator appears
6. Wait 10-30 seconds
7. **Expected:** "Total Places" count updates
8. Navigate to Timeline or Map
9. **Expected:** See detected places

**Pass Criteria:**
- ✅ Place detection completes without crash
- ✅ Places appear in database (check Total Places count)
- ✅ Places visible in Map/Timeline screens
- ✅ Error message if insufficient data

---

### Test 2.2: Automatic Place Detection
**Objective:** Verify automatic detection at threshold

**Setup:**
```
Settings → Place Detection:
- Enable Place Detection: ON
- Auto Trigger Count: 50
```

**Steps:**
1. Start tracking
2. Track at least 50 location points (monitor in Dashboard)
3. **Expected:** Automatic place detection triggers
4. **Expected:** Notification "Analyzing your places..."
5. Wait for completion
6. Check Total Places count

**Pass Criteria:**
- ✅ Auto-detection triggers at 50 locations
- ✅ Notification shows progress
- ✅ New places detected automatically

**Debug If Fails:**
- Check Settings → enablePlaceDetection is true
- Check autoDetectTriggerCount = 50
- Use Dashboard debug function to check preferences

---

### Test 2.3: Place Naming and Geocoding
**Objective:** Verify places get human-readable names

**Steps:**
1. After place detection (Test 2.1 or 2.2)
2. Navigate to Map screen
3. Tap on a detected place marker
4. **Expected:** Place has a name (not just "Place #123")
5. **Expected:** Address information shown
6. Check multiple places

**Pass Criteria:**
- ✅ Places have names from geocoding
- ✅ Address shows street/city
- ✅ Category assigned (HOME, WORK, etc.)

**Known Issues:**
- If all places show generic names, geocoding might be failing
- Check network connectivity
- Check logs for geocoding errors

---

## Test Suite 3: Visit Tracking

### Test 3.1: Visit Creation
**Objective:** Verify visits are created when staying at a place

**Setup:** You need at least 1 detected place

**Steps:**
1. Start tracking
2. Stay at a detected place for 10+ minutes
3. Open Timeline screen
4. **Expected:** Active visit shown at top
5. **Expected:** Visit duration updates in real-time
6. Leave the place (walk >100m away)
7. Wait 2 minutes
8. Return to Timeline
9. **Expected:** Visit has an end time now

**Pass Criteria:**
- ✅ Visit created when entering place
- ✅ Duration updates while at place
- ✅ Visit ends when leaving place
- ✅ Visit shows in Timeline screen

---

### Test 3.2: Multiple Visits Same Day
**Objective:** Verify multiple visits are tracked correctly

**Steps:**
1. Start tracking in morning
2. Visit Place A for 30 min
3. Leave, go to Place B for 30 min
4. Leave, return to Place A for 30 min
5. Open Timeline screen
6. **Expected:** See 3 separate visits
7. **Expected:** Each has correct place and duration
8. Open Dashboard
9. **Expected:** "Total Time Tracked" = sum of all visits

**Pass Criteria:**
- ✅ All visits recorded separately
- ✅ Correct timestamps for each
- ✅ Total time matches sum of visits
- ✅ No duplicate visits

---

## Test Suite 4: Analytics & Insights

### Test 4.1: Daily Analytics
**Objective:** Verify Dashboard shows today's stats

**Setup:** Complete Test 3.2 (have multiple visits)

**Steps:**
1. Open Dashboard
2. Check stats cards:
   - Total Locations
   - Total Places
   - Total Time Tracked
3. **Expected:** Time Tracked matches your visit durations
4. **Expected:** Places Visited Today > 0
5. Navigate to Insights screen
6. **Expected:** Weekly analytics shown

**Pass Criteria:**
- ✅ Dashboard stats match actual data
- ✅ Time calculations correct
- ✅ Stats update after new visits

---

### Test 4.2: Weekly Comparison
**Objective:** Test weekly vs monthly comparison feature

**Setup:** Need data from at least 2 weeks

**Steps:**
1. Navigate to Insights screen
2. Tap "Weekly Comparison" card
3. **Expected:** Weekly Comparison screen opens
4. **Expected:** Shows "This Week" vs "Last Week"
5. Check metrics:
   - Total time at places
   - Number of visits
   - Number of unique places
6. Tap "Monthly" tab
7. **Expected:** Shows month comparison
8. **Expected:** Per-place breakdowns shown

**Pass Criteria:**
- ✅ Weekly comparison loads without error
- ✅ Metrics calculated correctly
- ✅ Monthly tab switches view
- ✅ Percentage changes shown with trend arrows

**Known Issues:**
- Monthly comparison currently returns weekly data (see GAP_ANALYSIS - HIGH #1)
- Fix needed in CompareMonthlyAnalyticsUseCase

---

### Test 4.3: Place Patterns Detection
**Objective:** Verify behavioral pattern detection

**Setup:** Need 2+ weeks of consistent visit data

**Steps:**
1. Navigate to Insights screen
2. Tap "Patterns & Insights" card
3. **Expected:** PlacePatterns screen opens
4. **Expected:** See patterns like:
   - "You usually visit [Place] on Weekdays"
   - "You typically visit [Place] around 9:00 AM"
   - "You visit [Place] 3x per week"
5. Check "Anomalies" section
6. **Expected:** Any unusual behavior flagged

**Pass Criteria:**
- ✅ Patterns screen loads
- ✅ At least 1 pattern detected (if data sufficient)
- ✅ Descriptions are human-readable
- ✅ Confidence scores shown

**If No Patterns Shown:**
- Check you have 3+ visits to same place
- Patterns require consistent timing/days
- Debug: Check console logs for pattern analysis

---

## Test Suite 5: Settings & Preferences

### Test 5.1: Tracking Accuracy Modes
**Objective:** Test different accuracy modes

**Steps:**
1. Go to Settings screen
2. Find "Tracking Accuracy" setting
3. Change to "BALANCED"
4. **Expected:** Settings save immediately
5. Start tracking
6. **Expected:** Location updates less frequent (every 60s)
7. Stop tracking, change to "HIGH_ACCURACY"
8. Start tracking
9. **Expected:** Location updates more frequent (every 15s)

**Pass Criteria:**
- ✅ Settings persist after app restart
- ✅ Tracking interval changes with accuracy mode
- ✅ Battery usage differs by mode

---

### Test 5.2: Sleep Schedule
**Objective:** Verify tracking pauses during sleep hours

**Steps:**
1. Go to Settings → Sleep Schedule
2. Enable sleep schedule
3. Set sleep: 11 PM - 7 AM
4. Start tracking at 10:30 PM
5. Wait until 11:05 PM
6. **Expected:** Tracking pauses automatically
7. **Expected:** Notification shows "Paused for sleep"
8. Wait until 7:05 AM next day
9. **Expected:** Tracking resumes automatically

**Pass Criteria:**
- ✅ Tracking pauses at sleep time
- ✅ Tracking resumes at wake time
- ✅ No locations collected during sleep
- ✅ User notified of pause/resume

**Quick Test (Don't want to wait):**
- Set sleep time to "next 5 minutes" for testing

---

### Test 5.3: Data Export
**Objective:** Export user data

**⚠️ NOTE:** Export UI is currently MISSING (see GAP_ANALYSIS - CRITICAL #2)
**After fix is applied:**

**Steps:**
1. Go to Settings screen
2. Scroll to "Data Management" section
3. Tap "Export Data (JSON)"
4. **Expected:** File picker opens
5. Choose location to save
6. **Expected:** Export completes with success message
7. Check saved file
8. **Expected:** Valid JSON with locations, places, visits

**Pass Criteria:**
- ✅ Export creates valid JSON file
- ✅ All data included in export
- ✅ File size reasonable (not empty)
- ✅ Can open and read JSON

---

## Test Suite 6: Map Visualization

### Test 6.1: Map Display
**Objective:** Verify map shows locations and places

**Steps:**
1. Complete tracking and place detection
2. Navigate to Map screen
3. **Expected:** Map centered on your area
4. **Expected:** Blue markers for places
5. **Expected:** Thin lines connecting locations (optional)
6. Tap a place marker
7. **Expected:** Info window with place name
8. Zoom in/out
9. **Expected:** Map responds smoothly

**Pass Criteria:**
- ✅ Map loads without errors
- ✅ All places visible as markers
- ✅ User can pan/zoom
- ✅ Tapping marker shows info

---

### Test 6.2: Location Heatmap
**Objective:** Verify location density visualization

**Steps:**
1. On Map screen
2. Look for heatmap/polyline of tracked locations
3. **Expected:** Path shows where you've been
4. **Expected:** Denser areas more visible
5. Toggle "Show Path" option (if available)
6. **Expected:** Path hides/shows

**Pass Criteria:**
- ✅ Path visualization renders
- ✅ Performance acceptable with 1000+ points
- ✅ Can toggle visibility

---

## Test Suite 7: Timeline View

### Test 7.1: Daily Timeline
**Objective:** View chronological list of visits

**Steps:**
1. Navigate to Timeline screen
2. **Expected:** List of visits for today
3. **Expected:** Most recent visit at top
4. Each visit shows:
   - Place name
   - Entry time
   - Exit time (if ended)
   - Duration
5. Scroll through timeline
6. **Expected:** Past days shown as grouped

**Pass Criteria:**
- ✅ Timeline loads visits
- ✅ Sorted by time (newest first)
- ✅ All visit info displayed
- ✅ Smooth scrolling

---

### Test 7.2: Timeline Date Picker
**Objective:** View visits for specific date

**Steps:**
1. On Timeline screen
2. Look for date selector
3. Select a date from past week
4. **Expected:** Timeline updates to show that day
5. **Expected:** Visits for selected date shown
6. Return to today
7. **Expected:** Today's visits shown

**Pass Criteria:**
- ✅ Can select any past date
- ✅ Correct visits for date shown
- ✅ "No visits" message if none that day

---

## Test Suite 8: Notifications

### Test 8.1: Daily Summary Notification
**Objective:** Receive end-of-day summary

**⚠️ NOTE:** DailySummaryWorker not initialized (see GAP_ANALYSIS - HIGH #3)
**After fix:**

**Setup:**
```
Settings → Daily Summary:
- Enable: ON
- Time: 9:00 PM
```

**Steps:**
1. Enable daily summary in settings
2. Track locations throughout the day
3. Visit at least 2 places
4. Wait until 9:00 PM
5. **Expected:** Notification appears
6. Notification shows:
   - Places visited today
   - Total time tracked
   - Top place by duration
7. Tap notification
8. **Expected:** Opens to Dashboard or Insights

**Pass Criteria:**
- ✅ Notification arrives at scheduled time
- ✅ Summary data is accurate
- ✅ Tapping opens app

---

### Test 8.2: Tracking Status Notifications
**Objective:** Get notified of tracking changes

**Steps:**
1. Start tracking
2. **Expected:** Persistent notification appears
3. Send app to background
4. **Expected:** Notification stays visible
5. (If sleep schedule enabled) Wait for sleep time
6. **Expected:** Notification updates to "Paused"
7. Stop tracking
8. **Expected:** Notification disappears

**Pass Criteria:**
- ✅ Notification shows when tracking active
- ✅ Updates status in real-time
- ✅ Dismisses when tracking stops

---

## Test Suite 9: Edge Cases & Error Handling

### Test 9.1: No GPS Signal
**Objective:** Handle poor GPS conditions

**Steps:**
1. Start tracking
2. Go indoors to basement or building center
3. Wait 5 minutes (GPS signal lost)
4. **Expected:** App doesn't crash
5. **Expected:** Dashboard shows tracking but no new points
6. Return to good GPS area
7. **Expected:** Tracking resumes

**Pass Criteria:**
- ✅ No crash when GPS unavailable
- ✅ Graceful degradation
- ✅ Resumes when signal returns

---

### Test 9.2: App Restart During Tracking
**Objective:** Verify tracking survives restart

**Steps:**
1. Start tracking
2. Force-stop app (Settings → Apps → Voyager → Force Stop)
3. **Expected:** Service continues (check notification)
4. Reopen app
5. **Expected:** Dashboard shows tracking is active
6. **Expected:** Location count has increased

**Pass Criteria:**
- ✅ Service keeps running after app kill
- ✅ UI reconnects to service state
- ✅ No data loss

---

### Test 9.3: Low Battery Behavior
**Objective:** Verify battery optimization handling

**Steps:**
1. Enable battery saver mode (device settings)
2. Start tracking
3. **Expected:** Warning about reduced accuracy
4. Track for 30 minutes
5. **Expected:** Tracking continues but less frequent
6. Disable battery saver
7. **Expected:** Tracking returns to normal

**Pass Criteria:**
- ✅ App works in battery saver mode
- ✅ User warned about impacts
- ✅ Graceful frequency reduction

---

### Test 9.4: Database Cleanup
**Objective:** Verify old data cleanup

**Setup:** Need to have data older than retention period

**Steps:**
1. Go to Settings → Data Management
2. Find "Data Retention" setting
3. Set to "Keep 30 days"
4. Trigger cleanup (button or wait for scheduled)
5. **Expected:** Locations older than 30 days deleted
6. **Expected:** Recent data preserved
7. Check Dashboard counts
8. **Expected:** Total Locations reduced

**Pass Criteria:**
- ✅ Old data deleted correctly
- ✅ Recent data preserved
- ✅ No corruption after cleanup
- ✅ Stats recalculate correctly

---

## Test Suite 10: Performance & Stability

### Test 10.1: Large Dataset Handling
**Objective:** Test with 1000+ locations

**Setup:** Track continuously for several days to accumulate data

**Steps:**
1. Accumulate 1000+ location points
2. Open each screen:
   - Dashboard
   - Map
   - Timeline
   - Insights
3. Measure load times
4. **Expected:** All screens load < 3 seconds
5. **Expected:** No UI freezing
6. Scroll through Timeline
7. **Expected:** Smooth scrolling with pagination

**Pass Criteria:**
- ✅ Dashboard loads < 2s
- ✅ Map renders all 1000 points < 5s
- ✅ Timeline scrolls smoothly
- ✅ No OutOfMemory errors

---

### Test 10.2: Memory Leaks
**Objective:** Check for memory leaks

**Steps:**
1. Open Android Studio Profiler
2. Start app and begin tracking
3. Navigate through all screens 10 times
4. Monitor memory usage
5. **Expected:** Memory stabilizes, doesn't grow indefinitely
6. Check for:
   - ViewModel leaks
   - Flow collection leaks
   - Context leaks

**Pass Criteria:**
- ✅ Memory usage plateaus
- ✅ GC reclaims memory
- ✅ No retained ViewModels after screen close

---

### Test 10.3: Network Error Handling
**Objective:** Test offline behavior

**Steps:**
1. Enable Airplane mode
2. Open app
3. **Expected:** App loads normally
4. Start tracking
5. **Expected:** Tracking works (GPS only)
6. Trigger place detection
7. **Expected:** Places detected using clustering
8. **Expected:** Geocoding fails gracefully (generic names)
9. Disable Airplane mode
10. **Expected:** Geocoding retries and updates names

**Pass Criteria:**
- ✅ App functional offline
- ✅ No crash on network errors
- ✅ Auto-retry when connection returns

---

## Regression Testing Checklist

After any code changes, run these quick smoke tests:

- [ ] App builds and installs
- [ ] Dashboard opens without crash
- [ ] Can start tracking
- [ ] Can stop tracking
- [ ] Map screen loads
- [ ] Timeline screen loads
- [ ] Settings screen loads
- [ ] No obvious UI glitches
- [ ] Notifications appear

**Time:** ~5 minutes

---

## Bug Reporting Template

When you find a bug:

```markdown
**Bug Title:** [Short description]

**Severity:** Critical / High / Medium / Low

**Steps to Reproduce:**
1.
2.
3.

**Expected Result:**

**Actual Result:**

**Screenshots:** [If applicable]

**Device Info:**
- Model:
- Android Version:
- App Version:

**Logs:** [Attach logcat if available]
```

---

## Testing Sign-Off

After completing all tests, fill in:

```
Date: ___________
Tester: ___________

Test Suites Completed:
[ ] Core Location Tracking (3 tests)
[ ] Place Detection (3 tests)
[ ] Visit Tracking (2 tests)
[ ] Analytics & Insights (3 tests)
[ ] Settings & Preferences (3 tests)
[ ] Map Visualization (2 tests)
[ ] Timeline View (2 tests)
[ ] Notifications (2 tests)
[ ] Edge Cases (4 tests)
[ ] Performance (3 tests)

Critical Bugs Found: ___
High Priority Bugs: ___
Medium Priority Bugs: ___
Low Priority Bugs: ___

Ready for Production: YES / NO

Notes:
```

---

## Next Steps After Manual Testing

1. Fix all Critical and High bugs
2. Implement automated tests (see AUTOMATED_TESTING_STRATEGY.md)
3. Run automated test suite
4. Beta release to limited users
5. Monitor crash reports and analytics
6. Iterate based on feedback
