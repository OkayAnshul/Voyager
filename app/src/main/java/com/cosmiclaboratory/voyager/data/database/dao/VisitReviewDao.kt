package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.VisitReviewEntity
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.VisitReviewReason
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitReviewDao {

    @Query("SELECT * FROM visit_reviews WHERE status = :status ORDER BY entryTime DESC")
    fun getReviewsByStatus(status: ReviewStatus): Flow<List<VisitReviewEntity>>

    @Query("SELECT * FROM visit_reviews WHERE status = 'PENDING' ORDER BY entryTime DESC")
    fun getPendingReviews(): Flow<List<VisitReviewEntity>>

    @Query("SELECT COUNT(*) FROM visit_reviews WHERE status = 'PENDING'")
    fun getPendingReviewCount(): Flow<Int>

    @Query("SELECT * FROM visit_reviews WHERE placeId = :placeId ORDER BY entryTime DESC")
    fun getReviewsForPlace(placeId: Long): Flow<List<VisitReviewEntity>>

    @Query("SELECT * FROM visit_reviews WHERE visitId = :visitId")
    suspend fun getReviewForVisit(visitId: Long): VisitReviewEntity?

    @Query("SELECT * FROM visit_reviews WHERE id = :id")
    suspend fun getReviewById(id: Long): VisitReviewEntity?

    @Query("SELECT * FROM visit_reviews WHERE reviewReason = :reason AND status = 'PENDING' ORDER BY entryTime DESC")
    fun getPendingReviewsByReason(reason: VisitReviewReason): Flow<List<VisitReviewEntity>>

    @Insert
    suspend fun insertReview(review: VisitReviewEntity): Long

    @Update
    suspend fun updateReview(review: VisitReviewEntity)

    @Delete
    suspend fun deleteReview(review: VisitReviewEntity)

    @Query("DELETE FROM visit_reviews WHERE id = :id")
    suspend fun deleteReviewById(id: Long)

    @Query("DELETE FROM visit_reviews WHERE status = :status")
    suspend fun deleteReviewsByStatus(status: ReviewStatus)
}
