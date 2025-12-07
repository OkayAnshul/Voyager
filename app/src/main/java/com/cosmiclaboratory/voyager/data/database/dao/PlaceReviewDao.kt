package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.PlaceReviewEntity
import com.cosmiclaboratory.voyager.domain.model.ReviewStatus
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import com.cosmiclaboratory.voyager.domain.model.ReviewType
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceReviewDao {

    @Query("SELECT * FROM place_reviews WHERE status = :status ORDER BY priority DESC, detectionTime DESC")
    fun getReviewsByStatus(status: ReviewStatus): Flow<List<PlaceReviewEntity>>

    @Query("SELECT * FROM place_reviews WHERE status = 'PENDING' ORDER BY priority DESC, detectionTime DESC")
    fun getPendingReviews(): Flow<List<PlaceReviewEntity>>

    @Query("SELECT COUNT(*) FROM place_reviews WHERE status = 'PENDING'")
    fun getPendingReviewCount(): Flow<Int>

    @Query("SELECT * FROM place_reviews WHERE priority = :priority AND status = 'PENDING' ORDER BY detectionTime DESC")
    fun getPendingReviewsByPriority(priority: ReviewPriority): Flow<List<PlaceReviewEntity>>

    @Query("SELECT * FROM place_reviews WHERE placeId = :placeId ORDER BY detectionTime DESC")
    fun getReviewsForPlace(placeId: Long): Flow<List<PlaceReviewEntity>>

    @Query("SELECT * FROM place_reviews WHERE id = :id")
    suspend fun getReviewById(id: Long): PlaceReviewEntity?

    @Query("SELECT * FROM place_reviews WHERE reviewType = :reviewType AND status = 'PENDING' ORDER BY priority DESC")
    fun getPendingReviewsByType(reviewType: ReviewType): Flow<List<PlaceReviewEntity>>

    @Insert
    suspend fun insertReview(review: PlaceReviewEntity): Long

    @Update
    suspend fun updateReview(review: PlaceReviewEntity)

    @Delete
    suspend fun deleteReview(review: PlaceReviewEntity)

    @Query("DELETE FROM place_reviews WHERE id = :id")
    suspend fun deleteReviewById(id: Long)

    @Query("DELETE FROM place_reviews WHERE status = :status")
    suspend fun deleteReviewsByStatus(status: ReviewStatus)

    @Query("UPDATE place_reviews SET status = :newStatus WHERE status = :oldStatus")
    suspend fun updateReviewStatus(oldStatus: ReviewStatus, newStatus: ReviewStatus)

    @Query("SELECT * FROM place_reviews WHERE detectionTime < :before AND status = 'PENDING'")
    suspend fun getExpiredPendingReviews(before: java.time.LocalDateTime): List<PlaceReviewEntity>
}
