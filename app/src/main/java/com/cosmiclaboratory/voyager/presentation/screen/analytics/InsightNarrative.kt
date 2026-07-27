package com.cosmiclaboratory.voyager.presentation.screen.analytics

/**
 * How a run of text inside a narrative sentence should be rendered. Keeping this
 * as data (not Compose spans) lets the copy be built and unit-tested off the main
 * thread, then styled once by [StoryHero].
 */
enum class NarrativeEmphasis {
    /** Normal prose — Inter. */
    PLAIN,

    /** A supporting number/data value — JetBrains Mono, ink colour. */
    NUMBER,

    /** The single figure that matters — JetBrains Mono in the chapter accent colour. */
    ACCENT
}

/** One styled run of a narrative headline. */
data class NarrativeSegment(
    val text: String,
    val emphasis: NarrativeEmphasis = NarrativeEmphasis.PLAIN
)

/**
 * Emotional colour of an insight. Drives verb/tone choices only — it is never a
 * claim and never the sole carrier of meaning (direction always lives in a word).
 */
enum class NarrativeTone { CELEBRATE, NEUTRAL, GENTLE, CURIOUS }

/**
 * A ready-to-render insight sentence, produced by [InsightNarrators] from
 * already-computed models. Lifting narration out of composables into pure data
 * keeps all nine lenses speaking in one voice and makes the copy testable.
 *
 * Honesty rules (carried over from the original SynthesisHero): direction is
 * always carried by a *word* ("farther"/"less"), never by colour alone; the
 * period label flows in from the caller and is never hardcoded.
 */
data class InsightNarrative(
    val eyebrow: String,
    val segments: List<NarrativeSegment>,
    val body: String? = null,
    val tone: NarrativeTone = NarrativeTone.NEUTRAL
) {
    /** Plain-text form — used for accessibility labels and tests. */
    val plainText: String get() = segments.joinToString("") { it.text }
}
