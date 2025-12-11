# Phase 8 Testing Logbook

**Created**: 2025-11-14
**Purpose**: Track manual and automated testing of Phase 8 implementation
**Status**: 🧪 READY FOR TESTING

---

## TESTING ENVIRONMENT

### Device Information
- **Device Model**: _________________________
- **Android Version**: _________________________
- **Has Accelerometer**: ⬜ Yes ⬜ No
- **Battery Level**: ________%
- **Date/Time**: _________________________

### App Version
- **Build**: Debug
- **Phase**: 8 (Performance & Battery Optimization)
- **Git Commit**: _________________________

---

## LOG MONITORING GUIDE

### Logcat Filters
Use these filters to monitor Phase 8 logs during testing:

```bash
# Sleep Schedule Logs
adb logcat -s SleepScheduleManager:D

# Motion Detection Logs
adb logcat -s MotionDetectionManager:D

# Service Integration Logs
adb logcat -s LocationTrackingService:D | grep -E "sleep|motion"

# All Phase 8 Logs
adb logcat -s SleepScheduleManager:D MotionDetectionManager:D LocationTrackingService:D
```

### Expected Log Output

**Sleep Mode Active**:
```
SleepScheduleManager: Sleep window check: current=23, range=22-6, inWindow=true
LocationTrackingService: Location skipped - sleep mode active
```

**Motion Detected During Sleep**:
```
MotionDetectionManager: Motion detected! Magnitude: 2.5 m/s² (threshold: 1.0)
LocationTrackingService: Sleep mode active but motion detected - continuing tracking
```

**Preferences Updated**:
```
LocationTrackingService: Motion detection restarted with updated preferences
```

---

## TEST PLAN

### Test Suite 1: Sleep Schedule (Basic)

#### TEST 1.1: Enable Sleep Mode
**Steps**:
1. Open Settings screen
2. Scroll to "Sleep Schedule (Battery Optimization)"
3. Toggle "Enable Sleep Mode" ON
4. Set start time: 22:00 (10 PM)
5. Set end time: 06:00 (6 AM)

**Expected Results**:
- [ ] Toggle switches ON
- [ ] Time pickers show 10:00 PM and 6:00 AM
- [ ] Battery savings shows "~33% daily"
- [ ] Settings persist after closing app

**Logcat Command**:
```bash
adb logcat -s SleepScheduleManager:D LocationTrackingService:D
```

**Actual Results**:
```
_________________________________________________________________________
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 1.2: Sleep Mode Pauses Tracking
**Prerequisites**: Sleep mode enabled (22:00 - 06:00)

**Steps**:
1. Set device time to 23:00 (within sleep window)
2. Start location tracking
3. Wait 2 minutes
4. Check location count

**Expected Results**:
- [ ] No new locations saved during sleep
- [ ] Logs show "Location skipped - sleep mode active"
- [ ] Notification remains active

**Logcat Expected**:
```
SleepScheduleManager: Sleep window check: current=23, range=22-6, inWindow=true
LocationTrackingService: Location skipped - sleep mode active
```

**Actual Logcat**:
```
_________________________________________________________________________
_________________________________________________________________________
```

**Actual Location Count**: ________
**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 1.3: Tracking Resumes After Sleep
**Prerequisites**: Sleep mode enabled (22:00 - 06:00), tracking active

**Steps**:
1. Set device time to 05:30 (within sleep)
2. Verify tracking paused
3. Set device time to 06:30 (after sleep)
4. Wait 2 minutes
5. Check location count

**Expected Results**:
- [ ] Tracking paused at 05:30
- [ ] Tracking resumed at 06:30
- [ ] New locations saved after 06:30
- [ ] Logs show sleep window = false

**Actual Results**:
```
_________________________________________________________________________
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 1.4: Midnight Crossing
**Steps**:
1. Set sleep schedule: 23:00 - 01:00
2. Test at 22:30 (before sleep)
3. Test at 23:30 (in sleep)
4. Test at 00:30 (still in sleep)
5. Test at 01:30 (after sleep)

**Expected Results**:
- [ ] 22:30: Tracking active, inWindow=false
- [ ] 23:30: Tracking paused, inWindow=true
- [ ] 00:30: Tracking paused, inWindow=true
- [ ] 01:30: Tracking active, inWindow=false

**Actual Results**:
| Time  | In Window | Tracking | Logcat |
|-------|-----------|----------|--------|
| 22:30 | _________ | ________ | ______ |
| 23:30 | _________ | ________ | ______ |
| 00:30 | _________ | ________ | ______ |
| 01:30 | _________ | ________ | ______ |

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

### Test Suite 2: Motion Detection

#### TEST 2.1: Motion Detection Available
**Steps**:
1. Open Settings
2. Navigate to Sleep Schedule section
3. Check if Motion Detection toggle visible

**Expected Results**:
- [ ] Motion Detection section visible (if accelerometer present)
- [ ] Section hidden (if no accelerometer)
- [ ] Toggle state matches saved preference

**Device Has Accelerometer**: ⬜ Yes ⬜ No
**Motion Detection Visible**: ⬜ Yes ⬜ No
**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 2.2: Motion Detection Starts
**Prerequisites**: Sleep mode ON, Motion detection ON, device has accelerometer

**Steps**:
1. Start location tracking
2. Check logcat for motion detection startup

**Expected Logcat**:
```
MotionDetectionManager: Motion detection started with sensitivity threshold: 1.0 m/s²
```

**Actual Logcat**:
```
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 2.3: Motion Wakes Up Tracking
**Prerequisites**: Sleep mode ON, Motion detection ON, currently in sleep window

**Steps**:
1. Verify tracking paused (in sleep window)
2. Shake/move device vigorously
3. Wait 2 seconds
4. Check logs and location count

**Expected Results**:
- [ ] Logs show "Motion detected!"
- [ ] Logs show "Sleep mode active but motion detected - continuing tracking"
- [ ] New location saved despite sleep mode

**Logcat Expected**:
```
MotionDetectionManager: Motion detected! Magnitude: X.X m/s² (threshold: Y.Y)
LocationTrackingService: Sleep mode active but motion detected - continuing tracking
```

**Actual Logcat**:
```
_________________________________________________________________________
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 2.4: Sensitivity Levels
**Steps**:
1. Set sensitivity to HIGH (slider left)
2. Make small movement
3. Set sensitivity to LOW (slider right)
4. Make small movement
5. Make large movement

**Expected Results**:
- [ ] HIGH: Detects small movements
- [ ] LOW: Only detects large movements
- [ ] Threshold values logged correctly

**Sensitivity Testing**:
| Setting | Threshold | Small Movement | Large Movement |
|---------|-----------|----------------|----------------|
| High    | ~0.5 m/s² | _____________ | _____________ |
| Medium  | ~1.0 m/s² | _____________ | _____________ |
| Low     | ~2.0 m/s² | _____________ | _____________ |

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

### Test Suite 3: Preference Persistence

#### TEST 3.1: Settings Persist After App Restart
**Steps**:
1. Configure sleep schedule: 22:00 - 06:00
2. Enable motion detection
3. Set sensitivity to Medium
4. Force stop app
5. Restart app
6. Check settings

**Expected Results**:
- [ ] Sleep mode still enabled
- [ ] Times still 22:00 - 06:00
- [ ] Motion detection still enabled
- [ ] Sensitivity still Medium

**Actual Results**:
```
Sleep Mode: ___________
Start Time: ___________
End Time: ___________
Motion Detection: ___________
Sensitivity: ___________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 3.2: Preferences Update During Tracking
**Steps**:
1. Start tracking
2. Open settings
3. Change sleep start time from 22:00 to 23:00
4. Check logs

**Expected Logcat**:
```
LocationTrackingService: Motion detection restarted with updated preferences
```

**Actual Logcat**:
```
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

### Test Suite 4: Edge Cases

#### TEST 4.1: No Accelerometer Device
**Prerequisites**: Device without accelerometer (or mock unavailable)

**Steps**:
1. Open Sleep Schedule settings
2. Check Motion Detection section

**Expected Results**:
- [ ] Motion Detection section hidden
- [ ] No crashes
- [ ] Sleep mode still works

**Actual Results**:
```
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 4.2: Battery Saver Mode
**Steps**:
1. Enable system Battery Saver
2. Enable sleep mode with motion detection
3. Test motion detection

**Expected Results**:
- [ ] Motion detection may be disabled by system
- [ ] App handles gracefully
- [ ] Logs show sensor state

**Actual Results**:
```
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 4.3: Service Restart During Sleep
**Steps**:
1. Enable sleep mode (current time in window)
2. Start tracking (should be paused)
3. Force stop service
4. Restart service
5. Check if sleep mode still active

**Expected Results**:
- [ ] Sleep mode still blocks tracking after restart
- [ ] Motion detection re-initialized

**Actual Results**:
```
_________________________________________________________________________
```

**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

### Test Suite 5: Battery Impact

#### TEST 5.1: Battery Usage Measurement
**Test Duration**: 2 hours

**Setup**:
1. Fully charge device
2. Configure sleep mode: 22:00 - 06:00
3. Set current time to 23:00 (in sleep window)
4. Start tracking

**Measurements**:
| Time | Battery % | Notes |
|------|-----------|-------|
| 23:00 | 100% | Test start |
| 23:30 | ____% | |
| 00:00 | ____% | |
| 00:30 | ____% | |
| 01:00 | ____% | Test end |

**Expected**: <1% drain during sleep (vs ~2-3% without sleep mode)
**Actual Drain**: ________%
**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

#### TEST 5.2: Motion Detection Overhead
**Test Duration**: 1 hour

**Setup**:
1. Fully charge device
2. Enable sleep mode + motion detection
3. Keep device stationary (no motion)
4. Monitor battery

**Expected**: <0.5% additional drain from sensor
**Actual Drain**: ________%
**Status**: ⬜ PASS ⬜ FAIL ⬜ SKIP

---

## BUG TRACKING

### Bug #1: _______________________________
**Severity**: ⬜ Critical ⬜ High ⬜ Medium ⬜ Low
**Description**:
```
_________________________________________________________________________
_________________________________________________________________________
```

**Steps to Reproduce**:
1.
2.
3.

**Expected**:
**Actual**:
**Logs**:
```
_________________________________________________________________________
```

**Status**: ⬜ Open ⬜ Fixed ⬜ Won't Fix

---

### Bug #2: _______________________________
(Add more as needed)

---

## PERFORMANCE NOTES

### Observations
```
_________________________________________________________________________
_________________________________________________________________________
_________________________________________________________________________
```

### Recommendations
```
_________________________________________________________________________
_________________________________________________________________________
_________________________________________________________________________
```

---

## SIGN-OFF

**Tester**: _________________________
**Date**: _________________________
**Overall Status**: ⬜ PASS ⬜ FAIL ⬜ PARTIAL

**Summary**:
```
_________________________________________________________________________
_________________________________________________________________________
_________________________________________________________________________
```

**Critical Issues Found**: ________
**Recommendation**: ⬜ Ready for Production ⬜ Needs Fixes ⬜ Block Release
