package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "place_rollups",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["placeId"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaceRollupEntity(
    @PrimaryKey
    val placeId: Long,
    val totalVisitCount: Int = 0,
    val totalDwellMs: Long = 0,
    val avgDwellMs: Long = 0,
    val lastVisitAt: Long? = null,
    val firstVisitAt: Long? = null,
    val visitCountLast7d: Int = 0,
    val visitCountLast30d: Int = 0,
    val visitCountLast90d: Int = 0,
    val dominantDayOfWeek: Int? = null,
    val dominantTimeOfDay: String? = null,
    val computedAt: Long
)
