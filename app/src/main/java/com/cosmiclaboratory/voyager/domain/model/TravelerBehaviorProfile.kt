package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.InsightCategory
import com.cosmiclaboratory.voyager.domain.model.enums.RouteColorMode
import com.cosmiclaboratory.voyager.domain.model.enums.RouteDetail
import com.cosmiclaboratory.voyager.domain.model.enums.TimelineGrouping

data class TravelerBehaviorProfile(
    val presetId: String,
    val minDwellForVisitMs: Long,
    val minDwellForConfirmedVisitMs: Long,
    val placeRadiusM: Int,
    val hdbscanMinClusterSize: Int,
    val hdbscanEpsilonM: Double,
    val transitStopMaxDwellMs: Long,
    val shortStopMaxDwellMs: Long,
    val accommodationMinDwellMs: Long,
    val routeDetailLevel: RouteDetail,
    val showDistanceMilestones: Boolean,
    val milestoneIntervalKm: Int,
    val routeColorMode: RouteColorMode,
    val tripDetectionEnabled: Boolean,
    val tripDistanceFromHomeKm: Int,
    val tripMinDurationHours: Int,
    val timelineGrouping: TimelineGrouping,
    val showTransitStopsOnTimeline: Boolean,
    val showShortStopsOnTimeline: Boolean,
    val insightsFocus: Set<InsightCategory>
)
