package com.cosmiclaboratory.voyager.pipeline

/**
 * Shared constants for the capture pipeline.
 * Used by both PipelineConsumer (to force departure + sampling boost)
 * and Segmenter (to override segment type on large displacement).
 */
object PipelineConstants {
    /** Displacement threshold to force transit when AR misses a transition.
     *  Raised from 100m to 150m — cold-start GPS drift can exceed 100m,
     *  150m provides 3x margin over worst-case jitter (~50m). */
    const val DISPLACEMENT_TRANSIT_THRESHOLD_M = 150.0
    /** Minimum implied speed (m/s) to confirm real movement vs GPS jump.
     *  1.5 m/s = comfortable walking pace. */
    const val DISPLACEMENT_SPEED_THRESHOLD_MPS = 1.5
    /** Max GPS accuracy to trust for displacement detection.
     *  Tightened from 50m to prevent GPS-jump false positives. */
    const val DISPLACEMENT_MAX_ACCURACY_M = 30f
    /** Tighter accuracy for displacement shortly after dormant exit.
     *  GPS chipsets report optimistic accuracy on cold start. */
    const val DISPLACEMENT_POST_DORMANT_MAX_ACCURACY_M = 15f
    /** Grace period after dormant exit — skip displacement detection
     *  while GPS settles from cold start. */
    const val DISPLACEMENT_DORMANT_GRACE_MS = 60_000L
    /** Cooldown between consecutive displacement overrides. */
    const val DISPLACEMENT_COOLDOWN_MS = 300_000L
}
