package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.VisitReview
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.VisitReviewReason
import kotlinx.coroutines.flow.Flow

interface VisitReviewRepository {

    fun getReviewsByStatus(status: ReviewStatus): Flow<List<VisitReview>>

    fun getPendingReviews(): Flow<List<VisitReview>>

    fun getPendingReviewCount(): Flow<Int>

    fun getReviewsForPlace(placeId: Long): Flow<List<VisitReview>>

    suspend fun getReviewForVisit(visitId: Long): VisitReview?

    suspend fun getReviewById(id: Long): VisitReview?

    fun getPendingReviewsByReason(reason: VisitReviewReason): Flow<List<VisitReview>>

    suspend fun insertReview(review: VisitReview): Long

    suspend fun updateReview(review: VisitReview)

    suspend fun deleteReview(review: VisitReview)

    suspend fun deleteReviewById(id: Long)

    suspend fun deleteReviewsByStatus(status: ReviewStatus)
}
