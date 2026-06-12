package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import javax.inject.Inject

/**
 * Lightweight integrity repair that can run after each visit departure.
 * Fixes overlapping visits for a single day without waiting for the daily worker.
 *
 * Every repair action writes an audit row to [HealthLogEntity] with
 * eventType `REPAIR_*` and detailsJson describing the before/after state.
 * That makes silent corrections visible to the user — "we adjusted this
 * visit from 4:23pm to 4:18pm because it overlapped the next one" — and
 * also gives the team a debuggable trail when production data drifts.
 */
class IntegrityRepairUseCase @Inject constructor(
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao,
    private val healthLogDao: HealthLogDao
) {

    private suspend fun audit(eventType: String, details: String) {
        runCatching {
            healthLogDao.insert(
                HealthLogEntity(
                    eventType = eventType,
                    eventAt = System.currentTimeMillis(),
                    detailsJson = details
                )
            )
        }
    }
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
                audit(
                    "REPAIR_VISIT_OVERLAP",
                    """{"visitId":${current.visitId},"oldDeparture":$currentDeparture,"newDeparture":$clampedDeparture,"reason":"clamped to next visit arrival"}"""
                )
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
                    val enclosed = seg.startAt >= visit.arrivalAt && seg.endAt <= visitDeparture
                    val spansVisit = seg.startAt < visit.arrivalAt && seg.endAt > visitDeparture
                    when {
                        enclosed -> {
                            // Segment fully enclosed within visit — delete it
                            movementSegmentDao.delete(seg)
                            audit(
                                "REPAIR_SEGMENT_DELETED",
                                """{"segmentId":${seg.segmentId},"type":"${seg.segmentType}","reason":"enclosed within visit ${visit.visitId}"}"""
                            )
                        }
                        spansVisit -> {
                            // Segment straddles the whole visit — split it so the post-departure
                            // travel isn't dropped (which would leave a timeline gap). Keep the
                            // original as the pre-arrival half and insert a fresh post-departure
                            // half; distance stays on the original to avoid inflating the total.
                            val afterId = movementSegmentDao.insert(
                                seg.copy(segmentId = 0, startAt = visitDeparture, distanceM = 0.0)
                            )
                            movementSegmentDao.update(seg.copy(endAt = visit.arrivalAt))
                            audit(
                                "REPAIR_SEGMENT_SPLIT",
                                """{"segmentId":${seg.segmentId},"newSegmentId":$afterId,"visitId":${visit.visitId},"arrival":${visit.arrivalAt},"departure":$visitDeparture,"reason":"split around enclosed visit"}"""
                            )
                        }
                        seg.startAt < visit.arrivalAt -> {
                            // Segment starts before visit — trim end
                            movementSegmentDao.update(seg.copy(endAt = visit.arrivalAt))
                            audit(
                                "REPAIR_SEGMENT_TRIMMED",
                                """{"segmentId":${seg.segmentId},"side":"end","oldEnd":${seg.endAt},"newEnd":${visit.arrivalAt}}"""
                            )
                        }
                        else -> {
                            // Segment ends after visit — trim start
                            movementSegmentDao.update(seg.copy(startAt = visitDeparture))
                            audit(
                                "REPAIR_SEGMENT_TRIMMED",
                                """{"segmentId":${seg.segmentId},"side":"start","oldStart":${seg.startAt},"newStart":$visitDeparture}"""
                            )
                        }
                    }
                    fixed++
                }
            }
        }

        return fixed
    }

    /**
     * Close stale open visits — those still open (departureAt null) whose arrival is older
     * than [staleBeforeMs]. Each is closed at [closeAtMs], which must be the last moment we
     * actually had tracking data (e.g. the latest raw sample's time), NOT the selection
     * cutoff — otherwise the dwell is truncated by the whole stale-gap window (T3). A stored
     * dwell, if present, wins; [closeAtMs] is clamped to never precede the visit's arrival.
     * @return number of visits closed
     */
    suspend fun closeStaleVisits(staleBeforeMs: Long, closeAtMs: Long): Int {
        val staleVisits = visitDao.getStaleOpenVisits(staleBeforeMs)
        for (visit in staleVisits) {
            val departureAt = if (visit.dwellMs != null && visit.dwellMs > 0) {
                visit.arrivalAt + visit.dwellMs
            } else {
                closeAtMs.coerceAtLeast(visit.arrivalAt)
            }
            val dwellMs = departureAt - visit.arrivalAt
            visitDao.endVisit(visit.visitId, departureAt, dwellMs)
            audit(
                "REPAIR_VISIT_STRANDED",
                """{"visitId":${visit.visitId},"arrival":${visit.arrivalAt},"departure":$departureAt,"reason":"open visit closed at last-known-alive time"}"""
            )
        }
        return staleVisits.size
    }
}
