package com.cosmiclaboratory.voyager.pipeline.stage

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 4-state Kalman filter for GPS smoothing.
 * State: [x, y, vx, vy] in meters (local tangent plane).
 * Constant velocity process model with GPS position measurements.
 *
 * Benefits over raw GPS:
 * - Smooth routes (5-15m jitter eliminated)
 * - Velocity-derived speed (more stable than raw GPS speed)
 * - Velocity-derived bearing (continuous, unlike raw GPS bearing which requires movement)
 * - Automatic outlier dampening (large innovations are weighted down)
 */
@Singleton
class LocationKalmanFilter @Inject constructor() {

    data class FilteredLocation(
        val lat: Double,
        val lng: Double,
        val estimatedSpeedMps: Float,
        val estimatedBearing: Float,
        val filterConfidence: Float
    )

    // State vector [x, y, vx, vy]
    private var x = DoubleArray(4)
    // 4x4 covariance matrix (stored flat as [row][col])
    private var P = Array(4) { DoubleArray(4) }
    private var lastTimestamp: Long = 0
    private var initialized = false
    private var referenceLat = 0.0
    private var referenceLng = 0.0

    // Process noise — how much we trust the constant velocity model.
    // Higher = filter adapts faster but smooths less.
    private val processNoiseAccel = 2.0 // m/s² — accounts for walking/driving acceleration

    fun reset() {
        initialized = false
        lastTimestamp = 0
    }

    fun filter(lat: Double, lng: Double, accuracyM: Float, timestamp: Long): FilteredLocation {
        if (!initialized) {
            referenceLat = lat
            referenceLng = lng
            x[0] = 0.0  // x position (meters from reference)
            x[1] = 0.0  // y position
            x[2] = 0.0  // vx
            x[3] = 0.0  // vy
            // Initial covariance: large position uncertainty, very large velocity uncertainty
            for (i in 0..3) for (j in 0..3) P[i][j] = 0.0
            P[0][0] = accuracyM.toDouble() * accuracyM.toDouble()
            P[1][1] = P[0][0]
            P[2][2] = 100.0  // 10 m/s uncertainty
            P[3][3] = 100.0
            lastTimestamp = timestamp
            initialized = true
            return FilteredLocation(lat, lng, 0f, 0f, 1f / accuracyM)
        }

        val dt = (timestamp - lastTimestamp) / 1000.0
        lastTimestamp = timestamp

        // Sanity: skip if time went backwards or huge gap (> 5 min → reset)
        if (dt <= 0) {
            return currentEstimate()
        }
        if (dt > 300) {
            reset()
            return filter(lat, lng, accuracyM, timestamp)
        }

        // === PREDICT ===
        // State transition: x' = F * x (constant velocity)
        x[0] += x[2] * dt
        x[1] += x[3] * dt
        // velocity stays the same in prediction

        // Covariance prediction: P' = F * P * F' + Q
        // F = [[1,0,dt,0],[0,1,0,dt],[0,0,1,0],[0,0,0,1]]
        // Q = process noise from acceleration (continuous white noise model)
        val dt2 = dt * dt
        val dt3 = dt2 * dt / 2.0
        val dt4 = dt2 * dt2 / 4.0
        val q = processNoiseAccel * processNoiseAccel

        // Apply F*P*F' (propagate covariance through state transition)
        val pNew = Array(4) { DoubleArray(4) }
        // Simplified for 4x4 with this specific F structure
        for (i in 0..3) for (j in 0..3) pNew[i][j] = P[i][j]

        // Add cross terms from velocity
        pNew[0][0] += 2 * dt * P[0][2] + dt2 * P[2][2]
        pNew[0][1] += dt * P[0][3] + dt * P[2][1] + dt2 * P[2][3]
        pNew[1][0] = pNew[0][1]
        pNew[1][1] += 2 * dt * P[1][3] + dt2 * P[3][3]
        pNew[0][2] += dt * P[2][2]
        pNew[2][0] = pNew[0][2]
        pNew[0][3] += dt * P[2][3]
        pNew[3][0] = pNew[0][3]
        pNew[1][2] += dt * P[3][2]
        pNew[2][1] = pNew[1][2]
        pNew[1][3] += dt * P[3][3]
        pNew[3][1] = pNew[1][3]

        // Add process noise Q
        pNew[0][0] += q * dt4; pNew[0][2] += q * dt3; pNew[2][0] += q * dt3
        pNew[1][1] += q * dt4; pNew[1][3] += q * dt3; pNew[3][1] += q * dt3
        pNew[2][2] += q * dt2
        pNew[3][3] += q * dt2

        for (i in 0..3) for (j in 0..3) P[i][j] = pNew[i][j]

        // === UPDATE ===
        // Convert GPS lat/lng to local meters
        val mx = (lng - referenceLng) * metersPerDegreeLng(referenceLat)
        val my = (lat - referenceLat) * METERS_PER_DEGREE_LAT

        // Innovation (measurement residual)
        val yx = mx - x[0]
        val yy = my - x[1]

        // Measurement noise R (from GPS accuracy)
        val r = (accuracyM * accuracyM).toDouble()

        // Innovation covariance S = H*P*H' + R (H selects position from state)
        val s00 = P[0][0] + r
        val s01 = P[0][1]
        val s10 = P[1][0]
        val s11 = P[1][1] + r

        // Invert 2x2 S matrix
        val det = s00 * s11 - s01 * s10
        if (det == 0.0) return currentEstimate()
        val si00 = s11 / det
        val si01 = -s01 / det
        val si10 = -s10 / det
        val si11 = s00 / det

        // Kalman gain K = P * H' * S^-1 (4x2 matrix)
        val k = Array(4) { DoubleArray(2) }
        for (i in 0..3) {
            k[i][0] = P[i][0] * si00 + P[i][1] * si10
            k[i][1] = P[i][0] * si01 + P[i][1] * si11
        }

        // State update: x = x + K * y
        for (i in 0..3) {
            x[i] += k[i][0] * yx + k[i][1] * yy
        }

        // Joseph form covariance update: P = (I-KH)*P*(I-KH)' + K*R*K'
        // Algebraically equivalent to P = (I-KH)*P but numerically stable —
        // guarantees P stays symmetric positive semi-definite across 10,000+ iterations,
        // preventing filter divergence in multi-day tracking sessions.
        val kh = Array(4) { DoubleArray(4) }
        for (i in 0..3) {
            kh[i][0] = k[i][0]
            kh[i][1] = k[i][1]
            // kh[i][2] = 0, kh[i][3] = 0
        }
        // I - K*H
        val imkh = Array(4) { i -> DoubleArray(4) { j ->
            (if (i == j) 1.0 else 0.0) - kh[i][j]
        }}
        // temp = (I-KH) * P
        val temp = Array(4) { i -> DoubleArray(4) { j ->
            var sum = 0.0
            for (m in 0..3) sum += imkh[i][m] * P[m][j]
            sum
        }}
        // P = temp * (I-KH)' + K * R * K'
        for (i in 0..3) for (j in 0..3) {
            var sum = 0.0
            for (m in 0..3) sum += temp[i][m] * imkh[j][m]
            sum += (k[i][0] * k[j][0] + k[i][1] * k[j][1]) * r
            P[i][j] = sum
        }
        // Force symmetry to eliminate floating-point asymmetry accumulation
        for (i in 0..3) for (j in i + 1..3) {
            val avg = (P[i][j] + P[j][i]) / 2.0
            P[i][j] = avg
            P[j][i] = avg
        }

        return currentEstimate()
    }

    private fun currentEstimate(): FilteredLocation {
        val lat = referenceLat + x[1] / METERS_PER_DEGREE_LAT
        val lng = referenceLng + x[0] / metersPerDegreeLng(referenceLat)
        val speed = sqrt(x[2] * x[2] + x[3] * x[3]).toFloat()
        val bearing = ((Math.toDegrees(atan2(x[2], x[3])) + 360) % 360).toFloat()
        val posUncertainty = sqrt(P[0][0] + P[1][1]).toFloat()
        val confidence = if (posUncertainty > 0) (1f / posUncertainty).coerceIn(0f, 1f) else 1f
        return FilteredLocation(lat, lng, speed, bearing, confidence)
    }

    companion object {
        private const val METERS_PER_DEGREE_LAT = 111_320.0

        private fun metersPerDegreeLng(lat: Double): Double {
            return 111_320.0 * cos(Math.toRadians(lat))
        }
    }
}
