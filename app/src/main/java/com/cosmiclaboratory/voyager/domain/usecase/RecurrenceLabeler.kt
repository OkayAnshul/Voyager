package com.cosmiclaboratory.voyager.domain.usecase

/**
 * Pure mapping from a place's stored repeatability evidence (the C1 signal) to a
 * short, honest recurrence label shown on the timeline. Kept pure + dependency-free
 * so it is unit-testable without Room or Android.
 *
 * The labels are deliberately frequency-based (not weekday-specific) because
 * `PlaceEvidenceEntity` does not carry a day-of-week distribution — we only know
 * how often and how regularly the place is visited.
 */
object RecurrenceLabeler {

    /**
     * @param repeatabilityScore 0..1 regularity of visits (higher = more rhythmic).
     * @param visitCountLast7d   visits in the trailing week.
     * @param visitCountLast30d  visits in the trailing month.
     * @return a short label, or null when the place is not visited often enough to
     *         claim a routine (avoids over-claiming on a one-off stop).
     */
    fun label(
        repeatabilityScore: Float,
        visitCountLast7d: Int,
        visitCountLast30d: Int
    ): String? = when {
        repeatabilityScore >= 0.7f || visitCountLast7d >= 5 -> "Part of your routine"
        repeatabilityScore >= 0.4f || visitCountLast7d >= 3 -> "You come here often"
        visitCountLast30d >= 6 -> "Regular spot"
        else -> null
    }
}
