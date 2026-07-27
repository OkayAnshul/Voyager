package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity
import com.cosmiclaboratory.voyager.domain.repository.AnalyticsRepository
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.storage.database.dao.*
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
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
    private val rawStepSampleDao: RawStepSampleDao,
    private val geocodingRepository: GeocodingRepository
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
                        displayName = geocodingRepository.resolveDisplayName(placeId),
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

            // Anomalies from recent data — across all tracked metrics.
            val recentRollups = dailyRollupDao.getRecent(14)
            val anomalies = detectAnomalies(recentRollups, range, limit = 3)

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
        emit(detectAnomalies(rollups, range, limit = 6))
    }

    /**
     * Statistical outliers across every tracked daily metric — not just distance.
     * For each metric we z-score the day against the window's mean/σ and flag days
     * beyond 2σ, so "you barely moved" or "record steps" surface too. Ranked by how
     * far out they sit and capped so the list stays a highlight reel, not noise.
     */
    private fun detectAnomalies(
        rollups: List<DailyRollupEntity>,
        baselinePeriod: DateRange,
        limit: Int
    ): List<Anomaly> {
        if (rollups.size < 7) return emptyList()
        val out = mutableListOf<Anomaly>()
        for (spec in ANOMALY_METRICS) {
            val values = rollups.map(spec.extract)
            val mean = values.average()
            val stdDev = kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average())
            if (stdDev <= 0.0) continue
            for (r in rollups) {
                val value = spec.extract(r)
                if (kotlin.math.abs(value - mean) <= 2 * stdDev) continue
                val sigma = ((value - mean) / stdDev).toFloat()
                out += Anomaly(
                    metricKey = spec.key,
                    observedValue = value,
                    baselineMean = mean,
                    baselineStdDev = stdDev,
                    deviationSigma = sigma,
                    severity = if (kotlin.math.abs(sigma) > 3) AnomalySeverity.SIGNIFICANT else AnomalySeverity.NOTABLE,
                    baselinePeriod = baselinePeriod,
                    impactedDay = r.dayKey,
                    humanExplanation = "${spec.label} ${if (sigma > 0) "well above" else "well below"} your average"
                )
            }
        }
        return out.sortedByDescending { kotlin.math.abs(it.deviationSigma) }.take(limit)
    }

    /**
     * Place analytics computed live from the visit history.
     *
     * The `place_rollups` cache is never populated in this build, so reading it
     * returned null and the place-detail "Temporal Pattern" / "Visit trend"
     * lines never appeared. Deriving straight from `visits` makes those honest
     * and always-current. A `place_rollups` writer can wrap this later without
     * changing the call site.
     *
     * `dominantDayOfWeek` / `dominantTimeOfDay` are only asserted when the
     * history is deep enough and the mode is a genuine plurality — otherwise
     * null, so the UI never over-claims a pattern that isn't there.
     */
    override suspend fun getPlaceAnalytics(placeId: Long): PlaceAnalytics? {
        placeDao.getById(placeId) ?: return null
        val visits = visitDao.getByPlaceId(placeId)
        if (visits.isEmpty()) return null

        val zone = java.time.ZoneId.systemDefault()
        val totalVisitCount = visits.size
        val dwells = visits.mapNotNull { it.dwellMs }.filter { it > 0 }
        val totalDwellMs = dwells.sum()
        val avgDwellMs = if (dwells.isNotEmpty()) totalDwellMs / dwells.size else 0L

        val dayCounts = IntArray(8) // ISO day-of-week: index 1..7 (Mon..Sun)
        val bucketCounts = HashMap<String, Int>()
        for (v in visits) {
            val zdt = java.time.Instant.ofEpochMilli(v.arrivalAt).atZone(zone)
            dayCounts[zdt.dayOfWeek.value]++
            val bucket = timeOfDayBucket(zdt.hour)
            bucketCounts[bucket] = (bucketCounts[bucket] ?: 0) + 1
        }

        val dominantDayOfWeek = if (totalVisitCount >= MIN_TEMPORAL_VISITS) {
            val top = (1..7).maxByOrNull { dayCounts[it] } ?: 0
            val topCount = dayCounts[top]
            val second = (1..7).filter { it != top }.maxOfOrNull { dayCounts[it] } ?: 0
            // A single-day habit (e.g. a weekly class), not a smear across the week.
            if (topCount >= totalVisitCount * 0.30 && topCount >= second * 1.5) top else null
        } else null

        val dominantTimeOfDay = if (totalVisitCount >= MIN_TEMPORAL_VISITS) {
            val top = bucketCounts.maxByOrNull { it.value }
            if (top != null && top.value >= totalVisitCount * 0.40) top.key else null
        } else null

        return PlaceAnalytics(
            placeId = placeId,
            totalVisitCount = totalVisitCount,
            totalDwellMs = totalDwellMs,
            avgDwellMs = avgDwellMs,
            dominantDayOfWeek = dominantDayOfWeek,
            dominantTimeOfDay = dominantTimeOfDay,
            visitTrend = computeVisitTrend(visits)
        )
    }

    /** Coarse arrival-time bucket, phrased to read as "Usually in the {bucket}". */
    private fun timeOfDayBucket(hour: Int): String = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "night"
    }

    /**
     * Is the user visiting this place more, less, or about the same lately?
     * Compares the last 7 days against the weekly rate over the preceding 3
     * weeks. Null when there isn't enough baseline to say anything honest.
     */
    private fun computeVisitTrend(
        visits: List<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity>
    ): Trend? {
        if (visits.size < MIN_TEMPORAL_VISITS) return null
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val last7 = visits.count { it.arrivalAt >= now - 7 * day }
        val prior21 = visits.count { it.arrivalAt in (now - 28 * day) until (now - 7 * day) }
        val priorWeekly = prior21 / 3.0
        return when {
            priorWeekly < 0.5 -> if (last7 >= 2) Trend.UP else null // newly frequent, or too thin to call
            last7 >= priorWeekly * 1.25 -> Trend.UP
            last7 <= priorWeekly * 0.75 -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    private data class AnomalyMetric(
        val key: String,
        val label: String,
        val extract: (DailyRollupEntity) -> Double
    )

    companion object {
        /** Minimum visits before we assert any temporal pattern for a place. */
        private const val MIN_TEMPORAL_VISITS = 4

        /** Daily metrics scanned for statistical outliers (was distance-only). */
        private val ANOMALY_METRICS = listOf(
            AnomalyMetric("distance", "Distance") { it.totalDistanceM },
            AnomalyMetric("steps", "Steps") { it.totalSteps.toDouble() },
            AnomalyMetric("places", "Places visited") { it.uniquePlacesVisited.toDouble() },
            AnomalyMetric("dwell", "Time at places") { it.totalDwellMs.toDouble() },
        )
    }
}
