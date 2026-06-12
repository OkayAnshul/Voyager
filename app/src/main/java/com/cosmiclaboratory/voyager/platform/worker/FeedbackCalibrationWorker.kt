package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.usecase.CorrectionCalibration
import com.cosmiclaboratory.voyager.storage.database.dao.CorrectionFeedbackDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.CorrectionFeedbackEntity
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Processes accumulated correction_feedback into calibration adjustments.
 *
 * Implemented slice — **place trust** ([CorrectionCalibration]): a CONFIRM / CONFIRM_VISIT /
 * RENAME / RECATEGORIZE means the user validated a place's identity, so the place's confidence
 * is raised toward a high floor (monotonic — only ever raises). Validated places then drop out
 * of the review queue and decay from a high base.
 *
 * Other correction types (segment reclassification, place merge/split, time adjustments) are
 * still consumed here but don't yet drive per-entry calibration; their aggregate calibration
 * slices read from [CorrectionFeedbackDao.getByCorrectionTypeSince] (independent of the
 * propagated flag), so marking entries propagated here doesn't lose them.
 */
@HiltWorker
class FeedbackCalibrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val correctionFeedbackDao: CorrectionFeedbackDao,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "feedback_calibration"
    }

    override suspend fun doWork(): Result {
        return try {
            val unpropagated = correctionFeedbackDao.getUnpropagated()
            if (unpropagated.isEmpty()) {
                logCompletion(processed = 0, calibrated = 0)
                return Result.success()
            }

            var processed = 0
            var calibrated = 0

            for (feedback in unpropagated) {
                try {
                    if (CorrectionCalibration.isPlaceTrustSignal(feedback.correctionType)) {
                        if (applyPlaceTrustBoost(feedback)) calibrated++
                    }
                    // Non-place-trust corrections are consumed without per-entry action yet —
                    // their calibration slices aggregate via getByCorrectionTypeSince.
                    correctionFeedbackDao.markPropagated(feedback.feedbackId)
                    processed++
                } catch (e: Exception) {
                    // Skip individual failures; they retry on the next run.
                }
            }

            logCompletion(processed = processed, calibrated = calibrated)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    /** Raise the corrected place's confidence to the user-validated floor. Returns true if changed. */
    private suspend fun applyPlaceTrustBoost(feedback: CorrectionFeedbackEntity): Boolean {
        val placeId = resolvePlaceId(feedback) ?: return false
        val place = placeDao.getById(placeId) ?: return false
        val boosted = CorrectionCalibration.boostedConfidence(place.confidence)
        if (boosted <= place.confidence) return false
        placeDao.update(place.copy(confidence = boosted))
        return true
    }

    /** The place a correction is about — directly for "place", via the visit for "visit". */
    private suspend fun resolvePlaceId(feedback: CorrectionFeedbackEntity): Long? = when {
        feedback.entityType.equals("place", ignoreCase = true) -> feedback.entityId.takeIf { it > 0 }
        feedback.entityType.equals("visit", ignoreCase = true) ->
            visitDao.getById(feedback.entityId)?.placeId?.takeIf { it != 0L }
        else -> null
    }

    private suspend fun logCompletion(processed: Int, calibrated: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","processed":$processed,"calibrated":$calibrated}""",
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
