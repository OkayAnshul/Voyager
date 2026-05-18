package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.CarbonFootprint
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.ModeFootprint
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import javax.inject.Inject

/**
 * Estimates the CO2e footprint of the user's travel over a date range by applying a
 * per-transport-mode emission factor to the distance already recorded in movement
 * segments. No new tracking — purely a different lens on existing data.
 */
class BuildCarbonFootprintUseCase @Inject constructor(
    private val movementSegmentDao: MovementSegmentDao
) {

    companion object {
        /**
         * Emission factors in grams CO2e per kilometre.
         *
         * Active modes are zero. DRIVE is an average petrol car per vehicle-km;
         * TRANSIT is a bus/rail blend and FLIGHT a short-haul figure, both per
         * passenger-km. Deliberately conservative, round public-source averages —
         * the screen frames the result as an estimate, not an audit.
         */
        val EMISSION_FACTORS_G_PER_KM: Map<SegmentType, Double> = mapOf(
            SegmentType.WALK to 0.0,
            SegmentType.RUN to 0.0,
            SegmentType.CYCLE to 0.0,
            SegmentType.TRANSIT to 41.0,
            SegmentType.DRIVE to 170.0,
            SegmentType.FLIGHT to 255.0
        )
    }

    suspend fun build(range: DateRange, rangeLabel: String): CarbonFootprint {
        val segments = movementSegmentDao.getByTypesBetween(
            types = EMISSION_FACTORS_G_PER_KM.keys.map { it.name },
            startDay = range.startDay,
            endDay = range.endDay
        )
        val metresByMode = segments
            .groupBy { it.segmentType }
            .mapValues { (_, segs) -> segs.sumOf { it.distanceM } }

        val modes = EMISSION_FACTORS_G_PER_KM.entries.mapNotNull { (mode, factor) ->
            val distanceKm = (metresByMode[mode.name] ?: 0.0) / 1000.0
            if (distanceKm <= 0.0) {
                null
            } else {
                ModeFootprint(
                    mode = mode,
                    distanceKm = distanceKm,
                    kgCo2 = distanceKm * factor / 1000.0,
                    gramsPerKm = factor
                )
            }
        }.sortedByDescending { it.kgCo2 }

        return CarbonFootprint(
            rangeLabel = rangeLabel,
            modes = modes,
            totalKgCo2 = modes.sumOf { it.kgCo2 },
            totalDistanceKm = modes.sumOf { it.distanceKm }
        )
    }
}
