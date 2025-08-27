package com.cosmiclaboratory.voyager.domain.util

import kotlin.math.*

object LocationUtils {

    private const val EARTH_RADIUS_M = 6371000.0

    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    fun speedMps(distanceM: Double, durationMs: Long): Float {
        if (durationMs <= 0) return 0f
        return (distanceM / (durationMs / 1000.0)).toFloat()
    }

    fun speedKmh(speedMps: Float): Float = speedMps * 3.6f
}
