package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.usecase.InferPlaceCategoryUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.TimeZone

/**
 * Daily category inference for places the POI mapper couldn't classify.
 *
 * Walks every UNKNOWN-category place and runs [InferPlaceCategoryUseCase]
 * over its visit history. When the use case proposes a category, the worker
 * writes it — but only when:
 * - the current category is still UNKNOWN AND
 * - the user hasn't set their own override (`userCategory == null`).
 *
 * This catches the highest-value gaps the POI tag map misses: HOME
 * (nightly recurrence), WORK (weekday 9-5), and others where no OSM POI
 * sits within the place's centroid.
 */
@HiltWorker
class InferPlaceCategoryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val inferPlaceCategoryUseCase: InferPlaceCategoryUseCase,
    private val settingsRepository: SettingsRepository,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "infer_place_category"
    }

    override suspend fun doWork(): Result {
        return try {
            val tz = runCatching {
                TimeZone.getTimeZone(settingsRepository.observeSettings().value.homeTimeZone)
            }.getOrDefault(TimeZone.getDefault())

            val places = placeDao.getAllActive()
            var inferred = 0
            for (place in places) {
                if (place.category != PlaceCategory.UNKNOWN.name) continue
                if (place.userCategory != null) continue
                val visits = visitDao.getByPlaceId(place.placeId)
                val proposal = inferPlaceCategoryUseCase.infer(visits, tz) ?: continue
                placeDao.update(
                    place.copy(
                        category = proposal.category.name,
                        categoryConfidence = proposal.confidence
                    )
                )
                inferred++
            }

            healthLogDao.insert(
                HealthLogEntity(
                    eventType = HEALTH_EVENT_WORKER_COMPLETE,
                    eventAt = System.currentTimeMillis(),
                    detailsJson = """{"worker":"$WORK_NAME","scanned":${places.size},"inferred":$inferred}""",
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
