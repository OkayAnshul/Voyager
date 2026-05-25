package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.PlaceLifecycleStatus
import com.cosmiclaboratory.voyager.domain.model.ids.PlaceId
import com.cosmiclaboratory.voyager.domain.model.ids.VisitId
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val database: VoyagerDatabase,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao
) : PlaceRepository {

    override fun observePlaces(): Flow<List<TimelinePlace>> {
        return placeDao.observeActivePlaces().map { entities ->
            entities.map { it.toTimelinePlace() }
        }
    }

    override fun observePlace(placeId: PlaceId): Flow<TimelinePlace?> {
        return placeDao.observeById(placeId.raw).map { it?.toTimelinePlace() }
    }

    override suspend fun renamePlace(placeId: PlaceId, name: String): Result<Unit> = runCatching {
        placeDao.updateDisplayName(placeId.raw, name)
    }

    override suspend fun mergePlaces(sourceIds: List<PlaceId>, targetId: PlaceId): Result<Unit> = runCatching {
        val target = targetId.raw
        database.withTransaction {
            for (source in sourceIds) {
                val src = source.raw
                if (src != target) {
                    // Re-point history to the target before hiding the source. Without
                    // this, visits/segments keep the merged-away placeId and the
                    // timeline still resolves them to the now-hidden source place.
                    visitDao.reassignPlace(src, target)
                    movementSegmentDao.reassignPlace(src, target)
                    placeDao.markMerged(src, target)
                }
            }
        }
    }

    override suspend fun splitPlace(placeId: PlaceId, visitIds: List<VisitId>): Result<PlaceId> = runCatching {
        val original = placeDao.getById(placeId.raw)
            ?: throw IllegalArgumentException("Place ${placeId.raw} not found")

        val newPlace = PlaceEntity(
            centroidLat = original.centroidLat,
            centroidLng = original.centroidLng,
            radiusM = original.radiusM,
            geohash = original.geohash,
            s2CellId = original.s2CellId,
            confidence = 0.5f,
            lifecycleStatus = PlaceLifecycleStatus.CANDIDATE.name,
            userDisplayName = null,
            bestProviderName = original.bestProviderName,
            bestProviderSource = original.bestProviderSource,
            category = original.category,
            categoryConfidence = 0f,
            userCategory = null,
            createdAt = System.currentTimeMillis(),
            lastVisitedAt = System.currentTimeMillis()
        )
        val newPlaceId = placeDao.insert(newPlace)

        // Reassign specified visits to the new place
        for (visitId in visitIds) {
            val visit = visitDao.getById(visitId.raw) ?: continue
            visitDao.update(visit.copy(placeId = newPlaceId))
        }

        PlaceId(newPlaceId)
    }

    override suspend fun setPlaceCategory(placeId: PlaceId, category: PlaceCategory): Result<Unit> = runCatching {
        placeDao.updateCategory(placeId.raw, category.name, category.name)
    }

    override suspend fun confirmPlace(placeId: PlaceId): Result<Unit> = runCatching {
        val place = placeDao.getById(placeId.raw)
            ?: throw IllegalArgumentException("Place ${placeId.raw} not found")
        placeDao.update(
            place.copy(
                lifecycleStatus = PlaceLifecycleStatus.CONFIRMED.name,
                confidence = maxOf(place.confidence, 0.8f)
            )
        )
    }

    override suspend fun getFrequentPlaces(limit: Int): List<TimelinePlace> {
        return placeDao.getFrequentPlaces(limit).map { it.toTimelinePlace() }
    }

    override suspend fun getHomePlace(): TimelinePlace? {
        return placeDao.getHomePlace()?.toTimelinePlace()
    }

    override suspend fun setPlaceEmoji(placeId: PlaceId, emoji: String?): Result<Unit> = runCatching {
        placeDao.updateEmoji(placeId.raw, emoji)
    }

    private fun PlaceEntity.toTimelinePlace(): TimelinePlace {
        val displayName = userDisplayName
            ?: bestProviderName
            ?: "%.4f, %.4f".format(centroidLat, centroidLng)

        val nameSource = when {
            userDisplayName != null -> "Custom name"
            bestProviderName != null -> "via ${bestProviderSource ?: "Provider"}"
            else -> "Coordinates"
        }

        return TimelinePlace(
            placeId = placeId,
            displayName = displayName,
            nameSource = nameSource,
            category = try {
                PlaceCategory.valueOf(userCategory ?: category)
            } catch (_: Exception) {
                PlaceCategory.UNKNOWN
            },
            confidence = confidence,
            lat = centroidLat,
            lng = centroidLng,
            emoji = emoji
        )
    }
}
