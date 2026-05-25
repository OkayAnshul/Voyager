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
    val lastClusterUpdateAt: Long? = null,
    // Multi-user scoping (v8) — inert until sync/multi-user ships; default = install id.
    val userId: String = "",
    // Cloud-ready audit columns (v3). Inert until sync ships — see MIGRATION_2_3.
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)
