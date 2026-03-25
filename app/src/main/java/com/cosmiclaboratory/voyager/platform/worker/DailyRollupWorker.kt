package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.platform.notification.VoyagerNotificationManager
import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Computes DailyRollupEntity from segments/visits for the previous day.
 * Scheduled daily at 03:00 via WorkerScheduler.
 */
@HiltWorker
class DailyRollupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val dailyRollupDao: DailyRollupDao,
    private val healthLogDao: HealthLogDao,
    private val notificationManager: VoyagerNotificationManager,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "daily_rollup"
    }

    override suspend fun doWork(): Result {
        return try {
            val yesterdayKey = computeYesterdayKey()

            // Idempotent: recompute even if rollup already exists
            val segments = movementSegmentDao.getByDayKey(yesterdayKey)
            val visits = visitDao.getByDayKey(yesterdayKey)

            val totalDistanceM = segments
                .filter { it.segmentType != SegmentType.VISIT.name && it.segmentType != SegmentType.GAP.name }
                .sumOf { it.distanceM }
            val totalDwellMs = visits.sumOf { it.dwellMs ?: 0L }

            val transitSegments = segments.filter { it.segmentType != SegmentType.VISIT.name && it.segmentType != SegmentType.GAP.name }
            val totalTransitMs = transitSegments.sumOf { it.endAt - it.startAt }
            val totalWalkMs = segments.filter { it.segmentType == SegmentType.WALK.name }.sumOf { it.endAt - it.startAt }
            val totalDriveMs = segments.filter { it.segmentType == SegmentType.DRIVE.name }.sumOf { it.endAt - it.startAt }

            val uniquePlaceIds = visits.map { it.placeId }.toSet()

            val firstActivityAt = segments.minOfOrNull { it.startAt }
            val lastActivityAt = segments.maxOfOrNull { it.endAt }

            // Determine dominant transport mode by duration
            val modeDurations = transitSegments.groupBy { it.segmentType }
                .mapValues { (_, segs) -> segs.sumOf { it.endAt - it.startAt } }
            val dominantMode = modeDurations.maxByOrNull { it.value }?.key

            val rollup = DailyRollupEntity(
                dayKey = yesterdayKey,
                totalDistanceM = totalDistanceM,
                totalSteps = 0, // Steps are handled by StepSyncWorker; merged at read time
                totalDwellMs = totalDwellMs,
                totalTransitMs = totalTransitMs,
                totalWalkMs = totalWalkMs,
                totalDriveMs = totalDriveMs,
                placeVisitCount = visits.size,
                uniquePlacesVisited = uniquePlaceIds.size,
                firstActivityAt = firstActivityAt,
                lastActivityAt = lastActivityAt,
                dominantTransportMode = dominantMode,
                computedAt = System.currentTimeMillis(),
            )

            dailyRollupDao.upsert(rollup)

            sendDailySummaryNotification(rollup)
            logCompletion(yesterdayKey, segments.size, visits.size)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private fun computeYesterdayKey(): String = LocalDate.now().minusDays(1).toString()

    private fun sendDailySummaryNotification(rollup: DailyRollupEntity) {
        if (rollup.uniquePlacesVisited == 0 && rollup.totalDistanceM < 100.0) return
        val km = rollup.totalDistanceM / 1000.0
        val walkMin = rollup.totalWalkMs / 60_000L
        val body = buildString {
            append("${rollup.uniquePlacesVisited} place${if (rollup.uniquePlacesVisited != 1) "s" else ""}")
            if (km >= 0.1) append(" · %.1f km".format(km))
            if (walkMin >= 1) append(" · ${walkMin} min walking")
        }
        notificationManager.showInsight("Yesterday at a glance", body)
    }

    private suspend fun logCompletion(dayKey: String, segmentCount: Int, visitCount: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"${WORK_NAME}","dayKey":"$dayKey","segments":$segmentCount,"visits":$visitCount}""",
            )
        )
    }

    private suspend fun logFailure(e: Exception) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HealthEventTypeWorkerFailure,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"${WORK_NAME}","error":"${e.message?.take(200)}"}""",
            )
        )
    }
}
