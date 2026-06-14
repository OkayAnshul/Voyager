package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Heatmap
import com.cosmiclaboratory.voyager.domain.model.HeatmapMetric
import com.cosmiclaboratory.voyager.domain.model.YearInReview
import com.cosmiclaboratory.voyager.storage.database.dao.DailyRollupDao
import javax.inject.Inject

/**
 * Supplies the heatmap / Year-in-Review surfaces (A6) from pre-aggregated daily rollups.
 * Thin DAO wrapper; all computation lives in the pure [HeatmapBuilder].
 */
class BuildHeatmapUseCase @Inject constructor(
    private val dailyRollupDao: DailyRollupDao,
) {

    /** Heatmap for [metric] over the inclusive [startDay]..[endDay] (`YYYY-MM-DD`). */
    suspend fun heatmap(metric: HeatmapMetric, startDay: String, endDay: String): Heatmap =
        HeatmapBuilder.build(metric, dailyRollupDao.getByRange(startDay, endDay))

    /** Year-in-Review for [year], reading that calendar year's rollups. */
    suspend fun yearInReview(year: Int): YearInReview =
        HeatmapBuilder.yearInReview(year, dailyRollupDao.getByRange("$year-01-01", "$year-12-31"))
}
