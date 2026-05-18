package com.cosmiclaboratory.voyager.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.cosmiclaboratory.voyager.domain.billing.BillingGateway
import com.cosmiclaboratory.voyager.domain.billing.EntitlementSource
import com.cosmiclaboratory.voyager.domain.billing.ProCatalog
import com.cosmiclaboratory.voyager.domain.billing.ProProduct
import com.cosmiclaboratory.voyager.domain.billing.ProProductType
import com.cosmiclaboratory.voyager.domain.billing.PurchaseFlowState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Play Billing v7 implementation of both billing surfaces.
 *
 * It is the [EntitlementSource] (does the user own Pro?) and the [BillingGateway]
 * (product catalog + purchase launch) — a single connected [BillingClient] backs
 * both, so one Hilt singleton is bound to both interfaces.
 *
 * Proprietary: this file lives only in `src/play` and never compiles into the
 * F-Droid build.
 */
@Singleton
class BillingClientWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) : EntitlementSource, BillingGateway, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    private val _entitlement = MutableStateFlow(false)
    override val proEntitlement: Flow<Boolean> = _entitlement.asStateFlow()

    private val _products = MutableStateFlow<List<ProProduct>>(emptyList())
    override val products: StateFlow<List<ProProduct>> = _products.asStateFlow()

    private val _purchaseFlow = MutableStateFlow<PurchaseFlowState>(PurchaseFlowState.Idle)
    override val purchaseFlow: StateFlow<PurchaseFlowState> = _purchaseFlow.asStateFlow()

    override val billingAvailable: Boolean = true

    /** ProductDetails kept so [launchPurchase] can build flow params by product id. */
    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init {
        // Re-verify entitlement on construction (app launch) so a purchase made on
        // another device — or a refund — is reflected without opening the paywall.
        scope.launch { runCatching { queryEntitlement() } }
    }

    // ── EntitlementSource ─────────────────────────────────────────────

    override suspend fun refresh() {
        runCatching { queryEntitlement() }
    }

    // ── BillingGateway ────────────────────────────────────────────────

    override suspend fun loadProducts() {
        if (!ensureConnected()) {
            _purchaseFlow.value = PurchaseFlowState.Failed("Google Play is unavailable.")
            return
        }
        runCatching {
            val subs = queryDetails(
                BillingClient.ProductType.SUBS, ProCatalog.MONTHLY, ProCatalog.YEARLY
            )
            val inApp = queryDetails(BillingClient.ProductType.INAPP, ProCatalog.LIFETIME)
            val all = subs + inApp
            all.forEach { productDetails[it.productId] = it }
            _products.value = all.mapNotNull(::toProProduct)
                .sortedBy { it.type.ordinal }
        }.onFailure {
            Log.w(TAG, "loadProducts failed", it)
            _purchaseFlow.value = PurchaseFlowState.Failed("Could not load Pro pricing.")
        }
    }

    override fun launchPurchase(activity: Activity, productId: String) {
        val details = productDetails[productId]
        if (details == null) {
            _purchaseFlow.value = PurchaseFlowState.Failed("Product not loaded yet — try again.")
            return
        }
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        if (details.productType == BillingClient.ProductType.SUBS) {
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _purchaseFlow.value = PurchaseFlowState.Failed("Subscription offer unavailable.")
                return
            }
            paramsBuilder.setOfferToken(offerToken)
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        _purchaseFlow.value = PurchaseFlowState.Working
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseFlow.value = PurchaseFlowState.Failed(
                result.debugMessage.ifBlank { "Could not start the purchase." }
            )
        }
    }

    override suspend fun restorePurchases() {
        _purchaseFlow.value = PurchaseFlowState.Working
        val ok = runCatching { queryEntitlement() }.getOrDefault(false)
        _purchaseFlow.value = when {
            !ok -> PurchaseFlowState.Failed("Could not reach Google Play.")
            _entitlement.value -> PurchaseFlowState.Purchased
            else -> PurchaseFlowState.Idle
        }
    }

    override fun resetPurchaseState() {
        _purchaseFlow.value = PurchaseFlowState.Idle
    }

    // ── PurchasesUpdatedListener ──────────────────────────────────────

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach(::handlePurchase)
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _purchaseFlow.value = PurchaseFlowState.Idle
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _purchaseFlow.value = PurchaseFlowState.Purchased
                _entitlement.value = true
            }
            else -> _purchaseFlow.value = PurchaseFlowState.Failed(
                result.debugMessage.ifBlank { "Purchase failed." }
            )
        }
    }

    // ── Internals ─────────────────────────────────────────────────────

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                _entitlement.value = true
                _purchaseFlow.value = PurchaseFlowState.Purchased
                // Failing to acknowledge within 3 days auto-refunds the purchase.
                if (!purchase.isAcknowledged) {
                    scope.launch {
                        runCatching {
                            billingClient.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.purchaseToken)
                                    .build()
                            )
                        }.onFailure { Log.w(TAG, "acknowledge failed", it) }
                    }
                }
            }
            Purchase.PurchaseState.PENDING ->
                _purchaseFlow.value = PurchaseFlowState.Pending
            else -> Unit
        }
    }

    /** Queries owned purchases across SUBS + INAPP and recomputes entitlement. */
    private suspend fun queryEntitlement(): Boolean {
        if (!ensureConnected()) return false
        val owned = buildList {
            addAll(queryOwned(BillingClient.ProductType.SUBS))
            addAll(queryOwned(BillingClient.ProductType.INAPP))
        }
        owned.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { purchase ->
                runCatching {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    )
                }
            }
        _entitlement.value = owned.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        return true
    }

    private suspend fun queryOwned(productType: String): List<Purchase> {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(productType).build()
        )
        return result.purchasesList
    }

    private suspend fun queryDetails(productType: String, vararg ids: String): List<ProductDetails> {
        val productList = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build()
        }
        val result = billingClient.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        )
        return result.productDetailsList.orEmpty()
    }

    /** Connects to Play if needed; serialized so concurrent callers share one attempt. */
    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true
        return connectionMutex.withLock {
            if (billingClient.isReady) return@withLock true
            suspendCancellableCoroutine { cont ->
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (!cont.isActive) return
                        cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
        }
    }

    private fun toProProduct(details: ProductDetails): ProProduct? {
        val type = when (details.productId) {
            ProCatalog.MONTHLY -> ProProductType.MONTHLY
            ProCatalog.YEARLY -> ProProductType.YEARLY
            ProCatalog.LIFETIME -> ProProductType.LIFETIME
            else -> return null
        }
        val price: String = when (type) {
            ProProductType.LIFETIME ->
                details.oneTimePurchaseOfferDetails?.formattedPrice ?: return null
            else -> details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice ?: return null
        }
        val period = when (type) {
            ProProductType.MONTHLY -> "per month"
            ProProductType.YEARLY -> "per year"
            ProProductType.LIFETIME -> "one-time"
        }
        return ProProduct(
            productId = details.productId,
            type = type,
            title = details.title.ifBlank { details.name },
            formattedPrice = price,
            periodLabel = period
        )
    }

    private companion object {
        const val TAG = "BillingClientWrapper"
    }
}
