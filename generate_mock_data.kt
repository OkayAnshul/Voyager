#!/usr/bin/env kotlin

/**
 * Mock Data Generation Script for Voyager
 *
 * Usage:
 * 1. Run this as an Android instrumented test or
 * 2. Add to app as debug-only feature
 *
 * Generates realistic location data based on current GPS position
 */

import android.location.Location
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

data class MockPlace(
    val name: String,
    val category: String,
    val offsetLat: Double, // Offset from current location in degrees
    val offsetLng: Double,
    val visitDurationMinutes: Int
)

// Mock places within ~5km radius of current location
val mockPlaces = listOf(
    MockPlace("Home", "Residential", 0.001, 0.001, 120),
    MockPlace("Workplace", "Office", 0.005, 0.003, 480),
    MockPlace("Gym", "Fitness", -0.003, 0.002, 60),
    MockPlace("Grocery Store", "Shopping", 0.002, -0.004, 30),
    MockPlace("Cafe", "Restaurant", -0.002, -0.002, 45),
    MockPlace("Park", "Recreation", 0.004, -0.003, 90)
)

/**
 * Generate mock location data for the past 7 days
 *
 * @param currentLat Current GPS latitude
 * @param currentLng Current GPS longitude
 * @param daysBack Number of days to generate data for
 * @return List of LocationEntity objects
 */
fun generateMockData(
    currentLat: Double,
    currentLng: Double,
    daysBack: Int = 7
): List<MockLocationData> {
    val mockData = mutableListOf<MockLocationData>()
    val now = LocalDateTime.now()

    for (day in 0 until daysBack) {
        val dayStart = now.minusDays(day.toLong()).withHour(7).withMinute(0)
        var currentTime = dayStart

        // Generate visits to 3-5 places per day
        val placesToVisit = mockPlaces.shuffled().take(Random.nextInt(3, 6))

        placesToVisit.forEach { place ->
            val placeLat = currentLat + place.offsetLat
            val placeLng = currentLng + place.offsetLng

            // Generate location points during visit (every 5 minutes)
            val visitDuration = place.visitDurationMinutes
            var visitTime = currentTime

            for (minute in 0 until visitDuration step 5) {
                mockData.add(
                    MockLocationData(
                        latitude = placeLat + Random.nextDouble(-0.0001, 0.0001), // Small GPS noise
                        longitude = placeLng + Random.nextDouble(-0.0001, 0.0001),
                        accuracy = Random.nextFloat() * 20f + 5f, // 5-25m accuracy
                        timestamp = visitTime.toEpochSecond(ZoneOffset.UTC) * 1000,
                        altitude = Random.nextDouble(50.0, 150.0),
                        speed = 0f,
                        bearing = 0f,
                        provider = "gps"
                    )
                )
                visitTime = visitTime.plusMinutes(5)
            }

            // Add travel time (30-60 min gap between places)
            currentTime = visitTime.plusMinutes(Random.nextLong(30, 61))
        }
    }

    return mockData.sortedBy { it.timestamp }
}

data class MockLocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val provider: String
)

/**
 * Insert mock data into Voyager database
 *
 * Call this from an Android test or debug activity:
 *
 * ```kotlin
 * @Test
 * fun insertMockData() = runBlocking {
 *     val locationManager = context.getSystemService(LocationManager::class.java)
 *     val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
 *
 *     if (lastLocation != null) {
 *         val mockData = generateMockData(lastLocation.latitude, lastLocation.longitude)
 *
 *         mockData.forEach { mock ->
 *             locationRepository.insertLocation(
 *                 Location("mock").apply {
 *                     latitude = mock.latitude
 *                     longitude = mock.longitude
 *                     accuracy = mock.accuracy
 *                     time = mock.timestamp
 *                     altitude = mock.altitude
 *                     speed = mock.speed
 *                     bearing = mock.bearing
 *                 }
 *             )
 *             delay(10) // Prevent overwhelming the database
 *         }
 *
 *         println("Inserted ${mockData.size} mock locations")
 *     }
 * }
 * ```
 */
