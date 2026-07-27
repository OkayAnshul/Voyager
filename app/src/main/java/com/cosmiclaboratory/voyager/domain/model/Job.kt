package com.cosmiclaboratory.voyager.domain.model

/**
 * The primary job a user hires Voyager to do, chosen during onboarding.
 *
 * A persona = a [Job] + a starting tracking preset ([SettingsPresets]). Onboarding
 * asks one question — the [Job] — and maps it to a sensible starting preset behind
 * the scenes (see PersonaPickViewModel). The job drives dashboard emphasis; the
 * mapped preset configures the capture pipeline (both stay tunable in Settings).
 *
 * @param forWho a "this is you if…" line so a user can self-identify at a glance.
 * @param whatYouSee one line describing what the choice surfaces first in the app.
 */
enum class Job(
    val id: String,
    val displayName: String,
    val tagline: String,
    val forWho: String,
    val whatYouSee: String
) {
    MEMORY(
        id = "MEMORY",
        displayName = "Memory",
        tagline = "A private timeline of where life happened",
        forWho = "You want a private diary of your places and days.",
        whatYouSee = "Timeline & Day Story up front"
    ),
    PROOF(
        id = "PROOF",
        displayName = "Proof",
        tagline = "Evidence-grade logs — mileage, visits, history",
        forWho = "You need evidence: mileage and a record of where you were.",
        whatYouSee = "Mileage & exportable proof up front"
    ),
    HABITS(
        id = "HABITS",
        displayName = "Habits",
        tagline = "Patterns, routines and how you move",
        forWho = "You want to understand your routines and how you move.",
        whatYouSee = "Insights & patterns up front"
    );

    companion object {
        fun fromId(id: String): Job? = entries.firstOrNull { it.id == id }
    }
}
