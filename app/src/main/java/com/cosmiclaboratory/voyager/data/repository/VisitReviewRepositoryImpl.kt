package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.VisitReviewDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.domain.model.VisitReview
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.VisitReviewReason
import com.cosmiclaboratory.voyager.domain.repository.VisitReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitReviewRepositoryImpl @Inject constructor(
    private val visitReviewDao: VisitReviewDao
) : VisitReviewRepository {

    override fun getReviewsByStatus(status: ReviewStatus): Flow<List<VisitReview>> {
        return visitReviewDao.getReviewsByStatus(status).map { it.toDomainModels() }
    }

    override fun getPendingReviews(): Flow<List<VisitReview>> {
        return visitReviewDao.getPendingReviews().map { it.toDomainModels() }
    }

    override fun getPendingReviewCount(): Flow<Int> {
        return visitReviewDao.getPendingReviewCount()
    }

    override fun getReviewsForPlace(placeId: Long): Flow<List<VisitReview>> {
        return visitReviewDao.getReviewsForPlace(placeId).map { it.toDomainModels() }
    }

    override suspend fun getReviewForVisit(visitId: Long): VisitReview? {
        return visitReviewDao.getReviewForVisit(visitId)?.toDomainModel()
    }

    override suspend fun getReviewById(id: Long): VisitReview? {
        return visitReviewDao.getReviewById(id)?.toDomainModel()
    }

    override fun getPendingReviewsByReason(reason: VisitReviewReason): Flow<List<VisitReview>> {
        return visitReviewDao.getPendingReviewsByReason(reason).map { it.toDomainModels() }
    }

    override suspend fun insertReview(review: VisitReview): Long {
        return visitReviewDao.insertReview(review.toEntity())
    }

    override suspend fun updateReview(review: VisitReview) {
        visitReviewDao.updateReview(review.toEntity())
    }

    override suspend fun deleteReview(review: VisitReview) {
        visitReviewDao.deleteReview(review.toEntity())
    }

    override suspend fun deleteReviewById(id: Long) {
        visitReviewDao.deleteReviewById(id)
    }

    override suspend fun deleteReviewsByStatus(status: ReviewStatus) {
        visitReviewDao.deleteReviewsByStatus(status)
    }
}
