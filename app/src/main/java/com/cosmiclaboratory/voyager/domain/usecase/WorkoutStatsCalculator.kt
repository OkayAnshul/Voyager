package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.DistanceUnit
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.Split
import com.cosmiclaboratory.voyager.domain.model.WorkoutStats
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import kotlin.math.roundToInt

/**
 * Pure summariser for a recorded route — distance, duration, average/peak speed, and elevation.
 *
 * Stateless and deterministic so it's trivially unit-testable; [WorkoutRecorder] uses it to
 * finalise an activity, and tests pin the maths directly.
 */
object WorkoutStatsCalculator {

    /**
     * A single implausible jump (GPS glitch / tunnel re-acquire) shouldn't become a
     * 300 m/s "max speed" — nor inflate the total distance. Legs whose implied speed exceeds
     * this (or with a non-positive time delta) are dropped from both distance and peak speed,
     * matching [WorkoutRecorder]'s live accumulation so the saved summary agrees with what the
     * user watched.
     */
    private const val MAX_PLAUSIBLE_SPEED_MPS = 50f // ~180 km/h

    /**
     * Raw GPS altitude jitters ±5–15 m even standing still. Only count elevation change once it
     * exceeds this band from the last committed reference — the standard hysteresis every fitness
     * app applies so a flat run doesn't report hundreds of phantom metres of climb.
     */
    private const val ELEVATION_NOISE_THRESHOLD_M = 3.0

    fun summarize(points: List<RoutePoint>): WorkoutStats {
        if (points.size < 2) return WorkoutStats(0.0, 0L, 0f, 0f)

        var distance = 0.0
        var maxSpeed = 0f
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val dtMs = cur.timeMs - prev.timeMs
            if (dtMs <= 0) continue // can't validate an out-of-order / zero-duration leg
            val segDist = LocationUtils.calculateDistance(prev.lat, prev.lng, cur.lat, cur.lng)
            val segSpeed = LocationUtils.speedMps(segDist, dtMs)
            if (segSpeed !in 0f..MAX_PLAUSIBLE_SPEED_MPS) continue // drop the glitch leg entirely
            distance += segDist
            if (segSpeed > maxSpeed) maxSpeed = segSpeed
        }
        val duration = points.last().timeMs - points.first().timeMs
        val avgSpeed = if (duration > 0) LocationUtils.speedMps(distance, duration) else 0f
        val (gain, loss) = elevation(points)
        return WorkoutStats(
            distanceMeters = distance,
            durationMs = duration,
            avgSpeedMps = avgSpeed,
            maxSpeedMps = maxSpeed,
            elevationGainM = gain,
            elevationLossM = loss
        )
    }

    /**
     * Cumulative elevation gain/loss over the route with noise hysteresis. Only advances the
     * reference altitude once movement clears [ELEVATION_NOISE_THRESHOLD_M], so GPS/baro jitter
     * around a stable altitude contributes nothing.
     */
    fun elevation(points: List<RoutePoint>): Pair<Double, Double> {
        val alts = points.mapNotNull { it.altitudeM }
        if (alts.size < 2) return 0.0 to 0.0
        var gain = 0.0
        var loss = 0.0
        var reference = alts.first()
        for (a in alts.drop(1)) {
            val delta = a - reference
            if (delta >= ELEVATION_NOISE_THRESHOLD_M) {
                gain += delta
                reference = a
            } else if (delta <= -ELEVATION_NOISE_THRESHOLD_M) {
                loss += -delta
                reference = a
            }
        }
        return gain to loss
    }

    /**
     * Per-[unit] splits (whole km or mile), interpolating time and elevation at each boundary
     * within the crossing leg so a split's pace reflects exactly that unit of distance.
     * The trailing partial distance is not returned as a split (matches Strava — only whole units).
     */
    fun splits(points: List<RoutePoint>, unit: DistanceUnit = DistanceUnit.KM): List<Split> {
        if (points.size < 2) return emptyList()
        val out = mutableListOf<Split>()
        val unitM = unit.meters

        var splitStartTimeMs = points.first().timeMs
        var splitGain = 0.0
        var cumulativeInSplit = 0.0
        var lastAlt = points.first().altitudeM

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val dtMs = cur.timeMs - prev.timeMs
            if (dtMs <= 0) continue
            val segDist = LocationUtils.calculateDistance(prev.lat, prev.lng, cur.lat, cur.lng)
            if (LocationUtils.speedMps(segDist, dtMs) !in 0f..MAX_PLAUSIBLE_SPEED_MPS) continue

            // Accumulate this leg's elevation gain (hysteresis-free within a leg; the coarse
            // per-split figure is fine — the authoritative total comes from [elevation]).
            val curAlt = cur.altitudeM
            if (curAlt != null && lastAlt != null && curAlt > lastAlt) splitGain += curAlt - lastAlt
            lastAlt = curAlt ?: lastAlt

            var legRemaining = segDist
            var legStartTime = prev.timeMs.toDouble()
            val legDurationMs = dtMs.toDouble()
            while (cumulativeInSplit + legRemaining >= unitM) {
                val need = unitM - cumulativeInSplit
                val frac = if (segDist > 0) need / segDist else 0.0
                val boundaryTimeMs = (legStartTime + legDurationMs * (frac)).toLong()
                out.add(
                    Split(
                        index = out.size + 1,
                        distanceMeters = unitM,
                        durationMs = boundaryTimeMs - splitStartTimeMs,
                        elevationGainM = splitGain
                    )
                )
                // Start the next split at the interpolated boundary.
                splitStartTimeMs = boundaryTimeMs
                splitGain = 0.0
                cumulativeInSplit = 0.0
                legRemaining -= need
                legStartTime = boundaryTimeMs.toDouble()
            }
            cumulativeInSplit += legRemaining
        }
        return out
    }

    /**
     * Rebuilds the [RoutePoint] stream from a persisted activity's parallel encoded streams
     * (lat/lng polyline + time offsets + altitudes). Used by the detail screen, splits, and GPX
     * export. Streams shorter than the polyline are tolerated (older rows lacked them).
     */
    fun reconstruct(
        encodedPolyline: String,
        encodedTimes: String,
        encodedAltitudes: String,
        startedAt: Long
    ): List<RoutePoint> {
        val coords = PolylineEncoder.decode(encodedPolyline)
        val timeOffsets = if (encodedTimes.isNotEmpty()) PolylineEncoder.decodeInts(encodedTimes) else emptyList()
        val altDecimetres = if (encodedAltitudes.isNotEmpty()) PolylineEncoder.decodeInts(encodedAltitudes) else emptyList()
        return coords.mapIndexed { i, (lat, lng) ->
            RoutePoint(
                lat = lat,
                lng = lng,
                timeMs = startedAt + (timeOffsets.getOrNull(i)?.toLong() ?: 0L),
                altitudeM = altDecimetres.getOrNull(i)?.let { it / 10.0 }
            )
        }
    }

    /** Encodes per-point time offsets (ms from [startedAt]) for compact storage. */
    fun encodeTimes(points: List<RoutePoint>, startedAt: Long): String =
        PolylineEncoder.encodeInts(points.map { (it.timeMs - startedAt).toInt() })

    /**
     * Encodes per-point altitude in decimetres. Missing altitudes carry the last known value
     * (or the first known, for leading gaps) so the stream stays aligned with the polyline;
     * returns "" when no point had any altitude.
     */
    fun encodeAltitudes(points: List<RoutePoint>): String {
        if (points.none { it.altitudeM != null }) return ""
        val firstKnown = points.firstNotNullOfOrNull { it.altitudeM } ?: 0.0
        var last = firstKnown
        val decimetres = points.map { p ->
            val a = p.altitudeM ?: last
            last = a
            (a * 10).roundToInt()
        }
        return PolylineEncoder.encodeInts(decimetres)
    }
}
