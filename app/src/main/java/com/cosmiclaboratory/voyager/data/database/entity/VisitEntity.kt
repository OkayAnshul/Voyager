package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDateTime

@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["placeId"]),
        Index(value = ["entryTime"]),
        Index(value = ["exitTime"]),
        Index(value = ["duration"])
    ]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val placeId: Long,
    val entryTime: LocalDateTime,
    val exitTime: LocalDateTime? = null,
    val duration: Long = 0L,
    val confidence: Float = 1.0f
)