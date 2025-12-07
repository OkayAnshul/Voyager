package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.PlaceReviewDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.domain.model.PlaceReview
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import com.cosmiclaboratory.voyager.domain.model.ReviewType
import com.cosmiclaboratory.voyager.domain.repository.PlaceReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceReviewRepositoryImpl @Inject constructor(
    private val placeReviewDao: PlaceReviewDao
) : PlaceReviewRepository {

    override fun getReviewsByStatus(status: ReviewStatus): Flow<List<PlaceReview>> {
        return placeReviewDao.getReviewsByStatus(status).map { it.toDomainModels() }
    }

    override fun getPendingReviews(): Flow<List<PlaceReview>> {
        return placeReviewDao.getPendingReviews().map { it.toDomainModels() }
    }

    override fun getPendingReviewCount(): Flow<Int> {
        return placeReviewDao.getPendingReviewCount()
    }

    override fun getPendingReviewsByPriority(priority: ReviewPriority): Flow<List<PlaceReview>> {
        return placeReviewDao.getPendingReviewsByPriority(priority).map { it.toDomainModels() }
    }

    override fun getReviewsForPlace(placeId: Long): Flow<List<PlaceReview>> {
        return placeReviewDao.getReviewsForPlace(placeId).map { it.toDomainModels() }
    }

    override suspend fun getReviewById(id: Long): PlaceReview? {
        return placeReviewDao.getReviewById(id)?.toDomainModel()
    }

    override fun getPendingReviewsByType(reviewType: ReviewType): Flow<List<PlaceReview>> {
        return placeReviewDao.getPendingReviewsByType(reviewType).map { it.toDomainModels() }
    }

    override suspend fun insertReview(review: PlaceReview): Long {
        return placeReviewDao.insertReview(review.toEntity())
    }

    override suspend fun updateReview(review: PlaceReview) {
        placeReviewDao.updateReview(review.toEntity())
    }

    override suspend fun deleteReview(review: PlaceReview) {
        placeReviewDao.deleteReview(review.toEntity())
    }

    override suspend fun deleteReviewById(id: Long) {
        placeReviewDao.deleteReviewById(id)
    }

    override suspend fun deleteReviewsByStatus(status: ReviewStatus) {
        placeReviewDao.deleteReviewsByStatus(status)
    }

    override suspend fun updateReviewStatus(oldStatus: ReviewStatus, newStatus: ReviewStatus) {
        placeReviewDao.updateReviewStatus(oldStatus, newStatus)
    }

    override suspend fun getExpiredPendingReviews(before: LocalDateTime): List<PlaceReview> {
        return placeReviewDao.getExpiredPendingReviews(before).toDomainModels()
    }

    override suspend fun cleanupExpiredReviews(before: LocalDateTime) {
        val expiredReviews = placeReviewDao.getExpiredPendingReviews(before)
        expiredReviews.forEach { review ->
            placeReviewDao.updateReview(review.copy(status = ReviewStatus.EXPIRED))
        }
    }
}
