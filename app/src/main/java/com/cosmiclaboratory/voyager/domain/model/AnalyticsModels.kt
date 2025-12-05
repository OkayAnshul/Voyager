package com.cosmiclaboratory.voyager.domain.model

data class DashboardState(
    val dailySummary: DailySummary?,
    val weeklyComparison: ComparisonResult?,
    val anomalies: List<Anomaly>,
    val insights: List<DashboardInsight>,
    val topPlaces: List<PlaceSummary>,
    val stepChart: List<HourlySteps>
)

data class DailySummary(
    val dayKey: String,
    val totalDistanceM: Double,
    val totalSteps: Int,
    val totalDwellMs: Long,
    val placeVisitCount: Int,
    val uniquePlacesVisited: Int,
    val dominantTransportMode: String?,
    val firstActivityAt: Long?,
    val lastActivityAt: Long?
)

data class DashboardInsight(
    val category: String,
    val title: String,
    val description: String,
    val metricValue: String?,
    val trend: Trend?
)

data class PlaceSummary(
    val placeId: Long,
    val displayName: String,
    val category: String,
    val visitCount: Int,
    val totalDwellMs: Long,
    val emoji: String? = null
)

data class HourlySteps(
    val hour: Int,
    val steps: Int
)

data class StepsSummary(
    val totalSteps: Int,
    val hourlyBreakdown: List<HourlySteps>,
    val goalProgress: Float?
)

data class StrideCalibration(
    val strideLengthM: Float,
    val sampleCount: Int,
    val confidence: Float
)

data class PlaceAnalytics(
    val placeId: Long,
    val totalVisitCount: Int,
    val totalDwellMs: Long,
    val avgDwellMs: Long,
    val dominantDayOfWeek: Int?,
    val dominantTimeOfDay: String?,
    val visitTrend: Trend?
)
