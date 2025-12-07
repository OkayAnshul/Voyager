package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.PlaceReviewRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.UserCorrectionRepository
import com.cosmiclaboratory.voyager.domain.repository.CategoryPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use cases for managing place reviews
 * Handles approval, rejection, editing, and batch operations
 */
@Singleton
class PlaceReviewUseCases @Inject constructor(
    private val placeReviewRepository: PlaceReviewRepository,
    private val placeRepository: PlaceRepository,
    private val userCorrectionRepository: UserCorrectionRepository,
    private val categoryPreferenceRepository: CategoryPreferenceRepository,
    private val categoryLearningEngine: CategoryLearningEngine
) {

    /**
     * Get all pending reviews
     */
    fun getPendingReviews(): Flow<List<PlaceReview>> {
        return placeReviewRepository.getPendingReviews()
    }

    /**
     * Get count of pending reviews
     */
    fun getPendingReviewCount(): Flow<Int> {
        return placeReviewRepository.getPendingReviewCount()
    }

    /**
     * Get pending reviews by priority
     */
    fun getPendingReviewsByPriority(priority: ReviewPriority): Flow<List<PlaceReview>> {
        return placeReviewRepository.getPendingReviewsByPriority(priority)
    }

    /**
     * Approve a place review as-is (no changes)
     */
    suspend fun approvePlace(reviewId: Long): Result<Unit> {
        return try {
            val review = placeReviewRepository.getReviewById(reviewId)
                ?: return Result.failure(Exception("Review not found"))

            // Update review status
            val approvedReview = review.copy(
                status = ReviewStatus.APPROVED,
                reviewedAt = LocalDateTime.now()
            )
            placeReviewRepository.updateReview(approvedReview)

            // Update category preference (user accepted this category)
            categoryLearningEngine.learnFromAcceptance(
                review.detectedCategory,
                review.confidence
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Edit and approve a place (user made changes)
     */
    suspend fun editAndApprovePlace(
        reviewId: Long,
        newName: String? = null,
        newCategory: PlaceCategory? = null,
        customCategoryName: String? = null  // ISSUE #3: Custom category name
    ): Result<Unit> {
        return try {
            val review = placeReviewRepository.getReviewById(reviewId)
                ?: return Result.failure(Exception("Review not found"))

            val place = placeRepository.getPlaceById(review.placeId)
                ?: return Result.failure(Exception("Place not found"))

            // Track corrections
            if (newName != null && newName != review.detectedName) {
                userCorrectionRepository.insertCorrection(
                    UserCorrection(
                        placeId = place.id,
                        correctionTime = LocalDateTime.now(),
                        correctionType = CorrectionType.NAME_CHANGE,
                        oldValue = review.detectedName,
                        newValue = newName,
                        confidence = review.confidence,
                        locationCount = review.locationCount,
                        visitCount = review.visitCount
                    )
                )
            }

            if (newCategory != null && newCategory != review.detectedCategory) {
                userCorrectionRepository.insertCorrection(
                    UserCorrection(
                        placeId = place.id,
                        correctionTime = LocalDateTime.now(),
                        correctionType = CorrectionType.CATEGORY_CHANGE,
                        oldValue = review.detectedCategory.name,
                        newValue = newCategory.name,
                        confidence = review.confidence,
                        locationCount = review.locationCount,
                        visitCount = review.visitCount
                    )
                )

                // Learn from category correction (only for non-CUSTOM categories)
                if (newCategory != PlaceCategory.CUSTOM) {
                    categoryLearningEngine.learnFromCorrection(
                        fromCategory = review.detectedCategory,
                        toCategory = newCategory,
                        confidence = review.confidence
                    )
                }
            }

            // Update place
            val updatedPlace = place.copy(
                name = newName ?: place.name,
                category = newCategory ?: place.category,
                customCategoryName = customCategoryName,  // ISSUE #3
                isUserRenamed = newName != null
            )
            placeRepository.updatePlace(updatedPlace)

            // Update review
            val modifiedReview = review.copy(
                status = ReviewStatus.MODIFIED,
                userApprovedName = newName,
                userApprovedCategory = newCategory,
                reviewedAt = LocalDateTime.now()
            )
            placeReviewRepository.updateReview(modifiedReview)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject a place review (false detection)
     */
    suspend fun rejectPlace(reviewId: Long, reason: String? = null): Result<Unit> {
        return try {
            val review = placeReviewRepository.getReviewById(reviewId)
                ?: return Result.failure(Exception("Review not found"))

            // Update review status
            val rejectedReview = review.copy(
                status = ReviewStatus.REJECTED,
                reviewedAt = LocalDateTime.now(),
                notes = reason
            )
            placeReviewRepository.updateReview(rejectedReview)

            // Learn from rejection
            categoryLearningEngine.learnFromRejection(
                review.detectedCategory,
                review.confidence
            )

            // Delete the place if it was a false detection
            placeRepository.deletePlaceById(review.placeId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch approve all high-confidence pending reviews
     */
    suspend fun batchApproveHighConfidence(threshold: Float = 0.8f): Result<Int> {
        return try {
            val pendingReviews = placeReviewRepository.getPendingReviews().first()
            var approvedCount = 0

            pendingReviews
                .filter { it.confidence >= threshold }
                .forEach { review ->
                    approvePlace(review.id).onSuccess {
                        approvedCount++
                    }
                }

            Result.success(approvedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cleanup expired reviews (older than N days)
     */
    suspend fun cleanupExpiredReviews(daysOld: Int = 30): Result<Int> {
        return try {
            val cutoffDate = LocalDateTime.now().minusDays(daysOld.toLong())
            val expiredReviews = placeReviewRepository.getExpiredPendingReviews(cutoffDate)

            placeReviewRepository.cleanupExpiredReviews(cutoffDate)

            Result.success(expiredReviews.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get reviews that need user attention (high priority)
     */
    fun getHighPriorityReviews(): Flow<List<PlaceReview>> {
        return placeReviewRepository.getPendingReviewsByPriority(ReviewPriority.HIGH)
    }

    /**
     * Create a new place review
     */
    suspend fun createPlaceReview(
        place: Place,
        confidence: Float,
        locationCount: Int,
        visitCount: Int,
        reviewType: ReviewType,
        priority: ReviewPriority,
        osmSuggestedName: String? = null,
        osmSuggestedCategory: PlaceCategory? = null,
        osmPlaceType: String? = null
    ): Result<Long> {
        return try {
            // Phase 1 UX: Calculate confidence breakdown for transparency
            val confidenceBreakdown = calculateConfidenceBreakdown(
                place = place,
                locationCount = locationCount,
                visitCount = visitCount,
                hasOsmMatch = osmSuggestedName != null,
                categoryMatch = osmSuggestedCategory == place.category
            )

            val review = PlaceReview(
                placeId = place.id,
                detectedName = place.name,
                detectedCategory = place.category,
                confidence = confidence,
                latitude = place.latitude,
                longitude = place.longitude,
                detectionTime = LocalDateTime.now(),
                status = ReviewStatus.PENDING,
                priority = priority,
                reviewType = reviewType,
                osmSuggestedName = osmSuggestedName,
                osmSuggestedCategory = osmSuggestedCategory,
                osmPlaceType = osmPlaceType,
                locationCount = locationCount,
                visitCount = visitCount,
                confidenceBreakdown = confidenceBreakdown
            )

            val reviewId = placeReviewRepository.insertReview(review)
            Result.success(reviewId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Phase 1 UX: Calculate breakdown of confidence factors for transparency
     */
    private fun calculateConfidenceBreakdown(
        place: Place,
        locationCount: Int,
        visitCount: Int,
        hasOsmMatch: Boolean,
        categoryMatch: Boolean
    ): Map<String, Float> {
        val breakdown = mutableMapOf<String, Float>()

        // Location count contribution (more locations = higher confidence)
        val locationScore = when {
            locationCount >= 50 -> 1.0f
            locationCount >= 20 -> 0.8f
            locationCount >= 10 -> 0.6f
            locationCount >= 5 -> 0.4f
            else -> 0.2f
        }
        breakdown["Location Data"] = locationScore

        // Visit count contribution (multiple visits = higher confidence)
        val visitScore = when {
            visitCount >= 10 -> 1.0f
            visitCount >= 5 -> 0.8f
            visitCount >= 3 -> 0.6f
            visitCount >= 2 -> 0.4f
            else -> 0.2f
        }
        breakdown["Visit Frequency"] = visitScore

        // OSM match bonus (real place in database)
        if (hasOsmMatch) {
            breakdown["OSM Database Match"] = if (categoryMatch) 1.0f else 0.7f
        }

        // Category confidence (based on place's own confidence)
        val categoryScore = when (place.category) {
            PlaceCategory.HOME, PlaceCategory.WORK -> place.confidence
            PlaceCategory.UNKNOWN -> 0.2f
            else -> place.confidence * 0.8f
        }
        breakdown["Category Detection"] = categoryScore

        // Dwell time indicator (if available via radius - proxy for stay duration)
        val dwellScore = when {
            place.radius >= 100 -> 0.9f // Large area = stayed long
            place.radius >= 50 -> 0.7f
            place.radius >= 20 -> 0.5f
            else -> 0.3f
        }
        breakdown["Dwell Pattern"] = dwellScore

        return breakdown
    }
}
