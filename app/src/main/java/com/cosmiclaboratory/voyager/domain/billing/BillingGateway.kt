package com.cosmiclaboratory.voyager.domain.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * The purchase-side billing surface the paywall consumes — distinct from
 * [EntitlementSource], which only answers "does the user have Pro?".
 *
 * Flavor-specific: the `play` flavor backs this with `BillingClientWrapper` (Play
 * Billing v7); the `fdroid` flavor has no billing channel and ships a stub with
 * [billingAvailable] false. Keeping the interface in `src/main` lets `PaywallScreen`
 * live there and stay flavor-agnostic and previewable.
 */
interface BillingGateway {

    /** False on builds with no billing channel (F-Droid) — the paywall shows an explainer. */
    val billingAvailable: Boolean

    /** The resolved Pro catalog with localized pricing; empty until [loadProducts]. */
    val products: StateFlow<List<ProProduct>>

    /** Progress of the active purchase/restore flow. */
    val purchaseFlow: StateFlow<PurchaseFlowState>

    /** Connects to the billing channel and loads product details. Safe to call repeatedly. */
    suspend fun loadProducts()

    /** Launches the Play purchase dialog for [productId] from [activity]. */
    fun launchPurchase(activity: Activity, productId: String)

    /** Re-queries owned purchases — "Restore purchases" and launch re-check. */
    suspend fun restorePurchases()

    /** Clears a terminal [PurchaseFlowState] back to [PurchaseFlowState.Idle]. */
    fun resetPurchaseState()
}
