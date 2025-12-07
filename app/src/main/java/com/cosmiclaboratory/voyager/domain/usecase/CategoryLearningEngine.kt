package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.CategoryPreference
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.CategoryLearningStats
import com.cosmiclaboratory.voyager.domain.repository.CategoryPreferenceRepository
import com.cosmiclaboratory.voyager.domain.repository.UserCorrectionRepository
import com.cosmiclaboratory.voyager.domain.model.CorrectionType
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Learning engine that adapts place categorization based on user corrections
 * Implements confidence boosting/reduction for categories
 */
@Singleton
class CategoryLearningEngine @Inject constructor(
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val userCorrectionRepository: UserCorrectionRepository
) {

    companion object {
        private const val ACCEPTANCE_BONUS = 0.05f  // Boost when user accepts
        private const val REJECTION_PENALTY = -0.10f  // Reduce when user rejects
        private const val CORRECTION_TO_BONUS = 0.10f  // Boost when user corrects TO this category
        private const val CORRECTION_FROM_PENALTY = -0.15f  // Reduce when user corrects FROM this category
        private const val MAX_PREFERENCE_SCORE = 1.0f
        private const val MIN_PREFERENCE_SCORE = -1.0f
    }

    /**
     * Learn from user accepting a detected category
     */
    suspend fun learnFromAcceptance(category: PlaceCategory, confidence: Float) {
        val now = LocalDateTime.now()
        val pref = getOrCreatePreference(category)

        // Increment acceptance count
        categoryPreferenceRepository.incrementAcceptanceCount(category, now)

        // Adjust preference score (higher confidence acceptance = bigger boost)
        val boost = ACCEPTANCE_BONUS * confidence
        adjustPreferenceScore(category, boost)
    }

    /**
     * Learn from user rejecting a detected category
     */
    suspend fun learnFromRejection(category: PlaceCategory, confidence: Float) {
        val now = LocalDateTime.now()
        val pref = getOrCreatePreference(category)

        // Increment rejection count
        categoryPreferenceRepository.incrementRejectionCount(category, now)

        // Adjust preference score (higher confidence rejection = bigger penalty)
        val penalty = REJECTION_PENALTY * confidence
        adjustPreferenceScore(category, penalty)
    }

    /**
     * Learn from user correcting category FROM one TO another
     */
    suspend fun learnFromCorrection(
        fromCategory: PlaceCategory,
        toCategory: PlaceCategory,
        confidence: Float
    ) {
        val now = LocalDateTime.now()

        // Penalize the wrong category
        categoryPreferenceRepository.incrementCorrectionCount(fromCategory, now)
        adjustPreferenceScore(fromCategory, CORRECTION_FROM_PENALTY * confidence)

        // Boost the correct category
        categoryPreferenceRepository.incrementCorrectionCount(toCategory, now)
        adjustPreferenceScore(toCategory, CORRECTION_TO_BONUS)
    }

    /**
     * Get confidence boost/reduction for a category based on learning
     * Returns value between -0.2 and +0.2
     */
    suspend fun getCategoryBonus(category: PlaceCategory): Float {
        val pref = categoryPreferenceRepository.getPreferenceForCategory(category)
            ?: return 0f

        // Preference score is already between -1.0 and 1.0
        // Scale it to -0.2 to +0.2 for confidence adjustment
        return (pref.preferenceScore * 0.2f).coerceIn(-0.2f, 0.2f)
    }

    /**
     * Check if a category is disabled by user
     */
    suspend fun isCategoryDisabled(category: PlaceCategory): Boolean {
        val pref = categoryPreferenceRepository.getPreferenceForCategory(category)
        return pref?.isDisabled ?: false
    }

    /**
     * Get learning statistics for all categories
     */
    suspend fun getLearningStats(): List<CategoryLearningStats> {
        val allPreferences = categoryPreferenceRepository.getAllPreferences().first()
        val recentCorrections = userCorrectionRepository.getUnappliedCorrections()

        return PlaceCategory.values().map { category ->
            val pref = allPreferences.find { it.category == category }
            val corrections = recentCorrections.filter {
                it.correctionType == CorrectionType.CATEGORY_CHANGE
            }

            val toThisCategory = corrections.count { it.newValue == category.name }
            val fromThisCategory = corrections.count { it.oldValue == category.name }

            CategoryLearningStats(
                category = category,
                totalCorrections = pref?.correctionCount ?: 0,
                toThisCategory = toThisCategory,
                fromThisCategory = fromThisCategory,
                netPreference = toThisCategory - fromThisCategory,
                confidenceBoost = pref?.preferenceScore?.times(0.2f)?.coerceIn(-0.2f, 0.2f) ?: 0f
            )
        }
    }

    /**
     * Apply unapplied corrections to learning
     */
    suspend fun applyUnappliedCorrections(): Int {
        val unapplied = userCorrectionRepository.getUnappliedCorrections()
        var appliedCount = 0

        unapplied.forEach { correction ->
            when (correction.correctionType) {
                CorrectionType.CATEGORY_CHANGE -> {
                    try {
                        val fromCategory = PlaceCategory.valueOf(correction.oldValue)
                        val toCategory = PlaceCategory.valueOf(correction.newValue)
                        learnFromCorrection(fromCategory, toCategory, correction.confidence)
                        userCorrectionRepository.markCorrectionAsApplied(correction.id)
                        appliedCount++
                    } catch (e: IllegalArgumentException) {
                        // Invalid category name, skip
                    }
                }
                else -> {
                    // Other correction types don't affect category learning
                    userCorrectionRepository.markCorrectionAsApplied(correction.id)
                }
            }
        }

        return appliedCount
    }

    /**
     * Reset learning for a specific category
     */
    suspend fun resetCategoryLearning(category: PlaceCategory) {
        val pref = CategoryPreference(
            category = category,
            preferenceScore = 0f,
            correctionCount = 0,
            acceptanceCount = 0,
            rejectionCount = 0,
            isDisabled = false,
            lastUpdated = LocalDateTime.now()
        )
        categoryPreferenceRepository.insertPreference(pref)
    }

    /**
     * Enable or disable a category
     */
    suspend fun setCategoryEnabled(category: PlaceCategory, enabled: Boolean) {
        categoryPreferenceRepository.setCategoryDisabled(category, !enabled)
    }

    // Private helper methods

    private suspend fun getOrCreatePreference(category: PlaceCategory): CategoryPreference {
        return categoryPreferenceRepository.getPreferenceForCategory(category)
            ?: run {
                val newPref = CategoryPreference(
                    category = category,
                    preferenceScore = 0f,
                    lastUpdated = LocalDateTime.now()
                )
                categoryPreferenceRepository.insertPreference(newPref)
                newPref
            }
    }

    private suspend fun adjustPreferenceScore(category: PlaceCategory, delta: Float) {
        val current = categoryPreferenceRepository.getPreferenceForCategory(category)
        if (current != null) {
            val newScore = (current.preferenceScore + delta).coerceIn(
                MIN_PREFERENCE_SCORE,
                MAX_PREFERENCE_SCORE
            )
            val updated = current.copy(
                preferenceScore = newScore,
                lastUpdated = LocalDateTime.now()
            )
            categoryPreferenceRepository.updatePreference(updated)
        } else {
            // Create if doesn't exist
            val newPref = CategoryPreference(
                category = category,
                preferenceScore = delta.coerceIn(MIN_PREFERENCE_SCORE, MAX_PREFERENCE_SCORE),
                lastUpdated = LocalDateTime.now()
            )
            categoryPreferenceRepository.insertPreference(newPref)
        }
    }
}
