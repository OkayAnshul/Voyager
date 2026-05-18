package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType

/**
 * Estimated CO2e footprint of the user's travel over a period, broken down by
 * transport mode. Derived on demand from movement segments — see
 * [com.cosmiclaboratory.voyager.domain.usecase.BuildCarbonFootprintUseCase].
 */
data class CarbonFootprint(
    val rangeLabel: String,
    /** Per-mode footprints, highest CO2 first. Modes with no travel are omitted. */
    val modes: List<ModeFootprint>,
    val totalKgCo2: Double,
    val totalDistanceKm: Double
) {
    val isEmpty: Boolean get() = modes.isEmpty()

    /**
     * Mature trees that would need a full year to absorb [totalKgCo2]
     * (~21 kg CO2 per tree-year) — a tangible way to read the number.
     */
    val treeYearEquivalent: Double get() = totalKgCo2 / 21.0
}

/** One transport mode's contribution to the carbon footprint. */
data class ModeFootprint(
    val mode: SegmentType,
    val distanceKm: Double,
    val kgCo2: Double,
    val gramsPerKm: Double
) {
    /** This mode's 0..1 share of [totalKg], for the breakdown bar. */
    fun shareOf(totalKg: Double): Float =
        if (totalKg > 0.0) (kgCo2 / totalKg).toFloat() else 0f
}
