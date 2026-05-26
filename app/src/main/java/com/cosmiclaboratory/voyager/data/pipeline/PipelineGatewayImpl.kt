package com.cosmiclaboratory.voyager.data.pipeline

import androidx.room.withTransaction
import com.cosmiclaboratory.voyager.pipeline.ActivityRef
import com.cosmiclaboratory.voyager.pipeline.EvidenceDraft
import com.cosmiclaboratory.voyager.pipeline.PipelineGateway
import com.cosmiclaboratory.voyager.pipeline.PlaceDraft
import com.cosmiclaboratory.voyager.pipeline.PlaceRef
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.pipeline.RouteDraft
import com.cosmiclaboratory.voyager.pipeline.SegmentDraft
import com.cosmiclaboratory.voyager.pipeline.SegmentRef
import com.cosmiclaboratory.voyager.pipeline.StepBucket
import com.cosmiclaboratory.voyager.pipeline.VisitRef
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawActivitySampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SegmentEvidenceEntity
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [PipelineGateway] — the one place that maps the pipeline's
 * domain-ish types to Room entities. Living in `data/` keeps Room out of the
 * `pipeline/` package (audit A6 / KMP seam).
 *
 * Methods are thin (the consumer keeps its logic). The one exception is
 * [commitClosedSegment]: the **atomic transaction** that writes segment +
 * evidence + route lives here so callers don't import `withTransaction`.
 */
@Singleton
class PipelineGatewayImpl @Inject constructor(
    private val database: VoyagerDatabase,
    private val rawLocationSampleDao: RawLocationSampleDao,
    private val rawActivitySampleDao: RawActivitySampleDao,
    private val rawStepSampleDao: RawStepSampleDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val routeDao: RouteDao,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao
) : PipelineGateway {

    // ---------- raw samples ----------

    override suspend fun recordSample(sample: RawSample): Long =
        rawLocationSampleDao.insert(
            RawLocationSampleEntity(
                capturedAt = sample.capturedAt,
                receivedAt = System.currentTimeMillis(),
                lat = sample.lat, lng = sample.lng,
                accuracyM = sample.accuracyM,
                verticalAccuracyM = sample.verticalAccuracyM,
                speedMps = sample.speedMps,
                bearingDeg = sample.bearingDeg,
                altitudeM = sample.altitudeM,
                provider = sample.provider,
                isMock = sample.isMock,
                batteryPct = sample.batteryPct,
                isCharging = sample.isCharging,
                deviceIdleMode = sample.deviceIdleMode,
                permissionSnapshot = sample.permissionSnapshot,
                trackingSessionId = sample.trackingSessionId,
                localTimeZone = sample.localTimeZone,
                geohash = sample.geohash
            )
        )

    // ---------- segments ----------

    override suspend fun latestSegment(): SegmentRef? =
        movementSegmentDao.getLatest()?.toRef()

    override suspend fun updateSegmentEndAt(segmentId: Long, endAt: Long) {
        // No targeted-update query on the DAO; fetch + copy + update keeps audit columns consistent.
        movementSegmentDao.getById(segmentId)?.let {
            movementSegmentDao.update(it.copy(endAt = endAt))
        }
    }

    override suspend fun recordGapSegment(startAt: Long, endAt: Long, dayKey: String, reason: String) {
        movementSegmentDao.insert(
            MovementSegmentEntity(
                segmentType = SegmentType.GAP.name,
                startAt = startAt,
                endAt = endAt,
                gapReason = reason,
                dayKey = dayKey,
                confidence = 1.0f
            )
        )
    }

    override suspend fun commitClosedSegment(
        segment: SegmentDraft,
        evidence: EvidenceDraft,
        route: RouteDraft?
    ): Long = database.withTransaction {
        val segmentEntity = MovementSegmentEntity(
            segmentType = segment.segmentType,
            startAt = segment.startAt,
            endAt = segment.endAt,
            startSampleId = segment.startSampleId,
            endSampleId = segment.endSampleId,
            distanceM = segment.distanceM,
            confidence = segment.confidence,
            dayKey = segment.dayKey
        )
        val segmentId = movementSegmentDao.insert(segmentEntity)
        segmentEvidenceDao.upsert(
            SegmentEvidenceEntity(
                segmentId = segmentId,
                avgSpeedMps = evidence.avgSpeedMps,
                maxSpeedMps = evidence.maxSpeedMps,
                sampleCount = evidence.sampleCount,
                activityVotesJson = evidence.activityVotesJson
            )
        )
        if (route != null) {
            val routeId = routeDao.insert(
                RouteEntity(
                    segmentId = segmentId,
                    encodedPolyline = route.encodedPolyline,
                    simplifiedPolyline = route.simplifiedPolyline,
                    totalDistanceM = route.totalDistanceM,
                    totalDurationMs = route.totalDurationMs,
                    avgSpeedMps = route.avgSpeedMps,
                    maxSpeedMps = route.maxSpeedMps,
                    transportMode = route.transportMode,
                    sampleCount = route.sampleCount
                )
            )
            movementSegmentDao.update(
                segmentEntity.copy(segmentId = segmentId, routeId = routeId)
            )
        }
        segmentId
    }

    override suspend fun segmentsForDay(dayKey: String): List<SegmentRef> =
        movementSegmentDao.getByDayKey(dayKey).map { it.toRef() }

    override suspend fun setSegmentPlace(segmentId: Long, placeId: Long) {
        movementSegmentDao.getById(segmentId)?.let {
            movementSegmentDao.update(it.copy(placeId = placeId))
        }
    }

    // ---------- visits ----------

    override suspend fun getVisit(visitId: Long): VisitRef? =
        visitDao.getById(visitId)?.let {
            VisitRef(
                visitId = it.visitId,
                placeId = it.placeId,
                dayKey = it.dayKey,
                arrivalAt = it.arrivalAt,
                departureAt = it.departureAt
            )
        }

    override suspend fun setVisitPlace(visitId: Long, placeId: Long) {
        visitDao.getById(visitId)?.let {
            visitDao.update(it.copy(placeId = placeId))
        }
    }

    override suspend fun countVisitsForPlace(placeId: Long): Int =
        visitDao.countByPlaceId(placeId)

    // ---------- activity / steps ----------

    override suspend fun latestActivity(): ActivityRef? =
        rawActivitySampleDao.getLatest()?.let {
            ActivityRef(activityType = it.activityType, capturedAt = it.capturedAt, confidence = it.confidence)
        }

    override suspend fun stepBuckets(fromMs: Long, toMs: Long): List<StepBucket> =
        rawStepSampleDao.getByTimeRange(fromMs, toMs).map {
            StepBucket(stepCount = it.stepCount, periodStart = it.periodStart, periodEnd = it.periodEnd)
        }

    // ---------- places ----------

    override suspend fun getPlace(placeId: Long): PlaceRef? =
        placeDao.getById(placeId)?.toRef()

    override suspend fun setPlaceLifecycle(placeId: Long, lifecycleStatus: String, confidence: Float) {
        placeDao.getById(placeId)?.let {
            placeDao.update(it.copy(lifecycleStatus = lifecycleStatus, confidence = confidence))
        }
    }

    override suspend fun touchPlaceVisited(placeId: Long, atMs: Long) {
        placeDao.getById(placeId)?.let {
            placeDao.update(it.copy(lastVisitedAt = atMs))
        }
    }

    override suspend fun placesNearGeohash(prefix: String): List<PlaceRef> =
        placeDao.getByGeohashPrefix(prefix).map { it.toRef() }

    override suspend fun createCandidatePlace(draft: PlaceDraft): Long =
        placeDao.insert(
            PlaceEntity(
                centroidLat = draft.centroidLat,
                centroidLng = draft.centroidLng,
                radiusM = draft.radiusM,
                geohash = draft.geohash,
                confidence = draft.confidence,
                lifecycleStatus = draft.lifecycleStatus,
                createdAt = draft.createdAt,
                lastVisitedAt = draft.lastVisitedAt
            )
        )

    // ---------- entity → ref mappers (private) ----------

    private fun MovementSegmentEntity.toRef() = SegmentRef(
        segmentId = segmentId,
        segmentType = segmentType,
        startAt = startAt,
        endAt = endAt,
        placeId = placeId
    )

    private fun PlaceEntity.toRef() = PlaceRef(
        placeId = placeId,
        centroidLat = centroidLat,
        centroidLng = centroidLng,
        lifecycleStatus = lifecycleStatus,
        confidence = confidence
    )
}
