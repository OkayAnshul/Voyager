package com.cosmiclaboratory.voyager.billing

import android.app.Activity
import com.cosmiclaboratory.voyager.domain.billing.BillingGateway
import com.cosmiclaboratory.voyager.domain.billing.ProProduct
import com.cosmiclaboratory.voyager.domain.billing.PurchaseFlowState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid-flavor [BillingGateway] stub.
 *
 * The F-Droid build carries no proprietary billing code, so there is nothing to
 * purchase: [billingAvailable] is false and the paywall renders an explainer
 * instead of products.
 */
@Singleton
class FdroidBillingGateway @Inject constructor() : BillingGateway {

    override val billingAvailable: Boolean = false

    override val products: StateFlow<List<ProProduct>> =
        MutableStateFlow(emptyList())

    override val purchaseFlow: StateFlow<PurchaseFlowState> =
        MutableStateFlow(PurchaseFlowState.Idle)

    override suspend fun loadProducts() = Unit

    override fun launchPurchase(activity: Activity, productId: String) = Unit

    override suspend fun restorePurchases() = Unit

    override fun resetPurchaseState() = Unit
}
