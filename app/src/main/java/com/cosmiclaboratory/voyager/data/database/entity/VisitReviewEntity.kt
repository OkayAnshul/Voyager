package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.VisitReviewReason
import java.time.LocalDateTime

@Entity(
    tableName = "visit_reviews",
    indices = [
        Index(value = ["visitId"]),
        Index(value = ["placeId"]),
        Index(value = ["status"]),
        Index(value = ["reviewReason"]),
        Index(value = ["entryTime"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VisitReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val visitId: Long,
    val placeId: Long,
    val placeName: String,
    val entryTime: LocalDateTime,
    val exitTime: LocalDateTime?,
    val duration: Long,
    val confidence: Float,
    val reviewReason: VisitReviewReason,
    val status: ReviewStatus = ReviewStatus.PENDING,

    // Alternative place suggestions
    val alternativePlaceId: Long? = null,
    val alternativePlaceName: String? = null,

    // User's decision
    val userConfirmedPlaceId: Long? = null,
    val reviewedAt: LocalDateTime? = null,
    val notes: String? = null
)
