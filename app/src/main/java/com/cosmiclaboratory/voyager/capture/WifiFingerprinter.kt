package com.cosmiclaboratory.voyager.capture

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures and matches Wi-Fi BSSID fingerprints at known places.
 * When GPS is unavailable indoors, Wi-Fi scans can identify which
 * known place the user is at based on visible access points.
 *
 * Note: Android throttles Wi-Fi scans to 4 per 2 minutes for foreground apps.
 * This is used opportunistically, not as a primary location source.
 */
@Singleton
class WifiFingerprinter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as? WifiManager

    // In-memory cache: placeId → Set<BSSID>
    // Persisted fingerprints would require a new DB table, but in-memory
    // cache is sufficient for session-level indoor detection.
    private val fingerprints = mutableMapOf<Long, MutableSet<String>>()

    val isAvailable: Boolean get() = wifiManager != null

    /**
     * Capture the current Wi-Fi environment and associate it with a place.
     * Call when a visit is confirmed at a CONFIRMED place with good GPS.
     */
    @SuppressLint("MissingPermission")
    fun captureFingerprint(placeId: Long) {
        val wm = wifiManager ?: return
        @Suppress("DEPRECATION")
        val scanResults = wm.scanResults ?: return
        val bssids = scanResults
            .filter { it.level >= MIN_SIGNAL_LEVEL }
            .map { it.BSSID }
            .toMutableSet()
        if (bssids.isNotEmpty()) {
            fingerprints.getOrPut(placeId) { mutableSetOf() }.addAll(bssids)
        }
    }

    /**
     * Match the current Wi-Fi environment against known place fingerprints.
     * Returns the best matching placeId, or null if no match.
     */
    @SuppressLint("MissingPermission")
    fun matchFingerprint(): Long? {
        val wm = wifiManager ?: return null
        @Suppress("DEPRECATION")
        val scanResults = wm.scanResults ?: return null
        val currentBssids = scanResults
            .filter { it.level >= MIN_SIGNAL_LEVEL }
            .map { it.BSSID }
            .toSet()
        if (currentBssids.isEmpty()) return null

        var bestPlaceId: Long? = null
        var bestOverlap = 0

        for ((placeId, knownBssids) in fingerprints) {
            val overlap = currentBssids.intersect(knownBssids).size
            if (overlap >= MIN_MATCH_BSSIDS && overlap > bestOverlap) {
                bestOverlap = overlap
                bestPlaceId = placeId
            }
        }

        return bestPlaceId
    }

    fun clearFingerprints() {
        fingerprints.clear()
    }

    companion object {
        private const val MIN_SIGNAL_LEVEL = -80 // dBm — ignore very weak signals
        private const val MIN_MATCH_BSSIDS = 2 // require at least 2 common APs
    }
}
