package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import java.time.LocalDateTime

@Entity(
    tableName = "places",
    indices = [
        Index(value = ["category"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["visitCount"]),
        Index(value = ["lastVisit"])
    ]
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val visitCount: Int = 0,
    val totalTimeSpent: Long = 0L,
    val lastVisit: LocalDateTime? = null,
    val isCustom: Boolean = false,
    val radius: Double = 100.0,
    val placeId: String? = null,
    val confidence: Float = 1.0f
)