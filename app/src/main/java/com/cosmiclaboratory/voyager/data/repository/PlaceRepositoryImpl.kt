package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.PlaceLifecycleStatus
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao
) : PlaceRepository {

    override fun observePlaces(): Flow<List<TimelinePlace>> {
        return placeDao.observeActivePlaces().map { entities ->
            entities.map { it.toTimelinePlace() }
        }
    }

    override fun observePlace(placeId: Long): Flow<TimelinePlace?> {
        return placeDao.observeById(placeId).map { it?.toTimelinePlace() }
    }

    override suspend fun renamePlace(placeId: Long, name: String): Result<Unit> = runCatching {
        placeDao.updateDisplayName(placeId, name)
    }

    override suspend fun mergePlaces(sourceIds: List<Long>, targetId: Long): Result<Unit> = runCatching {
        for (sourceId in sourceIds) {
            if (sourceId != targetId) {
                placeDao.markMerged(sourceId, targetId)
            }
        }
    }

    override suspend fun splitPlace(placeId: Long, visitIds: List<Long>): Result<Long> = runCatching {
        val original = placeDao.getById(placeId)
            ?: throw IllegalArgumentException("Place $placeId not found")

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
            val visit = visitDao.getById(visitId) ?: continue
            visitDao.update(visit.copy(placeId = newPlaceId))
        }

        newPlaceId
    }

    override suspend fun setPlaceCategory(placeId: Long, category: PlaceCategory): Result<Unit> = runCatching {
        placeDao.updateCategory(placeId, category.name, category.name)
    }

    override suspend fun confirmPlace(placeId: Long): Result<Unit> = runCatching {
        val place = placeDao.getById(placeId)
            ?: throw IllegalArgumentException("Place $placeId not found")
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

    override suspend fun setPlaceEmoji(placeId: Long, emoji: String?): Result<Unit> = runCatching {
        placeDao.updateEmoji(placeId, emoji)
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
