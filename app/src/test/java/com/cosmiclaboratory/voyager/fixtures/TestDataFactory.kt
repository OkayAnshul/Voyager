package com.cosmiclaboratory.voyager.fixtures

import com.cosmiclaboratory.voyager.domain.model.*
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * Factory for creating test data
 * Use this to inject realistic data into tests
 */
object TestDataFactory {

    /**
     * Generate realistic location points along a circular path
     * Simulates a person walking/moving in an area
     */
    fun generateLocationPath(
        startLat: Double = 37.7749,
        startLng: Double = -122.4194,
        points: Int = 100,
        timeIntervalSeconds: Long = 30,
        radiusMeters: Double = 500.0,
        startTime: LocalDateTime = LocalDateTime.now().minusHours(1)
    ): List<Location> {
        return (0 until points).map { i ->
            val angle = (i.toDouble() / points) * 2 * Math.PI
            val offsetLat = (cos(angle) * radiusMeters) / 111111.0
            val offsetLng = (sin(angle) * radiusMeters) / (111111.0 * cos(Math.toRadians(startLat)))

            Location(
                id = i.toLong() + 1,
                latitude = startLat + offsetLat,
                longitude = startLng + offsetLng,
                timestamp = startTime.plusSeconds(timeIntervalSeconds * i),
                accuracy = 10.0f + (Math.random() * 5).toFloat(),
                speed = 1.5f + (Math.random() * 2).toFloat(),
                altitude = 50.0 + (Math.random() * 10),
                bearing = (angle * 180 / Math.PI).toFloat()
            )
        }
    }

    /**
     * Generate stationary location points (simulating user at a place)
     */
    fun generateStationaryLocations(
        latitude: Double = 37.7749,
        longitude: Double = -122.4194,
        points: Int = 50,
        timeIntervalSeconds: Long = 60,
        startTime: LocalDateTime = LocalDateTime.now().minusHours(2)
    ): List<Location> {
        return (0 until points).map { i ->
            // Small random variation to simulate GPS drift
            val latVariation = (Math.random() - 0.5) * 0.0001
            val lngVariation = (Math.random() - 0.5) * 0.0001

            Location(
                id = i.toLong() + 1,
                latitude = latitude + latVariation,
                longitude = longitude + lngVariation,
                timestamp = startTime.plusSeconds(timeIntervalSeconds * i),
                accuracy = 8.0f + (Math.random() * 4).toFloat(),
                speed = 0.0f, // Stationary
                altitude = 50.0,
                bearing = 0.0f
            )
        }
    }

    /**
     * Generate a place with realistic data
     */
    fun createPlace(
        id: Long = 1,
        name: String = "Test Coffee Shop",
        category: PlaceCategory = PlaceCategory.FOOD,
        latitude: Double = 37.7749,
        longitude: Double = -122.4194,
        address: String = "123 Market St, San Francisco, CA",
        visitCount: Int = 5,
        totalTimeSpent: Long = 18000000L // 5 hours
    ) = Place(
        id = id,
        name = name,
        category = category,
        latitude = latitude,
        longitude = longitude,
        address = address,
        streetName = "Market St",
        city = "San Francisco",
        state = "CA",
        country = "USA",
        radius = 50.0,
        confidence = 0.85,
        visitCount = visitCount,
        totalTimeSpent = totalTimeSpent,
        lastVisit = LocalDateTime.now().minusHours(2),
        createdAt = LocalDateTime.now().minusDays(30),
        placeId = null,
        photoReference = null
    )

    /**
     * Generate a completed visit
     */
    fun createVisit(
        id: Long = 1,
        placeId: Long = 1,
        entryTime: LocalDateTime = LocalDateTime.now().minusHours(2),
        exitTime: LocalDateTime? = LocalDateTime.now().minusHours(1),
        duration: Long = 3600000L // 1 hour
    ) = Visit(
        id = id,
        placeId = placeId,
        entryTime = entryTime,
        exitTime = exitTime,
        duration = duration
    )

    /**
     * Generate an ongoing visit (no exit time)
     */
    fun createOngoingVisit(
        id: Long = 1,
        placeId: Long = 1,
        entryTime: LocalDateTime = LocalDateTime.now().minusMinutes(30)
    ) = Visit(
        id = id,
        placeId = placeId,
        entryTime = entryTime,
        exitTime = null,
        duration = java.time.Duration.between(entryTime, LocalDateTime.now()).toMillis()
    )

    /**
     * Generate a full day of realistic location data
     * Simulates: home → commute → work → lunch → work → commute → home
     */
    fun generateFullDayLocationData(date: LocalDateTime = LocalDateTime.now()): List<Location> {
        val locations = mutableListOf<Location>()
        val startOfDay = date.toLocalDate().atStartOfDay()

        // 6-7 AM: At home (stationary)
        locations.addAll(generateStationaryLocations(
            latitude = 37.7749,
            longitude = -122.4194,
            points = 12,
            timeIntervalSeconds = 300, // Every 5 minutes
            startTime = startOfDay.plusHours(6)
        ))

        // 7-8 AM: Morning commute (moving)
        locations.addAll(generateLocationPath(
            startLat = 37.7749,
            startLng = -122.4194,
            points = 40,
            timeIntervalSeconds = 90,
            radiusMeters = 3000.0,
            startTime = startOfDay.plusHours(7)
        ))

        // 8 AM - 12 PM: At work (stationary)
        locations.addAll(generateStationaryLocations(
            latitude = 37.7849,
            longitude = -122.4094,
            points = 48,
            timeIntervalSeconds = 300,
            startTime = startOfDay.plusHours(8)
        ))

        // 12-1 PM: Lunch (moving to restaurant)
        locations.addAll(generateLocationPath(
            startLat = 37.7849,
            startLng = -122.4094,
            points = 20,
            timeIntervalSeconds = 180,
            radiusMeters = 500.0,
            startTime = startOfDay.plusHours(12)
        ))

        // 1-5 PM: Back at work
        locations.addAll(generateStationaryLocations(
            latitude = 37.7849,
            longitude = -122.4094,
            points = 48,
            timeIntervalSeconds = 300,
            startTime = startOfDay.plusHours(13)
        ))

        // 5-6 PM: Evening commute
        locations.addAll(generateLocationPath(
            startLat = 37.7849,
            startLng = -122.4094,
            points = 40,
            timeIntervalSeconds = 90,
            radiusMeters = 3000.0,
            startTime = startOfDay.plusHours(17)
        ))

        // 6 PM - midnight: At home
        val hoursAtHome = 6
        locations.addAll(generateStationaryLocations(
            latitude = 37.7749,
            longitude = -122.4194,
            points = hoursAtHome * 10,
            timeIntervalSeconds = 360,
            startTime = startOfDay.plusHours(18)
        ))

        return locations
    }

    /**
     * Generate multiple days of location data
     */
    fun generateMultipleDaysData(days: Int = 7): List<Location> {
        val allLocations = mutableListOf<Location>()
        repeat(days) { dayIndex ->
            val date = LocalDateTime.now().minusDays(dayIndex.toLong())
            allLocations.addAll(generateFullDayLocationData(date))
        }
        return allLocations
    }

    /**
     * Generate visits for testing analytics
     */
    fun generateVisitsForAnalytics(
        placeId: Long = 1,
        count: Int = 20,
        startDate: LocalDateTime = LocalDateTime.now().minusDays(30)
    ): List<Visit> {
        return (0 until count).map { i ->
            val dayOffset = i * (30 / count) // Spread across 30 days
            val entryTime = startDate.plusDays(dayOffset.toLong()).plusHours(9)
            val duration = (3600000L + (Math.random() * 7200000).toLong()) // 1-3 hours

            createVisit(
                id = i.toLong() + 1,
                placeId = placeId,
                entryTime = entryTime,
                exitTime = entryTime.plusSeconds(duration / 1000),
                duration = duration
            )
        }
    }

    /**
     * Create user preferences for testing
     */
    fun createUserPreferences(
        enablePlaceDetection: Boolean = true,
        trackingAccuracyMode: TrackingAccuracyMode = TrackingAccuracyMode.BALANCED,
        maxGpsAccuracyMeters: Float = 50.0f,
        autoDetectTriggerCount: Int = 50
    ) = UserPreferences(
        enablePlaceDetection = enablePlaceDetection,
        trackingAccuracyMode = trackingAccuracyMode,
        maxGpsAccuracyMeters = maxGpsAccuracyMeters,
        autoDetectTriggerCount = autoDetectTriggerCount
    )
}
