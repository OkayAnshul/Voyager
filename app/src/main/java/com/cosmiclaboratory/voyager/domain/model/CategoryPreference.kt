package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

/**
 * Stores learned preferences for place categories
 * Used to boost/reduce confidence for specific categories based on user behavior
 */
data class CategoryPreference(
    val id: Long = 0L,
    val category: PlaceCategory,
    val preferenceScore: Float,  // -1.0 (never) to +1.0 (always prefer)
    val correctionCount: Int = 0,  // How many times user corrected to/from this category
    val acceptanceCount: Int = 0,  // How many times user accepted this category
    val rejectionCount: Int = 0,  // How many times user rejected this category
    val isDisabled: Boolean = false,  // User disabled this category completely
    val lastUpdated: LocalDateTime,

    // Pattern learning
    val timePatternWeight: Float = 0.0f,  // How much to weight time patterns for this category
    val durationPatternWeight: Float = 0.0f,  // How much to weight duration patterns
    val frequencyPatternWeight: Float = 0.0f,  // How much to weight visit frequency

    // Metadata
    val notes: String? = null
)

/**
 * Helper class to track category learning statistics
 */
data class CategoryLearningStats(
    val category: PlaceCategory,
    val totalCorrections: Int,
    val toThisCategory: Int,  // Corrections TO this category
    val fromThisCategory: Int,  // Corrections FROM this category
    val netPreference: Int,  // toThisCategory - fromThisCategory
    val confidenceBoost: Float  // Calculated boost to apply (-0.2 to +0.2)
)
