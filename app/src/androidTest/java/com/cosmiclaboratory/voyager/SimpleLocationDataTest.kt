package com.cosmiclaboratory.voyager

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cosmiclaboratory.voyager.data.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.data.database.entity.LocationEntity
import com.cosmiclaboratory.voyager.data.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.data.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * Comprehensive instrumented test to insert realistic location data with real place names.
 *
 * Tests cover:
 * - Real location tracking with actual place names and addresses
 * - Full day simulation with home, work, shopping, dining
 * - Multiple days of data
 * - Place detection and visit tracking
 * - Analytics scenarios
 *
 * To run this test:
 * 1. Open Android Studio
 * 2. Right-click on this file or individual test method
 * 3. Select "Run 'SimpleLocationDataTest'" or "Run 'testName'"
 * 4. The test will insert data into your app's REAL database
 * 5. Open the Voyager app to see the inserted data!
 *
 * NOTE: This uses REAL database, not in-memory, so data persists!
 */
@RunWith(AndroidJUnit4::class)
class SimpleLocationDataTest {

    private lateinit var database: VoyagerDatabase
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        // Use the actual app database (not in-memory) so data persists
        database = Room.databaseBuilder(
            context,
            VoyagerDatabase::class.java,
            "voyager_database"
        ).build()
    }

    @After
    fun cleanup() {
        database.close()
    }

    /**
     * Test 1: Full Day in Delhi with Real Places
     * Morning: Home → India Gate → Work (Connaught Place) → Lunch (Khan Market) → Work → Home
     */
    @Test
    fun testFullDayInDelhiWithRealPlaces(): Unit = runBlocking {
        println("\n🚀 Test 1: Simulating a full day in Delhi with real places...")
        println("=" .repeat(60))

        val today = LocalDateTime.now().toLocalDate().atStartOfDay()

        // 1. Morning at Home (Vasant Vihar) - 6:00 AM to 8:00 AM
        println("\n📍 6:00 AM - 8:00 AM: At Home (Vasant Vihar)")
        val homeLocations = generateStationaryLocations(
            latitude = 28.5672,
            longitude = 77.1580,
            count = 24,
            startTime = today.plusHours(6),
            intervalSeconds = 300 // Every 5 minutes
        )
        database.locationDao().insertLocations(homeLocations)

        // Create Home Place
        val homePlace = PlaceEntity(
            name = "My Home",
            category = PlaceCategory.HOME,
            latitude = 28.5672,
            longitude = 77.1580,
            address = "Vasant Vihar, New Delhi, Delhi 110057",
            streetName = "Pocket C Road",
            locality = "Vasant Vihar",
            subLocality = "South West Delhi",
            postalCode = "110057",
            countryCode = "IN",
            visitCount = 15,
            totalTimeSpent = 36000000L, // 10 hours
            lastVisit = today.plusHours(6),
            radius = 100.0,
            confidence = 0.95f
        )
        val homeId = database.placeDao().insertPlace(homePlace)
        println("   ✓ Created Home place (ID: $homeId)")

        // Home visit
        val homeVisit1 = VisitEntity(
            placeId = homeId,
            entryTime = today.plusHours(6),
            exitTime = today.plusHours(8),
            duration = 7200000L, // 2 hours
            confidence = 0.95f
        )
        database.visitDao().insertVisit(homeVisit1)

        // 2. Morning Walk at India Gate - 8:00 AM to 9:00 AM
        println("📍 8:00 AM - 9:00 AM: Morning walk at India Gate")
        val indiaGateLocations = generateCircularPath(
            centerLat = 28.6129,
            centerLng = 77.2295,
            radiusMeters = 200.0,
            points = 40,
            startTime = today.plusHours(8),
            intervalSeconds = 90
        )
        database.locationDao().insertLocations(indiaGateLocations)

        val indiaGatePlace = PlaceEntity(
            name = "India Gate",
            category = PlaceCategory.OUTDOOR,
            latitude = 28.6129,
            longitude = 77.2295,
            address = "Rajpath, India Gate, New Delhi, Delhi 110001",
            streetName = "Rajpath",
            locality = "India Gate",
            subLocality = "Central Delhi",
            postalCode = "110001",
            countryCode = "IN",
            visitCount = 5,
            totalTimeSpent = 3600000L,
            lastVisit = today.plusHours(8),
            radius = 150.0,
            confidence = 0.88f
        )
        val indiaGateId = database.placeDao().insertPlace(indiaGatePlace)
        println("   ✓ Created India Gate place (ID: $indiaGateId)")

        val indiaGateVisit = VisitEntity(
            placeId = indiaGateId,
            entryTime = today.plusHours(8),
            exitTime = today.plusHours(9),
            duration = 3600000L,
            confidence = 0.88f
        )
        database.visitDao().insertVisit(indiaGateVisit)

        // 3. Commute to Work - 9:00 AM to 9:30 AM
        println("📍 9:00 AM - 9:30 AM: Commuting to work (India Gate → Connaught Place)")
        val commuteToWorkLocations = generateMovementPath(
            fromLat = 28.6129, fromLng = 77.2295,
            toLat = 28.6304, toLng = 77.2177,
            points = 20,
            startTime = today.plusHours(9),
            intervalSeconds = 90
        )
        database.locationDao().insertLocations(commuteToWorkLocations)

        // 4. At Work (Connaught Place) - 9:30 AM to 1:00 PM
        println("📍 9:30 AM - 1:00 PM: At Work (Connaught Place)")
        val workMorningLocations = generateStationaryLocations(
            latitude = 28.6304,
            longitude = 77.2177,
            count = 42,
            startTime = today.plusHours(9).plusMinutes(30),
            intervalSeconds = 300
        )
        database.locationDao().insertLocations(workMorningLocations)

        val workPlace = PlaceEntity(
            name = "My Office",
            category = PlaceCategory.WORK,
            latitude = 28.6304,
            longitude = 77.2177,
            address = "Connaught Place, New Delhi, Delhi 110001",
            streetName = "Barakhamba Road",
            locality = "Connaught Place",
            subLocality = "Central Delhi",
            postalCode = "110001",
            countryCode = "IN",
            visitCount = 20,
            totalTimeSpent = 72000000L, // 20 hours
            lastVisit = today.plusHours(9).plusMinutes(30),
            radius = 80.0,
            confidence = 0.92f
        )
        val workId = database.placeDao().insertPlace(workPlace)
        println("   ✓ Created Work place (ID: $workId)")

        val workMorningVisit = VisitEntity(
            placeId = workId,
            entryTime = today.plusHours(9).plusMinutes(30),
            exitTime = today.plusHours(13),
            duration = 12600000L, // 3.5 hours
            confidence = 0.92f
        )
        database.visitDao().insertVisit(workMorningVisit)

        // 5. Lunch at Khan Market - 1:00 PM to 2:00 PM
        println("📍 1:00 PM - 2:00 PM: Lunch at Khan Market")
        val lunchCommuteLocations = generateMovementPath(
            fromLat = 28.6304, fromLng = 77.2177,
            toLat = 28.5526, toLng = 77.2434,
            points = 15,
            startTime = today.plusHours(13),
            intervalSeconds = 120
        )
        database.locationDao().insertLocations(lunchCommuteLocations)

        val lunchLocations = generateStationaryLocations(
            latitude = 28.5526,
            longitude = 77.2434,
            count = 12,
            startTime = today.plusHours(13).plusMinutes(30),
            intervalSeconds = 300
        )
        database.locationDao().insertLocations(lunchLocations)

        val lunchPlace = PlaceEntity(
            name = "Khan Market Food Court",
            category = PlaceCategory.RESTAURANT,
            latitude = 28.5526,
            longitude = 77.2434,
            address = "Khan Market, New Delhi, Delhi 110003",
            streetName = "Middle Lane",
            locality = "Khan Market",
            subLocality = "South Delhi",
            postalCode = "110003",
            countryCode = "IN",
            visitCount = 8,
            totalTimeSpent = 7200000L,
            lastVisit = today.plusHours(13).plusMinutes(30),
            radius = 50.0,
            confidence = 0.85f
        )
        val lunchId = database.placeDao().insertPlace(lunchPlace)
        println("   ✓ Created Lunch place (ID: $lunchId)")

        val lunchVisit = VisitEntity(
            placeId = lunchId,
            entryTime = today.plusHours(13).plusMinutes(30),
            exitTime = today.plusHours(14),
            duration = 1800000L,
            confidence = 0.85f
        )
        database.visitDao().insertVisit(lunchVisit)

        // 6. Return to Work - 2:00 PM to 6:00 PM
        println("📍 2:00 PM - 6:00 PM: Back at work")
        val returnToWorkLocations = generateMovementPath(
            fromLat = 28.5526, fromLng = 77.2434,
            toLat = 28.6304, toLng = 77.2177,
            points = 15,
            startTime = today.plusHours(14),
            intervalSeconds = 120
        )
        database.locationDao().insertLocations(returnToWorkLocations)

        val workAfternoonLocations = generateStationaryLocations(
            latitude = 28.6304,
            longitude = 77.2177,
            count = 48,
            startTime = today.plusHours(14).plusMinutes(30),
            intervalSeconds = 300
        )
        database.locationDao().insertLocations(workAfternoonLocations)

        val workAfternoonVisit = VisitEntity(
            placeId = workId,
            entryTime = today.plusHours(14).plusMinutes(30),
            exitTime = today.plusHours(18),
            duration = 12600000L,
            confidence = 0.92f
        )
        database.visitDao().insertVisit(workAfternoonVisit)

        // 7. Evening Shopping at Select Citywalk - 6:00 PM to 7:30 PM
        println("📍 6:00 PM - 7:30 PM: Shopping at Select Citywalk Mall")
        val shoppingCommuteLocations = generateMovementPath(
            fromLat = 28.6304, fromLng = 77.2177,
            toLat = 28.5244, toLng = 77.2066,
            points = 20,
            startTime = today.plusHours(18),
            intervalSeconds = 90
        )
        database.locationDao().insertLocations(shoppingCommuteLocations)

        val shoppingLocations = generateStationaryLocations(
            latitude = 28.5244,
            longitude = 77.2066,
            count = 18,
            startTime = today.plusHours(18).plusMinutes(30),
            intervalSeconds = 300
        )
        database.locationDao().insertLocations(shoppingLocations)

        val mallPlace = PlaceEntity(
            name = "Select Citywalk",
            category = PlaceCategory.SHOPPING,
            latitude = 28.5244,
            longitude = 77.2066,
            address = "District Centre, Saket, New Delhi, Delhi 110017",
            streetName = "A-3, District Centre",
            locality = "Saket",
            subLocality = "South Delhi",
            postalCode = "110017",
            countryCode = "IN",
            visitCount = 6,
            totalTimeSpent = 9000000L,
            lastVisit = today.plusHours(18).plusMinutes(30),
            radius = 120.0,
            confidence = 0.87f
        )
        val mallId = database.placeDao().insertPlace(mallPlace)
        println("   ✓ Created Mall place (ID: $mallId)")

        val mallVisit = VisitEntity(
            placeId = mallId,
            entryTime = today.plusHours(18).plusMinutes(30),
            exitTime = today.plusHours(19).plusMinutes(30),
            duration = 3600000L,
            confidence = 0.87f
        )
        database.visitDao().insertVisit(mallVisit)

        // 8. Return Home - 7:30 PM to 8:00 PM
        println("📍 7:30 PM - 8:00 PM: Returning home")
        val returnHomeLocations = generateMovementPath(
            fromLat = 28.5244, fromLng = 77.2066,
            toLat = 28.5672, toLng = 77.1580,
            points = 20,
            startTime = today.plusHours(19).plusMinutes(30),
            intervalSeconds = 90
        )
        database.locationDao().insertLocations(returnHomeLocations)

        // 9. Evening at Home - 8:00 PM onwards
        println("📍 8:00 PM - 11:00 PM: Evening at home")
        val homeEveningLocations = generateStationaryLocations(
            latitude = 28.5672,
            longitude = 77.1580,
            count = 36,
            startTime = today.plusHours(20),
            intervalSeconds = 300
        )
        database.locationDao().insertLocations(homeEveningLocations)

        val homeVisit2 = VisitEntity(
            placeId = homeId,
            entryTime = today.plusHours(20),
            exitTime = today.plusHours(23),
            duration = 10800000L, // 3 hours
            confidence = 0.95f
        )
        database.visitDao().insertVisit(homeVisit2)

        // Verify inserted data
        val locationCount = database.locationDao().getLocationCount()

        // Query each place by ID to verify they exist
        val homePlaceCheck = database.placeDao().getPlaceById(homeId)
        val indiaGatePlaceCheck = database.placeDao().getPlaceById(indiaGateId)
        val workPlaceCheck = database.placeDao().getPlaceById(workId)
        val lunchPlaceCheck = database.placeDao().getPlaceById(lunchId)
        val mallPlaceCheck = database.placeDao().getPlaceById(mallId)

        val insertedPlaces = listOfNotNull(
            homePlaceCheck, indiaGatePlaceCheck, workPlaceCheck, lunchPlaceCheck, mallPlaceCheck
        )

        // Get all visits
        val allVisits = database.visitDao().getAllVisits().first()

        println("\n" + "=".repeat(60))
        println("✅ Test 1 Complete!")
        println("📊 Statistics:")
        println("   - Total Locations: $locationCount")
        println("   - Total Places Inserted: ${insertedPlaces.size}")
        println("   - Total Visits: ${allVisits.size}")
        println("\n📱 Places Created:")
        insertedPlaces.forEach { place ->
            println("   • ${place.name} (${place.category.displayName}) at ${place.locality}")
        }
        println("\n📅 Visits Created:")
        println("   1. Home (morning): 6:00 AM - 8:00 AM")
        println("   2. India Gate (walk): 8:00 AM - 9:00 AM")
        println("   3. Work (morning): 9:30 AM - 1:00 PM")
        println("   4. Lunch at Khan Market: 1:00 PM - 2:00 PM")
        println("   5. Work (afternoon): 2:00 PM - 6:00 PM")
        println("   6. Shopping at Mall: 6:00 PM - 7:30 PM")
        println("   7. Home (evening): 8:00 PM - 11:00 PM")
        println("\n💡 Open Voyager app to see your full day in Delhi!")
        println("=".repeat(60))

        assertTrue("Should have locations", locationCount > 0)
        assertTrue("Should have at least 5 places (got ${insertedPlaces.size})", insertedPlaces.size >= 5)
        assertTrue("Should have 7 visits (got ${allVisits.size})", allVisits.size >= 7)
    }

    /**
     * Test 2: Week of Realistic Data - Different patterns each day
     */
    @Test
    fun testWeekOfRealisticData(): Unit = runBlocking {
        println("\n🚀 Test 2: Generating a week of realistic location data...")
        println("=".repeat(60))

        val startDate = LocalDateTime.now().minusDays(7).toLocalDate().atStartOfDay()

        // Create recurring places once
        val homePlace = PlaceEntity(
            name = "My Home",
            category = PlaceCategory.HOME,
            latitude = 28.5672,
            longitude = 77.1580,
            address = "Vasant Vihar, New Delhi, Delhi 110057",
            locality = "Vasant Vihar",
            visitCount = 0,
            totalTimeSpent = 0L,
            radius = 100.0
        )
        val homeId = database.placeDao().insertPlace(homePlace)

        val workPlace = PlaceEntity(
            name = "My Office",
            category = PlaceCategory.WORK,
            latitude = 28.6304,
            longitude = 77.2177,
            address = "Connaught Place, New Delhi, Delhi 110001",
            locality = "Connaught Place",
            visitCount = 0,
            totalTimeSpent = 0L,
            radius = 80.0
        )
        val workId = database.placeDao().insertPlace(workPlace)

        val gymPlace = PlaceEntity(
            name = "Fitness First Gym",
            category = PlaceCategory.GYM,
            latitude = 28.5450,
            longitude = 77.2020,
            address = "Green Park, New Delhi, Delhi 110016",
            locality = "Green Park",
            visitCount = 0,
            totalTimeSpent = 0L,
            radius = 60.0
        )
        val gymId = database.placeDao().insertPlace(gymPlace)

        var totalLocations = 0
        var totalVisits = 0

        // Generate 7 days of data
        for (dayIndex in 0..6) {
            val currentDay = startDate.plusDays(dayIndex.toLong())
            val dayName = currentDay.dayOfWeek.toString()

            println("\n📅 Day ${dayIndex + 1}: $dayName")

            when (dayIndex) {
                0, 1, 2, 3, 4 -> { // Monday to Friday - Work days
                    println("   Work day pattern")

                    // Morning at home
                    val morningHome = generateStationaryLocations(
                        latitude = 28.5672, longitude = 77.1580,
                        count = 24, startTime = currentDay.plusHours(6),
                        intervalSeconds = 300
                    )
                    database.locationDao().insertLocations(morningHome)
                    database.visitDao().insertVisit(VisitEntity(
                        placeId = homeId,
                        entryTime = currentDay.plusHours(6),
                        exitTime = currentDay.plusHours(8),
                        duration = 7200000L
                    ))

                    // Commute to work
                    val commute = generateMovementPath(
                        fromLat = 28.5672, fromLng = 77.1580,
                        toLat = 28.6304, toLng = 77.2177,
                        points = 20, startTime = currentDay.plusHours(8),
                        intervalSeconds = 90
                    )
                    database.locationDao().insertLocations(commute)

                    // At work
                    val atWork = generateStationaryLocations(
                        latitude = 28.6304, longitude = 77.2177,
                        count = 90, startTime = currentDay.plusHours(9),
                        intervalSeconds = 300
                    )
                    database.locationDao().insertLocations(atWork)
                    database.visitDao().insertVisit(VisitEntity(
                        placeId = workId,
                        entryTime = currentDay.plusHours(9),
                        exitTime = currentDay.plusHours(18),
                        duration = 32400000L // 9 hours
                    ))

                    // Return home
                    val returnHome = generateMovementPath(
                        fromLat = 28.6304, fromLng = 77.2177,
                        toLat = 28.5672, toLng = 77.1580,
                        points = 20, startTime = currentDay.plusHours(18),
                        intervalSeconds = 90
                    )
                    database.locationDao().insertLocations(returnHome)

                    // Evening at home
                    val eveningHome = generateStationaryLocations(
                        latitude = 28.5672, longitude = 77.1580,
                        count = 36, startTime = currentDay.plusHours(19),
                        intervalSeconds = 300
                    )
                    database.locationDao().insertLocations(eveningHome)
                    database.visitDao().insertVisit(VisitEntity(
                        placeId = homeId,
                        entryTime = currentDay.plusHours(19),
                        exitTime = currentDay.plusHours(23),
                        duration = 14400000L
                    ))

                    totalLocations += morningHome.size + commute.size + atWork.size + returnHome.size + eveningHome.size
                    totalVisits += 3
                }
                5 -> { // Saturday - Gym day
                    println("   Weekend - Gym day")

                    // Morning at home
                    val morningHome = generateStationaryLocations(
                        latitude = 28.5672, longitude = 77.1580,
                        count = 36, startTime = currentDay.plusHours(7),
                        intervalSeconds = 300
                    )
                    database.locationDao().insertLocations(morningHome)
                    database.visitDao().insertVisit(VisitEntity(
                        placeId = homeId,
                        entryTime = currentDay.plusHours(7),
                        exitTime = currentDay.plusHours(10),
                        duration = 10800000L
                    ))

                    // Go to gym
                    val toGym = generateMovementPath(
                        fromLat = 28.5672, fromLng = 77.1580,
                        toLat = 28.5450, toLng = 77.2020,
                        points = 15, startTime = currentDay.plusHours(10),
                        intervalSeconds = 90
                    )
                    database.locationDao().insertLocations(toGym)

                    // At gym
                    val atGym = generateStationaryLocations(
                        latitude = 28.5450, longitude = 77.2020,
                        count = 24, startTime = currentDay.plusHours(10).plusMinutes(30),
                        intervalSeconds = 300
                    )
                    database.locationDao().insertLocations(atGym)
                    database.visitDao().insertVisit(VisitEntity(
                        placeId = gymId,
                        entryTime = currentDay.plusHours(10).plusMinutes(30),
                        exitTime = currentDay.plusHours(12).plusMinutes(30),
                        duration = 7200000L
                    ))

                    // Return home
                    val returnHome = generateMovementPath(
                        fromLat = 28.5450, fromLng = 77.2020,
                        toLat = 28.5672, toLng = 77.1580,
                        points = 15, startTime = currentDay.plusHours(12).plusMinutes(30),
                        intervalSeconds = 90
                    )
                    database.locationDao().insertLocations(returnHome)

                    // Rest of day at home
                    val restOfDay = generateStationaryLocations(
                        latitude = 28.5672, longitude = 77.1580,
                        count = 72, startTime = currentDay.plusHours(13),
                        intervalSeconds = 300
                    )
                    database.locationDao().insertLocations(restOfDay)
                    database.visitDao().insertVisit(VisitEntity(
                        placeId = homeId,
                        entryTime = currentDay.plusHours(13),
                        exitTime = currentDay.plusHours(23),
                        duration = 36000000L
                    ))

                    totalLocations += morningHome.size + toGym.size + atGym.size + returnHome.size + restOfDay.size
                    totalVisits += 3
                }
                6 -> { // Sunday - Mostly at home
                    println("   Weekend - Relaxing at home")

                    // All day at home with occasional movements
                    val allDayHome = generateStationaryLocations(
                        latitude = 28.5672, longitude = 77.1580,
                        count = 120, startTime = currentDay.plusHours(8),
                        intervalSeconds = 300
                    )
                    database.locationDao().insertLocations(allDayHome)
                    database.visitDao().insertVisit(VisitEntity(
                        placeId = homeId,
                        entryTime = currentDay.plusHours(8),
                        exitTime = currentDay.plusHours(22),
                        duration = 50400000L // 14 hours
                    ))

                    totalLocations += allDayHome.size
                    totalVisits += 1
                }
            }
        }

        // Verify
        val locationCount = database.locationDao().getLocationCount()
        val allVisits = database.visitDao().getAllVisits().first()

        // Verify places exist
        val homePlaceCheck = database.placeDao().getPlaceById(homeId)
        val workPlaceCheck = database.placeDao().getPlaceById(workId)
        val gymPlaceCheck = database.placeDao().getPlaceById(gymId)

        val placesCount = listOfNotNull(homePlaceCheck, workPlaceCheck, gymPlaceCheck).size

        println("\n" + "=".repeat(60))
        println("✅ Test 2 Complete!")
        println("📊 Week Statistics:")
        println("   - Total Locations: $locationCount")
        println("   - Total Places: $placesCount")
        println("   - Total Visits: ${allVisits.size}")
        println("\n💡 Open Voyager app to see your week of activities!")
        println("=".repeat(60))

        assertTrue("Should have all locations (got $locationCount)", locationCount >= totalLocations)
        assertTrue("Should have 3 places (got $placesCount)", placesCount >= 3)
        assertTrue("Should have all visits (got ${allVisits.size})", allVisits.size >= totalVisits)
    }

    /**
     * Test 3: Current Location - Simulate you're at a place right now
     */
    @Test
    fun testCurrentLocationAtCoffeeShop(): Unit = runBlocking {
        println("\n🚀 Test 3: Simulating current location at a coffee shop...")
        println("=".repeat(60))

        val now = LocalDateTime.now()
        val thirtyMinutesAgo = now.minusMinutes(30)

        // Create coffee shop
        val coffeeShop = PlaceEntity(
            name = "Café Coffee Day - CP",
            category = PlaceCategory.RESTAURANT,
            latitude = 28.6315,
            longitude = 77.2167,
            address = "N Block, Connaught Place, New Delhi, Delhi 110001",
            streetName = "N Block Circle",
            locality = "Connaught Place",
            postalCode = "110001",
            countryCode = "IN",
            visitCount = 12,
            totalTimeSpent = 14400000L,
            lastVisit = thirtyMinutesAgo,
            radius = 40.0,
            confidence = 0.90f
        )
        val coffeeShopId = database.placeDao().insertPlace(coffeeShop)

        // Generate locations for the last 30 minutes
        val currentLocations = generateStationaryLocations(
            latitude = 28.6315,
            longitude = 77.2167,
            count = 30,
            startTime = thirtyMinutesAgo,
            intervalSeconds = 60 // Every minute
        )
        database.locationDao().insertLocations(currentLocations)

        // Create ongoing visit (no exit time = still there!)
        val ongoingVisit = VisitEntity(
            placeId = coffeeShopId,
            entryTime = thirtyMinutesAgo,
            exitTime = null, // Still at the location!
            duration = 1800000L, // 30 minutes so far
            confidence = 0.90f
        )
        database.visitDao().insertVisit(ongoingVisit)

        // Verify
        val activeVisits = database.visitDao().getActiveVisits()
        val lastLocation = database.locationDao().getLastLocation()
        val coffeeShopCheck = database.placeDao().getPlaceById(coffeeShopId)

        println("\n📍 Current Status:")
        println("   - Location: Café Coffee Day, Connaught Place")
        println("   - Place Created: ${coffeeShopCheck != null}")
        println("   - Entry Time: $thirtyMinutesAgo")
        println("   - Duration: 30 minutes (ongoing)")
        println("   - Latest Location: ${lastLocation?.latitude}, ${lastLocation?.longitude}")
        println("   - Active Visits: ${activeVisits.size}")

        println("\n=".repeat(60))
        println("✅ Test 3 Complete!")
        println("💡 Open Voyager app - you should see an active visit at the coffee shop!")
        println("=".repeat(60))

        assertTrue("Should have created coffee shop place", coffeeShopCheck != null)
        assertTrue("Should have active visits (got ${activeVisits.size})", activeVisits.isNotEmpty())
        assertTrue("Should have latest location", lastLocation != null)
    }

    // Helper functions to generate location data

    private fun generateStationaryLocations(
        latitude: Double,
        longitude: Double,
        count: Int,
        startTime: LocalDateTime,
        intervalSeconds: Long
    ): List<LocationEntity> {
        return (0 until count).map { i ->
            // Small random variation to simulate GPS drift
            val latVariation = (Math.random() - 0.5) * 0.0001
            val lngVariation = (Math.random() - 0.5) * 0.0001

            LocationEntity(
                latitude = latitude + latVariation,
                longitude = longitude + lngVariation,
                timestamp = startTime.plusSeconds(intervalSeconds * i),
                accuracy = 8.0f + (Math.random() * 4).toFloat(),
                speed = 0.0f,
                altitude = 50.0 + (Math.random() * 10),
                bearing = 0.0f
            )
        }
    }

    private fun generateCircularPath(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Double,
        points: Int,
        startTime: LocalDateTime,
        intervalSeconds: Long
    ): List<LocationEntity> {
        return (0 until points).map { i ->
            val angle = (i.toDouble() / points) * 2 * Math.PI
            val offsetLat = (cos(angle) * radiusMeters) / 111111.0
            val offsetLng = (sin(angle) * radiusMeters) / (111111.0 * cos(Math.toRadians(centerLat)))

            LocationEntity(
                latitude = centerLat + offsetLat,
                longitude = centerLng + offsetLng,
                timestamp = startTime.plusSeconds(intervalSeconds * i),
                accuracy = 10.0f + (Math.random() * 5).toFloat(),
                speed = 1.5f + (Math.random() * 2).toFloat(),
                altitude = 50.0 + (Math.random() * 10),
                bearing = ((angle * 180 / Math.PI) % 360).toFloat()
            )
        }
    }

    private fun generateMovementPath(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        points: Int,
        startTime: LocalDateTime,
        intervalSeconds: Long
    ): List<LocationEntity> {
        return (0 until points).map { i ->
            val progress = i.toDouble() / (points - 1)
            val currentLat = fromLat + (toLat - fromLat) * progress
            val currentLng = fromLng + (toLng - fromLng) * progress

            // Add small random variation
            val latVariation = (Math.random() - 0.5) * 0.0002
            val lngVariation = (Math.random() - 0.5) * 0.0002

            LocationEntity(
                latitude = currentLat + latVariation,
                longitude = currentLng + lngVariation,
                timestamp = startTime.plusSeconds(intervalSeconds * i),
                accuracy = 12.0f + (Math.random() * 8).toFloat(),
                speed = 5.0f + (Math.random() * 10).toFloat(), // 5-15 m/s (walking to driving)
                altitude = 50.0 + (Math.random() * 20),
                bearing = Math.toDegrees(Math.atan2(toLng - fromLng, toLat - fromLat)).toFloat()
            )
        }
    }
}
