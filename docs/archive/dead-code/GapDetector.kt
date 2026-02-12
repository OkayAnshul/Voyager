package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.model.enums.GapReason
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SegmentEvidenceEntity
import javax.inject.Inject

class GapDetector @Inject constructor(
    private val movementSegmentDao: MovementSegmentDao,
    private val segmentEvidenceDao: SegmentEvidenceDao
) {
    private var lastSampleTime: Long? = null
    private var expectedIntervalMs: Long = 15_000

    fun setExpectedInterval(intervalMs: Long) {
        expectedIntervalMs = intervalMs
    }

    suspend fun checkForGap(
        currentSample: RawSample,
        dayKey: String
    ): Boolean {
        val lastTime = lastSampleTime
        lastSampleTime = currentSample.capturedAt

        if (lastTime == null) return false

        val elapsed = currentSample.capturedAt - lastTime
        if (elapsed <= expectedIntervalMs * 3) return false

        // Gap detected — determine reason
        val reason = determineGapReason(currentSample, elapsed)

        val gapSegment = MovementSegmentEntity(
            segmentType = SegmentType.GAP.name,
            startAt = lastTime,
            endAt = currentSample.capturedAt,
            gapReason = reason.name,
            dayKey = dayKey,
            confidence = 1.0f
        )
        val segmentId = movementSegmentDao.insert(gapSegment)

        segmentEvidenceDao.upsert(
            SegmentEvidenceEntity(
                segmentId = segmentId,
                sampleCount = 0,
                explanationJson = """{"gapDurationMs":$elapsed,"reason":"${reason.name}"}"""
            )
        )

        return true
    }

    private fun determineGapReason(sample: RawSample, gapDurationMs: Long): GapReason {
        return when {
            sample.permissionSnapshot == "none" -> GapReason.PERMISSION
            sample.deviceIdleMode -> GapReason.DOZE
            gapDurationMs > 600_000 -> GapReason.PROCESS_DEAD // > 10 min likely process death
            else -> GapReason.GPS_LOSS
        }
    }

    fun reset() {
        lastSampleTime = null
    }
}
