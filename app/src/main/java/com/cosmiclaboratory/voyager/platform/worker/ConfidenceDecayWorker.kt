package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.usecase.PlaceConfidenceDecay
import com.cosmiclaboratory.voyager.domain.usecase.PlaceRepeatability
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceRollupDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.abs

/**
 * Daily place-confidence decay.
 *
 * Places not visited for [PlaceConfidenceDecay.GRACE_DAYS] days have their
 * confidence multiplied down toward the floor each day. A revisit re-bumps
 * confidence via the place-discovery / linking path.
 */
@HiltWorker
class ConfidenceDecayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val placeRollupDao: PlaceRollupDao,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "place_confidence_decay"
        /** Skip writes for changes smaller than this to keep churn low. */
        private const val MIN_DELTA = 0.005f
    }

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val places = placeDao.getAllActive()
            var decayed = 0
            for (place in places) {
                // Recurring places resist decay (C1) — a weekly haunt keeps its trust.
                val rollup = placeRollupDao.getByPlaceId(place.placeId)
                val repeatability = if (rollup != null) {
                    PlaceRepeatability.score(rollup.totalVisitCount, rollup.visitCountLast30d)
                } else 0f
                val newConfidence = PlaceConfidenceDecay.decay(
                    currentConfidence = place.confidence,
                    lastVisitedAt = place.lastVisitedAt,
                    now = now,
                    repeatability = repeatability
                )
                if (abs(newConfidence - place.confidence) >= MIN_DELTA) {
                    placeDao.update(place.copy(confidence = newConfidence))
                    decayed++
                }
            }
            healthLogDao.insert(
                HealthLogEntity(
                    eventType = HEALTH_EVENT_WORKER_COMPLETE,
                    eventAt = now,
                    detailsJson = """{"worker":"$WORK_NAME","scanned":${places.size},"decayed":$decayed}""",
                )
            )
            Result.success()
        } catch (e: Exception) {
            healthLogDao.insert(
                HealthLogEntity(
                    eventType = HealthEventTypeWorkerFailure,
                    eventAt = System.currentTimeMillis(),
                    detailsJson = """{"worker":"$WORK_NAME","error":"${e.message?.take(200)}"}""",
                )
            )
            Result.retry()
        }
    }
}
