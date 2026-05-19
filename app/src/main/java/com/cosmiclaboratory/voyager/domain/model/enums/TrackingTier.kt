package com.cosmiclaboratory.voyager.domain.model.enums

/**
 * The user-facing tracking mode — how aggressively Voyager captures location.
 *
 * This is the primary tracking control. It sets the *structural* behaviour:
 * whether an active GPS request runs at all, and the base cadence when it does.
 * [SamplingPreset] remains a secondary fine-tune multiplier applied on top for
 * the [BALANCED] / [ACCURATE] tiers.
 *
 * - [OFF] / [PASSIVE] run **no active GPS request** — Voyager relies on the
 *   always-on passive location listener (free fixes other apps already trigger)
 *   plus activity-recognition, geofence and significant-motion wakeups.
 * - [WORKOUT] is a foreground, maximum-accuracy recording mode — used while an
 *   activity is being recorded, never as a background tier.
 *
 * [estimatedBatteryPctPerDay] is a rough whole-day figure on a reference device
 * with default settings — surfaced in the tier picker so the cost is honest.
 */
enum class TrackingTier(
    val displayName: String,
    val description: String,
    val estimatedBatteryPctPerDay: String
) {
    OFF(
        displayName = "Off",
        description = "Tracking paused — no location captured.",
        estimatedBatteryPctPerDay = "0%"
    ),
    PASSIVE(
        displayName = "Passive",
        description = "Near-zero battery. No active GPS — Voyager rides location " +
            "other apps already request, plus motion and step sensors.",
        estimatedBatteryPctPerDay = "~1%"
    ),
    BALANCED(
        displayName = "Balanced",
        description = "The everyday default. GPS samples while you move and " +
            "sleeps while you're still.",
        estimatedBatteryPctPerDay = "~2–4%"
    ),
    ACCURATE(
        displayName = "Accurate",
        description = "Tighter, more frequent fixes — best for drive logs and " +
            "mileage proof.",
        estimatedBatteryPctPerDay = "~4–7%"
    ),
    WORKOUT(
        displayName = "Workout",
        description = "Maximum-accuracy recording for an activity in progress. " +
            "Use only while recording — it is not a background mode.",
        estimatedBatteryPctPerDay = "while recording"
    );

    companion object {
        /** Lenient parse for persisted values — unknown/blank falls back to [BALANCED]. */
        fun fromName(name: String?): TrackingTier =
            entries.firstOrNull { it.name == name } ?: BALANCED
    }
}
