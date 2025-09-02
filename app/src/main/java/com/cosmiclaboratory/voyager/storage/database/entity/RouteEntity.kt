package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routes",
    foreignKeys = [
        ForeignKey(
            entity = MovementSegmentEntity::class,
            parentColumns = ["segmentId"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["segmentId"])
    ]
)
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val routeId: Long = 0,
    val segmentId: Long,
    val encodedPolyline: String,
    val simplifiedPolyline: String? = null,
    val totalDistanceM: Double,
    val totalDurationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val transportMode: String,
    val sampleCount: Int,
    val boundingBoxJson: String? = null
)
