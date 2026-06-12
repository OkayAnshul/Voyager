package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType

/**
 * Pure decision for what to do when a sample arrives with no active visit candidate but a
 * recent departure on record: **continue** the just-departed visit (the user never really
 * left — e.g. the app was killed for a few minutes), **start fresh** (the departure is stale
 * or the user genuinely went somewhere), or **keep waiting** (within the window and not yet
 * back inside the place).
 *
 * Why this exists (T6): the previous inline logic treated *any* return within 30 minutes that
 * landed back inside the place radius as a continuation, gating it only on motorised movement
 * (DRIVE/CYCLE/RUN/FLIGHT) during the gap. That was overeager — it silently merged genuine
 * on-foot round trips (walk to a shop 400 m away and back) and public-transport hops (TRANSIT)
 * into one unbroken visit. This policy broadens the "moved away" signal:
 *  - **TRANSIT** now counts as motorised displacement.
 *  - a **WALK** covering a meaningful distance during the gap (≥ [walkMovedAwayThresholdM])
 *    counts as a real excursion.
 *
 * Crucially this does **not** regress the case the return window exists for — a PROCESS_DEAD
 * gap (app killed while the user sat still) produces *no* movement segments, so bridging it
 * still continues the visit.
 */
object QuickReturnPolicy {

    /** Max time after departure during which a return can continue the previous visit.
     *  30 minutes covers typical 15–30 min PROCESS_DEAD gaps; the place radius gates space. */
    const val RETURN_WINDOW_MS = 1_800_000L // 30 minutes

    /** A gap WALK of at least this many place-radii is a genuine excursion, not a step-outside. */
    const val MOVED_AWAY_WALK_RADIUS_MULTIPLE = 2.0

    /** Absolute floor for the walk-excursion threshold, so small-radius profiles don't split a
     *  visit for stepping just outside (e.g. to a building's parking lot and back). */
    const val MOVED_AWAY_WALK_FLOOR_M = 100.0

    /** Segment types that are unambiguous displacement away from the place. */
    private val MOTORIZED_TYPES = setOf(
        SegmentType.DRIVE.name,
        SegmentType.CYCLE.name,
        SegmentType.RUN.name,
        SegmentType.FLIGHT.name,
        SegmentType.TRANSIT.name,
    )

    /** A movement segment that closed during the departure→return gap. */
    data class GapSegment(val type: String, val distanceM: Double)

    enum class Decision {
        /** Reopen the just-departed visit as one continuous visit. */
        CONTINUE,

        /** Forget the departure — the user genuinely moved away (or the window expired). */
        CLEAR_MEMORY,

        /** Still within the window but not back inside the place yet — retain departure memory. */
        KEEP_WAITING,
    }

    /** Distance a gap WALK must cover to count as having moved away, for this place radius. */
    fun walkMovedAwayThresholdM(placeRadiusM: Double): Double =
        maxOf(placeRadiusM * MOVED_AWAY_WALK_RADIUS_MULTIPLE, MOVED_AWAY_WALK_FLOOR_M)

    /**
     * @param gapSegments segments that closed strictly after the departure and at/before now.
     */
    fun decide(
        timeSinceDepartureMs: Long,
        distFromPreviousM: Double,
        placeRadiusM: Double,
        hasPreviousVisit: Boolean,
        gapSegments: List<GapSegment>,
    ): Decision {
        if (timeSinceDepartureMs > RETURN_WINDOW_MS) return Decision.CLEAR_MEMORY
        if (movedAway(gapSegments, placeRadiusM)) return Decision.CLEAR_MEMORY
        // Back inside the place's footprint and we know which visit to reopen → continue.
        if (distFromPreviousM <= placeRadiusM && hasPreviousVisit) return Decision.CONTINUE
        // Within the window but still outside the place — wait for a later returning sample.
        return Decision.KEEP_WAITING
    }

    private fun movedAway(gapSegments: List<GapSegment>, placeRadiusM: Double): Boolean {
        if (gapSegments.any { it.type in MOTORIZED_TYPES }) return true
        val walkDistance = gapSegments
            .filter { it.type == SegmentType.WALK.name }
            .sumOf { it.distanceM }
        return walkDistance >= walkMovedAwayThresholdM(placeRadiusM)
    }
}
