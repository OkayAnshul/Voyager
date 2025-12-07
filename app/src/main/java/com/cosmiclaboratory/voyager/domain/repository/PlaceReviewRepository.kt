package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.PlaceReview
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import com.cosmiclaboratory.voyager.domain.model.ReviewType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface PlaceReviewRepository {

    fun getReviewsByStatus(status: ReviewStatus): Flow<List<PlaceReview>>

    fun getPendingReviews(): Flow<List<PlaceReview>>

    fun getPendingReviewCount(): Flow<Int>

    fun getPendingReviewsByPriority(priority: ReviewPriority): Flow<List<PlaceReview>>

    fun getReviewsForPlace(placeId: Long): Flow<List<PlaceReview>>

    suspend fun getReviewById(id: Long): PlaceReview?

    fun getPendingReviewsByType(reviewType: ReviewType): Flow<List<PlaceReview>>

    suspend fun insertReview(review: PlaceReview): Long

    suspend fun updateReview(review: PlaceReview)

    suspend fun deleteReview(review: PlaceReview)

    suspend fun deleteReviewById(id: Long)

    suspend fun deleteReviewsByStatus(status: ReviewStatus)

    suspend fun updateReviewStatus(oldStatus: ReviewStatus, newStatus: ReviewStatus)

    suspend fun getExpiredPendingReviews(before: LocalDateTime): List<PlaceReview>

    suspend fun cleanupExpiredReviews(before: LocalDateTime)
}
