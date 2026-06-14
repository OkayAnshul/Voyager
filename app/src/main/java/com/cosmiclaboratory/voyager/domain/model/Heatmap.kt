package com.cosmiclaboratory.voyager.domain.model

/** Which daily metric a heatmap colours by. */
enum class HeatmapMetric { DISTANCE, STEPS, PLACES, ACTIVE_TIME }

/** One day cell: its raw [value] for the chosen metric and a 0–4 [intensity] bucket (0 = no data). */
data class HeatmapDay(val dayKey: String, val value: Double, val intensity: Int)

/** A calendar heatmap over a date range — the shareable "your year in motion" surface. */
data class Heatmap(
    val metric: HeatmapMetric,
    val days: List<HeatmapDay>,
    val maxValue: Double,
)

/** Honest year-in-review totals (only summable/derivable metrics — no fake distinct counts). */
data class YearInReview(
    val year: Int,
    val activeDays: Int,
    val totalDistanceM: Double,
    val totalSteps: Long,
    val totalVisits: Int,
    val longestDistanceDay: HeatmapDay?,
    val mostStepsDay: HeatmapDay?,
)
