package com.cosmiclaboratory.voyager.data.pipeline

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.pipeline.ActivityRef
import com.cosmiclaboratory.voyager.pipeline.PipelineGateway
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.pipeline.SegmentRef
import com.cosmiclaboratory.voyager.pipeline.StepBucket
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawActivitySampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [PipelineGateway] — the one place that maps the pipeline's
 * domain-ish types to Room entities. Living in `data/` keeps Room out of the
 * pipeline package (audit A6 / KMP seam).
 *
 * Each method is intentionally thin: the consumer keeps its logic, this only
 * shuttles values across the storage boundary.
 */
@Singleton
class PipelineGatewayImpl @Inject constructor(
    private val rawLocationSampleDao: RawLocationSampleDao,
    private val rawActivitySampleDao: RawActivitySampleDao,
    private val rawStepSampleDao: RawStepSampleDao,
    private val movementSegmentDao: MovementSegmentDao
) : PipelineGateway {

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

    override suspend fun latestSegment(): SegmentRef? =
        movementSegmentDao.getLatest()?.let {
            SegmentRef(segmentId = it.segmentId, segmentType = it.segmentType, endAt = it.endAt)
        }

    override suspend fun updateSegmentEndAt(segmentId: Long, endAt: Long) {
        // No targeted-update query on the DAO; fetch + copy + update keeps row-version
        // and audit columns consistent rather than touching only one column.
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

    override suspend fun latestActivity(): ActivityRef? =
        rawActivitySampleDao.getLatest()?.let {
            ActivityRef(activityType = it.activityType, capturedAt = it.capturedAt, confidence = it.confidence)
        }

    override suspend fun stepBuckets(fromMs: Long, toMs: Long): List<StepBucket> =
        rawStepSampleDao.getByTimeRange(fromMs, toMs).map {
            StepBucket(stepCount = it.stepCount, periodStart = it.periodStart, periodEnd = it.periodEnd)
        }
}
