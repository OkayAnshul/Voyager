package com.cosmiclaboratory.voyager.domain.billing

import kotlinx.coroutines.flow.Flow

/**
 * The distribution-channel-specific source of Pro entitlement.
 *
 * Implemented once per build flavor: the `play` flavor backs this with Play Billing
 * (proprietary code that must never reach the F-Droid build); the `fdroid` flavor has
 * no billing channel. The flavor's Hilt `BillingModule` binds the right implementation.
 *
 * Gate code never depends on this directly — [ProEntitlementManager] consumes it and
 * adds the offline cache and a single app-wide [kotlinx.coroutines.flow.StateFlow].
 */
interface EntitlementSource {

    /**
     * Pro entitlement as currently known to this channel.
     *
     * May emit slowly (after a billing round-trip) or stay unresolved while offline,
     * so callers must treat the first frame as "not yet known", never block on it.
     */
    val proEntitlement: Flow<Boolean>

    /** Forces a re-check against the channel. A no-op where the channel cannot be queried. */
    suspend fun refresh()
}
