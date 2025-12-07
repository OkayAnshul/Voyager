package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey
import com.cosmiclaboratory.voyager.domain.model.CorrectionType
import java.time.LocalDateTime

@Entity(
    tableName = "user_corrections",
    indices = [
        Index(value = ["placeId"]),
        Index(value = ["correctionTime"]),
        Index(value = ["correctionType"]),
        Index(value = ["wasAppliedToLearning"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserCorrectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val placeId: Long,
    val correctionTime: LocalDateTime,
    val correctionType: CorrectionType,

    // What was changed
    val oldValue: String,
    val newValue: String,

    // Context
    val confidence: Float,
    val locationCount: Int,
    val visitCount: Int,

    // Learning metadata
    val wasAppliedToLearning: Boolean = false,
    val similarCorrectionCount: Int = 0
)
