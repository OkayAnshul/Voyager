package com.cosmiclaboratory.voyager.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.repository.PlaceReviewRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.model.ReviewPriority
import com.cosmiclaboratory.voyager.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Worker that generates and shows daily place review summary notifications
 *
 * Features:
 * - Shows count of pending place reviews
 * - Highlights high-priority reviews
 * - Provides summary of places needing review
 * - Respects user's review notification preferences
 *
 * Scheduled to run once per day (default: 7 PM)
 * Week 6 implementation
 */
@HiltWorker
class DailyReviewSummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeReviewRepository: PlaceReviewRepository,
    private val preferencesRepository: PreferencesRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "DailyReviewSummaryWorker"
        const val WORK_NAME = "daily_review_summary_work"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting daily review summary generation")

            // Check if review notifications are enabled
            val preferences = preferencesRepository.getUserPreferences().first()
            if (!preferences.reviewNotificationsEnabled) {
                Log.d(TAG, "Review notifications disabled, skipping")
                return Result.success()
            }

            // Get all pending reviews
            val pendingReviews = placeReviewRepository.getPendingReviews().first()

            if (pendingReviews.isEmpty()) {
                Log.d(TAG, "No pending reviews, skipping notification")
                return Result.success()
            }

            // Count high priority reviews
            val highPriorityCount = pendingReviews.count { it.priority == ReviewPriority.HIGH }

            // Generate place summary (top 3 places)
            val placeSummary = pendingReviews
                .take(3)
                .joinToString("\n") { review ->
                    val confidenceStr = String.format("%.0f%%", review.confidence * 100)
                    "• ${review.detectedName ?: "Unknown"} ($confidenceStr)"
                }

            // Show notification
            notificationHelper.showDailyReviewSummaryNotification(
                pendingCount = pendingReviews.size,
                highPriorityCount = highPriorityCount,
                placeSummary = placeSummary
            )

            Log.d(TAG, "Daily review summary notification shown: ${pendingReviews.size} pending")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily review summary", e)
            // Retry on failure
            Result.retry()
        }
    }
}
