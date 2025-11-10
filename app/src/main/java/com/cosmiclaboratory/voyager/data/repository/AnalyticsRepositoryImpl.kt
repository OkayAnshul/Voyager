package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity
import com.cosmiclaboratory.voyager.domain.repository.AnalyticsRepository
import com.cosmiclaboratory.voyager.storage.database.dao.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val dailyRollupDao: DailyRollupDao,
    private val weeklyRollupDao: WeeklyRollupDao,
    private val placeRollupDao: PlaceRollupDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val rawStepSampleDao: RawStepSampleDao
) : AnalyticsRepository {

    override fun observeDashboard(range: DateRange): Flow<DashboardState> {
        return combine(
            dailyRollupDao.observeByRange(range.startDay, range.endDay),
            movementSegmentDao.observeByDayKey(range.startDay),
            visitDao.observeByDayKey(range.startDay)
        ) { rollups, segments, visits ->
            // If we have a rollup from DB, use it. Otherwise compute live from segments/visits.
            val dbRollup = rollups.lastOrNull()
            val dailySummary = if (dbRollup != null) {
                DailySummary(
                    dayKey = dbRollup.dayKey,
                    totalDistanceM = dbRollup.totalDistanceM,
                    totalSteps = dbRollup.totalSteps,
                    totalDwellMs = dbRollup.totalDwellMs,
                    placeVisitCount = dbRollup.placeVisitCount,
                    uniquePlacesVisited = dbRollup.uniquePlacesVisited,
                    dominantTransportMode = dbRollup.dominantTransportMode,
                    firstActivityAt = dbRollup.firstActivityAt,
                    lastActivityAt = dbRollup.lastActivityAt
                )
            } else {
                // Live computation from today's segments and visits
                computeLiveDailySummary(range.startDay, segments, visits)
            }

            // Top places from today's visits
            val topPlaces = visits
                .filter { it.placeId != 0L }
                .groupBy { it.placeId }
                .mapNotNull { (placeId, placeVisits) ->
                    val place = placeDao.getById(placeId) ?: return@mapNotNull null
                    PlaceSummary(
                        placeId = placeId,
                        displayName = place.userDisplayName ?: place.bestProviderName ?: "Unknown",
                        category = place.category,
                        visitCount = placeVisits.size,
                        totalDwellMs = placeVisits.sumOf { it.dwellMs ?: 0L },
                        emoji = place.emoji
                    )
                }
                .sortedByDescending { it.totalDwellMs }
                .take(5)

            // Generate insights from daily data
            val insights = buildList {
                if (dailySummary != null) {
                    if (dailySummary.uniquePlacesVisited > 3) {
                        add(DashboardInsight(
                            category = "exploration",
                            title = "Active Explorer",
                            description = "You visited ${dailySummary.uniquePlacesVisited} unique places today",
                            metricValue = "${dailySummary.uniquePlacesVisited}",
                            trend = Trend.UP
                        ))
                    }
                    if (dailySummary.totalDistanceM > 5000) {
                        add(DashboardInsight(
                            category = "movement",
                            title = "On the Move",
                            description = "You traveled %.1f km today".format(dailySummary.totalDistanceM / 1000),
                            metricValue = "%.1f km".format(dailySummary.totalDistanceM / 1000),
                            trend = null
                        ))
                    }
                    if (dailySummary.totalSteps > 8000) {
                        add(DashboardInsight(
                            category = "fitness",
                            title = "Step Goal Progress",
                            description = "Great job! ${dailySummary.totalSteps} steps and counting",
                            metricValue = "${dailySummary.totalSteps}",
                            trend = Trend.UP
                        ))
                    }
                }
            }

            // Anomalies from recent data
            val recentRollups = dailyRollupDao.getRecent(14)
            val anomalies = if (recentRollups.size >= 7) {
                val distances = recentRollups.map { it.totalDistanceM }
                val mean = distances.average()
                val stdDev = kotlin.math.sqrt(distances.map { (it - mean) * (it - mean) }.average())
                recentRollups.filter { r ->
                    stdDev > 0 && kotlin.math.abs(r.totalDistanceM - mean) > 2 * stdDev
                }.map { r ->
                    val sigma = ((r.totalDistanceM - mean) / stdDev).toFloat()
                    Anomaly(
                        metricKey = "distance",
                        observedValue = r.totalDistanceM,
                        baselineMean = mean,
                        baselineStdDev = stdDev,
                        deviationSigma = sigma,
                        severity = if (kotlin.math.abs(sigma) > 3) AnomalySeverity.SIGNIFICANT else AnomalySeverity.NOTABLE,
                        baselinePeriod = range,
                        impactedDay = r.dayKey,
                        humanExplanation = "Distance ${if (sigma > 0) "significantly above" else "significantly below"} average"
                    )
                }.take(3)
            } else emptyList()

            DashboardState(
                dailySummary = dailySummary,
                weeklyComparison = null,
                anomalies = anomalies,
                insights = insights,
                topPlaces = topPlaces,
                stepChart = emptyList()
            )
        }
    }

    /**
     * Compute a live DailySummary from today's segments and visits when no rollup exists yet.
     */
    private suspend fun computeLiveDailySummary(
        dayKey: String,
        segments: List<com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity>,
        visits: List<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity>
    ): DailySummary? {
        if (segments.isEmpty() && visits.isEmpty()) return null

        // Only sum distance from movement segments (exclude VISIT/GAP GPS jitter)
        val totalDistanceM = segments
            .filter { it.segmentType != "VISIT" && it.segmentType != "GAP" }
            .sumOf { it.distanceM }
        val totalDwellMs = visits.sumOf { it.dwellMs ?: 0L }
        val uniquePlaces = visits.filter { it.placeId != 0L }.map { it.placeId }.toSet().size

        // Compute steps from raw step samples for today
        val dayStart = try {
            java.time.LocalDate.parse(dayKey)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } catch (_: Exception) { 0L }
        val dayEnd = dayStart + 86_400_000L
        val totalSteps = rawStepSampleDao.sumStepsByTimeRange(dayStart, dayEnd) ?: 0

        // Determine dominant transport mode from movement segments only
        val movementSegments = segments.filter {
            it.segmentType != "VISIT" && it.segmentType != "GAP" && it.segmentType != "UNKNOWN_MOTION"
        }
        val modeGroups = movementSegments.groupBy { it.segmentType }
        val dominantMode = modeGroups.maxByOrNull { (_, segs) -> segs.sumOf { it.distanceM } }?.key

        return DailySummary(
            dayKey = dayKey,
            totalDistanceM = totalDistanceM,
            totalSteps = totalSteps,
            totalDwellMs = totalDwellMs,
            placeVisitCount = visits.size,
            uniquePlacesVisited = uniquePlaces,
            dominantTransportMode = dominantMode,
            firstActivityAt = segments.minOfOrNull { it.startAt },
            lastActivityAt = segments.maxOfOrNull { it.endAt }
        )
    }

    override fun observeComparisons(periodA: DateRange, periodB: DateRange): Flow<ComparisonResult> = flow {
        val rollupsA = dailyRollupDao.getByRange(periodA.startDay, periodA.endDay)
        val rollupsB = dailyRollupDao.getByRange(periodB.startDay, periodB.endDay)

        val distA = rollupsA.sumOf { it.totalDistanceM }
        val distB = rollupsB.sumOf { it.totalDistanceM }
        val stepsA = rollupsA.sumOf { it.totalSteps.toDouble() }
        val stepsB = rollupsB.sumOf { it.totalSteps.toDouble() }

        fun delta(a: Double, b: Double) = MetricDelta(
            valueA = a, valueB = b,
            absoluteDelta = a - b,
            percentDelta = if (b != 0.0) ((a - b) / b) * 100 else 0.0,
            trend = when { a > b -> Trend.UP; a < b -> Trend.DOWN; else -> Trend.STABLE }
        )

        emit(ComparisonResult(
            periodA = periodA,
            periodB = periodB,
            metricDeltas = mapOf(
                "distance" to delta(distA, distB),
                "steps" to delta(stepsA, stepsB)
            ),
            highlights = emptyList(),
            confidence = 0.8f
        ))
    }

    override fun observeAnomalies(range: DateRange): Flow<List<Anomaly>> = flow {
        val rollups = dailyRollupDao.getByRange(range.startDay, range.endDay)
        if (rollups.size < 7) {
            emit(emptyList())
            return@flow
        }
        val distances = rollups.map { it.totalDistanceM }
        val mean = distances.average()
        val stdDev = kotlin.math.sqrt(distances.map { (it - mean) * (it - mean) }.average())

        val anomalies = rollups.filter { r ->
            stdDev > 0 && kotlin.math.abs(r.totalDistanceM - mean) > 2 * stdDev
        }.map { r ->
            val sigma = ((r.totalDistanceM - mean) / stdDev).toFloat()
            Anomaly(
                metricKey = "distance",
                observedValue = r.totalDistanceM,
                baselineMean = mean,
                baselineStdDev = stdDev,
                deviationSigma = sigma,
                severity = if (kotlin.math.abs(sigma) > 3) AnomalySeverity.SIGNIFICANT else AnomalySeverity.NOTABLE,
                baselinePeriod = range,
                impactedDay = r.dayKey,
                humanExplanation = "Distance ${if (sigma > 0) "significantly above" else "significantly below"} average"
            )
        }
        emit(anomalies)
    }

    override suspend fun getPlaceAnalytics(placeId: Long): PlaceAnalytics? {
        val rollup = placeRollupDao.getByPlaceId(placeId) ?: return null
        val place = placeDao.getById(placeId) ?: return null
        return PlaceAnalytics(
            placeId = placeId,
            totalVisitCount = rollup.totalVisitCount,
            totalDwellMs = rollup.totalDwellMs,
            avgDwellMs = rollup.avgDwellMs,
            dominantDayOfWeek = rollup.dominantDayOfWeek,
            dominantTimeOfDay = null,
            visitTrend = null
        )
    }
}
