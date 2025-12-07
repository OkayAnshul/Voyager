package com.cosmiclaboratory.voyager.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import com.cosmiclaboratory.voyager.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Worker that generates and shows daily summary notifications
 *
 * Features:
 * - Analyzes yesterday's activity
 * - Shows top visited places
 * - Calculates total time at places
 * - Shows visit count
 * - Respects user's notification preferences
 *
 * Scheduled to run once per day (configurable time)
 */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository,
    private val preferencesRepository: PreferencesRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "DailySummaryWorker"
        const val WORK_NAME = "daily_summary_work"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting daily summary generation")

            // Check if notifications are enabled in preferences
            val preferences = preferencesRepository.getUserPreferences().first()
            if (!preferences.enablePatternNotifications) {
                Log.d(TAG, "Pattern notifications disabled in preferences, skipping")
                return Result.success()
            }

            // Check if system notifications are enabled
            if (!notificationHelper.areNotificationsEnabled()) {
                Log.d(TAG, "System notifications disabled, skipping")
                return Result.success()
            }

            // Generate summary
            val summary = generateDailySummary()

            // Show notification if there's meaningful activity
            if (summary.totalVisits > 0) {
                notificationHelper.showDailySummaryNotification(
                    title = summary.title,
                    message = summary.message,
                    expandedText = summary.expandedText
                )
                Log.d(TAG, "Daily summary notification shown")
            } else {
                Log.d(TAG, "No activity yesterday, skipping notification")
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily summary", e)
            Result.failure()
        }
    }

    /**
     * Generate daily summary data
     */
    private suspend fun generateDailySummary(): DailySummary {
        // Get yesterday's date range
        val now = LocalDateTime.now()
        val yesterdayStart = now.minusDays(1).withHour(0).withMinute(0).withSecond(0)
        val yesterdayEnd = now.minusDays(1).withHour(23).withMinute(59).withSecond(59)

        Log.d(TAG, "Analyzing activity from $yesterdayStart to $yesterdayEnd")

        // Get all visits from yesterday
        val visits = visitRepository.getVisitsBetween(yesterdayStart, yesterdayEnd).first()
        val totalVisits = visits.size

        if (totalVisits == 0) {
            return DailySummary(
                title = "No Activity Yesterday",
                message = "You didn't visit any tracked places yesterday",
                expandedText = null,
                totalVisits = 0
            )
        }

        // Get all places
        val places = placeRepository.getAllPlaces().first()
        val placeIdToName = places.associate { it.id to it.name }

        // Calculate statistics
        val totalTime = visits.sumOf { it.duration }
        val totalTimeHours = totalTime / (1000 * 60 * 60)
        val totalTimeMinutes = (totalTime / (1000 * 60)) % 60

        // Find top places by visit count
        val placeVisitCounts = visits
            .groupBy { it.placeId }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        // Find top places by time spent
        val placeTimeCounts = visits
            .groupBy { it.placeId }
            .mapValues { entries -> entries.value.sumOf { it.duration } }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        // Build notification text
        val title = "Yesterday's Summary"

        val message = buildString {
            append("Visited $totalVisits place")
            if (totalVisits != 1) append("s")
            append(" • ")
            append("${totalTimeHours}h ${totalTimeMinutes}m total")
        }

        val expandedText = buildString {
            append("📊 Yesterday's Activity Summary\n\n")

            append("Total Visits: $totalVisits\n")
            append("Total Time: ${totalTimeHours}h ${totalTimeMinutes}m\n\n")

            if (placeVisitCounts.isNotEmpty()) {
                append("🏆 Most Visited:\n")
                placeVisitCounts.forEachIndexed { index, (placeId, count) ->
                    val placeName = placeIdToName[placeId] ?: "Unknown"
                    append("${index + 1}. $placeName ($count visit")
                    if (count != 1) append("s")
                    append(")\n")
                }
                append("\n")
            }

            if (placeTimeCounts.isNotEmpty()) {
                append("⏱️ Most Time Spent:\n")
                placeTimeCounts.forEachIndexed { index, (placeId, time) ->
                    val placeName = placeIdToName[placeId] ?: "Unknown"
                    val hours = time / (1000 * 60 * 60)
                    val minutes = (time / (1000 * 60)) % 60
                    append("${index + 1}. $placeName (${hours}h ${minutes}m)\n")
                }
            }
        }

        return DailySummary(
            title = title,
            message = message,
            expandedText = expandedText,
            totalVisits = totalVisits
        )
    }

    /**
     * Data class for daily summary
     */
    private data class DailySummary(
        val title: String,
        val message: String,
        val expandedText: String?,
        val totalVisits: Int
    )
}
