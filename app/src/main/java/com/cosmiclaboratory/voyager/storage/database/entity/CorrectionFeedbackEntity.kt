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
    val propagated: Boolean = false,
    // Multi-user scoping (v8) — inert until sync/multi-user ships; default = install id.
    val userId: String = "",
    // Cloud-ready audit columns (v3). Inert until sync ships — see MIGRATION_2_3.
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)
