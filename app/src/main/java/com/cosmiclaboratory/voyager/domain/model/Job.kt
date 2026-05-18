package com.cosmiclaboratory.voyager.domain.model

/**
 * The primary job a user hires Voyager to do, chosen during onboarding.
 *
 * A persona = a [Job] + a starting tracking preset ([SettingsPresets]). The job
 * drives dashboard emphasis; the preset configures the capture pipeline.
 */
enum class Job(
    val id: String,
    val displayName: String,
    val tagline: String
) {
    MEMORY(
        id = "MEMORY",
        displayName = "Memory",
        tagline = "A private timeline of where life happened"
    ),
    PROOF(
        id = "PROOF",
        displayName = "Proof",
        tagline = "Evidence-grade logs — mileage, visits, history"
    ),
    HABITS(
        id = "HABITS",
        displayName = "Habits",
        tagline = "Patterns, routines and how you move"
    );

    companion object {
        fun fromId(id: String): Job? = entries.firstOrNull { it.id == id }
    }
}
