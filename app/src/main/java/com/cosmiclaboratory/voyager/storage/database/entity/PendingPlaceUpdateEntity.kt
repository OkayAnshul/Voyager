package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_place_updates")
data class PendingPlaceUpdateEntity(
    @PrimaryKey(autoGenerate = true)
    val updateId: Long = 0,
    val placeId: Long,
    val updateType: String, // RECLASSIFY/MERGE/RENAME/CATEGORY_CHANGE
    val payloadJson: String,
    val createdAt: Long,
    val consumedAt: Long? = null
)
