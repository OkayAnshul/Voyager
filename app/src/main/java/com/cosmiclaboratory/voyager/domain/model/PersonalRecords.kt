package com.cosmiclaboratory.voyager.domain.model

/**
 * Standard "best effort" distances — the fastest time an athlete has ever covered this distance
 * within a single recorded activity (Strava's fastest-1k/5k/… but computed on-device, private).
 */
enum class BestEffortDistance(val label: String, val meters: Double) {
    ONE_K("1 km", 1_000.0),
    FIVE_K("5 km", 5_000.0),
    TEN_K("10 km", 10_000.0),
    HALF_MARATHON("Half marathon", 21_097.5),
    MARATHON("Marathon", 42_195.0),
}

/**
 * The user's all-time personal records, derived purely from their recorded activities. Entirely
 * on-device — the private answer to Strava's public leaderboards ("race yourself, not strangers").
 */
data class PersonalRecords(
    val longestDistanceM: Double = 0.0,
    val biggestClimbM: Double = 0.0,
    val longestStreakDays: Int = 0,
    /** Fastest time (ms) for each distance actually achieved; absent = never covered. */
    val bestEfforts: Map<BestEffortDistance, Long> = emptyMap(),
    val perTypeLongestM: Map<WorkoutType, Double> = emptyMap(),
) {
    val hasAny: Boolean
        get() = longestDistanceM > 0 || biggestClimbM > 0 || bestEfforts.isNotEmpty() || longestStreakDays > 1
}

/** A record a specific activity set or holds — surfaced as a "New PR!" chip. */
data class Achievement(val label: String)
