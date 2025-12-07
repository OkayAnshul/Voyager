package com.cosmiclaboratory.voyager.utils

import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sleep schedule logic for battery optimization
 *
 * Pauses location tracking during user's sleep hours to save ~40% battery.
 * Handles midnight crossing (e.g., 22:00 - 06:00) correctly.
 */
@Singleton
class SleepScheduleManager @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {
    companion object {
        private const val TAG = "SleepScheduleManager"
    }

    /**
     * Check if current time is within the configured sleep window
     *
     * @return true if sleep mode is enabled and current time is in sleep window
     */
    suspend fun isInSleepWindow(): Boolean {
        val prefs = preferencesRepository.getUserPreferences().first()

        if (!prefs.sleepModeEnabled) {
            return false
        }

        val currentHour = LocalDateTime.now().hour
        val inWindow = isInRange(currentHour, prefs.sleepStartHour, prefs.sleepEndHour)

        Log.d(TAG, "Sleep window check: current=$currentHour, range=${prefs.sleepStartHour}-${prefs.sleepEndHour}, inWindow=$inWindow")

        return inWindow
    }

    /**
     * Check if a specific hour is within the sleep window
     *
     * @param hour Hour to check (0-23)
     * @param prefs User preferences containing sleep schedule
     * @return true if the hour is in the sleep window
     */
    fun isInSleepWindow(hour: Int, prefs: UserPreferences): Boolean {
        if (!prefs.sleepModeEnabled) {
            return false
        }

        return isInRange(hour, prefs.sleepStartHour, prefs.sleepEndHour)
    }

    /**
     * Helper function to check if a value is within a range, handling midnight crossing
     *
     * Examples:
     * - isInRange(23, 22, 6) = true  // 11 PM is between 10 PM and 6 AM
     * - isInRange(3, 22, 6) = true   // 3 AM is between 10 PM and 6 AM
     * - isInRange(12, 22, 6) = false // 12 PM is NOT between 10 PM and 6 AM
     * - isInRange(10, 9, 17) = true  // 10 AM is between 9 AM and 5 PM
     *
     * @param current Current hour (0-23)
     * @param start Sleep start hour (0-23)
     * @param end Sleep end hour (0-23)
     * @return true if current is within [start, end)
     */
    private fun isInRange(current: Int, start: Int, end: Int): Boolean {
        return if (start < end) {
            // Normal range (e.g., 9 AM to 5 PM)
            current >= start && current < end
        } else {
            // Crosses midnight (e.g., 10 PM to 6 AM)
            current >= start || current < end
        }
    }

    /**
     * Calculate sleep duration in hours
     *
     * @param prefs User preferences containing sleep schedule
     * @return Duration in hours
     */
    fun getSleepDuration(prefs: UserPreferences): Int {
        return if (prefs.sleepStartHour < prefs.sleepEndHour) {
            prefs.sleepEndHour - prefs.sleepStartHour
        } else {
            24 - prefs.sleepStartHour + prefs.sleepEndHour
        }
    }

    /**
     * Format sleep schedule as human-readable string
     *
     * Examples:
     * - "10:00 PM - 6:00 AM"
     * - "11:00 PM - 7:00 AM"
     *
     * @param prefs User preferences containing sleep schedule
     * @return Formatted string
     */
    fun formatSleepSchedule(prefs: UserPreferences): String {
        return "${formatHour(prefs.sleepStartHour)} - ${formatHour(prefs.sleepEndHour)}"
    }

    /**
     * Format hour as 12-hour time string
     *
     * Examples:
     * - 0 -> "12:00 AM"
     * - 6 -> "6:00 AM"
     * - 12 -> "12:00 PM"
     * - 22 -> "10:00 PM"
     *
     * @param hour Hour in 24-hour format (0-23)
     * @return Formatted string
     */
    private fun formatHour(hour: Int): String {
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val amPm = if (hour < 12) "AM" else "PM"
        return "$h:00 $amPm"
    }

    /**
     * Estimate battery savings percentage from sleep mode
     *
     * Calculation:
     * - Sleep duration / 24 hours = percentage of day with no tracking
     * - Example: 8 hours sleep = 33% battery savings
     *
     * @param prefs User preferences containing sleep schedule
     * @return Estimated savings percentage (0-100)
     */
    fun estimateBatterySavings(prefs: UserPreferences): Int {
        if (!prefs.sleepModeEnabled) {
            return 0
        }

        val sleepHours = getSleepDuration(prefs)
        return ((sleepHours.toFloat() / 24f) * 100).toInt()
    }
}
