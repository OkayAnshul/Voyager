package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_metadata")
data class SearchMetadataEntity(
    @PrimaryKey
    val searchRowId: Long,
    val sourceTable: String, // PLACE/VISIT/SEGMENT/DAY
    val sourceId: Long,
    val relevanceBoost: Float = 1.0f
)
