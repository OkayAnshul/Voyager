package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.SearchDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SearchIndexEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SearchMetadataEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Rebuilds the FTS search index from places, visits, and segments.
 * Triggered after geocode, rename, or correction operations.
 */
@HiltWorker
class SearchIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val searchDao: SearchDao,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "search_index"
    }

    override suspend fun doWork(): Result {
        return try {
            val entries = mutableListOf<Pair<SearchIndexEntity, SearchMetadataEntity>>()

            // Index all active places
            val places = placeDao.getAllActive()
            for (place in places) {
                val displayName = place.userDisplayName ?: place.bestProviderName
                if (displayName != null) {
                    entries.add(
                        SearchIndexEntity(
                            placeDisplayName = displayName,
                            placeCategory = place.category,
                            geocodeDisplayName = place.bestProviderName,
                        ) to SearchMetadataEntity(
                            searchRowId = 0, // Will be set by rebuildIndex
                            sourceTable = "PLACE",
                            sourceId = place.placeId,
                            relevanceBoost = if (place.lastVisitedAt != null) 1.5f else 1.0f,
                        )
                    )
                }
            }

            // Index day keys from segments for day-level search
            val dayKeys = movementSegmentDao.getAllDayKeys()
            for (dayKey in dayKeys) {
                val segments = movementSegmentDao.getByDayKey(dayKey)
                val segmentTypes = segments.map { it.segmentType }.distinct().joinToString(" ")
                entries.add(
                    SearchIndexEntity(
                        dayKey = dayKey,
                        segmentType = segmentTypes,
                    ) to SearchMetadataEntity(
                        searchRowId = 0,
                        sourceTable = "DAY",
                        sourceId = dayKey.hashCode().toLong(),
                        relevanceBoost = 1.0f,
                    )
                )
            }

            // Rebuild atomically
            searchDao.rebuildIndex(entries)

            logCompletion(placeCount = places.size, dayCount = dayKeys.size, totalEntries = entries.size)
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private suspend fun logCompletion(placeCount: Int, dayCount: Int, totalEntries: Int) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","places":$placeCount,"days":$dayCount,"total":$totalEntries}""",
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
