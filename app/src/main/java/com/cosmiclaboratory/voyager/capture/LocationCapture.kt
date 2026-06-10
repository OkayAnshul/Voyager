package com.cosmiclaboratory.voyager.capture

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.cosmiclaboratory.voyager.domain.time.Clock
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import com.cosmiclaboratory.voyager.pipeline.PipelineSerializer
import com.cosmiclaboratory.voyager.pipeline.RawSample
import com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipelineSerializer: PipelineSerializer,
    private val permissionMonitor: PermissionMonitor,
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy,
    private val timelineStateStore: TimelineStateStore,
    private val clock: Clock,
    private val healthLogDao: HealthLogDao,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var passiveCallback: LocationCallback? = null
    @Volatile private var activeSessionId: Long = 0
    private val admissionGate = SampleAdmissionGate()

    @SuppressLint("MissingPermission")
    fun start(sessionId: Long) {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        activeSessionId = sessionId

        // Seed lastProcessedTimestamp asynchronously from persisted state to reject old
        // cached FLP locations that arrive after process restart with stale timestamps.
        // Until the seed lands, dedup uses 0 — first sample always passes, which is fine.
        scope.launch {
            try {
                val persisted = timelineStateStore.getState().lastAcceptedAt
                // seed() does a CAS-max so it never clobbers a newer real fix that
                // arrived ahead of the seed.
                if (persisted != null) admissionGate.seed(persisted)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("LocationCapture", "Failed to seed lastProcessedTimestamp", e)
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val policy = adaptiveSamplingPolicy.getCurrentPolicy()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    handleIncoming(location)
                }
            }
        }

        // OFF / PASSIVE tier (interval <= 0): run no active GPS request — rely on
        // the always-on passive listener below plus motion/step sensors. A later
        // tier change is picked up by the policy-update path.
        if (policy.intervalMs > 0) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, policy.intervalMs)
                .setMinUpdateDistanceMeters(policy.minDistanceM)
                .setMaxUpdateDelayMillis(policy.intervalMs * 2)
                .build()
            fusedLocationClient?.requestLocationUpdates(
                request, locationCallback!!, Looper.getMainLooper()
            )
        }

        // Passive location: piggyback on other apps' location requests for free data.
        passiveCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    handleIncoming(location)
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

    /**
     * Dedup + sanity gate. Runs on Main thread (FLP callback dispatcher). The decision
     * logic lives in [SampleAdmissionGate] (pure + unit-tested); this only wires the
     * outcome to logging / pipeline submission. Covers manual time-travel (US2) and
     * stale-cached fixes from FLP (S2), and drops exact-duplicate fixes delivered by
     * both the active and passive callbacks.
     */
    private fun handleIncoming(location: android.location.Location) {
        val now = clock.wallClockMs()
        when (admissionGate.admit(location.time, now)) {
            SampleAdmissionGate.Admission.Accepted -> scope.launch { processLocation(location) }
            SampleAdmissionGate.Admission.RejectedFuture -> logSanityRejection("FUTURE", location, now)
            SampleAdmissionGate.Admission.RejectedPast ->
                logSanityRejection("PAST", location, admissionGate.lastAccepted)
            SampleAdmissionGate.Admission.Invalid,
            SampleAdmissionGate.Admission.Duplicate -> Unit // drop silently
        }
    }

    private fun logSanityRejection(kind: String, location: android.location.Location, reference: Long) {
        scope.launch {
            try {
                healthLogDao.insert(
                    HealthLogEntity(
                        eventType = "SAMPLE_REJECTED_$kind",
                        eventAt = clock.wallClockMs(),
                        detailsJson = "{\"locTs\":${location.time},\"ref\":$reference," +
                            "\"lat\":${location.latitude},\"lng\":${location.longitude}," +
                            "\"acc\":${location.accuracy}}"
                    )
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // best effort
            }
        }
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
