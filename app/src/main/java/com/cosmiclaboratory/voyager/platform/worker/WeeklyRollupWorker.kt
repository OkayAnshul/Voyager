package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.platform.notification.VoyagerNotificationManager
import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.WeeklyRollupDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.WeeklyRollupEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.Locale

/**
 * Computes WeeklyRollupEntity from daily rollups for the previous week.
 * Scheduled every Monday at 04:00 via WorkerScheduler.
 */
@HiltWorker
class WeeklyRollupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dailyRollupDao: DailyRollupDao,
    private val weeklyRollupDao: WeeklyRollupDao,
    private val healthLogDao: HealthLogDao,
    private val notificationManager: VoyagerNotificationManager,
    private val settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weekly_rollup"
    }

    override suspend fun doWork(): Result {
        return try {
            val (weekKey, startDay, endDay) = computePreviousWeekRange()

            val dailyRollups = dailyRollupDao.getByRange(startDay, endDay)

            if (dailyRollups.isEmpty()) {
                logCompletion(weekKey, daysFound = 0)
                return Result.success()
            }

            val activeDayCount = dailyRollups.size
            val totalDistanceM = dailyRollups.sumOf { it.totalDistanceM }
            val totalSteps = dailyRollups.sumOf { it.totalSteps }

            // Compute transport mode distribution across the week
            val modeDistribution = dailyRollups
                .mapNotNull { it.dominantTransportMode }
                .groupingBy { it }
                .eachCount()
            val modeDistJson = modeDistribution.entries
                .joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

            // Compute comparison to previous week
            val (prevWeekKey, prevStartDay, prevEndDay) = computeWeekRange(weeksAgo = 2)
            val prevWeekRollup = weeklyRollupDao.getByWeekKey(prevWeekKey)
            val comparisonJson = if (prevWeekRollup != null) {
                val distDelta = totalDistanceM - prevWeekRollup.totalDistanceM
                val stepDelta = totalSteps - prevWeekRollup.totalSteps
                """{"distanceDelta":$distDelta,"stepsDelta":$stepDelta}"""
            } else null

            // Top places from daily rollups are not directly available — leave as null
            val rollup = WeeklyRollupEntity(
                weekKey = weekKey,
                avgDailyDistanceM = if (activeDayCount > 0) totalDistanceM / activeDayCount else 0.0,
                avgDailySteps = if (activeDayCount > 0) totalSteps / activeDayCount else 0,
                totalDistanceM = totalDistanceM,
                totalSteps = totalSteps,
                activeDayCount = activeDayCount,
                topPlacesJson = null,
                transportModeDistributionJson = modeDistJson,
                comparisonToPrevWeekJson = comparisonJson,
                computedAt = System.currentTimeMillis(),
            )

            weeklyRollupDao.upsert(rollup)

            sendWeeklySummaryNotification(rollup)
            logCompletion(weekKey, daysFound = activeDayCount)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private fun computePreviousWeekRange(): Triple<String, String, String> {
        return computeWeekRange(weeksAgo = 1)
    }

    private fun computeWeekRange(weeksAgo: Int): Triple<String, String, String> {
        val cal = Calendar.getInstance(Locale.getDefault()).apply {
            firstDayOfWeek = Calendar.MONDAY
            add(Calendar.WEEK_OF_YEAR, -weeksAgo)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val startDay = formatDayKey(cal)
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val weekKey = "%04d-W%02d".format(year, week)

        cal.add(Calendar.DAY_OF_YEAR, 6)
        val endDay = formatDayKey(cal)

        return Triple(weekKey, startDay, endDay)
    }

    private fun formatDayKey(cal: Calendar): String {
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun sendWeeklySummaryNotification(rollup: WeeklyRollupEntity) {
        // Respect the weekly-insights notification toggle.
        if (!settingsRepository.observeSettings().value.weeklyInsightsEnabled) return
        if (rollup.activeDayCount == 0) return
        val km = rollup.totalDistanceM / 1000.0
        val body = buildString {
            append("${rollup.activeDayCount} day${if (rollup.activeDayCount != 1) "s" else ""} active")
            if (km >= 0.1) append(" · %.1f km".format(km))
            if (rollup.totalSteps > 0) append(" · ${rollup.totalSteps} steps")
        }
        notificationManager.showInsight("Your week in review", body)
    }

    private suspend fun logCompletion(weekKey: String, daysFound: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","weekKey":"$weekKey","daysFound":$daysFound}""",
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
