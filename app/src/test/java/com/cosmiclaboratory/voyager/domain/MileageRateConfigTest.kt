package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.DistanceUnit
import com.cosmiclaboratory.voyager.domain.model.MileagePurpose
import com.cosmiclaboratory.voyager.domain.model.MileageRateConfig
import com.cosmiclaboratory.voyager.domain.model.MileageRatePreset
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class MileageRateConfigTest {

    @Test
    fun `IRS preset returns published per-mile rates`() {
        val config = MileageRateConfig(DistanceUnit.MILE, "USD", MileageRatePreset.IRS_2025)
        assertThat(config.rateFor(MileagePurpose.BUSINESS)).isWithin(1e-9).of(0.70)
        assertThat(config.rateFor(MileagePurpose.MEDICAL)).isWithin(1e-9).of(0.21)
        assertThat(config.rateFor(MileagePurpose.CHARITABLE)).isWithin(1e-9).of(0.14)
    }

    @Test
    fun `a per-mile preset converts to a smaller per-km rate`() {
        val perMile = MileageRateConfig(DistanceUnit.MILE, "USD", MileageRatePreset.IRS_2025)
            .rateFor(MileagePurpose.BUSINESS)!!
        val perKm = MileageRateConfig(DistanceUnit.KM, "USD", MileageRatePreset.IRS_2025)
            .rateFor(MileagePurpose.BUSINESS)!!

        assertThat(perKm).isWithin(1e-9)
            .of(perMile * (DistanceUnit.KM.meters / DistanceUnit.MILE.meters))
        assertThat(perKm).isLessThan(perMile)
    }

    @Test
    fun `non-deductible purposes never have a rate`() {
        val config = MileageRateConfig()
        assertThat(config.rateFor(MileagePurpose.PERSONAL)).isNull()
        assertThat(config.rateFor(MileagePurpose.UNCLASSIFIED)).isNull()
    }

    @Test
    fun `HMRC preset is a flat business rate with no medical rate`() {
        val config = MileageRateConfig(DistanceUnit.MILE, "GBP", MileageRatePreset.HMRC)
        assertThat(config.rateFor(MileagePurpose.BUSINESS)).isWithin(1e-9).of(0.45)
        assertThat(config.rateFor(MileagePurpose.MEDICAL)).isNull()
    }

    @Test
    fun `custom preset uses provided rates and treats zero as unset`() {
        val config = MileageRateConfig(
            distanceUnit = DistanceUnit.KM,
            currencyCode = "INR",
            ratePreset = MileageRatePreset.CUSTOM,
            customRates = mapOf(MileagePurpose.BUSINESS to 12.0, MileagePurpose.MEDICAL to 0.0)
        )
        assertThat(config.rateFor(MileagePurpose.BUSINESS)).isWithin(1e-9).of(12.0)
        assertThat(config.rateFor(MileagePurpose.MEDICAL)).isNull() // 0 ⇒ unset
        assertThat(config.rateFor(MileagePurpose.CHARITABLE)).isNull() // absent
    }

    @Test
    fun `defaultForLocale picks a jurisdiction-appropriate config`() {
        val us = MileageRateConfig.defaultForLocale(Locale.US)
        assertThat(us.ratePreset).isEqualTo(MileageRatePreset.IRS_2025)
        assertThat(us.distanceUnit).isEqualTo(DistanceUnit.MILE)
        assertThat(us.currencyCode).isEqualTo("USD")

        val uk = MileageRateConfig.defaultForLocale(Locale.UK)
        assertThat(uk.ratePreset).isEqualTo(MileageRatePreset.HMRC)
        assertThat(uk.currencyCode).isEqualTo("GBP")

        val india = MileageRateConfig.defaultForLocale(Locale("en", "IN"))
        assertThat(india.ratePreset).isEqualTo(MileageRatePreset.CUSTOM)
        assertThat(india.distanceUnit).isEqualTo(DistanceUnit.KM)
        assertThat(india.currencyCode).isEqualTo("INR")
    }
}
