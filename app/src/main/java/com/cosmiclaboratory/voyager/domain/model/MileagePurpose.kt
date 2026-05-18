package com.cosmiclaboratory.voyager.domain.model

/**
 * Tax/accounting purpose a user assigns to a drive segment in the mileage log.
 *
 * Stored by [name] in `mileage_classifications.purpose`. Categories mirror the
 * deductible classes used by IRS (business / medical / charitable) and HMRC
 * (business); personal driving is tracked but non-deductible. [UNCLASSIFIED] is
 * the implicit state of a drive with no row in the table — it is never persisted.
 */
enum class MileagePurpose(
    val displayName: String,
    /** Whether mileage of this purpose is generally tax-deductible. */
    val deductible: Boolean
) {
    UNCLASSIFIED("Unclassified", deductible = false),
    BUSINESS("Business", deductible = true),
    PERSONAL("Personal", deductible = false),
    MEDICAL("Medical", deductible = true),
    CHARITABLE("Charitable", deductible = true);

    companion object {
        /** Safe parse — unknown/missing values fall back to [UNCLASSIFIED]. */
        fun fromName(name: String?): MileagePurpose =
            entries.firstOrNull { it.name == name } ?: UNCLASSIFIED
    }
}
