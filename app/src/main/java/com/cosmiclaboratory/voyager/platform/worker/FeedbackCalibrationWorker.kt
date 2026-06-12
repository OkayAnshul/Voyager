package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.usecase.CorrectionCalibration
import com.cosmiclaboratory.voyager.storage.database.dao.CorrectionFeedbackDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
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
    private val movementSegmentDao: MovementSegmentDao,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "feedback_calibration"
        /** Window over which reclassification corrections are scanned for systematic bias. */
        private const val BIAS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }

    override suspend fun doWork(): Result {
        return try {
            val unpropagated = correctionFeedbackDao.getUnpropagated()
            if (unpropagated.isEmpty()) {
                logCompletion(processed = 0, calibrated = 0, biasPatterns = 0)
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

            // Aggregate pass (report-only): surface systematic transport-mode bias.
            val biasPatterns = detectSystematicReclassBias()
            if (biasPatterns.isNotEmpty()) logSystematicBias(biasPatterns)

            logCompletion(processed = processed, calibrated = calibrated, biasPatterns = biasPatterns.size)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    /**
     * Recurring (classifier → user) transport-mode corrections over the last [BIAS_WINDOW_MS].
     * Reads the segment itself (segmentType = classifier label, userOverrideType = the user's
     * choice) rather than the correction's before/after JSON, which some entry paths leave null.
     * Uses `getByCorrectionTypeSince` so it sees corrections regardless of the propagated flag.
     */
    private suspend fun detectSystematicReclassBias(): List<CorrectionCalibration.MisclassificationPattern> {
        val sinceMs = System.currentTimeMillis() - BIAS_WINDOW_MS
        val types = listOf(CorrectionType.RECLASSIFY_SEGMENT.name, CorrectionType.CHANGE_TRANSPORT_MODE.name)
        val pairs = types
            .flatMap { correctionFeedbackDao.getByCorrectionTypeSince(it, sinceMs) }
            .filter { it.entityType.equals("segment", ignoreCase = true) }
            .mapNotNull { feedback ->
                val seg = movementSegmentDao.getById(feedback.entityId) ?: return@mapNotNull null
                val userType = seg.userOverrideType ?: return@mapNotNull null
                seg.segmentType to userType
            }
        return CorrectionCalibration.systematicMisclassifications(pairs)
    }

    private suspend fun logSystematicBias(patterns: List<CorrectionCalibration.MisclassificationPattern>) {
        val json = patterns.joinToString(",", "[", "]") {
            """{"from":"${it.from}","to":"${it.to}","count":${it.count}}"""
        }
        healthLogDao.insert(
            HealthLogEntity(
                eventType = "SYSTEMATIC_MISCLASSIFICATION",
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","patterns":$json}""",
            )
        )
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

    private suspend fun logCompletion(processed: Int, calibrated: Int, biasPatterns: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","processed":$processed,"calibrated":$calibrated,"biasPatterns":$biasPatterns}""",
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
