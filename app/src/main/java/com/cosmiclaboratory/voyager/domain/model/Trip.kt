package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A detected multi-day trip away from home — the denormalized summary that powers the
 * trip list. The day-by-day breakdown lives in [TripDetail], rebuilt on demand.
 */
data class Trip(
    val id: Long,
    val startDayKey: String,
    val endDayKey: String,
    /** The auto-derived title. Prefer [displayTitle] for anything user-facing. */
    val title: String,
    val placeCount: Int,
    val visitCount: Int,
    val distanceMeters: Double,
    val isOngoing: Boolean,
    val detectedAt: Long,
    /** User-authored override for [title]; null/blank means use the derived title. */
    val userTitle: String? = null,
    val notes: String? = null,
    val coverPhotoUri: String? = null
) {
    /** Inclusive calendar length of the trip, e.g. Mon→Wed is 3 days. */
    val durationDays: Int
        get() = (ChronoUnit.DAYS.between(
            LocalDate.parse(startDayKey), LocalDate.parse(endDayKey)
        ) + 1).toInt()

    val distanceKm: Double get() = distanceMeters / 1000.0

    /** What to show the user — the user's title if they set one, else the derived title. */
    val displayTitle: String get() = userTitle?.takeIf { it.isNotBlank() } ?: title
}

/** A trip plus its day-by-day breakdown — built on demand for the detail screen. */
data class TripDetail(
    val trip: Trip,
    val days: List<TripDay>
)

/** One day of a trip: the places visited and the distance covered. */
data class TripDay(
    val dayKey: String,
    val places: List<TripPlaceVisit>,
    val distanceMeters: Double
)

/** A single place visited during a trip day. */
data class TripPlaceVisit(
    val placeId: Long,
    val displayName: String,
    val emoji: String?,
    val arrivalAt: Long,
    val departureAt: Long?,
    val dwellMs: Long
)
