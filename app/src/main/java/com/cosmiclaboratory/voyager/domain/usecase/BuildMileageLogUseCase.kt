package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.MileageEntry
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.dao.MileageClassificationDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.entity.MileageClassificationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Builds the mileage log — every DRIVE segment in a date range joined with the user's
 * tax classification — and applies classification edits.
 *
 * Classifications live in their own sparse table, so a drive with no row is implicitly
 * [MileagePurpose.UNCLASSIFIED]. The log is reactive to classification edits; it
 * re-queries the (slowly changing) drive segments on each emission.
 */
class BuildMileageLogUseCase @Inject constructor(
    private val movementSegmentDao: MovementSegmentDao,
    private val mileageClassificationDao: MileageClassificationDao
) {

    /** Reactive mileage log for [range] — re-emits whenever any classification changes. */
    fun observeLog(range: DateRangePeriod): Flow<MileageLog> =
        mileageClassificationDao.observeAll().map { classifications ->
            build(range, classifications)
        }

    private suspend fun build(
        range: DateRangePeriod,
        classifications: List<MileageClassificationEntity>
    ): MileageLog {
        val (startDt, endDt) = range.toDateTimeRange()
        val segments = movementSegmentDao.getByTypesBetween(
            types = listOf(SegmentType.DRIVE.name),
            startDay = startDt.toLocalDate().toString(),
            endDay = endDt.toLocalDate().toString()
        )
        val bySegment = classifications.associateBy { it.segmentId }
        val entries = segments.map { seg ->
            val classification = bySegment[seg.segmentId]
            MileageEntry(
                segmentId = seg.segmentId,
                startAt = seg.startAt,
                endAt = seg.endAt,
                dayKey = seg.dayKey,
                distanceMeters = seg.distanceM,
                purpose = MileagePurpose.fromName(classification?.purpose),
                note = classification?.note
            )
        }
        return MileageLog(entries = entries, rangeLabel = range.displayLabel())
    }

    /**
     * Sets the tax [purpose] of a drive. Classifying as [MileagePurpose.UNCLASSIFIED]
     * removes the row — the absence of a row *is* "unclassified".
     */
    suspend fun classify(segmentId: Long, purpose: MileagePurpose, note: String? = null) {
        if (purpose == MileagePurpose.UNCLASSIFIED && note.isNullOrBlank()) {
            mileageClassificationDao.deleteBySegmentId(segmentId)
            return
        }
        val existing = mileageClassificationDao.getBySegmentId(segmentId)
        mileageClassificationDao.upsert(
            MileageClassificationEntity(
                segmentId = segmentId,
                purpose = purpose.name,
                note = note?.takeIf { it.isNotBlank() },
                lastModifiedAt = System.currentTimeMillis(),
                revision = (existing?.revision ?: 0L) + 1L
            )
        )
    }

    suspend fun clearClassification(segmentId: Long) {
        mileageClassificationDao.deleteBySegmentId(segmentId)
    }
}
