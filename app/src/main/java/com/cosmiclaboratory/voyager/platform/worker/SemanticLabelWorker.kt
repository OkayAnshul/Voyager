package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

/**
 * Infers HOME/WORK semantic labels from visit patterns.
 * Scheduled weekly via WorkerScheduler.
 *
 * Heuristics:
 * - HOME: place with the most overnight visits (22:00–06:00) in the last 30 days
 * - WORK: place with the most weekday daytime visits (09:00–17:00) in the last 30 days,
 *   excluding the HOME place
 */
@HiltWorker
class SemanticLabelWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val placeEvidenceDao: PlaceEvidenceDao,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "semantic_label"
        private const val ANALYSIS_WINDOW_DAYS = 30
        private const val MIN_OVERNIGHT_VISITS = 5
        private const val MIN_WORK_VISITS = 4
    }

    override suspend fun doWork(): Result {
        return try {
            val activePlaces = placeDao.getAllActive()
            if (activePlaces.isEmpty()) {
                logCompletion(homeId = null, workId = null)
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val windowStartMs = now - ANALYSIS_WINDOW_DAYS.toLong() * 24 * 60 * 60 * 1000

            // Collect visit data for each place
            data class PlaceVisitStats(
                val placeId: Long,
                var overnightCount: Int = 0,
                var weekdayDaytimeCount: Int = 0,
                var totalVisitCount: Int = 0,
            )

            val statsMap = mutableMapOf<Long, PlaceVisitStats>()

            for (place in activePlaces) {
                // Skip user-categorized places; respect user intent
                if (place.userCategory != null) continue

                val visits = visitDao.getByPlaceId(place.placeId)
                    .filter { it.arrivalAt >= windowStartMs }

                if (visits.isEmpty()) continue

                val stats = PlaceVisitStats(placeId = place.placeId, totalVisitCount = visits.size)

                for (visit in visits) {
                    val cal = Calendar.getInstance().apply { timeInMillis = visit.arrivalAt }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY

                    if (hour in 22..23 || hour in 0..5) {
                        stats.overnightCount++
                    }
                    if (isWeekday && hour in 9..16) {
                        stats.weekdayDaytimeCount++
                    }
                }

                statsMap[place.placeId] = stats
            }

            // Determine HOME: highest overnight visit count above threshold
            val homeCandidate = statsMap.values
                .filter { it.overnightCount >= MIN_OVERNIGHT_VISITS }
                .maxByOrNull { it.overnightCount }

            if (homeCandidate != null) {
                placeDao.updateCategory(
                    placeId = homeCandidate.placeId,
                    category = PlaceCategory.HOME.name,
                    userCategory = null,
                )
            }

            // Determine WORK: highest weekday daytime count above threshold, excluding HOME
            val workCandidate = statsMap.values
                .filter { it.placeId != homeCandidate?.placeId }
                .filter { it.weekdayDaytimeCount >= MIN_WORK_VISITS }
                .maxByOrNull { it.weekdayDaytimeCount }

            if (workCandidate != null) {
                placeDao.updateCategory(
                    placeId = workCandidate.placeId,
                    category = PlaceCategory.WORK.name,
                    userCategory = null,
                )
            }

            logCompletion(homeId = homeCandidate?.placeId, workId = workCandidate?.placeId)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private suspend fun logCompletion(homeId: Long?, workId: Long?) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","homeId":${homeId ?: "null"},"workId":${workId ?: "null"}}""",
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
