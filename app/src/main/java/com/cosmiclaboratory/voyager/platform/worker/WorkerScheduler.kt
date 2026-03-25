package com.cosmiclaboratory.voyager.platform.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Central scheduler for all periodic WorkManager workers.
 * Call [scheduleAll] once during app initialization (e.g., from Application.onCreate).
 *
 * All periodic workers use [ExistingPeriodicWorkPolicy.KEEP] to avoid duplicate scheduling.
 */
object WorkerScheduler {

    fun scheduleAll(workManager: WorkManager) {
        scheduleDiscoverPlaces(workManager)
        scheduleGeocodeBackfill(workManager)
        scheduleDailyRollup(workManager)
        scheduleWeeklyRollup(workManager)
        scheduleSemanticLabel(workManager)
        scheduleDataRetention(workManager)
        scheduleIntegrityRepair(workManager)
        scheduleStepSync(workManager)
        scheduleFeedbackCalibration(workManager)
        scheduleSearchIndex(workManager)
        scheduleTrackingHealthCheck(workManager)
    }

    private fun scheduleDiscoverPlaces(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<DiscoverPlacesWorker>(6, TimeUnit.HOURS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            DiscoverPlacesWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleGeocodeBackfill(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<GeocodeBackfillWorker>(4, TimeUnit.HOURS)
            .setConstraints(networkConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            GeocodeBackfillWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleDailyRollup(workManager: WorkManager) {
        val initialDelay = computeDelayUntil(hour = 3, minute = 0)
        val request = PeriodicWorkRequestBuilder<DailyRollupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            DailyRollupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleWeeklyRollup(workManager: WorkManager) {
        val initialDelay = computeDelayUntilDayAndTime(
            targetDayOfWeek = Calendar.MONDAY,
            hour = 4,
            minute = 0,
        )
        val request = PeriodicWorkRequestBuilder<WeeklyRollupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            WeeklyRollupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleSemanticLabel(workManager: WorkManager) {
        val initialDelay = computeDelayUntilDayAndTime(
            targetDayOfWeek = Calendar.SUNDAY,
            hour = 5,
            minute = 0,
        )
        val request = PeriodicWorkRequestBuilder<SemanticLabelWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            SemanticLabelWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleDataRetention(workManager: WorkManager) {
        val initialDelay = computeDelayUntil(hour = 4, minute = 0)
        val request = PeriodicWorkRequestBuilder<DataRetentionWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            DataRetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleIntegrityRepair(workManager: WorkManager) {
        val initialDelay = computeDelayUntil(hour = 5, minute = 0)
        val request = PeriodicWorkRequestBuilder<IntegrityRepairWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            IntegrityRepairWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleStepSync(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            StepSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleFeedbackCalibration(workManager: WorkManager) {
        val initialDelay = computeDelayUntilDayAndTime(
            targetDayOfWeek = Calendar.WEDNESDAY,
            hour = 3,
            minute = 30,
        )
        val request = PeriodicWorkRequestBuilder<FeedbackCalibrationWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            FeedbackCalibrationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleSearchIndex(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<SearchIndexWorker>(12, TimeUnit.HOURS)
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            SearchIndexWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleTrackingHealthCheck(workManager: WorkManager) {
        // No battery constraint — this is critical for tracking reliability.
        // Must run even at low battery to detect and recover from service death.
        val request = PeriodicWorkRequestBuilder<TrackingHealthCheckWorker>(
            15, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            TrackingHealthCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    // --- Helpers ---

    /**
     * Enqueues a one-shot SearchIndexWorker. Call after geocode, rename, or correction events.
     */
    fun triggerSearchIndexRebuild(workManager: WorkManager) {
        val request = androidx.work.OneTimeWorkRequestBuilder<SearchIndexWorker>()
            .setConstraints(defaultConstraints())
            .build()

        workManager.enqueueUniqueWork(
            SearchIndexWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun defaultConstraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Computes the delay in milliseconds from now until the next occurrence of [hour]:[minute].
     */
    private fun computeDelayUntil(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Computes the delay in milliseconds from now until the next occurrence of
     * [targetDayOfWeek] at [hour]:[minute].
     */
    private fun computeDelayUntilDayAndTime(targetDayOfWeek: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            val currentDay = get(Calendar.DAY_OF_WEEK)
            var daysUntil = targetDayOfWeek - currentDay
            if (daysUntil < 0) daysUntil += 7
            if (daysUntil == 0 && before(now)) daysUntil = 7
            add(Calendar.DAY_OF_YEAR, daysUntil)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
