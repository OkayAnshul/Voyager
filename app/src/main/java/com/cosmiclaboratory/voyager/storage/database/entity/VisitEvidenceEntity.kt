package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "visit_evidence",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["visitId"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VisitEvidenceEntity(
    @PrimaryKey
    val visitId: Long,
    val entrySampleIdsJson: String? = null,
    val exitSampleIdsJson: String? = null,
    val dwellCurveJson: String? = null,
    val insideCount: Int = 0,
    val outsideCount: Int = 0,
    val suppressionReasonsJson: String? = null,
    val geofenceHints: String? = null,
    val confirmationRuleUsed: String? = null,
    val arrivalConfidence: Float = 0.5f,
    val departureConfidence: Float = 0.5f
)
