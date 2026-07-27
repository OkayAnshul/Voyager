package com.cosmiclaboratory.voyager.domain.model

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A named set of standard mileage rates. [IRS_2025] and [HMRC] are the built-in
 * jurisdictions; [CUSTOM] means the user supplies their own per-unit rates (for any
 * other country or a reimbursement policy that differs from the tax-office figure).
 */
enum class MileageRatePreset(val displayName: String) {
    IRS_2025("IRS 2025 (US)"),
    HMRC("HMRC (UK)"),
    CUSTOM("Custom");

    companion object {
        fun fromName(name: String?): MileageRatePreset =
            entries.firstOrNull { it.name == name } ?: IRS_2025
    }
}

/**
 * Everything the mileage money math needs from the user: which distance unit to work in,
 * which currency to report, and the per-purpose rates (a built-in preset or a custom set).
 *
 * [customRates] are keyed by [MileagePurpose] and expressed in [currencyCode] **per
 * [distanceUnit]** — i.e. the number the user typed for their chosen unit. Built-in preset
 * rates are stored canonically per mile (that is how IRS/HMRC publish them) and converted to
 * the active [distanceUnit] on demand by [rateFor]; because that is a pure unit conversion of
 * the same physical distance, the resulting money is identical whichever unit is displayed.
 */
data class MileageRateConfig(
    val distanceUnit: DistanceUnit = DistanceUnit.MILE,
    val currencyCode: String = "USD",
    val ratePreset: MileageRatePreset = MileageRatePreset.IRS_2025,
    val customRates: Map<MileagePurpose, Double> = emptyMap()
) {

    /**
     * The effective rate for [purpose] in [currencyCode] per [distanceUnit], or null when the
     * purpose is non-deductible or has no rate under the active preset (renders as "—").
     */
    fun rateFor(purpose: MileagePurpose): Double? {
        if (!purpose.deductible) return null
        val perMile = when (ratePreset) {
            MileageRatePreset.CUSTOM -> return customRates[purpose]?.takeIf { it > 0.0 }
            MileageRatePreset.IRS_2025 -> IRS_2025_PER_MILE[purpose]
            MileageRatePreset.HMRC -> HMRC_PER_MILE[purpose]
        } ?: return null
        return when (distanceUnit) {
            DistanceUnit.MILE -> perMile
            // currency/mile → currency/km: 1 mile = MILE.meters/KM.meters km.
            DistanceUnit.KM -> perMile * (DistanceUnit.KM.meters / DistanceUnit.MILE.meters)
        }
    }

    companion object {
        /** IRS 2025 standard mileage rates, USD per mile. Absent purposes are non-deductible. */
        val IRS_2025_PER_MILE: Map<MileagePurpose, Double> = mapOf(
            MileagePurpose.BUSINESS to 0.70,
            MileagePurpose.MEDICAL to 0.21,
            MileagePurpose.CHARITABLE to 0.14,
        )

        /**
         * HMRC approved mileage rate, GBP per mile. Simplified to a single business rate — the
         * real scheme drops from 45p to 25p after 10,000 business miles/year, which needs a
         * running year-to-date total the log's date range doesn't carry. The exporter disclaimer
         * flags this; users past the threshold should switch to a [MileageRatePreset.CUSTOM] rate.
         */
        val HMRC_PER_MILE: Map<MileagePurpose, Double> = mapOf(
            MileagePurpose.BUSINESS to 0.45,
        )

        /**
         * A sensible default for the device's locale: US → IRS/USD/miles, UK → HMRC/GBP/miles,
         * everywhere else → a custom set in the local currency and kilometres (business rate left
         * at 0 for the user to fill in, so nothing is presented as an official figure).
         */
        fun defaultForLocale(locale: Locale = Locale.getDefault()): MileageRateConfig =
            when (locale.country) {
                "US" -> MileageRateConfig(DistanceUnit.MILE, "USD", MileageRatePreset.IRS_2025)
                "GB" -> MileageRateConfig(DistanceUnit.MILE, "GBP", MileageRatePreset.HMRC)
                else -> {
                    val currency = runCatching { Currency.getInstance(locale).currencyCode }
                        .getOrNull() ?: "USD"
                    MileageRateConfig(
                        distanceUnit = DistanceUnit.KM,
                        currencyCode = currency,
                        ratePreset = MileageRatePreset.CUSTOM,
                        customRates = mapOf(MileagePurpose.BUSINESS to 0.0)
                    )
                }
            }
    }
}

/** Projects the mileage-related fields of [UserSettings] into the config the money math uses. */
fun UserSettings.toMileageRateConfig(): MileageRateConfig =
    MileageRateConfig(
        distanceUnit = mileageDistanceUnit,
        currencyCode = mileageCurrencyCode,
        ratePreset = mileageRatePreset,
        customRates = mapOf(
            MileagePurpose.BUSINESS to mileageCustomRateBusiness,
            MileagePurpose.MEDICAL to mileageCustomRateMedical,
            MileagePurpose.CHARITABLE to mileageCustomRateCharitable,
        )
    )

/** Short unit label for the mileage UI/exports — "mi" or "km". */
val DistanceUnit.shortLabel: String
    get() = when (this) {
        DistanceUnit.MILE -> "mi"
        DistanceUnit.KM -> "km"
    }

/** "12.3 mi" / "12.3 km" — one decimal, matching the rest of the mileage UI. */
fun formatDistance(distance: Double, unit: DistanceUnit): String =
    "%.1f %s".format(distance, unit.shortLabel)

/**
 * Format [amount] in [currencyCode] using the device locale's conventions (grouping and symbol
 * placement), falling back to a plain number if the code is not a known ISO-4217 currency.
 */
fun formatMoney(amount: Double, currencyCode: String): String {
    val fmt = NumberFormat.getCurrencyInstance()
    val applied = runCatching { fmt.currency = Currency.getInstance(currencyCode) }.isSuccess
    return if (applied) fmt.format(amount) else "%s %.2f".format(currencyCode, amount)
}
