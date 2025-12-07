package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Represents a detected anomaly in user behavior
 *
 * Anomalies help users notice when they break their patterns:
 * - "You haven't been to Gym in 2 weeks!" (normally go 3x/week)
 * - "You spent 3 hours at Coffee Shop today" (normally 30 min)
 * - "You visited Gym at 10 AM" (normally 6 PM)
 */
sealed class Anomaly {
    abstract val place: Place
    abstract val message: String
    abstract val severity: AnomalySeverity
    abstract val detectedAt: LocalDateTime

    /**
     * User missed visiting a regular place
     */
    data class MissedPlace(
        override val place: Place,
        val daysSinceLastVisit: Int,
        val expectedFrequency: Float,  // visits per week
        override val message: String,
        override val severity: AnomalySeverity = AnomalySeverity.MEDIUM,
        override val detectedAt: LocalDateTime = LocalDateTime.now()
    ) : Anomaly()

    /**
     * Visit duration is significantly different from usual
     */
    data class UnusualDuration(
        override val place: Place,
        val actualDuration: Long,  // milliseconds
        val expectedDuration: Long,  // milliseconds
        val percentageDifference: Float,
        override val message: String,
        override val severity: AnomalySeverity = AnomalySeverity.LOW,
        override val detectedAt: LocalDateTime = LocalDateTime.now()
    ) : Anomaly()

    /**
     * Visit occurred at an unusual time
     */
    data class UnusualTime(
        override val place: Place,
        val visitTime: LocalTime,
        val expectedTime: LocalTime,
        val hoursDifference: Int,
        override val message: String,
        override val severity: AnomalySeverity = AnomalySeverity.LOW,
        override val detectedAt: LocalDateTime = LocalDateTime.now()
    ) : Anomaly()

    /**
     * Visited on an unusual day
     */
    data class UnusualDay(
        override val place: Place,
        val visitDay: java.time.DayOfWeek,
        val expectedDays: List<java.time.DayOfWeek>,
        override val message: String,
        override val severity: AnomalySeverity = AnomalySeverity.LOW,
        override val detectedAt: LocalDateTime = LocalDateTime.now()
    ) : Anomaly()

    /**
     * New place detected (could be interesting, not necessarily negative)
     */
    data class NewPlace(
        override val place: Place,
        val firstVisit: LocalDateTime,
        override val message: String,
        override val severity: AnomalySeverity = AnomalySeverity.INFO,
        override val detectedAt: LocalDateTime = LocalDateTime.now()
    ) : Anomaly()
}

/**
 * Severity level of the anomaly
 */
enum class AnomalySeverity {
    INFO,      // Informational, just interesting
    LOW,       // Minor deviation
    MEDIUM,    // Notable deviation
    HIGH       // Significant deviation
}

/**
 * Helper to get emoji for anomaly severity
 */
fun AnomalySeverity.toEmoji(): String {
    return when (this) {
        AnomalySeverity.INFO -> "ℹ️"
        AnomalySeverity.LOW -> "⚠️"
        AnomalySeverity.MEDIUM -> "⚠️"
        AnomalySeverity.HIGH -> "🚨"
    }
}

/**
 * Helper to get color for anomaly severity (for UI)
 */
fun AnomalySeverity.toColorName(): String {
    return when (this) {
        AnomalySeverity.INFO -> "primary"
        AnomalySeverity.LOW -> "tertiary"
        AnomalySeverity.MEDIUM -> "secondary"
        AnomalySeverity.HIGH -> "error"
    }
}

/**
 * Helper to format anomaly for display
 */
fun Anomaly.toDisplayString(): String {
    return "${severity.toEmoji()} $message"
}
