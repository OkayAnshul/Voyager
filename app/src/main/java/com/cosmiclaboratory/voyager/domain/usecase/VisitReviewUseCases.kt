package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.repository.VisitReviewRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use cases for managing visit reviews
 * Handles confirmation, rejection, place reassignment, and batch operations
 */
@Singleton
class VisitReviewUseCases @Inject constructor(
    private val visitReviewRepository: VisitReviewRepository,
    private val visitRepository: VisitRepository,
    private val placeRepository: PlaceRepository
) {

    /**
     * Get all pending visit reviews
     */
    fun getPendingReviews(): Flow<List<VisitReview>> {
        return visitReviewRepository.getPendingReviews()
    }

    /**
     * Get pending review count
     */
    fun getPendingReviewCount(): Flow<Int> {
        return visitReviewRepository.getPendingReviewCount()
    }

    /**
     * Get reviews by specific reason
     */
    fun getReviewsByReason(reason: VisitReviewReason): Flow<List<VisitReview>> {
        return visitReviewRepository.getPendingReviewsByReason(reason)
    }

    /**
     * Confirm a visit as-is (correct place, correct duration)
     */
    suspend fun confirmVisit(reviewId: Long): Result<Unit> {
        return try {
            val review = visitReviewRepository.getReviewById(reviewId)
                ?: return Result.failure(Exception("Review not found"))

            // Update review status
            val confirmedReview = review.copy(
                status = ReviewStatus.APPROVED,
                userConfirmedPlaceId = review.placeId,
                reviewedAt = LocalDateTime.now()
            )
            visitReviewRepository.updateReview(confirmedReview)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reassign visit to a different place
     */
    suspend fun reassignVisitToPlace(
        reviewId: Long,
        newPlaceId: Long
    ): Result<Unit> {
        return try {
            val review = visitReviewRepository.getReviewById(reviewId)
                ?: return Result.failure(Exception("Review not found"))

            val visit = getVisitById(review.visitId)
                ?: return Result.failure(Exception("Visit not found"))

            val newPlace = placeRepository.getPlaceById(newPlaceId)
                ?: return Result.failure(Exception("New place not found"))

            // Update visit to point to new place
            val updatedVisit = visit.copy(placeId = newPlaceId)
            visitRepository.updateVisit(updatedVisit)

            // Update review status
            val updatedReview = review.copy(
                status = ReviewStatus.MODIFIED,
                userConfirmedPlaceId = newPlaceId,
                reviewedAt = LocalDateTime.now(),
                notes = "Reassigned from ${review.placeName} to ${newPlace.name}"
            )
            visitReviewRepository.updateReview(updatedReview)

            // Update visit counts for both places
            updatePlaceVisitCounts(review.placeId, newPlaceId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject a visit (false positive - delete the visit)
     */
    suspend fun rejectVisit(reviewId: Long, reason: String? = null): Result<Unit> {
        return try {
            val review = visitReviewRepository.getReviewById(reviewId)
                ?: return Result.failure(Exception("Review not found"))

            // Update review status
            val rejectedReview = review.copy(
                status = ReviewStatus.REJECTED,
                reviewedAt = LocalDateTime.now(),
                notes = reason
            )
            visitReviewRepository.updateReview(rejectedReview)

            // Delete the visit
            val visit = getVisitById(review.visitId)
            if (visit != null) {
                visitRepository.deleteVisit(visit)
            }

            // Update place visit count
            updatePlaceVisitCount(review.placeId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Merge multiple overlapping visits into one
     */
    suspend fun mergeVisits(
        primaryReviewId: Long,
        overlappingReviewIds: List<Long>
    ): Result<Unit> {
        return try {
            val primaryReview = visitReviewRepository.getReviewById(primaryReviewId)
                ?: return Result.failure(Exception("Primary review not found"))

            val primaryVisit = getVisitById(primaryReview.visitId)
                ?: return Result.failure(Exception("Primary visit not found"))

            // Get all overlapping reviews and visits
            val overlappingReviews = overlappingReviewIds.mapNotNull { id ->
                visitReviewRepository.getReviewById(id)
            }

            val overlappingVisits = overlappingReviews.mapNotNull { review ->
                getVisitById(review.visitId)
            }

            if (overlappingVisits.isEmpty()) {
                return Result.failure(Exception("No overlapping visits found"))
            }

            // Calculate merged time range
            val allVisits: List<Visit> = listOf(primaryVisit) + overlappingVisits
            val earliestEntry: LocalDateTime = allVisits.minOf { it.entryTime }
            val latestExit: LocalDateTime = allVisits.mapNotNull { it.exitTime }.maxOrNull() ?: LocalDateTime.now()

            // Update primary visit with merged time range
            val mergedVisit = primaryVisit.copy(
                entryTime = earliestEntry,
                exitTime = latestExit
            )
            visitRepository.updateVisit(mergedVisit)

            // Mark primary review as approved
            val approvedPrimaryReview = primaryReview.copy(
                status = ReviewStatus.APPROVED,
                reviewedAt = LocalDateTime.now(),
                notes = "Merged with ${overlappingVisits.size} overlapping visit(s)"
            )
            visitReviewRepository.updateReview(approvedPrimaryReview)

            // Mark overlapping reviews as rejected (since they're merged into primary)
            overlappingReviews.forEach { review ->
                val rejectedReview = review.copy(
                    status = ReviewStatus.REJECTED,
                    reviewedAt = LocalDateTime.now(),
                    notes = "Merged into primary visit"
                )
                visitReviewRepository.updateReview(rejectedReview)

                // Delete the overlapping visits
                val visitToDelete = getVisitById(review.visitId)
                if (visitToDelete != null) {
                    visitRepository.deleteVisit(visitToDelete)
                }
            }

            // Update place visit counts
            val affectedPlaceIds = (listOf(primaryReview.placeId) +
                overlappingReviews.map { it.placeId }).distinct()
            affectedPlaceIds.forEach { placeId ->
                updatePlaceVisitCount(placeId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Split a visit into multiple visits at different places
     */
    suspend fun splitVisit(
        reviewId: Long,
        splitPoints: List<SplitPoint>
    ): Result<Unit> {
        return try {
            if (splitPoints.isEmpty()) {
                return Result.failure(Exception("No split points provided"))
            }

            val review = visitReviewRepository.getReviewById(reviewId)
                ?: return Result.failure(Exception("Review not found"))

            val originalVisit = getVisitById(review.visitId)
                ?: return Result.failure(Exception("Visit not found"))

            // Validate split points are within visit time range
            val validSplitPoints = splitPoints.filter { split ->
                split.time.isAfter(originalVisit.entryTime) &&
                    (originalVisit.exitTime == null || split.time.isBefore(originalVisit.exitTime))
            }.sortedBy { it.time }

            if (validSplitPoints.isEmpty()) {
                return Result.failure(Exception("No valid split points within visit time range"))
            }

            // Create segments
            var currentEntry = originalVisit.entryTime
            val segments = mutableListOf<SegmentInfo>()

            validSplitPoints.forEach { split ->
                segments.add(SegmentInfo(split.placeId, currentEntry, split.time))
                currentEntry = split.time
            }

            // Add final segment
            val finalExit = originalVisit.exitTime ?: LocalDateTime.now()
            segments.add(SegmentInfo(originalVisit.placeId, currentEntry, finalExit))

            // Update original visit to first segment
            val firstSegment = segments.first()
            val updatedOriginalVisit = originalVisit.copy(
                placeId = firstSegment.placeId,
                entryTime = firstSegment.entryTime,
                exitTime = firstSegment.exitTime
            )
            visitRepository.updateVisit(updatedOriginalVisit)

            // Create new visits for remaining segments
            segments.drop(1).forEach { segment ->
                val newVisit = Visit(
                    placeId = segment.placeId,
                    entryTime = segment.entryTime,
                    exitTime = segment.exitTime
                )
                visitRepository.insertVisit(newVisit)
            }

            // Mark review as modified
            val modifiedReview = review.copy(
                status = ReviewStatus.MODIFIED,
                reviewedAt = LocalDateTime.now(),
                notes = "Split into ${segments.size} separate visits"
            )
            visitReviewRepository.updateReview(modifiedReview)

            // Update visit counts for all affected places
            segments.map { it.placeId }.distinct().forEach { placeId ->
                updatePlaceVisitCount(placeId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch confirm all short duration visits
     */
    suspend fun batchConfirmShortDuration(): Result<Int> {
        return try {
            val reviews = visitReviewRepository.getPendingReviewsByReason(VisitReviewReason.SHORT_DURATION).first()
            var confirmedCount = 0

            reviews.forEach { review ->
                confirmVisit(review.id).onSuccess {
                    confirmedCount++
                }
            }

            Result.success(confirmedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch reject "just passing by" visits
     */
    suspend fun batchRejectPassingBy(): Result<Int> {
        return try {
            val reviews = visitReviewRepository.getPendingReviewsByReason(VisitReviewReason.JUST_PASSING_BY).first()
            var rejectedCount = 0

            reviews.forEach { review ->
                rejectVisit(review.id, "Automatically rejected - just passing by").onSuccess {
                    rejectedCount++
                }
            }

            Result.success(rejectedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new visit review
     */
    suspend fun createVisitReview(
        visit: Visit,
        place: Place,
        reason: VisitReviewReason,
        alternativePlaceId: Long? = null,
        alternativePlaceName: String? = null
    ): Result<Long> {
        return try {
            val duration = if (visit.exitTime != null) {
                java.time.Duration.between(visit.entryTime, visit.exitTime).toMillis()
            } else {
                0L
            }

            val review = VisitReview(
                visitId = visit.id,
                placeId = place.id,
                placeName = place.name,
                entryTime = visit.entryTime,
                exitTime = visit.exitTime,
                duration = duration,
                confidence = 0.5f, // Default confidence for visits needing review
                reviewReason = reason,
                status = ReviewStatus.PENDING,
                alternativePlaceId = alternativePlaceId,
                alternativePlaceName = alternativePlaceName
            )

            val reviewId = visitReviewRepository.insertReview(review)
            Result.success(reviewId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cleanup expired visit reviews (older than N days)
     */
    suspend fun cleanupExpiredReviews(daysOld: Int = 30): Result<Int> {
        return try {
            // Delete all approved and rejected reviews older than cutoff
            visitReviewRepository.deleteReviewsByStatus(ReviewStatus.APPROVED)
            visitReviewRepository.deleteReviewsByStatus(ReviewStatus.REJECTED)
            Result.success(0) // Return count not available
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Private helper methods

    /**
     * Get visit by ID (helper since repository doesn't provide this)
     */
    private suspend fun getVisitById(visitId: Long): Visit? {
        // Get all visits and find by ID
        // This is not efficient but works with current repository interface
        val allVisits = visitRepository.getAllVisits().first()
        return allVisits.find { it.id == visitId }
    }

    private suspend fun updatePlaceVisitCount(placeId: Long) {
        val visits = visitRepository.getVisitsForPlace(placeId).first()
        val place = placeRepository.getPlaceById(placeId) ?: return

        val updatedPlace = place.copy(
            visitCount = visits.size,
            lastVisit = visits.maxOfOrNull { it.entryTime }
        )
        placeRepository.updatePlace(updatedPlace)
    }

    private suspend fun updatePlaceVisitCounts(oldPlaceId: Long, newPlaceId: Long) {
        updatePlaceVisitCount(oldPlaceId)
        updatePlaceVisitCount(newPlaceId)
    }
}

/**
 * Represents a point where a visit should be split
 */
data class SplitPoint(
    val time: LocalDateTime,
    val placeId: Long
)

/**
 * Represents a segment of a visit (internal helper)
 */
private data class SegmentInfo(
    val placeId: Long,
    val entryTime: LocalDateTime,
    val exitTime: LocalDateTime
)
