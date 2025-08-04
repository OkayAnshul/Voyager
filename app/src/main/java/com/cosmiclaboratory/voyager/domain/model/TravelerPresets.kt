package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.InsightCategory
import com.cosmiclaboratory.voyager.domain.model.enums.RouteColorMode
import com.cosmiclaboratory.voyager.domain.model.enums.RouteDetail
import com.cosmiclaboratory.voyager.domain.model.enums.TimelineGrouping

/**
 * Predefined traveler behavior profiles for common use cases.
 * Referenced by SettingsRepository.applyPreset() to configure
 * place detection, timeline, and insight settings in bulk.
 */
object TravelerPresets {

    val CITY_EXPLORER = TravelerBehaviorProfile(
        presetId = "CITY_EXPLORER",
        minDwellForVisitMs = 3 * 60_000L,           // 3 min — short café stops count
        minDwellForConfirmedVisitMs = 8 * 60_000L,   // 8 min
        placeRadiusM = 60,                            // tighter radius for dense urban POIs
        hdbscanMinClusterSize = 3,
        hdbscanEpsilonM = 40.0,
        transitStopMaxDwellMs = 5 * 60_000L,
        shortStopMaxDwellMs = 10 * 60_000L,
        accommodationMinDwellMs = 4 * 3_600_000L,    // 4 h
        routeDetailLevel = RouteDetail.DETAILED,
        showDistanceMilestones = false,
        milestoneIntervalKm = 5,
        routeColorMode = RouteColorMode.BY_TRANSPORT,
        tripDetectionEnabled = false,
        tripDistanceFromHomeKm = 50,
        tripMinDurationHours = 4,
        timelineGrouping = TimelineGrouping.BY_DAY,
        showTransitStopsOnTimeline = true,
        showShortStopsOnTimeline = true,
        insightsFocus = setOf(
            InsightCategory.PLACES_DISCOVERED,
            InsightCategory.DISTANCE,
            InsightCategory.TRANSPORT_MIX
        )
    )

    val SHORT_TRIPPER = TravelerBehaviorProfile(
        presetId = "SHORT_TRIPPER",
        minDwellForVisitMs = 5 * 60_000L,
        minDwellForConfirmedVisitMs = 15 * 60_000L,
        placeRadiusM = 100,
        hdbscanMinClusterSize = 4,
        hdbscanEpsilonM = 60.0,
        transitStopMaxDwellMs = 10 * 60_000L,
        shortStopMaxDwellMs = 20 * 60_000L,
        accommodationMinDwellMs = 5 * 3_600_000L,
        routeDetailLevel = RouteDetail.STANDARD,
        showDistanceMilestones = true,
        milestoneIntervalKm = 25,
        routeColorMode = RouteColorMode.BY_TRANSPORT,
        tripDetectionEnabled = true,
        tripDistanceFromHomeKm = 30,
        tripMinDurationHours = 4,
        timelineGrouping = TimelineGrouping.BY_TRIP,
        showTransitStopsOnTimeline = true,
        showShortStopsOnTimeline = false,
        insightsFocus = setOf(
            InsightCategory.TRIP_SUMMARY,
            InsightCategory.PLACES_DISCOVERED,
            InsightCategory.DISTANCE
        )
    )

    val LONG_TRAVELER = TravelerBehaviorProfile(
        presetId = "LONG_TRAVELER",
        minDwellForVisitMs = 10 * 60_000L,           // 10 min — ignore brief stops
        minDwellForConfirmedVisitMs = 30 * 60_000L,
        placeRadiusM = 150,                           // larger radius for less-precise GPS abroad
        hdbscanMinClusterSize = 5,
        hdbscanEpsilonM = 80.0,
        transitStopMaxDwellMs = 15 * 60_000L,
        shortStopMaxDwellMs = 30 * 60_000L,
        accommodationMinDwellMs = 6 * 3_600_000L,
        routeDetailLevel = RouteDetail.STANDARD,
        showDistanceMilestones = true,
        milestoneIntervalKm = 50,
        routeColorMode = RouteColorMode.BY_TRANSPORT,
        tripDetectionEnabled = true,
        tripDistanceFromHomeKm = 100,
        tripMinDurationHours = 24,
        timelineGrouping = TimelineGrouping.BY_CITY,
        showTransitStopsOnTimeline = false,
        showShortStopsOnTimeline = false,
        insightsFocus = setOf(
            InsightCategory.TRIP_SUMMARY,
            InsightCategory.DISTANCE,
            InsightCategory.ACHIEVEMENT
        )
    )

    val ROAD_TRIPPER = TravelerBehaviorProfile(
        presetId = "ROAD_TRIPPER",
        minDwellForVisitMs = 8 * 60_000L,
        minDwellForConfirmedVisitMs = 20 * 60_000L,
        placeRadiusM = 120,
        hdbscanMinClusterSize = 4,
        hdbscanEpsilonM = 70.0,
        transitStopMaxDwellMs = 10 * 60_000L,
        shortStopMaxDwellMs = 20 * 60_000L,
        accommodationMinDwellMs = 5 * 3_600_000L,
        routeDetailLevel = RouteDetail.DETAILED,      // full route trace matters
        showDistanceMilestones = true,
        milestoneIntervalKm = 50,
        routeColorMode = RouteColorMode.BY_SPEED,
        tripDetectionEnabled = true,
        tripDistanceFromHomeKm = 50,
        tripMinDurationHours = 6,
        timelineGrouping = TimelineGrouping.BY_TRIP,
        showTransitStopsOnTimeline = false,
        showShortStopsOnTimeline = true,               // gas stations, rest stops
        insightsFocus = setOf(
            InsightCategory.DISTANCE,
            InsightCategory.TRIP_SUMMARY,
            InsightCategory.ACHIEVEMENT
        )
    )

    val TRANSIT_COMMUTER = TravelerBehaviorProfile(
        presetId = "TRANSIT_COMMUTER",
        minDwellForVisitMs = 5 * 60_000L,
        minDwellForConfirmedVisitMs = 10 * 60_000L,
        placeRadiusM = 80,
        hdbscanMinClusterSize = 4,
        hdbscanEpsilonM = 50.0,
        transitStopMaxDwellMs = 3 * 60_000L,
        shortStopMaxDwellMs = 10 * 60_000L,
        accommodationMinDwellMs = 5 * 3_600_000L,
        routeDetailLevel = RouteDetail.STANDARD,
        showDistanceMilestones = false,
        milestoneIntervalKm = 10,
        routeColorMode = RouteColorMode.BY_TRANSPORT,
        tripDetectionEnabled = false,
        tripDistanceFromHomeKm = 50,
        tripMinDurationHours = 8,
        timelineGrouping = TimelineGrouping.BY_DAY,
        showTransitStopsOnTimeline = true,
        showShortStopsOnTimeline = false,
        insightsFocus = setOf(
            InsightCategory.COMMUTE,
            InsightCategory.ROUTINE,
            InsightCategory.TRANSPORT_MIX
        )
    )

    val BACKPACKER = TravelerBehaviorProfile(
        presetId = "BACKPACKER",
        minDwellForVisitMs = 10 * 60_000L,
        minDwellForConfirmedVisitMs = 20 * 60_000L,
        placeRadiusM = 130,
        hdbscanMinClusterSize = 4,
        hdbscanEpsilonM = 70.0,
        transitStopMaxDwellMs = 15 * 60_000L,
        shortStopMaxDwellMs = 30 * 60_000L,
        accommodationMinDwellMs = 4 * 3_600_000L,
        routeDetailLevel = RouteDetail.MINIMAL,        // save battery
        showDistanceMilestones = true,
        milestoneIntervalKm = 10,
        routeColorMode = RouteColorMode.SOLID,
        tripDetectionEnabled = true,
        tripDistanceFromHomeKm = 80,
        tripMinDurationHours = 12,
        timelineGrouping = TimelineGrouping.BY_CITY,
        showTransitStopsOnTimeline = false,
        showShortStopsOnTimeline = false,
        insightsFocus = setOf(
            InsightCategory.PLACES_DISCOVERED,
            InsightCategory.STEPS,
            InsightCategory.TRIP_SUMMARY,
            InsightCategory.ACHIEVEMENT
        )
    )

    /** Look up a preset by ID, returning null for unknown IDs. */
    fun forId(presetId: String): TravelerBehaviorProfile? = when (presetId) {
        "CITY_EXPLORER" -> CITY_EXPLORER
        "SHORT_TRIPPER" -> SHORT_TRIPPER
        "LONG_TRAVELER" -> LONG_TRAVELER
        "ROAD_TRIPPER" -> ROAD_TRIPPER
        "TRANSIT_COMMUTER" -> TRANSIT_COMMUTER
        "BACKPACKER" -> BACKPACKER
        else -> null
    }

    /** All available traveler presets. */
    val all: List<TravelerBehaviorProfile> = listOf(
        CITY_EXPLORER, SHORT_TRIPPER, LONG_TRAVELER,
        ROAD_TRIPPER, TRANSIT_COMMUTER, BACKPACKER
    )
}
