package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.DistanceUnit
import com.cosmiclaboratory.voyager.domain.model.METERS_PER_MILE
import com.cosmiclaboratory.voyager.domain.model.MileageEntry
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.model.MileageRateConfig
import com.cosmiclaboratory.voyager.domain.model.MileageRatePreset
import com.cosmiclaboratory.voyager.domain.usecase.MileageDeduction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MileageDeductionTest {

    private var nextId = 1L

    /** A drive of [meters], classified [purpose]. */
    private fun entryMeters(meters: Double, purpose: MileagePurpose) = MileageEntry(
        segmentId = nextId++,
        startAt = 0L,
        endAt = 0L,
        dayKey = "2026-05-01",
        distanceMeters = meters,
        purpose = purpose,
        note = null
    )

    /** A drive of [miles], classified [purpose]. */
    private fun entry(miles: Double, purpose: MileagePurpose) =
        entryMeters(miles * METERS_PER_MILE, purpose)

    private fun log(vararg entries: MileageEntry) = MileageLog(entries.toList(), rangeLabel = "test")

    /** IRS 2025, USD, miles — the default config used by most cases. */
    private val irsMiles = MileageRateConfig()

    @Test
    fun `business mileage uses the IRS business rate`() {
        val estimate = MileageDeduction.estimate(log(entry(100.0, MileagePurpose.BUSINESS)), irsMiles)

        assertThat(estimate.lines).hasSize(1)
        val line = estimate.lines.single()
        assertThat(line.purpose).isEqualTo(MileagePurpose.BUSINESS)
        assertThat(line.distance).isWithin(0.001).of(100.0)
        assertThat(line.rate).isEqualTo(0.70)
        assertThat(line.amount).isWithin(0.001).of(70.0)
        assertThat(estimate.totalAmount).isWithin(0.001).of(70.0)
        assertThat(estimate.currencyCode).isEqualTo("USD")
        assertThat(estimate.distanceUnit).isEqualTo(DistanceUnit.MILE)
    }

    @Test
    fun `each deductible purpose uses its own rate and they sum`() {
        val estimate = MileageDeduction.estimate(
            log(
                entry(100.0, MileagePurpose.BUSINESS),    // 100 × 0.70 = 70.00
                entry(50.0, MileagePurpose.MEDICAL),      //  50 × 0.21 = 10.50
                entry(200.0, MileagePurpose.CHARITABLE)   // 200 × 0.14 = 28.00
            ),
            irsMiles
        )

        assertThat(estimate.totalAmount).isWithin(0.001).of(108.50)
        assertThat(estimate.totalDistance).isWithin(0.001).of(350.0)
    }

    @Test
    fun `personal and unclassified miles produce a line with no rate and no amount`() {
        val estimate = MileageDeduction.estimate(
            log(
                entry(40.0, MileagePurpose.PERSONAL),
                entry(10.0, MileagePurpose.UNCLASSIFIED)
            ),
            irsMiles
        )

        assertThat(estimate.lines.map { it.purpose })
            .containsExactly(MileagePurpose.PERSONAL, MileagePurpose.UNCLASSIFIED)
        assertThat(estimate.lines.all { it.rate == null && it.amount == 0.0 }).isTrue()
        assertThat(estimate.totalAmount).isEqualTo(0.0)
        // Non-deductible miles still count toward total distance driven.
        assertThat(estimate.totalDistance).isWithin(0.001).of(50.0)
    }

    @Test
    fun `only purposes with driving appear, in declaration order`() {
        val estimate = MileageDeduction.estimate(
            log(
                entry(5.0, MileagePurpose.CHARITABLE),
                entry(5.0, MileagePurpose.BUSINESS)
            ),
            irsMiles
        )

        // BUSINESS is declared before CHARITABLE in the enum — output follows declaration order,
        // not insertion order, and PERSONAL/MEDICAL/UNCLASSIFIED (no driving) are omitted.
        assertThat(estimate.lines.map { it.purpose })
            .containsExactly(MileagePurpose.BUSINESS, MileagePurpose.CHARITABLE)
            .inOrder()
    }

    @Test
    fun `an empty log has no lines and no amount`() {
        val estimate = MileageDeduction.estimate(log(), irsMiles)

        assertThat(estimate.lines).isEmpty()
        assertThat(estimate.totalDistance).isEqualTo(0.0)
        assertThat(estimate.totalAmount).isEqualTo(0.0)
    }

    @Test
    fun `custom rates and currency drive the estimate`() {
        val config = MileageRateConfig(
            distanceUnit = DistanceUnit.MILE,
            currencyCode = "GBP",
            ratePreset = MileageRatePreset.CUSTOM,
            customRates = mapOf(MileagePurpose.BUSINESS to 0.45) // e.g. HMRC-style rate
        )
        val estimate = MileageDeduction.estimate(log(entry(100.0, MileagePurpose.BUSINESS)), config)

        assertThat(estimate.totalAmount).isWithin(0.001).of(45.0)
        assertThat(estimate.currencyCode).isEqualTo("GBP")
    }

    @Test
    fun `kilometre config measures distance and money in km`() {
        val config = MileageRateConfig(
            distanceUnit = DistanceUnit.KM,
            currencyCode = "INR",
            ratePreset = MileageRatePreset.CUSTOM,
            customRates = mapOf(MileagePurpose.BUSINESS to 12.0) // ₹12 / km
        )
        val estimate = MileageDeduction.estimate(
            log(entryMeters(10_000.0, MileagePurpose.BUSINESS)), // 10 km
            config
        )

        val line = estimate.lines.single()
        assertThat(line.distance).isWithin(0.001).of(10.0)
        assertThat(line.amount).isWithin(0.001).of(120.0)
        assertThat(estimate.distanceUnit).isEqualTo(DistanceUnit.KM)
        assertThat(estimate.currencyCode).isEqualTo("INR")
    }

    @Test
    fun `a preset's money is invariant to the display unit`() {
        // Same physical distance, same IRS preset — the deduction is identical whether the user
        // views miles or km, because the per-mile rate is unit-converted, not reinterpreted.
        val drive = log(entryMeters(100.0 * METERS_PER_MILE, MileagePurpose.BUSINESS))
        val inMiles = MileageDeduction.estimate(drive, MileageRateConfig(DistanceUnit.MILE, "USD", MileageRatePreset.IRS_2025))
        val inKm = MileageDeduction.estimate(drive, MileageRateConfig(DistanceUnit.KM, "USD", MileageRatePreset.IRS_2025))

        assertThat(inMiles.totalAmount).isWithin(0.001).of(70.0)
        assertThat(inKm.totalAmount).isWithin(0.001).of(inMiles.totalAmount)
    }
}
