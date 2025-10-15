package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_rollups")
data class WeeklyRollupEntity(
    @PrimaryKey
    val weekKey: String, // ISO YYYY-Www
    val avgDailyDistanceM: Double = 0.0,
    val avgDailySteps: Int = 0,
    val totalDistanceM: Double = 0.0,
    val totalSteps: Int = 0,
    val activeDayCount: Int = 0,
    val topPlacesJson: String? = null,
    val transportModeDistributionJson: String? = null,
    val comparisonToPrevWeekJson: String? = null,
    val computedAt: Long
)
