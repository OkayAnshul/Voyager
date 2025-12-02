package com.cosmiclaboratory.voyager.domain.model

/**
 * Semantic context - what the user was likely doing at a location
 * Inferred from activity + time patterns + place category
 *
 * Phase 1: Activity-First Implementation
 */
enum class SemanticContext(val displayName: String) {
    // Work-related
    WORKING("Working"),
    COMMUTING("Commuting"),
    WORK_MEETING("Work Meeting"),

    // Health & Fitness
    WORKING_OUT("Working Out"),
    OUTDOOR_EXERCISE("Outdoor Exercise"),

    // Daily Activities
    EATING("Eating"),
    SHOPPING("Shopping"),
    RUNNING_ERRANDS("Running Errands"),

    // Social & Leisure
    SOCIALIZING("Socializing"),
    ENTERTAINMENT("Entertainment"),
    RELAXING_HOME("Relaxing at Home"),

    // Transit
    IN_TRANSIT("In Transit"),
    TRAVELING("Traveling"),

    UNKNOWN("Unknown")
}
