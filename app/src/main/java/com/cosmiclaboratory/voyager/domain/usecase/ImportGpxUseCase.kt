package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import com.cosmiclaboratory.voyager.platform.export.GpxImporter
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.entity.ActivityEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Imports a GPX track as a recorded [com.cosmiclaboratory.voyager.domain.model.Activity], so users
 * can bring their Strava/Garmin history into Voyager. Uses the same stats + encoders as a live
 * recording, so an imported activity gets splits/elevation/pace when the GPX carries timestamps.
 */
class ImportGpxUseCase @Inject constructor(
    private val activityDao: ActivityDao,
) {
    private companion object {
        val DAY_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    /** Returns the new activity id, or null if the GPX had no usable track. */
    suspend fun import(xml: String, type: WorkoutType, nowMs: Long = System.currentTimeMillis()): Long? {
        val parsed = runCatching { GpxImporter.parse(xml) }.getOrNull() ?: return null
        val points = parsed.points
        if (points.size < 2) return null

        val allTimed = points.all { it.timeMs > 0 }
        val startedAt = if (allTimed) points.first().timeMs else nowMs
        val endedAt = if (allTimed) points.last().timeMs else nowMs

        val distance: Double
        val duration: Long
        val avg: Float
        val max: Float
        if (allTimed) {
            val stats = WorkoutStatsCalculator.summarize(points)
            distance = stats.distanceMeters
            duration = stats.durationMs
            avg = stats.avgSpeedMps
            max = stats.maxSpeedMps
        } else {
            distance = distanceOnly(points)
            duration = 0L
            avg = 0f
            max = 0f
        }
        val (gain, loss) = WorkoutStatsCalculator.elevation(points)

        val entity = ActivityEntity(
            activityType = type.name,
            startedAt = startedAt,
            endedAt = endedAt,
            distanceMeters = distance,
            durationMs = duration,
            avgSpeedMps = avg,
            maxSpeedMps = max,
            encodedPolyline = PolylineEncoder.encode(points.map { it.lat to it.lng }),
            dayKey = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY_KEY),
            title = parsed.name,
            elevationGainM = gain,
            elevationLossM = loss,
            encodedTimes = if (allTimed) WorkoutStatsCalculator.encodeTimes(points, startedAt) else "",
            encodedAltitudes = WorkoutStatsCalculator.encodeAltitudes(points),
        )
        return activityDao.insert(entity)
    }

    private fun distanceOnly(points: List<RoutePoint>): Double {
        var d = 0.0
        for (i in 1 until points.size) {
            d += LocationUtils.calculateDistance(points[i - 1].lat, points[i - 1].lng, points[i].lat, points[i].lng)
        }
        return d
    }
}
