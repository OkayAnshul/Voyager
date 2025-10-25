package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "search_index")
data class SearchIndexEntity(
    val placeDisplayName: String? = null,
    val placeCategory: String? = null,
    val dayKey: String? = null,
    val segmentType: String? = null,
    val geocodeDisplayName: String? = null,
    val userNotes: String? = null
)
