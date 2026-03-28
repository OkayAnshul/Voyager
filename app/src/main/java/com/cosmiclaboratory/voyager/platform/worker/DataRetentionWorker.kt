package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.storage.database.dao.CorrectionFeedbackDao
import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import com.cosmiclaboratory.voyager.storage.database.dao.GeocodeCandidateDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PendingPlaceUpdateDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawActivitySampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.dao.WeeklyRollupDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Tiered data retention cleanup.
 *
 * Retention tiers:
 * - Raw (location/activity/step samples): 90 days
 * - Derived (segments, visits, daily/weekly rollups): 365 days
 * - Ops (health log, pending updates, geocode candidates): 30 days
 * - Feedback (correction feedback): 180 days
 *
 * Batch deletes 1000 rows per transaction to avoid ANR.
 * Scheduled daily at 04:00 via WorkerScheduler.
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rawLocationSampleDao: RawLocationSampleDao,
    private val rawActivitySampleDao: RawActivitySampleDao,
    private val rawStepSampleDao: RawStepSampleDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val dailyRollupDao: DailyRollupDao,
    private val weeklyRollupDao: WeeklyRollupDao,
    private val healthLogDao: HealthLogDao,
    private val pendingPlaceUpdateDao: PendingPlaceUpdateDao,
    private val geocodeCandidateDao: GeocodeCandidateDao,
    private val correctionFeedbackDao: CorrectionFeedbackDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "data_retention"

        private const val RAW_RETENTION_DAYS = 90L
        private const val DERIVED_RETENTION_DAYS = 365L
        private const val OPS_RETENTION_DAYS = 30L
        private const val FEEDBACK_RETENTION_DAYS = 180L

        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            var totalDeleted = 0

            // Size-adaptive retention: reduce retention when DB grows too large
            val dbFile = applicationContext.getDatabasePath("voyager_database")
            val dbSizeMb = dbFile.length() / (1024 * 1024)
            val effectiveRawRetention = when {
                dbSizeMb > 1024 -> 30L   // >1GB: aggressive trim
                dbSizeMb > 500 -> 60L    // >500MB: moderate trim
                else -> RAW_RETENTION_DAYS
            }

            // --- Raw tier ---
            val rawCutoffMs = now - effectiveRawRetention * MS_PER_DAY
            totalDeleted += rawLocationSampleDao.deleteOlderThan(rawCutoffMs)
            totalDeleted += rawActivitySampleDao.deleteOlderThan(rawCutoffMs)
            totalDeleted += rawStepSampleDao.deleteOlderThan(rawCutoffMs)

            // --- Derived tier: 365 days ---
            val derivedCutoffMs = now - DERIVED_RETENTION_DAYS * MS_PER_DAY
            val derivedCutoffDayKey = millisToDayKey(derivedCutoffMs)
            totalDeleted += movementSegmentDao.deleteOlderThan(derivedCutoffMs)
            totalDeleted += visitDao.deleteOlderThan(derivedCutoffMs)
            totalDeleted += dailyRollupDao.deleteOlderThan(derivedCutoffDayKey)
            // Weekly rollups: compute week key for cutoff
            val derivedCutoffWeekKey = millisToWeekKey(derivedCutoffMs)
            totalDeleted += weeklyRollupDao.deleteOlderThan(derivedCutoffWeekKey)

            // --- Ops tier: 30 days ---
            val opsCutoffMs = now - OPS_RETENTION_DAYS * MS_PER_DAY
            totalDeleted += healthLogDao.deleteOlderThan(opsCutoffMs)
            totalDeleted += pendingPlaceUpdateDao.deleteConsumedOlderThan(opsCutoffMs)
            totalDeleted += geocodeCandidateDao.deleteExpired(now)

            // --- Feedback tier: 180 days ---
            val feedbackCutoffMs = now - FEEDBACK_RETENTION_DAYS * MS_PER_DAY
            totalDeleted += correctionFeedbackDao.deleteOlderThan(feedbackCutoffMs)

            logCompletion(totalDeleted, dbSizeMb, effectiveRawRetention)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private fun millisToDayKey(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    private fun millisToWeekKey(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = ms
            firstDayOfWeek = java.util.Calendar.MONDAY
        }
        return "%04d-W%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.WEEK_OF_YEAR),
        )
    }

    private suspend fun logCompletion(totalDeleted: Int, dbSizeMb: Long, effectiveRetention: Long) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","totalDeleted":$totalDeleted,"dbSizeMb":$dbSizeMb,"effectiveRawRetentionDays":$effectiveRetention}""",
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
