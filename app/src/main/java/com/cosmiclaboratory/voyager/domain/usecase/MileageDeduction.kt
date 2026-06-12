package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose

/**
 * Pure tax-deduction estimate for a [MileageLog] — the financial output a filer relies on, so
 * it lives here (testable in isolation) rather than entangled in the PDF renderer that draws it.
 *
 * Uses standard *per-mile* rates: deductible purposes (business/medical/charitable) get
 * `rate × miles`; personal/unclassified driving has no rate and contributes $0. Rates are
 * jurisdiction- and year-specific — the default is IRS 2025 and the renderer labels the figure
 * an estimate the filer must verify.
 */
object MileageDeduction {

    /** IRS 2025 standard mileage rates, USD per mile. Purposes absent here are non-deductible. */
    val IRS_2025_RATES: Map<MileagePurpose, Double> = mapOf(
        MileagePurpose.BUSINESS to 0.70,
        MileagePurpose.MEDICAL to 0.21,
        MileagePurpose.CHARITABLE to 0.14,
    )

    /** One row of the deduction summary. [rate] is null for non-deductible purposes. */
    data class PurposeDeduction(
        val purpose: MileagePurpose,
        val miles: Double,
        val rate: Double?,
        val deductionUsd: Double,
    )

    /** Full estimate: one line per purpose that has driving, plus the totals. */
    data class Estimate(
        val lines: List<PurposeDeduction>,
        val totalMiles: Double,
        val totalDeductionUsd: Double,
    )

    /**
     * Per-purpose deduction estimate for [log] under [rates]. Only purposes with actual driving
     * (> 0 miles) produce a line, in [MileagePurpose] declaration order; deductible ones get
     * `rate × miles`, the rest a null rate and $0. The total sums only the line deductions.
     */
    fun estimate(log: MileageLog, rates: Map<MileagePurpose, Double> = IRS_2025_RATES): Estimate {
        val lines = MileagePurpose.entries.mapNotNull { purpose ->
            val miles = log.milesFor(purpose)
            if (miles <= 0.0) return@mapNotNull null
            val rate = rates[purpose]
            PurposeDeduction(
                purpose = purpose,
                miles = miles,
                rate = rate,
                deductionUsd = rate?.let { it * miles } ?: 0.0,
            )
        }
        return Estimate(
            lines = lines,
            totalMiles = log.totalMiles,
            totalDeductionUsd = lines.sumOf { it.deductionUsd },
        )
    }
}
