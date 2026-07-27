package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.DistanceUnit
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.model.MileageRateConfig

/**
 * Pure tax-deduction / reimbursement estimate for a [MileageLog] — the financial output a filer
 * relies on, so it lives here (testable in isolation) rather than entangled in the PDF renderer
 * that draws it.
 *
 * Rates, currency, and distance unit all come from the user's [MileageRateConfig]: deductible
 * purposes (business/medical/charitable) get `rate × distance`; personal/unclassified driving has
 * no rate and contributes nothing. The renderer labels the figure an estimate the filer must
 * verify — rates are jurisdiction- and year-specific.
 */
object MileageDeduction {

    /** One row of the deduction summary. [rate] is null for non-deductible/unset purposes. */
    data class PurposeDeduction(
        val purpose: MileagePurpose,
        val distance: Double,
        val rate: Double?,
        val amount: Double,
    )

    /** Full estimate: one line per purpose that has driving, plus the totals and the units used. */
    data class Estimate(
        val lines: List<PurposeDeduction>,
        val totalDistance: Double,
        val totalAmount: Double,
        val distanceUnit: DistanceUnit,
        val currencyCode: String,
    )

    /**
     * Per-purpose deduction estimate for [log] under [config]. Only purposes with actual driving
     * (> 0 distance) produce a line, in [MileagePurpose] declaration order; deductible ones get
     * `rate × distance` (distance measured in [MileageRateConfig.distanceUnit]), the rest a null
     * rate and 0. The total sums only the line amounts.
     */
    fun estimate(log: MileageLog, config: MileageRateConfig): Estimate {
        val unit = config.distanceUnit
        val lines = MileagePurpose.entries.mapNotNull { purpose ->
            val distance = log.distanceFor(purpose, unit)
            if (distance <= 0.0) return@mapNotNull null
            val rate = config.rateFor(purpose)
            PurposeDeduction(
                purpose = purpose,
                distance = distance,
                rate = rate,
                amount = rate?.let { it * distance } ?: 0.0,
            )
        }
        return Estimate(
            lines = lines,
            totalDistance = log.totalDistance(unit),
            totalAmount = lines.sumOf { it.amount },
            distanceUnit = unit,
            currencyCode = config.currencyCode,
        )
    }
}
