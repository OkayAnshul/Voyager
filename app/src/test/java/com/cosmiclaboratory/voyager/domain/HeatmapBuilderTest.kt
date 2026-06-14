package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.HeatmapMetric
import com.cosmiclaboratory.voyager.domain.usecase.HeatmapBuilder
import com.cosmiclaboratory.voyager.storage.database.entity.DailyRollupEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HeatmapBuilderTest {

    private fun rollup(
        dayKey: String,
        distanceM: Double = 0.0,
        steps: Int = 0,
        visits: Int = 0,
    ) = DailyRollupEntity(
        dayKey = dayKey, totalDistanceM = distanceM, totalSteps = steps,
        placeVisitCount = visits, computedAt = 0L,
    )

    @Test
    fun `intensity is a 0-4 bucket relative to the max, zero for no value`() {
        assertThat(HeatmapBuilder.intensity(0.0, 100.0)).isEqualTo(0)
        assertThat(HeatmapBuilder.intensity(20.0, 100.0)).isEqualTo(1) // ≤25%
        assertThat(HeatmapBuilder.intensity(40.0, 100.0)).isEqualTo(2) // ≤50%
        assertThat(HeatmapBuilder.intensity(70.0, 100.0)).isEqualTo(3) // ≤75%
        assertThat(HeatmapBuilder.intensity(100.0, 100.0)).isEqualTo(4) // top
    }

    @Test
    fun `an empty or zero-max range never divides by zero`() {
        assertThat(HeatmapBuilder.intensity(5.0, 0.0)).isEqualTo(0)
        val empty = HeatmapBuilder.build(HeatmapMetric.DISTANCE, emptyList())
        assertThat(empty.days).isEmpty()
        assertThat(empty.maxValue).isEqualTo(0.0)
    }

    @Test
    fun `build sorts by day and scales intensity to the range max`() {
        val map = HeatmapBuilder.build(
            HeatmapMetric.DISTANCE,
            listOf(
                rollup("2026-01-03", distanceM = 10_000.0), // max → 4
                rollup("2026-01-01", distanceM = 0.0),       // none → 0
                rollup("2026-01-02", distanceM = 2_500.0),   // 25% → 1
            ),
        )
        assertThat(map.days.map { it.dayKey }).containsExactly("2026-01-01", "2026-01-02", "2026-01-03").inOrder()
        assertThat(map.days.map { it.intensity }).containsExactly(0, 1, 4).inOrder()
        assertThat(map.maxValue).isEqualTo(10_000.0)
    }

    @Test
    fun `each metric reads its own field`() {
        val r = rollup("2026-02-01", distanceM = 5_000.0, steps = 8_000, visits = 4)
        assertThat(HeatmapBuilder.metricValue(r, HeatmapMetric.DISTANCE)).isEqualTo(5_000.0)
        assertThat(HeatmapBuilder.metricValue(r, HeatmapMetric.STEPS)).isEqualTo(8_000.0)
        assertThat(HeatmapBuilder.metricValue(r, HeatmapMetric.PLACES)).isEqualTo(4.0)
    }

    @Test
    fun `year in review sums totals, counts active days, and finds standout days`() {
        val yir = HeatmapBuilder.yearInReview(
            2026,
            listOf(
                rollup("2026-01-01", distanceM = 1_000.0, steps = 2_000, visits = 2),
                rollup("2026-06-15", distanceM = 40_000.0, steps = 5_000, visits = 3), // longest distance
                rollup("2026-09-09", distanceM = 500.0, steps = 12_000, visits = 1),   // most steps
                rollup("2026-12-25"),                                                  // inactive day
            ),
        )
        assertThat(yir.year).isEqualTo(2026)
        assertThat(yir.activeDays).isEqualTo(3) // the empty Christmas day doesn't count
        assertThat(yir.totalDistanceM).isWithin(0.001).of(41_500.0)
        assertThat(yir.totalSteps).isEqualTo(19_000L)
        assertThat(yir.totalVisits).isEqualTo(6)
        assertThat(yir.longestDistanceDay?.dayKey).isEqualTo("2026-06-15")
        assertThat(yir.mostStepsDay?.dayKey).isEqualTo("2026-09-09")
    }
}
