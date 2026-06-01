package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.enums.PlaceLifecycleStatus
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Daily place-fragmentation merge.
 *
 * Two real entrances of one café, two ends of a campus, or two GPS-jittery
 * sides of a building used to spawn separate places. This worker walks each
 * CANDIDATE place and, if a nearby CONFIRMED place with a matching name
 * exists inside [MERGE_RADIUS_M], reassigns the candidate's visits + segments
 * to the confirmed place and marks the candidate MERGED.
 *
 * Conservative by design:
 * - Only CANDIDATE → CONFIRMED merges happen. Two CONFIRMED places stay
 *   distinct — the user may have intentionally separated them.
 * - Requires a non-empty name match (case-insensitive, exact). Two places
 *   with no name don't merge.
 * - All write ops run inside one transaction per merge so a failure can't
 *   leave dangling visit/segment foreign keys.
 */
@HiltWorker
class MergePlacesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val database: VoyagerDatabase,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "merge_places"
        private const val MERGE_RADIUS_M = 200.0
    }

    override suspend fun doWork(): Result {
        return try {
            val all = placeDao.getAllActive()
            val candidates = all.filter {
                it.lifecycleStatus == PlaceLifecycleStatus.CANDIDATE.name
            }
            val confirmed = all.filter {
                it.lifecycleStatus == PlaceLifecycleStatus.CONFIRMED.name
            }
            var mergedCount = 0

            for (candidate in candidates) {
                val best = nearestMergeable(candidate, confirmed) ?: continue
                database.withTransaction {
                    visitDao.reassignPlace(candidate.placeId, best.placeId)
                    movementSegmentDao.reassignPlace(candidate.placeId, best.placeId)
                    placeDao.markMerged(candidate.placeId, best.placeId)
                }
                mergedCount++
            }

            healthLogDao.insert(
                HealthLogEntity(
                    eventType = HEALTH_EVENT_WORKER_COMPLETE,
                    eventAt = System.currentTimeMillis(),
                    detailsJson = """{"worker":"$WORK_NAME","candidatesScanned":${candidates.size},"merged":$mergedCount}""",
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

    private fun nearestMergeable(
        candidate: PlaceEntity,
        confirmedPlaces: List<PlaceEntity>
    ): PlaceEntity? {
        val candidateName = effectiveName(candidate) ?: return null
        return confirmedPlaces
            .asSequence()
            .filter { effectiveName(it)?.equals(candidateName, ignoreCase = true) == true }
            .map { place ->
                val distance = LocationUtils.calculateDistance(
                    candidate.centroidLat, candidate.centroidLng,
                    place.centroidLat, place.centroidLng
                )
                place to distance
            }
            .filter { it.second <= MERGE_RADIUS_M }
            .minByOrNull { it.second }
            ?.first
    }

    private fun effectiveName(place: PlaceEntity): String? {
        val name = place.userDisplayName?.takeIf { it.isNotBlank() }
            ?: place.bestProviderName?.takeIf { it.isNotBlank() }
        return name?.trim()
    }
}
