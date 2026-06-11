package com.cosmiclaboratory.voyager.presentation.screen.reliability

/**
 * Pure OEM reliability helpers — which manufacturers aggressively kill background
 * apps, and the matching per-device guide on dontkillmyapp.com.
 *
 * Extracted from the ViewModel so the matching + URL logic is unit-testable.
 */
object OemReliability {

    /** OEMs known to aggressively kill background apps (dontkillmyapp.com). */
    private val AGGRESSIVE_OEMS = listOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo",
        "vivo", "oneplus", "realme", "samsung", "meizu", "asus"
    )

    /** Sub-brands that don't have their own dontkillmyapp page → parent slug. */
    private val SLUG_ALIASES = mapOf("redmi" to "xiaomi", "poco" to "xiaomi")

    private const val HOME = "https://dontkillmyapp.com/"

    fun isAggressive(manufacturer: String): Boolean =
        AGGRESSIVE_OEMS.any { manufacturer.contains(it, ignoreCase = true) }

    /**
     * Device-specific dontkillmyapp guide for [manufacturer], or the homepage when the
     * OEM isn't one we have a known page for — deep-linking beats dumping the user on a
     * generic index and is what the button promises.
     */
    fun dontKillMyAppUrl(manufacturer: String): String {
        val m = manufacturer.trim().lowercase()
        val matched = AGGRESSIVE_OEMS.firstOrNull { m.contains(it) } ?: return HOME
        val slug = SLUG_ALIASES[matched] ?: matched
        return "$HOME$slug"
    }
}
