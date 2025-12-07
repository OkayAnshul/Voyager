package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import java.time.LocalDateTime

@Entity(
    tableName = "category_preferences",
    indices = [
        Index(value = ["category"], unique = true),
        Index(value = ["isDisabled"]),
        Index(value = ["lastUpdated"])
    ]
)
data class CategoryPreferenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val category: PlaceCategory,
    val preferenceScore: Float,  // -1.0 (never) to +1.0 (always prefer)
    val correctionCount: Int = 0,
    val acceptanceCount: Int = 0,
    val rejectionCount: Int = 0,
    val isDisabled: Boolean = false,
    val lastUpdated: LocalDateTime,

    // Pattern learning
    val timePatternWeight: Float = 0.0f,
    val durationPatternWeight: Float = 0.0f,
    val frequencyPatternWeight: Float = 0.0f,

    // Metadata
    val notes: String? = null
)
