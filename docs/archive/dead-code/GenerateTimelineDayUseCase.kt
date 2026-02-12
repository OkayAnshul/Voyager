package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.EvidenceBlock
import com.cosmiclaboratory.voyager.domain.model.TimelineDay
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.model.TimelineRoute
import com.cosmiclaboratory.voyager.domain.model.TimelineSegment
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SegmentEvidenceEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject

class GenerateTimelineDayUseCase @Inject constructor(
    private val segmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val routeDao: RouteDao,
    private val placeDao: PlaceDao,
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val visitEvidenceDao: VisitEvidenceDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(dayKey: String): TimelineDay {
        val segmentEntities = segmentDao.getByDayKey(dayKey)

        val segments = segmentEntities.map { entity ->
            val evidence = segmentEvidenceDao.getBySegmentId(entity.segmentId)
            val place = entity.placeId?.let { placeDao.getById(it) }
            val route = routeDao.getBySegmentId(entity.segmentId)

            mapToTimelineSegment(entity, evidence, place, route)
        }

        val totalDistanceM = segments.sumOf { it.distanceM }
        val totalSteps = segments.mapNotNull { it.evidence?.stepCount }.sum()
        val firstActivityAt = segments.firstOrNull()?.startAt
        val lastActivityAt = segments.lastOrNull()?.endAt

        return TimelineDay(
            dayKey = dayKey,
            segments = segments,
            totalDistanceM = totalDistanceM,
            totalSteps = totalSteps,
            firstActivityAt = firstActivityAt,
            lastActivityAt = lastActivityAt
        )
    }

    private fun mapToTimelineSegment(
        entity: MovementSegmentEntity,
        evidenceEntity: SegmentEvidenceEntity?,
        placeEntity: PlaceEntity?,
        routeEntity: RouteEntity?
    ): TimelineSegment {
        return TimelineSegment(
            segmentId = entity.segmentId,
            type = parseSegmentType(entity.segmentType),
            startAt = entity.startAt,
            endAt = entity.endAt,
            durationMs = entity.endAt - entity.startAt,
            distanceM = entity.distanceM,
            confidence = entity.confidence,
            evidence = evidenceEntity?.let { mapToEvidenceBlock(it) },
            place = placeEntity?.let { mapToTimelinePlace(it) },
            route = routeEntity?.let { mapToTimelineRoute(it) },
            gapReason = entity.gapReason,
            isUserCorrected = entity.isUserCorrected
        )
    }

    private fun mapToEvidenceBlock(entity: SegmentEvidenceEntity): EvidenceBlock {
        val activityVotes: Map<String, Int> = entity.activityVotesJson?.let {
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), Int.serializer()), it
                )
            } catch (_: Exception) {
                emptyMap()
            }
        } ?: emptyMap()

        val providerMix: Map<String, Int> = entity.providerMixJson?.let {
            try {
                json.decodeFromString(
                    MapSerializer(String.serializer(), Int.serializer()), it
                )
            } catch (_: Exception) {
                emptyMap()
            }
        } ?: emptyMap()

        return EvidenceBlock(
            sampleCount = entity.sampleCount,
            avgSpeed = entity.avgSpeedMps,
            maxSpeed = entity.maxSpeedMps,
            stepCount = entity.stepCount,
            headingConsistency = entity.headingConsistency,
            activityVotes = activityVotes,
            providerMix = providerMix,
            explanation = null // Parsed separately if needed via EvidenceRepository
        )
    }

    private fun mapToTimelinePlace(entity: PlaceEntity): TimelinePlace {
        val displayName = entity.userDisplayName
            ?: entity.bestProviderName
            ?: formatCoordinates(entity.centroidLat, entity.centroidLng)

        val nameSource = when {
            entity.userDisplayName != null -> "Custom name"
            entity.bestProviderName != null -> "via ${entity.bestProviderSource ?: "Provider"}"
            else -> "Coordinates"
        }

        return TimelinePlace(
            placeId = entity.placeId,
            displayName = displayName,
            nameSource = nameSource,
            category = parsePlaceCategory(entity.category),
            confidence = entity.confidence,
            lat = entity.centroidLat,
            lng = entity.centroidLng
        )
    }

    private fun mapToTimelineRoute(entity: RouteEntity): TimelineRoute {
        return TimelineRoute(
            routeId = entity.routeId,
            encodedPolyline = entity.encodedPolyline,
            simplifiedPolyline = entity.simplifiedPolyline,
            totalDistanceM = entity.totalDistanceM,
            avgSpeedMps = entity.avgSpeedMps,
            transportMode = entity.transportMode,
            boundingBoxJson = entity.boundingBoxJson
        )
    }

    private fun parseSegmentType(raw: String): SegmentType {
        return try {
            SegmentType.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            SegmentType.UNKNOWN_MOTION
        }
    }

    private fun parsePlaceCategory(raw: String): PlaceCategory {
        return try {
            PlaceCategory.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            PlaceCategory.UNKNOWN
        }
    }

    private fun formatCoordinates(lat: Double, lng: Double): String {
        return "%.4f, %.4f".format(lat, lng)
    }
}
