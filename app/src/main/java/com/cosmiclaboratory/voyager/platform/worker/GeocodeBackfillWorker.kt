package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Reverse geocodes unnamed places (where bestProviderName is null).
 * Runs every 4 hours via WorkerScheduler.
 */
@HiltWorker
class GeocodeBackfillWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val geocodingRepository: GeocodingRepository,
    private val healthLogDao: HealthLogDao,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "geocode_backfill"
        private const val BATCH_SIZE = 20
    }

    override suspend fun doWork(): Result {
        // Respect the privacy lever — when auto-geocoding is off, names are not
        // looked up over the network; places keep their coordinate name.
        if (!settingsRepository.observeSettings().value.autoGeocodeNewPlaces) {
            logCompletion(geocodedCount = 0, failedCount = 0)
            return Result.success()
        }
        return try {
            val unnamedPlaces = placeDao.getAllActive()
                .filter { it.bestProviderName == null }
                .take(BATCH_SIZE)

            if (unnamedPlaces.isEmpty()) {
                logCompletion(geocodedCount = 0, failedCount = 0)
                return Result.success()
            }

            var geocodedCount = 0
            var failedCount = 0

            for (place in unnamedPlaces) {
                try {
                    geocodingRepository.refreshGeocodeForPlace(place.placeId)
                    geocodedCount++
                } catch (e: Exception) {
                    failedCount++
                    // Continue processing remaining places; transient failures should not abort
                }
            }

            logCompletion(geocodedCount = geocodedCount, failedCount = failedCount)

            // If all failed, signal retry for transient issues
            if (geocodedCount == 0 && failedCount > 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private suspend fun logCompletion(geocodedCount: Int, failedCount: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","geocoded":$geocodedCount,"failed":$failedCount}""",
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
