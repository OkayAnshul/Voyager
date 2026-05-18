package com.cosmiclaboratory.voyager.presentation.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.billing.BillingGateway
import com.cosmiclaboratory.voyager.domain.billing.ProProduct
import com.cosmiclaboratory.voyager.domain.billing.PurchaseFlowState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [PaywallScreen]. Flavor-agnostic — it only touches [BillingGateway], so the
 * paywall renders identically against Play Billing or the F-Droid stub.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billingGateway: BillingGateway
) : ViewModel() {

    /** False on builds without a billing channel (F-Droid) — show an explainer. */
    val billingAvailable: Boolean = billingGateway.billingAvailable

    val products: StateFlow<List<ProProduct>> = billingGateway.products
    val purchaseFlow: StateFlow<PurchaseFlowState> = billingGateway.purchaseFlow

    init {
        if (billingAvailable) {
            viewModelScope.launch { billingGateway.loadProducts() }
        }
    }

    fun purchase(activity: Activity, productId: String) {
        billingGateway.launchPurchase(activity, productId)
    }

    fun restore() {
        viewModelScope.launch { billingGateway.restorePurchases() }
    }

    /** Clears a terminal purchase state once the UI has shown it. */
    fun consumePurchaseState() {
        billingGateway.resetPurchaseState()
    }
}
