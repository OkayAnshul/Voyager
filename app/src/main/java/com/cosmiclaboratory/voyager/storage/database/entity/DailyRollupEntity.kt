package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_rollups")
data class DailyRollupEntity(
    @PrimaryKey
    val dayKey: String, // YYYY-MM-DD
    val totalDistanceM: Double = 0.0,
    val totalSteps: Int = 0,
    val totalDwellMs: Long = 0,
    val totalTransitMs: Long = 0,
    val totalWalkMs: Long = 0,
    val totalDriveMs: Long = 0,
    val placeVisitCount: Int = 0,
    val uniquePlacesVisited: Int = 0,
    val firstActivityAt: Long? = null,
    val lastActivityAt: Long? = null,
    val dominantTransportMode: String? = null,
    val anomalyFlagsJson: String? = null,
    val computedAt: Long
)
