package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.METERS_PER_MILE
import com.cosmiclaboratory.voyager.domain.model.MileageEntry
import com.cosmiclaboratory.voyager.domain.model.MileageLog
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.usecase.MileageDeduction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MileageDeductionTest {

    private var nextId = 1L

    /** A drive of [miles] classified [purpose]. */
    private fun entry(miles: Double, purpose: MileagePurpose) = MileageEntry(
        segmentId = nextId++,
        startAt = 0L,
        endAt = 0L,
        dayKey = "2026-05-01",
        distanceMeters = miles * METERS_PER_MILE,
        purpose = purpose,
        note = null
    )

    private fun log(vararg entries: MileageEntry) = MileageLog(entries.toList(), rangeLabel = "test")

    @Test
    fun `business mileage uses the IRS business rate`() {
        val estimate = MileageDeduction.estimate(log(entry(100.0, MileagePurpose.BUSINESS)))

        assertThat(estimate.lines).hasSize(1)
        val line = estimate.lines.single()
        assertThat(line.purpose).isEqualTo(MileagePurpose.BUSINESS)
        assertThat(line.miles).isWithin(0.001).of(100.0)
        assertThat(line.rate).isEqualTo(0.70)
        assertThat(line.deductionUsd).isWithin(0.001).of(70.0)
        assertThat(estimate.totalDeductionUsd).isWithin(0.001).of(70.0)
    }

    @Test
    fun `each deductible purpose uses its own rate and they sum`() {
        val estimate = MileageDeduction.estimate(
            log(
                entry(100.0, MileagePurpose.BUSINESS),    // 100 × 0.70 = 70.00
                entry(50.0, MileagePurpose.MEDICAL),      //  50 × 0.21 = 10.50
                entry(200.0, MileagePurpose.CHARITABLE)   // 200 × 0.14 = 28.00
            )
        )

        assertThat(estimate.totalDeductionUsd).isWithin(0.001).of(108.50)
        assertThat(estimate.totalMiles).isWithin(0.001).of(350.0)
    }

    @Test
    fun `personal and unclassified miles produce a line with no rate and no deduction`() {
        val estimate = MileageDeduction.estimate(
            log(
                entry(40.0, MileagePurpose.PERSONAL),
                entry(10.0, MileagePurpose.UNCLASSIFIED)
            )
        )

        assertThat(estimate.lines.map { it.purpose })
            .containsExactly(MileagePurpose.PERSONAL, MileagePurpose.UNCLASSIFIED)
        assertThat(estimate.lines.all { it.rate == null && it.deductionUsd == 0.0 }).isTrue()
        assertThat(estimate.totalDeductionUsd).isEqualTo(0.0)
        // Non-deductible miles still count toward total miles driven.
        assertThat(estimate.totalMiles).isWithin(0.001).of(50.0)
    }

    @Test
    fun `only purposes with driving appear, in declaration order`() {
        val estimate = MileageDeduction.estimate(
            log(
                entry(5.0, MileagePurpose.CHARITABLE),
                entry(5.0, MileagePurpose.BUSINESS)
            )
        )

        // BUSINESS is declared before CHARITABLE in the enum — output follows declaration order,
        // not insertion order, and PERSONAL/MEDICAL/UNCLASSIFIED (no driving) are omitted.
        assertThat(estimate.lines.map { it.purpose })
            .containsExactly(MileagePurpose.BUSINESS, MileagePurpose.CHARITABLE)
            .inOrder()
    }

    @Test
    fun `an empty log has no lines and no deduction`() {
        val estimate = MileageDeduction.estimate(log())

        assertThat(estimate.lines).isEmpty()
        assertThat(estimate.totalMiles).isEqualTo(0.0)
        assertThat(estimate.totalDeductionUsd).isEqualTo(0.0)
    }

    @Test
    fun `custom rates override the IRS defaults`() {
        val estimate = MileageDeduction.estimate(
            log(entry(100.0, MileagePurpose.BUSINESS)),
            rates = mapOf(MileagePurpose.BUSINESS to 0.45) // e.g. HMRC-style override
        )

        assertThat(estimate.totalDeductionUsd).isWithin(0.001).of(45.0)
    }
}
