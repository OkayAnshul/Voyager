package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import com.cosmiclaboratory.voyager.domain.model.ReviewType
import java.time.LocalDateTime

@Entity(
    tableName = "place_reviews",
    indices = [
        Index(value = ["placeId"]),
        Index(value = ["status"]),
        Index(value = ["priority"]),
        Index(value = ["detectionTime"]),
        Index(value = ["reviewType"])
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
data class PlaceReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val placeId: Long,
    val detectedName: String,
    val detectedCategory: PlaceCategory,
    val confidence: Float,
    val latitude: Double,
    val longitude: Double,
    val detectionTime: LocalDateTime,
    val status: ReviewStatus = ReviewStatus.PENDING,
    val priority: ReviewPriority = ReviewPriority.NORMAL,
    val reviewType: ReviewType,

    // OSM data if available
    val osmSuggestedName: String? = null,
    val osmSuggestedCategory: PlaceCategory? = null,
    val osmPlaceType: String? = null,

    // User's decision
    val userApprovedName: String? = null,
    val userApprovedCategory: PlaceCategory? = null,
    val reviewedAt: LocalDateTime? = null,

    // Metadata
    val locationCount: Int = 0,
    val visitCount: Int = 0,
    val notes: String? = null,

    // Phase 1 UX: Confidence transparency - JSON string of breakdown factors
    val confidenceBreakdown: String? = null
)
