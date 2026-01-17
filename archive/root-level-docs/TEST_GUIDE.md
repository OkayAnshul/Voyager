# Voyager Testing Guide - Insert Test Location Data

## Overview
You can now test your Voyager app with realistic location data **without needing real GPS tracking**! This guide shows you how to insert test data directly from Android Studio.

## Quick Start

### Step 1: Open the Test File
The comprehensive test file is located at:
```
app/src/androidTest/java/com/cosmiclaboratory/voyager/SimpleLocationDataTest.kt
```

### Step 2: Run a Test
1. **Open** `SimpleLocationDataTest.kt` in Android Studio
2. **Right-click** on any test method (marked with `@Test`)
3. **Select** "Run 'testName'"
4. Wait for the test to complete
5. **Open your Voyager app** - the data will be there!

## Available Tests

### Test 1: Full Day in Delhi (`testFullDayInDelhiWithRealPlaces`)
**What it does:**
- Simulates a complete realistic day with actual places in Delhi
- Morning at home (Vasant Vihar) → Morning walk at India Gate → Work at Connaught Place → Lunch at Khan Market → Back to work → Evening shopping at Select Citywalk → Back home

**Real places inserted:**
- ✅ My Home (Vasant Vihar)
- ✅ India Gate (outdoor walk)
- ✅ My Office (Connaught Place)
- ✅ Khan Market Food Court
- ✅ Select Citywalk Mall (Saket)

**Data generated:**
- ~300+ location points
- 5 places with full addresses
- 7 visits with entry/exit times
- Realistic movement between locations

**Timeline & Visits:**
1. 6:00 AM - 8:00 AM: At home (Vasant Vihar)
2. 8:00 AM - 9:00 AM: Walking at India Gate
3. 9:30 AM - 1:00 PM: At work (Connaught Place)
4. 1:00 PM - 2:00 PM: Lunch (Khan Market)
5. 2:00 PM - 6:00 PM: Back at work
6. 6:00 PM - 7:30 PM: Shopping (Select Citywalk)
7. 8:00 PM - 11:00 PM: At home (evening)

---

### Test 2: Week of Data (`testWeekOfRealisticData`)
**What it does:**
- Generates 7 days of realistic location tracking
- Different patterns for weekdays vs weekends
- Recurring places (home, work, gym)

**Pattern:**
- **Monday-Friday**: Home → Work → Home (typical work day)
- **Saturday**: Home → Gym → Home (weekend workout)
- **Sunday**: Mostly at home (rest day)

**Data generated:**
- 1000+ location points over 7 days
- 3 recurring places
- 20+ visits across the week

---

### Test 3: Current Location (`testCurrentLocationAtCoffeeShop`)
**What it does:**
- Simulates you being at a location **right now**
- Creates an **active/ongoing visit** (no exit time)
- Perfect for testing real-time features

**Place:**
- Café Coffee Day, Connaught Place

**Data generated:**
- 30 location points over last 30 minutes
- 1 active visit (still in progress)
- Latest location timestamp = now

---

## What Each Test Validates

### ✅ Location Tracking
- GPS coordinates storage
- Timestamp accuracy
- Movement vs stationary detection

### ✅ Place Detection
- Real place names (not "Unknown Place")
- Full addresses with street, locality, postal code
- Correct categorization (Home, Work, Restaurant, etc.)

### ✅ Visit Tracking
- Entry and exit times
- Duration calculation
- Active vs completed visits

### ✅ Real-Time Data Flow
- Location → Place → Visit pipeline
- Database insertion and querying
- UI display in the app

### ✅ Analytics Scenarios
- Multiple visits to same place
- Time-based queries
- Visit statistics and patterns

---

## Test Data Details

### Real Places with Complete Information

Each place includes:
```kotlin
PlaceEntity(
    name = "Khan Market Food Court",           // Real place name
    category = PlaceCategory.RESTAURANT,       // Proper category
    latitude = 28.5526,                        // Actual coordinates
    longitude = 77.2434,
    address = "Khan Market, New Delhi, Delhi 110003",  // Full address
    streetName = "Middle Lane",                // Street
    locality = "Khan Market",                  // Area/locality
    subLocality = "South Delhi",              // Sub-area
    postalCode = "110003",                     // Zip code
    countryCode = "IN",                        // Country
    visitCount = 8,                            // Visit history
    totalTimeSpent = 7200000L,                // 2 hours
    radius = 50.0,                             // Geofence size
    confidence = 0.85f                         // Detection confidence
)
```

### Location Point Details

```kotlin
LocationEntity(
    latitude = 28.5526,                        // GPS coordinates
    longitude = 77.2434,
    timestamp = LocalDateTime.now(),           // Real timestamp
    accuracy = 12.0f,                          // GPS accuracy in meters
    speed = 5.0f,                              // Speed in m/s
    altitude = 50.0,                           // Altitude
    bearing = 45.0f                            // Direction
)
```

### Visit Details

```kotlin
VisitEntity(
    placeId = 1,                               // Links to place
    entryTime = LocalDateTime(...),            // When entered
    exitTime = LocalDateTime(...),             // When left (null = ongoing)
    duration = 3600000L,                       // Duration in milliseconds
    confidence = 0.90f                         // Detection confidence
)
```

---

## How to Customize Test Data

### Change Locations
Edit the coordinates in the test methods:

```kotlin
// Change to your city
val homeLocations = generateStationaryLocations(
    latitude = YOUR_LATITUDE,   // ← Change these
    longitude = YOUR_LONGITUDE, // ← Change these
    count = 24,
    startTime = today.plusHours(6),
    intervalSeconds = 300
)
```

### Change Place Names
```kotlin
val homePlace = PlaceEntity(
    name = "Your Custom Name",              // ← Change this
    category = PlaceCategory.HOME,
    latitude = 28.5672,
    longitude = 77.1580,
    address = "Your Address",               // ← Change this
    // ... rest of fields
)
```

### Change Timings
```kotlin
// For yesterday's data
val yesterday = LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay()

// For next week
val nextWeek = LocalDateTime.now().plusDays(7).toLocalDate().atStartOfDay()

// For specific date
val specificDate = LocalDateTime.of(2025, 1, 15, 9, 0)
```

---

## Helper Functions Available

### `generateStationaryLocations()`
Creates location points for staying at one place
```kotlin
generateStationaryLocations(
    latitude = 28.5672,
    longitude = 77.1580,
    count = 50,              // Number of points
    startTime = now,
    intervalSeconds = 60     // Point every 60 seconds
)
```

### `generateCircularPath()`
Creates movement in a circular pattern (like walking around a park)
```kotlin
generateCircularPath(
    centerLat = 28.6129,
    centerLng = 77.2295,
    radiusMeters = 200.0,    // Circle radius
    points = 40,
    startTime = now,
    intervalSeconds = 90
)
```

### `generateMovementPath()`
Creates linear movement from point A to point B
```kotlin
generateMovementPath(
    fromLat = 28.5672, fromLng = 77.1580,  // Start
    toLat = 28.6304, toLng = 77.2177,      // End
    points = 20,
    startTime = now,
    intervalSeconds = 90
)
```

---

## Running Tests

### From Android Studio
1. Right-click on test method → "Run 'testName'"
2. Wait for completion
3. Open Voyager app to see data

### From Command Line
```bash
# Run all tests
./gradlew connectedDebugAndroidTest

# Run specific test
./gradlew connectedDebugAndroidTest \
  --tests "com.cosmiclaboratory.voyager.SimpleLocationDataTest.testFullDayInDelhiWithRealPlaces"
```

### Run on Emulator or Physical Device
- Tests work on both emulators and real devices
- Data persists in the app's database
- You can run multiple tests to accumulate more data

---

## Troubleshooting

### Test fails with "Database locked"
- Close the Voyager app before running tests
- The test needs exclusive access to the database

### No data appears in app
- Make sure you're using the same app variant (debug)
- Check logcat for test output messages
- Verify the test completed successfully

### Want to clear test data
```kotlin
// Add this to a test method
database.locationDao().deleteAllLocations()
database.placeDao().getAllPlaces().first().forEach {
    database.placeDao().deletePlace(it)
}
```

---

## What This Tests

### ✅ App Functionality
- Location tracking works
- Place detection works
- Visit tracking works
- Database storage works
- UI displays data correctly

### ✅ Real-world Scenarios
- Daily commute patterns
- Multiple visits to same place
- Different place categories
- Active/ongoing visits
- Week-long tracking

### ✅ Edge Cases
- GPS drift while stationary
- Movement between locations
- Timestamps and durations
- Place clustering
- Visit continuity

---

## Next Steps

After running tests:

1. **Open Voyager app** and explore:
   - Map view with location points
   - Places list with visit counts
   - Visit history with timestamps
   - Analytics and insights

2. **Test specific features**:
   - Place renaming
   - Category changes
   - Visit editing
   - Analytics filtering
   - Export functionality

3. **Add more test scenarios**:
   - Create custom test methods
   - Use different cities/locations
   - Test specific workflows
   - Validate bug fixes

---

## Time Conversions (Handy Reference)

```kotlin
// Milliseconds
1 second  = 1,000 ms
1 minute  = 60,000 ms
1 hour    = 3,600,000 ms
1 day     = 86,400,000 ms
1 week    = 604,800,000 ms

// Common durations
30 minutes = 1,800,000 ms
2 hours    = 7,200,000 ms
8 hours    = 28,800,000 ms
```

---

## Summary

✅ **Test file location**: `app/src/androidTest/java/com/cosmiclaboratory/voyager/SimpleLocationDataTest.kt`

✅ **3 comprehensive tests** covering:
- Full day with real places
- Week of realistic data
- Current/active location

✅ **Real place names and addresses** - not fake data!

✅ **Easy to run** - just right-click and run

✅ **Data persists** - opens immediately in the app

✅ **Fully customizable** - change locations, times, places

✅ **Tests all aspects** - locations, places, visits, analytics

**You can now test your entire app workflow without leaving your desk!** 🎉
