package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Heatmap
import com.cosmiclaboratory.voyager.domain.model.HeatmapDay
import com.cosmiclaboratory.voyager.domain.model.HeatmapMetric
import com.cosmiclaboratory.voyager.domain.model.YearInReview
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity

/**
 * Pure builder for the heatmap / Year-in-Review surfaces from daily rollups. No DAO/Android, so
 * the intensity bucketing and the annual aggregation are unit-tested directly;
 * [com.cosmiclaboratory.voyager.domain.usecase.BuildHeatmapUseCase] just supplies the rows.
 */
object HeatmapBuilder {

    /** The day's value for [metric]. */
    fun metricValue(rollup: DailyRollupEntity, metric: HeatmapMetric): Double = when (metric) {
        HeatmapMetric.DISTANCE -> rollup.totalDistanceM
        HeatmapMetric.STEPS -> rollup.totalSteps.toDouble()
        HeatmapMetric.PLACES -> rollup.placeVisitCount.toDouble()
        HeatmapMetric.ACTIVE_TIME -> (rollup.totalWalkMs + rollup.totalDriveMs + rollup.totalTransitMs).toDouble()
    }

    /**
     * GitHub-style 0–4 intensity bucket: 0 for no value, then quartiles of [maxValue].
     * A non-positive [maxValue] degrades to 0 so an empty range never divides by zero.
     */
    fun intensity(value: Double, maxValue: Double): Int {
        if (value <= 0.0 || maxValue <= 0.0) return 0
        return when (value / maxValue) {
            in 0.0..0.25 -> 1
            in 0.25..0.50 -> 2
            in 0.50..0.75 -> 3
            else -> 4
        }
    }

    /** Builds a heatmap for [metric] over [rollups] (any order; result is dayKey-sorted). */
    fun build(metric: HeatmapMetric, rollups: List<DailyRollupEntity>): Heatmap {
        val values = rollups.map { it to metricValue(it, metric) }
        val max = values.maxOfOrNull { it.second } ?: 0.0
        val days = values
            .sortedBy { it.first.dayKey }
            .map { (rollup, value) -> HeatmapDay(rollup.dayKey, value, intensity(value, max)) }
        return Heatmap(metric = metric, days = days, maxValue = max)
    }

    /** Aggregates a year of rollups into honest totals + standout days. */
    fun yearInReview(year: Int, rollups: List<DailyRollupEntity>): YearInReview {
        val active = rollups.filter {
            it.totalDistanceM > 0 || it.totalSteps > 0 || it.placeVisitCount > 0
        }
        val longest = active.maxByOrNull { it.totalDistanceM }
            ?.let { HeatmapDay(it.dayKey, it.totalDistanceM, 4) }
        val mostSteps = active.maxByOrNull { it.totalSteps }
            ?.let { HeatmapDay(it.dayKey, it.totalSteps.toDouble(), 4) }
        return YearInReview(
            year = year,
            activeDays = active.size,
            totalDistanceM = rollups.sumOf { it.totalDistanceM },
            totalSteps = rollups.sumOf { it.totalSteps.toLong() },
            totalVisits = rollups.sumOf { it.placeVisitCount },
            longestDistanceDay = longest,
            mostStepsDay = mostSteps,
        )
    }
}
