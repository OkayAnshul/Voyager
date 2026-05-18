package com.cosmiclaboratory.voyager.domain.model

/** Metres per statute mile — the unit US/HMRC mileage deductions are claimed in. */
const val METERS_PER_MILE = 1609.344

/**
 * One classified drive in the mileage log: a [com.cosmiclaboratory.voyager.domain.model.enums.SegmentType.DRIVE]
 * movement segment plus the user's tax [purpose].
 */
data class MileageEntry(
    val segmentId: Long,
    val startAt: Long,
    val endAt: Long,
    val dayKey: String,
    val distanceMeters: Double,
    val purpose: MileagePurpose,
    val note: String?
) {
    val distanceMiles: Double get() = distanceMeters / METERS_PER_MILE
    val distanceKm: Double get() = distanceMeters / 1000.0
}

/**
 * The full mileage log for a date range — every drive plus per-purpose totals.
 * Entries are newest-first; totals are derived so they always agree with [entries].
 */
data class MileageLog(
    val entries: List<MileageEntry>,
    val rangeLabel: String
) {
    /** Total metres driven per purpose; purposes with no drives are omitted. */
    val metersByPurpose: Map<MileagePurpose, Double> =
        entries.groupBy { it.purpose }
            .mapValues { (_, list) -> list.sumOf { it.distanceMeters } }

    val totalMeters: Double get() = entries.sumOf { it.distanceMeters }
    val totalMiles: Double get() = totalMeters / METERS_PER_MILE

    /** Metres of tax-deductible driving (business/medical/charitable). */
    val deductibleMeters: Double
        get() = metersByPurpose.filterKeys { it.deductible }.values.sum()

    val unclassifiedCount: Int
        get() = entries.count { it.purpose == MileagePurpose.UNCLASSIFIED }

    fun milesFor(purpose: MileagePurpose): Double =
        (metersByPurpose[purpose] ?: 0.0) / METERS_PER_MILE
}
