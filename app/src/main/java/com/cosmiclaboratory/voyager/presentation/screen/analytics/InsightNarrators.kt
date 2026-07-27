package com.cosmiclaboratory.voyager.presentation.screen.analytics

import com.cosmiclaboratory.voyager.domain.model.CarbonFootprint
import com.cosmiclaboratory.voyager.domain.usecase.SleepRhythm
import com.cosmiclaboratory.voyager.domain.usecase.TimeBudget
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure builders that turn computed insight models into [InsightNarrative]s — one
 * per lens. No Android/Compose dependencies, so each is unit-tested directly (see
 * InsightNarratorsTest). This is the single place the storybook's voice lives.
 */
object InsightNarrators {

    private fun plain(text: String) = NarrativeSegment(text, NarrativeEmphasis.PLAIN)
    private fun number(text: String) = NarrativeSegment(text, NarrativeEmphasis.NUMBER)
    private fun accent(text: String) = NarrativeSegment(text, NarrativeEmphasis.ACCENT)

    /**
     * Overview lead. Mirrors the honest SynthesisHero it replaces: a percentage
     * paired with a direction *word*, the period from [periodLabel] (never
     * hardcoded), and an optional "across N places" tail.
     */
    fun overview(
        weekly: WeeklyComparisonData?,
        movement: MovementStats?,
        placeCount: Int,
        periodLabel: String
    ): InsightNarrative {
        val eyebrow = "Overview · ${periodLabel.lowercase()}"

        if (weekly == null) {
            return InsightNarrative(
                eyebrow = eyebrow,
                segments = listOf(
                    plain("Keep moving — a few days of data unlock your trends, patterns and anomalies.")
                ),
                tone = NarrativeTone.GENTLE
            )
        }

        val pct = weekly.distanceChange
        val pctRounded = abs(pct).roundToInt()
        val directionWord = if (pct >= 0) "farther" else "less"
        val tone = if (pct >= 0) NarrativeTone.CELEBRATE else NarrativeTone.GENTLE

        val segments = buildList {
            add(plain("You wandered "))
            add(accent("$pctRounded% $directionWord"))
            add(plain(" ${periodLabel.lowercase()}"))
            if (placeCount > 0) {
                add(plain(" — across "))
                add(number("$placeCount"))
                add(plain(" place${if (placeCount == 1) "" else "s"}."))
            } else {
                add(plain("."))
            }
        }
        return InsightNarrative(eyebrow, segments, tone = tone)
    }

    /**
     * Weekly comparison lead. Leads on the place count when it moved (the most
     * legible change), else falls back to distance. Direction is always a word.
     */
    fun weekly(weekly: WeeklyComparisonData?, periodLabel: String): InsightNarrative {
        val eyebrow = "Weekly · vs previous"
        if (weekly == null) {
            return InsightNarrative(
                eyebrow = eyebrow,
                segments = listOf(plain("Keep tracking to see how this ${periodLabel.lowercase()} compares.")),
                tone = NarrativeTone.GENTLE
            )
        }
        val placeDelta = weekly.placesThisWeek - weekly.placesLastWeek
        return when {
            placeDelta > 0 -> InsightNarrative(
                eyebrow,
                listOf(
                    plain("Busier than before — "),
                    accent("$placeDelta more"),
                    plain(" place${if (placeDelta == 1) "" else "s"} this ${periodLabel.lowercase()}.")
                ),
                tone = NarrativeTone.CELEBRATE
            )
            placeDelta < 0 -> InsightNarrative(
                eyebrow,
                listOf(
                    plain("A quieter stretch — "),
                    accent("${-placeDelta} fewer"),
                    plain(" place${if (-placeDelta == 1) "" else "s"} than before.")
                ),
                tone = NarrativeTone.GENTLE
            )
            else -> {
                val d = weekly.distanceChange
                val word = if (d >= 0) "farther" else "less"
                InsightNarrative(
                    eyebrow,
                    listOf(
                        plain("About the same rhythm — you travelled "),
                        accent("${abs(d).roundToInt()}% $word"),
                        plain(" than before.")
                    ),
                    tone = NarrativeTone.NEUTRAL
                )
            }
        }
    }

    /** Highlights lead — an invitation into the keepsakes below. */
    fun highlights(highlightCount: Int, memoryCount: Int): InsightNarrative {
        val hasAny = highlightCount + memoryCount > 0
        return InsightNarrative(
            eyebrow = "Highlights",
            segments = listOf(
                plain(
                    if (hasAny) "Some moments worth keeping."
                    else "Records, firsts and past-year memories will collect here as you build history."
                )
            ),
            tone = if (hasAny) NarrativeTone.CELEBRATE else NarrativeTone.GENTLE
        )
    }

    /** Routines lead — an invitation into the habits below. */
    fun routines(hasRoutines: Boolean, hasUpcoming: Boolean): InsightNarrative = when {
        hasRoutines -> InsightNarrative(
            "Routines",
            listOf(plain("A few places have quietly become part of your rhythm.")),
            tone = NarrativeTone.CELEBRATE
        )
        hasUpcoming -> InsightNarrative(
            "Routines",
            listOf(plain("Here's what your day usually holds.")),
            tone = NarrativeTone.NEUTRAL
        )
        else -> InsightNarrative(
            "Routines",
            listOf(plain("Your routines surface here as patterns repeat.")),
            tone = NarrativeTone.GENTLE
        )
    }

    /** Rhythm lead — home-overnight consistency, framed honestly (a proxy). */
    fun rhythm(sleep: SleepRhythm?): InsightNarrative = when {
        sleep != null && sleep.consistency == SleepRhythm.Consistency.CONSISTENT -> InsightNarrative(
            "Rhythm",
            listOf(plain("Your days keep a "), accent("steady beat"), plain(".")),
            tone = NarrativeTone.CELEBRATE
        )
        sleep != null -> InsightNarrative(
            "Rhythm",
            listOf(plain("Your rhythm is still finding its shape.")),
            tone = NarrativeTone.NEUTRAL
        )
        else -> InsightNarrative(
            "Rhythm",
            listOf(plain("A few weeks of home overnights reveal your rhythm.")),
            tone = NarrativeTone.GENTLE
        )
    }

    /** Movement lead — distance covered over the period. */
    fun movement(movement: MovementStats?, periodLabel: String): InsightNarrative {
        val eyebrow = "Movement · ${periodLabel.lowercase()}"
        return if (movement != null && movement.totalDistanceKm > 0) {
            InsightNarrative(
                eyebrow,
                listOf(
                    plain("You covered "),
                    accent("%.1f km".format(movement.totalDistanceKm)),
                    plain(" this ${periodLabel.lowercase()}.")
                ),
                tone = NarrativeTone.CELEBRATE
            )
        } else {
            InsightNarrative(eyebrow, listOf(plain("Your movement story appears as you travel.")), tone = NarrativeTone.GENTLE)
        }
    }

    /** Balance lead — where most of the period's time was spent. */
    fun balance(budget: TimeBudget?, periodLabel: String): InsightNarrative {
        val eyebrow = "Balance · ${periodLabel.lowercase()}"
        if (budget == null || budget.isEmpty) {
            return InsightNarrative(eyebrow, listOf(plain("Your time splits into view as you track more.")), tone = NarrativeTone.GENTLE)
        }
        val total = budget.totalMs.coerceAtLeast(1L)
        val entries = listOf(
            "home" to budget.homeMs,
            "work" to budget.workMs,
            "the world outside" to budget.outMs,
            "moving" to budget.movingMs
        )
        val top = entries.maxByOrNull { it.second } ?: ("home" to 0L)
        val pct = ((top.second * 100) / total).toInt()
        return if (top.first == "home") {
            InsightNarrative(
                eyebrow,
                listOf(plain("Most of your time was spent where you recharge — "), accent("home"), plain(", $pct% of it.")),
                tone = NarrativeTone.NEUTRAL
            )
        } else {
            InsightNarrative(
                eyebrow,
                listOf(plain("Most of your time went to "), accent(top.first), plain(" — $pct%.")),
                tone = NarrativeTone.NEUTRAL
            )
        }
    }

    /** Carbon lead — celebrates zero-emission travel; never guilts. */
    fun carbon(footprint: CarbonFootprint?): InsightNarrative {
        val eyebrow = "Carbon"
        if (footprint == null || footprint.isEmpty) {
            return InsightNarrative(eyebrow, listOf(plain("Your travel footprint appears once you've recorded trips.")), tone = NarrativeTone.GENTLE)
        }
        val greenKm = footprint.modes.filter { it.kgCo2 <= 0.0 }.sumOf { it.distanceKm }
        val pct = if (footprint.totalDistanceKm > 0) (greenKm / footprint.totalDistanceKm * 100).roundToInt() else 0
        return if (pct >= 40) {
            InsightNarrative(
                eyebrow,
                listOf(accent("$pct%"), plain(" of your travel was on foot or by bike — nicely done.")),
                tone = NarrativeTone.CELEBRATE
            )
        } else {
            InsightNarrative(
                eyebrow,
                listOf(plain("You travelled "), accent("%.1f kg".format(footprint.totalKgCo2)), plain(" of CO₂ — a guide, not an audit.")),
                tone = NarrativeTone.NEUTRAL
            )
        }
    }

    /** Anomalies lead — framed as curiosity, never a warning. */
    fun anomalies(anomalyCount: Int, breakCount: Int, periodLabel: String): InsightNarrative {
        val eyebrow = "Anomalies · ${periodLabel.lowercase()}"
        return when {
            anomalyCount > 0 -> InsightNarrative(
                eyebrow,
                listOf(
                    plain("You had "),
                    accent("$anomalyCount"),
                    plain(" unusual moment${if (anomalyCount == 1) "" else "s"} this ${periodLabel.lowercase()} — worth a look.")
                ),
                tone = NarrativeTone.CURIOUS
            )
            breakCount > 0 -> InsightNarrative(
                eyebrow,
                listOf(plain("A couple of routines went differently than usual.")),
                tone = NarrativeTone.CURIOUS
            )
            else -> InsightNarrative(
                eyebrow,
                listOf(plain("Nothing unusual — a steady ${periodLabel.lowercase()}.")),
                tone = NarrativeTone.NEUTRAL
            )
        }
    }
}
