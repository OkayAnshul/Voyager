package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.platform.notification.VoyagerNotificationManager
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Same-day evening recap — a retention hook. Fires ~22:00, summarises today's
 * movement straight from segments/visits (today's DailyRollupEntity is not
 * computed until 03:00 tomorrow), and posts a tappable recap notification.
 *
 * Scheduled via WorkerScheduler; respects the daily-insights notification toggle.
 */
@HiltWorker
class DailyRecapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val notificationManager: VoyagerNotificationManager,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "daily_recap"
        private const val MIN_DISTANCE_M = 100.0
    }

    override suspend fun doWork(): Result {
        if (!settingsRepository.observeSettings().value.dailyInsightsEnabled) {
            return Result.success()
        }
        return try {
            val today = LocalDate.now().toString()
            val segments = movementSegmentDao.getByDayKey(today)
            val visits = visitDao.getByDayKey(today)

            val distanceM = segments
                .filter { it.segmentType != SegmentType.VISIT.name && it.segmentType != SegmentType.GAP.name }
                .sumOf { it.distanceM }
            val uniquePlaces = visits.map { it.placeId }.filter { it != 0L }.toSet().size

            // Nothing meaningful happened today — stay quiet.
            if (distanceM < MIN_DISTANCE_M && uniquePlaces == 0) return Result.success()

            val km = distanceM / 1000.0
            val body = buildString {
                if (km >= 0.1) append("You moved %.1f km".format(km))
                if (uniquePlaces > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$uniquePlaces place${if (uniquePlaces != 1) "s" else ""} visited")
                }
                append(". Tap to see your day.")
            }
            notificationManager.showInsight("Your day so far", body)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
