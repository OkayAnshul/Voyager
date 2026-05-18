package com.cosmiclaboratory.voyager.billing

import com.cosmiclaboratory.voyager.domain.billing.EntitlementSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play-flavor [EntitlementSource].
 *
 * TODO(Phase 5): back this with `BillingClientWrapper` (Play Billing v7) — query
 * owned purchases for `pro_monthly` / `pro_yearly` / `pro_lifetime` and emit the
 * result here, re-verifying on launch.
 *
 * Until billing lands, the Play build reports no entitlement; Pro stays reachable
 * only via the debug override in `ProEntitlementManager`. This keeps the wiring —
 * interface, DI, gate composable — in place so the billing client is a drop-in.
 */
@Singleton
class PlayEntitlementSource @Inject constructor() : EntitlementSource {

    override val proEntitlement: Flow<Boolean> = flowOf(false)

    override suspend fun refresh() {
        // No-op until BillingClientWrapper exists.
    }
}
