package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import javax.inject.Inject

/**
 * Lightweight integrity repair that can run after each visit departure.
 * Fixes overlapping visits for a single day without waiting for the daily worker.
 */
class IntegrityRepairUseCase @Inject constructor(
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao
) {
    /**
     * Check and fix visit overlaps for a single day.
     * Clamps the departure of earlier visits to the arrival of later ones.
     * @return number of overlaps fixed
     */
    suspend fun repairDay(dayKey: String): Int {
        val visits = visitDao.getByDayKey(dayKey).sortedBy { it.arrivalAt }
        var fixed = 0
        for (i in 0 until visits.size - 1) {
            val current = visits[i]
            val next = visits[i + 1]
            val currentDeparture = current.departureAt ?: continue
            if (currentDeparture > next.arrivalAt) {
                val clampedDeparture = next.arrivalAt
                val clampedDwell = clampedDeparture - current.arrivalAt
                visitDao.endVisit(current.visitId, clampedDeparture, clampedDwell)
                fixed++
            }
        }

        // Trim movement segments that overlap with completed visits.
        // Segmenter flush can create segments that cross visit arrival/departure times.
        val stationaryTypes = setOf(SegmentType.VISIT.name, SegmentType.DWELL.name, SegmentType.GAP.name)
        val segments = movementSegmentDao.getByDayKey(dayKey)
        for (visit in visits.filter { it.departureAt != null }) {
            for (seg in segments) {
                if (seg.segmentType in stationaryTypes) continue
                val visitDeparture = visit.departureAt!!
                if (seg.startAt < visitDeparture && seg.endAt > visit.arrivalAt) {
                    if (seg.startAt >= visit.arrivalAt && seg.endAt <= visitDeparture) {
                        // Segment fully enclosed within visit — delete it
                        movementSegmentDao.delete(seg)
                    } else if (seg.startAt < visit.arrivalAt) {
                        // Segment starts before visit — trim end
                        movementSegmentDao.update(seg.copy(endAt = visit.arrivalAt))
                    } else {
                        // Segment ends after visit — trim start
                        movementSegmentDao.update(seg.copy(startAt = visitDeparture))
                    }
                    fixed++
                }
            }
        }

        return fixed
    }

    /**
     * Close stale open visits whose arrival is older than [cutoffMs].
     * @return number of visits closed
     */
    suspend fun closeStaleVisits(cutoffMs: Long): Int {
        val staleVisits = visitDao.getStaleOpenVisits(cutoffMs)
        for (visit in staleVisits) {
            // Use stored dwell if available; otherwise close at the cutoff time
            // (the latest moment we know tracking was still alive). This is a better
            // approximation than arrival + arbitrary 1 hour, which is wrong for both
            // quick stops (inflated) and long stays (possibly underestimated).
            val departureAt = if (visit.dwellMs != null && visit.dwellMs > 0) {
                visit.arrivalAt + visit.dwellMs
            } else {
                cutoffMs
            }
            val dwellMs = departureAt - visit.arrivalAt
            visitDao.endVisit(visit.visitId, departureAt, dwellMs)
        }
        return staleVisits.size
    }
}
