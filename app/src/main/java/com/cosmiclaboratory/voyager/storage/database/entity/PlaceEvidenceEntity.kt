package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "place_evidence",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["placeId"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaceEvidenceEntity(
    @PrimaryKey
    val placeId: Long,
    val clusterDensity: Float? = null,
    val totalVisitCount: Int = 0,
    val visitCountLast7d: Int = 0,
    val visitCountLast30d: Int = 0,
    val avgDwellMs: Long? = null,
    val repeatabilityScore: Float = 0.0f,
    val namingCandidatesJson: String? = null,
    val userConfirmationCount: Int = 0,
    val categoryReasoningJson: String? = null,
    val lastClusterUpdateAt: Long? = null
)
