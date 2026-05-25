package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.LiveWorkoutStats
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.model.enums.TrackingTier
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.domain.util.PolylineEncoder
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.entity.ActivityEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives an in-progress workout recording (Athlete persona).
 *
 * [start] switches the engine to the high-accuracy [TrackingTier.WORKOUT] tier; the
 * foreground location stream calls [onLocation] for each fix; [stop] finalises and
 * persists an [ActivityEntity] and restores the prior tier. [liveStats] feeds the
 * (deferred) Record screen.
 *
 * Live stats are accumulated incrementally; the persisted summary is recomputed
 * authoritatively by [WorkoutStatsCalculator] at save time.
 */
@Singleton
class WorkoutRecorder @Inject constructor(
    private val activityDao: ActivityDao,
    private val settingsRepository: SettingsRepository
) {
    private companion object {
        /** Drop implausible legs (GPS glitch) from live distance/speed. ~180 km/h. */
        const val MAX_PLAUSIBLE_SPEED_MPS = 50f
        val DAY_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val mutex = Mutex()

    private val _liveStats = MutableStateFlow<LiveWorkoutStats?>(null)
    val liveStats: StateFlow<LiveWorkoutStats?> = _liveStats.asStateFlow()

    @Volatile
    var isRecording: Boolean = false
        private set

    private var type = WorkoutType.OTHER
    private var startedAt = 0L
    private var priorTier = TrackingTier.BALANCED
    private val points = mutableListOf<RoutePoint>()
    private var runningDistance = 0.0
    private var maxSpeed = 0f
    private var lastPoint: RoutePoint? = null

    /** Begins recording — switches to the WORKOUT tracking tier. No-op if already recording. */
    suspend fun start(type: WorkoutType, nowMs: Long = System.currentTimeMillis()) = mutex.withLock {
        if (isRecording) return@withLock
        priorTier = settingsRepository.observeSettings().value.trackingTier
        settingsRepository.updateSetting("tracking_tier", TrackingTier.WORKOUT.name)
        this.type = type
        startedAt = nowMs
        points.clear()
        runningDistance = 0.0
        maxSpeed = 0f
        lastPoint = null
        isRecording = true
        _liveStats.value = LiveWorkoutStats(type, 0.0, 0L, 0f, 0f)
    }

    /** Feeds one GPS fix into the active recording (ignored when not recording). */
    fun onLocation(lat: Double, lng: Double, timeMs: Long) {
        if (!isRecording) return
        val point = RoutePoint(lat, lng, timeMs)
        var currentSpeed = 0f
        val prev = lastPoint
        if (prev != null) {
            val segDist = LocationUtils.calculateDistance(prev.lat, prev.lng, lat, lng)
            val dt = timeMs - prev.timeMs
            if (dt > 0) {
                val s = LocationUtils.speedMps(segDist, dt)
                if (s in 0f..MAX_PLAUSIBLE_SPEED_MPS) {
                    currentSpeed = s
                    runningDistance += segDist
                    if (s > maxSpeed) maxSpeed = s
                }
            }
        }
        points.add(point)
        lastPoint = point
        val duration = timeMs - startedAt
        val avg = if (duration > 0) LocationUtils.speedMps(runningDistance, duration) else 0f
        _liveStats.value = LiveWorkoutStats(type, runningDistance, duration, currentSpeed, avg)
    }

    /**
     * Finalises the recording: persists the [Activity], restores the prior tracking
     * tier, and returns the saved activity — or null if too few points were captured.
     */
    suspend fun stop(): Activity? = mutex.withLock {
        if (!isRecording) return@withLock null
        isRecording = false
        settingsRepository.updateSetting("tracking_tier", priorTier.name)
        _liveStats.value = null
        val captured = points.toList()
        points.clear()
        if (captured.size < 2) return@withLock null

        val stats = WorkoutStatsCalculator.summarize(captured)
        val entity = ActivityEntity(
            activityType = type.name,
            startedAt = startedAt,
            endedAt = captured.last().timeMs,
            distanceMeters = stats.distanceMeters,
            durationMs = stats.durationMs,
            avgSpeedMps = stats.avgSpeedMps,
            maxSpeedMps = stats.maxSpeedMps,
            encodedPolyline = PolylineEncoder.encode(captured.map { it.lat to it.lng }),
            dayKey = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY_KEY)
        )
        val id = activityDao.insert(entity)
        entity.copy(activityId = id).toDomain()
    }
}

/** Maps a persisted recorded workout to its domain model. */
fun ActivityEntity.toDomain(): Activity = Activity(
    id = activityId,
    type = WorkoutType.fromName(activityType),
    startedAt = startedAt,
    endedAt = endedAt,
    distanceMeters = distanceMeters,
    durationMs = durationMs,
    avgSpeedMps = avgSpeedMps,
    maxSpeedMps = maxSpeedMps,
    steps = steps,
    encodedPolyline = encodedPolyline,
    dayKey = dayKey,
    title = title,
    notes = notes
)
