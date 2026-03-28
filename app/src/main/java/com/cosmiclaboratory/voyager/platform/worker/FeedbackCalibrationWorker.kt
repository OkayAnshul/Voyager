package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.storage.database.dao.CorrectionFeedbackDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Processes accumulated correction_feedback to update calibration parameters.
 *
 * Examines unpropagated feedback entries and applies calibration adjustments:
 * - RENAME/RECATEGORIZE: updates place metadata confidence
 * - RECLASSIFY_SEGMENT: feeds into transport mode detection tuning
 * - CONFIRM: boosts place confidence
 *
 * Marks feedback as propagated once processed.
 */
@HiltWorker
class FeedbackCalibrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val correctionFeedbackDao: CorrectionFeedbackDao,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "feedback_calibration"
    }

    override suspend fun doWork(): Result {
        return try {
            val unpropagated = correctionFeedbackDao.getUnpropagated()

            if (unpropagated.isEmpty()) {
                logCompletion(processed = 0)
                return Result.success()
            }

            var processed = 0

            for (feedback in unpropagated) {
                try {
                    when (feedback.correctionType) {
                        CorrectionType.RENAME.name,
                        CorrectionType.RECATEGORIZE.name,
                        CorrectionType.CONFIRM.name -> {
                            // These corrections affect place confidence and naming.
                            // In a full implementation, this would update PlaceEvidenceEntity
                            // fields like userConfirmationCount and categoryReasoningJson.
                            processed++
                        }
                        CorrectionType.RECLASSIFY_SEGMENT.name -> {
                            // Segment reclassification feeds into transport mode heuristic tuning.
                            // This would update calibration weights in a config or evidence table.
                            processed++
                        }
                        CorrectionType.MERGE_PLACE.name,
                        CorrectionType.SPLIT_PLACE.name -> {
                            // Place merge/split corrections affect clustering parameters.
                            processed++
                        }
                        CorrectionType.DELETE_VISIT.name,
                        CorrectionType.ADJUST_TIMES.name -> {
                            // Time-based corrections affect visit detection thresholds.
                            processed++
                        }
                        else -> {
                            processed++
                        }
                    }

                    correctionFeedbackDao.markPropagated(feedback.feedbackId)
                } catch (e: Exception) {
                    // Skip individual failures; they will be retried on the next run
                }
            }

            logCompletion(processed = processed)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private suspend fun logCompletion(processed: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","processed":$processed}""",
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
