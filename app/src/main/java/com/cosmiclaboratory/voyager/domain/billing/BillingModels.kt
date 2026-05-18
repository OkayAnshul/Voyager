package com.cosmiclaboratory.voyager.domain.billing

/**
 * The Voyager Pro product catalog — IDs must match the products configured in the
 * Play Console. Two subscriptions and one non-consumable lifetime unlock.
 */
object ProCatalog {
    /** Monthly subscription (Play product type SUBS). */
    const val MONTHLY = "pro_monthly"

    /** Yearly subscription (Play product type SUBS). */
    const val YEARLY = "pro_yearly"

    /** One-time lifetime unlock (Play product type INAPP, non-consumable). */
    const val LIFETIME = "pro_lifetime"
}

enum class ProProductType { MONTHLY, YEARLY, LIFETIME }

/**
 * A purchasable Pro product, resolved from Play with localized pricing.
 * Flavor-agnostic — the paywall renders these without touching billing APIs.
 */
data class ProProduct(
    val productId: String,
    val type: ProProductType,
    val title: String,
    /** Localized price string from Play, e.g. "$4.99". */
    val formattedPrice: String,
    /** Human period suffix, e.g. "per month", "per year", "one-time". */
    val periodLabel: String
)

/** Progress of a purchase or restore flow, surfaced to the paywall UI. */
sealed interface PurchaseFlowState {
    data object Idle : PurchaseFlowState

    /** Talking to Play — connecting, loading products, or launching the flow. */
    data object Working : PurchaseFlowState

    /** Purchase accepted but not yet completed (e.g. cash / pending payment). */
    data object Pending : PurchaseFlowState

    /** Entitlement granted — the user now has Pro. */
    data object Purchased : PurchaseFlowState

    data class Failed(val message: String) : PurchaseFlowState
}
