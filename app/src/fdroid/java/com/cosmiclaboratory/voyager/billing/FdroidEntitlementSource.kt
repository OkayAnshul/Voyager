package com.cosmiclaboratory.voyager.billing

import com.cosmiclaboratory.voyager.domain.billing.EntitlementSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid-flavor [EntitlementSource].
 *
 * The F-Droid build carries no proprietary billing code, so it has no channel to
 * purchase through — it is the free tier and reports no Pro entitlement.
 *
 * To instead ship an all-unlocked FOSS build, emit `true` here; that is a product
 * decision, deliberately left as a one-line change.
 */
@Singleton
class FdroidEntitlementSource @Inject constructor() : EntitlementSource {

    override val proEntitlement: Flow<Boolean> = flowOf(false)

    override suspend fun refresh() {
        // No billing channel on F-Droid — nothing to re-check.
    }
}
