package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import javax.inject.Inject

/**
 * User-applied override of a segment's movement type.
 *
 * The classifier never gets this 100% right — the user tap is the
 * authoritative correction. The override is stored separately from the
 * classifier output (`segmentType`) so a future reclassification pass
 * cannot stomp on the user's choice. Display and aggregate queries
 * COALESCE the override with the classifier output.
 *
 * Also updates the linked RouteEntity's `transportMode` so map/route
 * rendering stays consistent with the user's choice — without losing
 * the original classifier label.
 *
 * Every override is mirrored into health_log so the user can see their
 * own correction history.
 */
class OverrideSegmentTypeUseCase @Inject constructor(
    private val movementSegmentDao: MovementSegmentDao,
    private val routeDao: RouteDao,
    private val healthLogDao: HealthLogDao,
) {

    /** Set a user override. Pass null to clear an existing one. */
    suspend fun setOverride(segmentId: Long, newType: SegmentType?) {
        val segment = movementSegmentDao.getById(segmentId) ?: return
        val now = System.currentTimeMillis()

        movementSegmentDao.setUserOverride(
            segmentId = segmentId,
            overrideType = newType?.name,
            overrideAt = if (newType != null) now else null,
            corrected = newType != null,
        )

        // Keep the route's transportMode aligned with the effective type so
        // map rendering uses the user's choice; falling back to the classifier
        // label when the override is cleared.
        val routeId = segment.routeId
        if (routeId != null) {
            val route = routeDao.getById(routeId)
            if (route != null) {
                val effectiveType = newType?.name ?: segment.segmentType
                if (route.transportMode != effectiveType) {
                    routeDao.update(route.copy(transportMode = effectiveType))
                }
            }
        }

        healthLogDao.insert(
            HealthLogEntity(
                eventType = if (newType != null) "USER_OVERRIDE_SEGMENT_TYPE"
                else "USER_CLEARED_SEGMENT_OVERRIDE",
                eventAt = now,
                detailsJson = """{"segmentId":$segmentId,"classifier":"${segment.segmentType}","override":${
                    if (newType != null) "\"${newType.name}\"" else "null"
                }}""",
            )
        )
    }
}
