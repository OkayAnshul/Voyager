package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.SearchRepository
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.SearchDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.SearchIndexEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SearchMetadataEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val searchDao: SearchDao,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao
) : SearchRepository {

    override fun search(query: String, filters: SearchFilters): Flow<SearchResults> = flow {
        if (query.isBlank()) {
            emit(SearchResults(query, emptyList(), emptyList(), emptyList(), 0))
            return@flow
        }

        // Quote each token to escape FTS5 operators (OR, AND, NOT, -, :, ")
        // and enable prefix matching with trailing *
        val ftsQuery = query.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"${it.replace("\"", "")}\"*" }
        val metadataResults = searchDao.searchWithMetadata(ftsQuery)

        val places = mutableListOf<PlaceSearchResult>()
        val visits = mutableListOf<VisitSearchResult>()
        val daySet = mutableSetOf<String>()

        for (metadata in metadataResults) {
            if (metadata.sourceTable == "PLACE") {
                val place = placeDao.getById(metadata.sourceId) ?: continue
                val visitCount = visitDao.countByPlaceId(place.placeId)
                places.add(PlaceSearchResult(
                    placeId = place.placeId,
                    displayName = place.userDisplayName ?: place.bestProviderName ?: "Unknown",
                    category = try { PlaceCategory.valueOf(place.category) } catch (_: Exception) { PlaceCategory.UNKNOWN },
                    visitCount = visitCount,
                    relevanceScore = metadata.relevanceBoost
                ))

                // Collect recent visits for matched places
                val placeVisits = visitDao.getByPlaceId(place.placeId)
                for (v in placeVisits.take(5)) {
                    visits.add(VisitSearchResult(
                        visitId = v.visitId,
                        placeDisplayName = place.userDisplayName ?: place.bestProviderName ?: "Unknown",
                        arrivalAt = v.arrivalAt,
                        departureAt = v.departureAt,
                        dayKey = v.dayKey,
                        relevanceScore = metadata.relevanceBoost
                    ))
                    daySet.add(v.dayKey)
                }
            }
        }

        val days = daySet.map { dayKey ->
            DaySearchResult(
                dayKey = dayKey,
                matchingSegmentCount = movementSegmentDao.countByDayKey(dayKey),
                matchingPlaceCount = visits.count { it.dayKey == dayKey },
                relevanceScore = 1.0f
            )
        }

        emit(SearchResults(
            query = query,
            places = places,
            visits = visits,
            days = days,
            totalCount = places.size + visits.size
        ))
    }

    override suspend fun rebuildSearchIndex(): Result<Unit> = runCatching {
        val places = placeDao.getAllActive()
        val entries = places.map { place ->
            Pair(
                SearchIndexEntity(
                    placeDisplayName = place.userDisplayName ?: place.bestProviderName,
                    placeCategory = place.category,
                    dayKey = null,
                    segmentType = null,
                    geocodeDisplayName = place.bestProviderName,
                    userNotes = null
                ),
                SearchMetadataEntity(
                    searchRowId = 0,
                    sourceTable = "PLACE",
                    sourceId = place.placeId,
                    relevanceBoost = if (place.userDisplayName != null) 2.0f else 1.0f
                )
            )
        }
        searchDao.rebuildIndex(entries)
    }
}
