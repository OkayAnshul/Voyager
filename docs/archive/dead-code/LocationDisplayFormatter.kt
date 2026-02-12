package com.cosmiclaboratory.voyager.domain.ui

import com.cosmiclaboratory.voyager.domain.model.Place
import java.time.Duration

/**
 * Single source of truth for formatting place names, addresses, and durations
 * across all UI screens. Eliminates ad-hoc formatting inconsistencies.
 */
object LocationDisplayFormatter {

    /**
     * Returns the best display name for a place, using the model's priority:
     * user name > OSM suggested > first address segment > category
     */
    fun formatPlaceName(place: Place): String = place.getBestName()

    /**
     * Returns the place name uppercased, for use in card headers.
     */
    fun formatPlaceNameUppercase(place: Place): String = place.getBestName().uppercase()

    /**
     * Compact address: first comma segment only (e.g. "123 Main St")
     */
    fun formatAddressCompact(place: Place): String? {
        val address = place.address ?: return null
        return address.split(",").firstOrNull()?.trim() ?: address
    }

    /**
     * Full address string.
     */
    fun formatAddressFull(place: Place): String? = place.address

    /**
     * Standard duration format: "2h 15m", "45m", or "< 1m"
     */
    fun formatDuration(milliseconds: Long): String {
        val duration = Duration.ofMillis(milliseconds)
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    /**
     * Detailed duration format with seconds: "2h 15m 30s", "45m 10s", "30s"
     */
    fun formatDurationDetailed(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m ${secs}s"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    /**
     * Formats coordinates to 6 decimal places.
     */
    fun formatCoordinates(latitude: Double, longitude: Double): String {
        return "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"
    }
}
