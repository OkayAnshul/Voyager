package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.usecase.IntegrityRepairUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * Integrity check and repair worker.
 *
 * Checks:
 * 1. Visit non-overlap: no two visits for the same day overlap in time
 * 2. Orphan segments: segments referencing a routeId that no longer exists
 * 3. Orphan routes: routes whose segmentId no longer maps to a segment
 *
 * Fixes issues where possible, or logs them for manual review.
 * Scheduled daily at 05:00 via WorkerScheduler.
 */
@HiltWorker
class IntegrityRepairWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val routeDao: RouteDao,
    private val healthLogDao: HealthLogDao,
    private val integrityRepairUseCase: IntegrityRepairUseCase,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "integrity_repair"
        private const val LOOKBACK_DAYS = 30
    }

    override suspend fun doWork(): Result {
        return try {
            var overlapIssues = 0
            var orphanSegments = 0
            var orphanRoutes = 0
            var staleVisitsClosed = 0
            var fixed = 0

            // Check the last LOOKBACK_DAYS days
            val dayKeys = buildRecentDayKeys()

            // 0. Close stale open visits (departureAt=null, arrivalAt > 24h ago)
            val cutoffMs = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            staleVisitsClosed = integrityRepairUseCase.closeStaleVisits(cutoffMs)
            fixed += staleVisitsClosed

            // 1. Visit non-overlap check (delegated to shared use case)
            for (dayKey in dayKeys) {
                val dayFixed = integrityRepairUseCase.repairDay(dayKey)
                overlapIssues += dayFixed
                fixed += dayFixed
            }

            // 2. Orphan segments (referencing non-existent routes)
            for (dayKey in dayKeys) {
                val segments = movementSegmentDao.getByDayKey(dayKey)
                for (segment in segments) {
                    val routeId = segment.routeId ?: continue
                    val route = routeDao.getById(routeId)
                    if (route == null) {
                        // Clear the dangling routeId reference
                        movementSegmentDao.update(segment.copy(routeId = null))
                        orphanSegments++
                        fixed++
                    }
                }
            }

            // 3. Orphan routes (pointing to non-existent segments)
            // We check routes for recent segments; since RouteDao has getBySegmentId,
            // we check each segment's route in reverse direction
            for (dayKey in dayKeys) {
                val segments = movementSegmentDao.getByDayKey(dayKey)
                val segmentIds = segments.map { it.segmentId }.toSet()
                for (segment in segments) {
                    val routeId = segment.routeId ?: continue
                    val route = routeDao.getById(routeId) ?: continue
                    if (route.segmentId !in segmentIds) {
                        // Route points to a segment that doesn't exist in this day
                        val referencedSegment = movementSegmentDao.getById(route.segmentId)
                        if (referencedSegment == null) {
                            routeDao.delete(route)
                            orphanRoutes++
                            fixed++
                        }
                    }
                }
            }

            logCompletion(overlapIssues, orphanSegments, orphanRoutes, staleVisitsClosed, fixed)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private fun buildRecentDayKeys(): List<String> {
        val cal = Calendar.getInstance()
        return (0 until LOOKBACK_DAYS).map { daysAgo ->
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -daysAgo)
            "%04d-%02d-%02d".format(
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
            )
        }
    }

    private suspend fun logCompletion(
        overlapIssues: Int,
        orphanSegments: Int,
        orphanRoutes: Int,
        staleVisitsClosed: Int,
        fixed: Int,
    ) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","overlapIssues":$overlapIssues,"orphanSegments":$orphanSegments,"orphanRoutes":$orphanRoutes,"staleVisitsClosed":$staleVisitsClosed,"fixed":$fixed}""",
            )
        )
    }

    private suspend fun logFailure(e: Exception) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HealthEventTypeWorkerFailure,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","error":"${e.message?.take(200)}"}""",
            )
        )
    }
}
