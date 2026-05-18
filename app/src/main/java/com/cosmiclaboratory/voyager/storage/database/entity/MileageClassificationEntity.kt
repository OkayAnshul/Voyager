package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * User classification of a drive segment for the mileage log / tax PDF.
 *
 * Kept in a separate table from [MovementSegmentEntity] on purpose: classification is
 * sparse (only the drives a user actually tags), user-authored, and Pro-only, so it
 * has no business bloating the hot derived-pipeline table.
 *
 * The row is keyed 1:1 to its segment and CASCADE-deletes with it — if a segment is
 * removed (retention cleanup, re-derivation) its classification goes too.
 *
 * `purpose` stores a [com.cosmiclaboratory.voyager.domain.model.MileagePurpose] name.
 * Audit columns mirror the v3 syncable tables — inert until cloud sync ships.
 */
@Entity(
    tableName = "mileage_classifications",
    foreignKeys = [
        ForeignKey(
            entity = MovementSegmentEntity::class,
            parentColumns = ["segmentId"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MileageClassificationEntity(
    @PrimaryKey
    val segmentId: Long,
    val purpose: String,
    val note: String? = null,
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)
