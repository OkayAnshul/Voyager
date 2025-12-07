package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.CategoryPreferenceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.utils.OsmTypeMapper
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for determining if a place detection should be automatically accepted
 * Implements 4 strategies: NEVER, HIGH_CONFIDENCE_ONLY, AFTER_N_VISITS, ALWAYS
 */
@Singleton
class AutoAcceptDecisionUseCase @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val preferencesRepository: PreferencesRepository
) {

    /**
     * Decides if a detected place should be auto-accepted
     * Returns null if review is needed, otherwise returns approved PlaceReview
     */
    suspend fun shouldAutoAccept(
        detectedPlace: Place,
        confidence: Float,
        visitCount: Int,
        osmSuggestedCategory: PlaceCategory?
    ): AutoAcceptDecision {
        val preferences = preferencesRepository.getCurrentPreferences()
        val autoAcceptConfig = AutoAcceptConfig(
            strategy = preferences.autoAcceptStrategy,
            confidenceThreshold = preferences.autoAcceptConfidenceThreshold,
            requiredVisits = preferences.threeVisitAutoAcceptVisitCount,
            reviewPromptMode = preferences.reviewPromptMode,
            disabledCategories = preferences.disabledCategories,
            alwaysReviewCategories = preferences.alwaysReviewCategories
        )

        // Check if category is disabled
        if (detectedPlace.category in autoAcceptConfig.disabledCategories) {
            return AutoAcceptDecision.Reject("Category ${detectedPlace.category} is disabled")
        }

        // Check if category always requires review
        if (detectedPlace.category in autoAcceptConfig.alwaysReviewCategories) {
            return AutoAcceptDecision.NeedsReview(
                ReviewPriority.HIGH,
                ReviewType.CATEGORY_UNCERTAIN
            )
        }

        // Apply strategy
        return when (autoAcceptConfig.strategy) {
            AutoAcceptStrategy.NEVER -> {
                AutoAcceptDecision.NeedsReview(
                    ReviewPriority.NORMAL,
                    ReviewType.NEW_PLACE
                )
            }

            AutoAcceptStrategy.HIGH_CONFIDENCE_ONLY -> {
                evaluateHighConfidenceStrategy(
                    detectedPlace,
                    confidence,
                    autoAcceptConfig.confidenceThreshold,
                    osmSuggestedCategory
                )
            }

            AutoAcceptStrategy.AFTER_N_VISITS -> {
                evaluateAfterNVisitsStrategy(
                    detectedPlace,
                    visitCount,
                    confidence,
                    autoAcceptConfig.requiredVisits
                )
            }

            AutoAcceptStrategy.ALWAYS -> {
                AutoAcceptDecision.AutoAccept(ReviewStatus.AUTO_ACCEPTED)
            }
        }
    }

    /**
     * HIGH_CONFIDENCE_ONLY strategy:
     * Auto-accept if confidence >= threshold
     * Apply category learning bonus
     */
    private suspend fun evaluateHighConfidenceStrategy(
        detectedPlace: Place,
        confidence: Float,
        threshold: Float,
        osmSuggestedCategory: PlaceCategory?
    ): AutoAcceptDecision {
        // Get category learning bonus
        val categoryPref = categoryPreferenceRepository.getPreferenceForCategory(detectedPlace.category)
        val learningBonus = categoryPref?.preferenceScore?.coerceIn(-0.2f, 0.2f) ?: 0f

        // Week 4: Apply OSM confidence boost using intelligent matching
        val osmBonus = OsmTypeMapper.getOsmConfidenceBoost(
            detectedPlace.category,
            osmSuggestedCategory
        )

        val adjustedConfidence = (confidence + learningBonus + osmBonus).coerceIn(0f, 1f)

        return if (adjustedConfidence >= threshold) {
            AutoAcceptDecision.AutoAccept(ReviewStatus.AUTO_ACCEPTED)
        } else {
            val priority = when {
                adjustedConfidence >= threshold - 0.1f -> ReviewPriority.LOW  // Close, low priority
                adjustedConfidence >= threshold - 0.2f -> ReviewPriority.NORMAL
                else -> ReviewPriority.HIGH  // Very uncertain
            }
            AutoAcceptDecision.NeedsReview(priority, ReviewType.LOW_CONFIDENCE)
        }
    }

    /**
     * AFTER_N_VISITS strategy:
     * Auto-accept after N visits to the same location
     * Still applies basic confidence threshold (0.3f minimum)
     */
    private suspend fun evaluateAfterNVisitsStrategy(
        detectedPlace: Place,
        visitCount: Int,
        confidence: Float,
        requiredVisits: Int
    ): AutoAcceptDecision {
        // Minimum confidence threshold even for repeated visits
        if (confidence < 0.3f) {
            return AutoAcceptDecision.NeedsReview(
                ReviewPriority.HIGH,
                ReviewType.LOW_CONFIDENCE
            )
        }

        return if (visitCount >= requiredVisits) {
            AutoAcceptDecision.AutoAccept(ReviewStatus.AUTO_ACCEPTED)
        } else {
            AutoAcceptDecision.NeedsReview(
                ReviewPriority.NORMAL,
                ReviewType.MULTIPLE_VISITS,
                note = "Visit $visitCount/$requiredVisits"
            )
        }
    }

    /**
     * Get appropriate review prompt mode based on priority
     */
    fun getPromptMode(priority: ReviewPriority, config: AutoAcceptConfig): ReviewPromptMode {
        return when (config.reviewPromptMode) {
            ReviewPromptMode.IMMEDIATE -> ReviewPromptMode.IMMEDIATE
            ReviewPromptMode.NOTIFICATION_ONLY -> {
                // High priority uses immediate, others use notification
                if (priority == ReviewPriority.HIGH) ReviewPromptMode.IMMEDIATE
                else ReviewPromptMode.NOTIFICATION_ONLY
            }
            ReviewPromptMode.DAILY_SUMMARY -> ReviewPromptMode.DAILY_SUMMARY
            ReviewPromptMode.MANUAL_ONLY -> ReviewPromptMode.MANUAL_ONLY
        }
    }
}

/**
 * Result of auto-accept decision
 */
sealed class AutoAcceptDecision {
    data class AutoAccept(val status: ReviewStatus) : AutoAcceptDecision()
    data class NeedsReview(
        val priority: ReviewPriority,
        val reviewType: ReviewType,
        val note: String? = null
    ) : AutoAcceptDecision()
    data class Reject(val reason: String) : AutoAcceptDecision()
}
