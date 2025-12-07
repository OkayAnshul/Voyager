package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.time.LocalDateTime

/**
 * Location entity with activity context
 * Phase 1: Activity-First Implementation - Added activity tracking columns
 */
@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["userActivity"]),  // Phase 1: Query by activity
        Index(value = ["semanticContext"]) // Phase 1: Query by context
    ]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val timestamp: LocalDateTime,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null,

    // Phase 1: Activity tracking
    val userActivity: String = "UNKNOWN",  // UserActivity enum name
    val activityConfidence: Float = 0f,    // 0.0 - 1.0
    val semanticContext: String? = null    // SemanticContext enum name (nullable)
)