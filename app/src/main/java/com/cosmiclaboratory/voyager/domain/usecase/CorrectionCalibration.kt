package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType

/**
 * Pure calibration policy applied to user corrections by [com.cosmiclaboratory.voyager.platform.worker.FeedbackCalibrationWorker].
 *
 * First implemented slice: **place-trust**. When a user confirms a visit/place or edits a
 * place's name/category, that's strong evidence the place is real and correctly identified —
 * so its confidence is raised toward a high floor. This is deliberately conservative: it only
 * ever *raises* confidence (never lowers), and only for places the user actually touched.
 *
 * Effect: user-validated places drop out of the review queue (W4.4, threshold 0.7) and start
 * decay (W2.7) from a high base, so the app stops re-asking about places the user already settled.
 *
 * Not yet covered (future slices, fed by `CorrectionFeedbackDao.getByCorrectionTypeSince` so the
 * propagated flag doesn't matter): RECLASSIFY_SEGMENT → transport-mode weight tuning,
 * MERGE/SPLIT_PLACE → clustering params, DELETE_VISIT/ADJUST_TIMES → visit-detection thresholds.
 */
object CorrectionCalibration {

    /** Confidence a place is lifted to once the user validates its identity. */
    const val USER_VALIDATED_CONFIDENCE = 0.95f

    /** Correction types that signal the user validated a *place's* identity. */
    fun isPlaceTrustSignal(correctionType: String): Boolean = correctionType in PLACE_TRUST_SIGNALS

    /** Calibrated confidence after a validation signal — monotonic (only ever raises). */
    fun boostedConfidence(current: Float): Float = maxOf(current, USER_VALIDATED_CONFIDENCE)

    private val PLACE_TRUST_SIGNALS: Set<String> = setOf(
        CorrectionType.CONFIRM.name,
        CorrectionType.CONFIRM_VISIT.name,
        CorrectionType.RENAME.name,
        CorrectionType.RECATEGORIZE.name,
    )

    // ── Segment reclassification: detect systematic bias (do NOT auto-tune) ──

    /** A classifier→user correction pattern that the user repeats. */
    data class MisclassificationPattern(val from: String, val to: String, val count: Int)

    /** Number of times one (classifier → user) correction must recur to be "systematic". */
    const val SYSTEMATIC_MIN_COUNT = 3

    /**
     * Systematic transport-mode misclassifications: (classifierType → userType) corrections that
     * recur at least [minCount] times. Deliberately **report-only** — calibration here surfaces a
     * consistent bias (e.g. "DRIVE keeps being corrected to CYCLE") for diagnostics and a future,
     * carefully-validated tuning pass. It never auto-adjusts global detection, because a handful
     * of corrections must not swing behaviour for everyone (the flagship "conservative" bar).
     */
    fun systematicMisclassifications(
        classifierToUser: List<Pair<String, String>>,
        minCount: Int = SYSTEMATIC_MIN_COUNT,
    ): List<MisclassificationPattern> =
        classifierToUser
            .filter { it.first != it.second }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= minCount }
            .map { (pair, count) -> MisclassificationPattern(pair.first, pair.second, count) }
            .sortedByDescending { it.count }
}
