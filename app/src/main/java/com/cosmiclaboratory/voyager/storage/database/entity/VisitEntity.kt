package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "visits",
    indices = [
        Index(value = ["placeId", "arrivalAt"]),
        Index(value = ["dayKey", "arrivalAt"])
    ]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    val visitId: Long = 0,
    val placeId: Long,
    val arrivalAt: Long,
    val departureAt: Long? = null,
    val dwellMs: Long? = null,
    val source: String, // LIVE_DETECTION/BATCH_DISCOVERY/USER_CREATED
    val confidence: Float = 0.5f,
    val supersedesVisitId: Long? = null,
    val isUserCorrected: Boolean = false,
    val dayKey: String,
    val centroidLat: Double? = null,
    val centroidLng: Double? = null
)
