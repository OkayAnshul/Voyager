package com.cosmiclaboratory.voyager.capture

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import com.cosmiclaboratory.voyager.pipeline.PipelineSerializer
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipelineSerializer: PipelineSerializer,
    private val permissionMonitor: PermissionMonitor,
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy,
    private val timelineStateStore: TimelineStateStore
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var passiveCallback: LocationCallback? = null
    @Volatile private var activeSessionId: Long = 0
    @Volatile private var lastProcessedTimestamp: Long = 0

    @SuppressLint("MissingPermission")
    fun start(sessionId: Long) {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        activeSessionId = sessionId

        // Restore lastProcessedTimestamp from persisted state to reject old cached
        // FLP locations that arrive after process restart with stale timestamps.
        val persistedTimestamp = runBlocking { timelineStateStore.getState().lastAcceptedAt }
        if (persistedTimestamp != null && persistedTimestamp > 0) {
            lastProcessedTimestamp = persistedTimestamp
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val policy = adaptiveSamplingPolicy.getCurrentPolicy()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, policy.intervalMs)
            .setMinUpdateDistanceMeters(policy.minDistanceM)
            .setMaxUpdateDelayMillis(policy.intervalMs * 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Deduplicate by timestamp — FLP can return the same cached fix
                // multiple times in a batch when stationary
                for (location in result.locations) {
                    if (location.time > lastProcessedTimestamp) {
                        lastProcessedTimestamp = location.time
                        scope.launch { processLocation(location) }
                    }
                }
            }
        }

        fusedLocationClient?.requestLocationUpdates(
            request, locationCallback!!, Looper.getMainLooper()
        )

        // Passive location: piggyback on other apps' location requests for free data.
        // Zero additional battery cost — only receives locations already being fetched.
        // Especially valuable in DORMANT mode when active GPS is off.
        passiveCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    if (location.time > lastProcessedTimestamp) {
                        lastProcessedTimestamp = location.time
                        scope.launch { processLocation(location) }
                    }
                }
            }
        }
        val passiveRequest = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 5_000)
            .setMinUpdateDistanceMeters(10f)
            .build()
        fusedLocationClient?.requestLocationUpdates(
            passiveRequest, passiveCallback!!, Looper.getMainLooper()
        )
    }

    fun stop() {
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        passiveCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        locationCallback = null
        passiveCallback = null
        fusedLocationClient = null
    }

    @SuppressLint("MissingPermission")
    fun updateSamplingPolicy() {
        val callback = locationCallback ?: return
        val client = fusedLocationClient ?: return

        // Guard against permission revocation mid-tracking — FLP call would crash
        if (!permissionMonitor.hasLocationPermission()) {
            client.removeLocationUpdates(callback)
            return
        }

        val policy = adaptiveSamplingPolicy.getCurrentPolicy()

        if (policy.intervalMs <= 0) {
            // DORMANT mode — stop active GPS requests entirely.
            // SignificantMotionDetector handles wake-up.
            client.removeLocationUpdates(callback)
            return
        }

        // FLP replaces the existing request for the same callback — no GPS gap
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, policy.intervalMs)
            .setMinUpdateDistanceMeters(policy.minDistanceM)
            .setMaxUpdateDelayMillis(policy.intervalMs * 2)
            .build()

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private suspend fun processLocation(location: android.location.Location) {
        val timeZone = java.util.TimeZone.getDefault().id
        val geohash = GeohashEncoder.encode(location.latitude, location.longitude)

        // Build RawSample only — PipelineConsumer is the single DB insert point
        val rawSample = RawSample(
            capturedAt = location.time,
            lat = location.latitude,
            lng = location.longitude,
            accuracyM = location.accuracy,
            verticalAccuracyM = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
            bearingDeg = if (location.hasBearing()) location.bearing else null,
            altitudeM = if (location.hasAltitude()) location.altitude else null,
            provider = location.provider ?: "fused",
            isMock = location.isFromMockProvider,
            batteryPct = getBatteryPct(),
            isCharging = isCharging(),
            deviceIdleMode = isDeviceIdle(),
            permissionSnapshot = permissionMonitor.getPermissionSnapshot(),
            trackingSessionId = activeSessionId,
            localTimeZone = timeZone,
            geohash = geohash
        )

        pipelineSerializer.submitSample(rawSample)
    }

    private fun getBatteryPct(): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isCharging(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        return bm?.isCharging ?: false
    }

    private fun isDeviceIdle(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        return pm?.isDeviceIdleMode ?: false
    }
}
