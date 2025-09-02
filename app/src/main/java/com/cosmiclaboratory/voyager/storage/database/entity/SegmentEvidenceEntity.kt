package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "segment_evidence",
    foreignKeys = [
        ForeignKey(
            entity = MovementSegmentEntity::class,
            parentColumns = ["segmentId"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SegmentEvidenceEntity(
    @PrimaryKey
    val segmentId: Long,
    val avgSpeedMps: Float? = null,
    val maxSpeedMps: Float? = null,
    val speedStdDev: Float? = null,
    val sampleCount: Int = 0,
    val activityVotesJson: String? = null, // JSON map activityType -> voteCount
    val stepCount: Int? = null,
    val headingConsistency: Float? = null, // 0.0-1.0
    val dwellMarkers: String? = null,
    val providerMixJson: String? = null, // JSON map provider -> count
    val decisionRuleVersion: String? = null,
    val explanationJson: String? = null,
    val counterEvidenceJson: String? = null
)
