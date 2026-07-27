package com.cosmiclaboratory.voyager.billing

import com.cosmiclaboratory.voyager.domain.billing.EntitlementSource
import com.cosmiclaboratory.voyager.domain.billing.ProEntitlementManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid-flavor [EntitlementSource].
 *
 * The F-Droid build carries no proprietary billing code, so it has no channel to
 * purchase through. While [ProEntitlementManager.FREE_EVERYTHING] is set, everything
 * is free anyway; when subscriptions return, this reverts to the free tier (`false`)
 * and Play remains the only paid channel.
 */
@Singleton
class FdroidEntitlementSource @Inject constructor() : EntitlementSource {

    override val proEntitlement: Flow<Boolean> = flowOf(ProEntitlementManager.FREE_EVERYTHING)

    override suspend fun refresh() {
        // No billing channel on F-Droid — nothing to re-check.
    }
}
