package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "correction_feedback")
data class CorrectionFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val feedbackId: Long = 0,
    val correctionType: String, // RENAME/RECATEGORIZE/RECLASSIFY_SEGMENT/MERGE_PLACE/SPLIT_PLACE/DELETE_VISIT/ADJUST_TIMES/CONFIRM
    val entityType: String, // PLACE/VISIT/SEGMENT
    val entityId: Long,
    val beforeValueJson: String? = null,
    val afterValueJson: String? = null,
    val createdAt: Long,
    val propagated: Boolean = false
)
