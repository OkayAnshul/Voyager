package com.cosmiclaboratory.voyager.domain.model

/**
 * Semantic context - what the user was likely doing at a location
 * Inferred from activity + time patterns + place category
 *
 * Phase 1: Activity-First Implementation
 */
enum class SemanticContext {
    // Work-related
    WORKING,           // At work, stationary, work hours
    COMMUTING,         // Driving/transit, between home-work
    WORK_MEETING,      // At non-work location during work hours

    // Health & Fitness
    WORKING_OUT,       // Gym, high movement, workout hours
    OUTDOOR_EXERCISE,  // Walking/cycling, sustained movement

    // Daily Activities
    EATING,            // Restaurant, meal times, stationary
    SHOPPING,          // Shopping area, walking, 30-120 min
    RUNNING_ERRANDS,   // Multiple short stops, varied activities

    // Social & Leisure
    SOCIALIZING,       // Restaurant/cafe, evening/weekend
    ENTERTAINMENT,     // Cinema/mall, leisure hours
    RELAXING_HOME,     // Home, stationary, evening

    // Transit
    IN_TRANSIT,        // Driving/cycling between places
    TRAVELING,         // Far from home, exploring new areas

    UNKNOWN            // Can't determine context
}
