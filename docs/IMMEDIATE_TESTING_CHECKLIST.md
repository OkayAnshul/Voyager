# Immediate Testing Checklist - Real Issue Verification
**Purpose:** Quick manual tests to verify the real-time data and background service issues
**Time Required:** 30-60 minutes
**Date:** 2025-11-14

---

## Issue #1: Real-time Data Not Reflected

### Test 1: Dashboard Live Updates (5 min)

**Steps:**
1. Open app → Go to Dashboard
2. Start location tracking
3. **Watch the "Total Locations" counter**
4. Walk around for 2 minutes
5. **DO NOT** manually refresh

**Expected:** Counter should increase automatically every 30-60 seconds
**If Fails:** Data is not flowing in real-time

**Debug:**
```bash
# Check logs for state updates
adb logcat | grep -i "DashboardViewModel\|AppState\|LocationUpdate"
```

---

### Test 2: Visit Duration Updates (10 min)

**Steps:**
1. Start tracking
2. Stay in one location for 10+ minutes (triggers place detection)
3. Go to Dashboard
4. **Watch "Current Visit Duration"** (if visible)
5. Wait 2 minutes without touching the screen

**Expected:** Duration should update every 30-60 seconds
**If Fails:** Time calculations not reactive

**Manual Verification:**
- Note the entry time
- Calculate expected duration yourself
- Compare with displayed duration
- Check if it matches or is stale

---

### Test 3: Timeline Auto-Update (5 min)

**Steps:**
1. Open Timeline screen
2. Keep it open
3. In background, add a new location (or wait for automatic collection)
4. **DO NOT** pull to refresh

**Expected:** New visit appears automatically
**If Fails:** Timeline not observing data changes

---

## Issue #2: Background Service Dies

### Test 4: Task Removal Survival (2 min)

**Steps:**
1. Start location tracking
2. Verify "Live" indicator shows on Dashboard
3. Press Home button
4. **Clear app from recent apps** (swipe away)
5. Wait 2 minutes
6. Check notification shade

**Expected:** "Voyager Tracking Active" notification still visible
**If Fails:** Service died with app

**Debug:**
```bash
# Check if service is still running
adb shell dumpsys activity services | grep -i LocationTrackingService

# Check service status
adb shell ps | grep voyager
```

---

### Test 5: Battery Optimization Impact (5 min)

**Steps:**
1. Go to Phone Settings → Apps → Voyager
2. Check "Battery" settings
3. Note if "Not optimized" or "Optimized"
4. If optimized:
   - Start tracking
   - Leave phone idle for 10 minutes
   - Check if still tracking

**Expected:** Service continues even when optimized
**If Fails:** Battery optimization killing service

**Fix:** Request battery optimization exemption

---

### Test 6: Reboot Persistence (Requires reboot)

**Steps:**
1. Start location tracking
2. Reboot device
3. After reboot, check if tracking auto-resumes

**Expected:** Service restarts on boot (if configured)
**If Fails:** Missing BOOT_COMPLETED receiver

---

## Issue #3: Time Calculation Accuracy

### Test 7: Manual Time Verification (10 min)

**Setup:** Track a visit with known start/end times

**Steps:**
1. Note current time: `10:00 AM`
2. Start tracking at a location
3. Stay for exactly 30 minutes (use timer)
4. Leave location at `10:30 AM`
5. Check Dashboard "Total Time Tracked"

**Expected:** Shows 30 minutes (1800000 ms or "30m")
**If Fails:** Time calculation bug

**Manual Calculation:**
```
Entry: 10:00 AM = 10:00:00
Exit:  10:30 AM = 10:30:00
Duration = 30 minutes = 1,800,000 milliseconds
```

---

### Test 8: Ongoing Visit Duration (5 min)

**Steps:**
1. Enter a place and stay
2. Note entry time (e.g., 2:00 PM)
3. After 10 minutes, check duration
4. Should show ~10 minutes

**Expected:** Duration updates continuously
**Actual:** Check what it shows

---

## Diagnostic Commands

### Check Current Service Status
```bash
# Is service running?
adb shell dumpsys activity services com.cosmiclaboratory.voyager | grep -A 20 LocationTrackingService

# Check notification
adb shell dumpsys notification | grep -A 5 voyager

# Check foreground services
adb shell dumpsys activity services | grep -i foreground
```

### Check Database Real-time
```bash
# Location count
adb shell "run-as com.cosmiclaboratory.voyager sqlite3 /data/data/com.cosmiclaboratory.voyager/databases/voyager_database 'SELECT COUNT(*) FROM locations;'"

# Latest locations
adb shell "run-as com.cosmiclaboratory.voyager sqlite3 /data/data/com.cosmiclaboratory.voyager/databases/voyager_database 'SELECT * FROM locations ORDER BY timestamp DESC LIMIT 5;'"

# Active visits
adb shell "run-as com.cosmiclaboratory.voyager sqlite3 /data/data/com.cosmiclaboratory.voyager/databases/voyager_database 'SELECT * FROM visits WHERE exitTime IS NULL;'"
```

### Monitor Logs in Real-time
```bash
# All Voyager logs
adb logcat -s "Voyager*:V" "LocationTrackingService:V" "DashboardViewModel:V"

# State changes only
adb logcat | grep -i "AppState\|tracking.*started\|tracking.*stopped"

# Location updates
adb logcat | grep -i "location.*received\|GPS"
```

---

## Quick Results Matrix

| Test | Pass/Fail | Notes |
|------|-----------|-------|
| Dashboard auto-updates | ⬜ | |
| Visit duration updates | ⬜ | |
| Timeline auto-updates | ⬜ | |
| Service survives task kill | ⬜ | |
| Battery optimization OK | ⬜ | |
| Reboot persistence | ⬜ | |
| Time calculation accurate | ⬜ | |
| Ongoing visit updates | ⬜ | |

---

## Common Issues & Fixes

### If Dashboard Doesn't Update:
**Problem:** ViewModel not collecting AppStateManager flow
**Check:** `DashboardViewModel.kt:90-119` - observeAppState() method
**Fix:** Ensure `.collect{}` is inside viewModelScope.launch

### If Service Dies on Task Kill:
**Problem:** Missing START_STICKY or not foreground
**Check:** `LocationTrackingService.kt:167` - onStartCommand return value
**Expected:** `return START_STICKY`
**Also Check:** Service started as foreground (line 176-195)

### If Time Doesn't Update:
**Problem:** Duration calculated once, not reactive
**Check:** How `currentVisitDuration` is computed
**Fix:** Needs periodic re-calculation (every 30-60 seconds)

---

## Next Steps After Testing

### If Tests PASS:
- Issues might be intermittent
- Test on different Android versions
- Test with airplane mode
- Test with different battery levels

### If Tests FAIL:
1. Note which specific tests failed
2. Check logs for errors
3. Review code in failure areas:
   - `DashboardViewModel.kt` - observeAppState()
   - `LocationTrackingService.kt` - onStartCommand()
   - `AppStateManager.kt` - state emission
4. **Run these tests again** after ANY code changes

---

## Report Format

After running all tests, report:

```
TEST RESULTS - [Date/Time]

Real-time Updates:
- Dashboard: PASS/FAIL - [Details]
- Timeline: PASS/FAIL - [Details]
- Visit Duration: PASS/FAIL - [Details]

Background Service:
- Task Kill Survival: PASS/FAIL - [Details]
- Battery Optimization: PASS/FAIL - [Details]
- Reboot Persistence: PASS/FAIL - [Details]

Time Accuracy:
- Manual Verification: PASS/FAIL - [Expected vs Actual]
- Ongoing Updates: PASS/FAIL - [Details]

Logs:
[Paste relevant adb logcat output]

Screenshots:
[Attach screenshots showing issues]
```

---

## Automated Alternative

If you want to automate some checks:

```bash
#!/bin/bash
# save as test_voyager.sh

echo "=== Voyager Quick Test ==="

echo "1. Checking if service is running..."
adb shell dumpsys activity services | grep -q LocationTrackingService && echo "✓ Service running" || echo "✗ Service NOT running"

echo "2. Checking notification..."
adb shell dumpsys notification | grep -q voyager && echo "✓ Notification present" || echo "✗ No notification"

echo "3. Checking location count..."
COUNT=$(adb shell "run-as com.cosmiclaboratory.voyager sqlite3 /data/data/com.cosmiclaboratory.voyager/databases/voyager_database 'SELECT COUNT(*) FROM locations;'" 2>/dev/null)
echo "Total locations: $COUNT"

echo "4. Checking active visits..."
ACTIVE=$(adb shell "run-as com.cosmiclaboratory.voyager sqlite3 /data/data/com.cosmiclaboratory.voyager/databases/voyager_database 'SELECT COUNT(*) FROM visits WHERE exitTime IS NULL;'" 2>/dev/null)
echo "Active visits: $ACTIVE"

echo "Done!"
```

Make executable: `chmod +x test_voyager.sh`
Run: `./test_voyager.sh`

---

**Start with Tests 1, 4, and 7 - they are the quickest and most revealing.**
