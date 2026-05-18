package com.cosmiclaboratory.voyager.domain.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cosmiclaboratory.voyager.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide source of truth for whether the user has Voyager Pro.
 *
 * Wraps the flavor-specific [EntitlementSource] with two things gate code needs:
 *
 *  - **Offline cache.** The last entitlement seen from the channel is persisted to
 *    DataStore, so [isPro] resolves correctly on the very next launch even with no
 *    network and before billing has been re-queried.
 *  - **One [StateFlow].** Every gate observes the same [isPro] flow.
 *
 * In debug builds a developer override can force Pro on without a real purchase, so
 * Pro surfaces are testable before Play Billing (the `BillingClientWrapper`) lands.
 *
 * Note: the cache is a UX convenience, not a security boundary — the `play`
 * [EntitlementSource] re-verifies the purchase against Play on launch.
 */
@Singleton
class ProEntitlementManager @Inject constructor(
    private val entitlementSource: EntitlementSource,
    private val dataStore: DataStore<Preferences>
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isPro = MutableStateFlow(false)

    /** Whether the user currently has Pro. Seeded from the offline cache, kept live by the channel. */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /**
     * Current state of the debug Pro override — for a developer-mode toggle to reflect.
     * Always effectively false in release builds (the override is ignored there).
     */
    val debugProOverride: StateFlow<Boolean> = dataStore.data
        .map { (it[DEBUG_OVERRIDE_KEY] ?: false) && BuildConfig.DEBUG }
        .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Mirror the channel's entitlement into the offline cache.
        scope.launch {
            entitlementSource.proEntitlement.collect { entitled ->
                dataStore.edit { it[PRO_CACHE_KEY] = entitled }
            }
        }
        // Derive isPro from the cache (+ debug override). DataStore is the single
        // source of truth, so isPro is correct offline and survives process death.
        scope.launch {
            _isPro.value = effectivePro(dataStore.data.first())
            dataStore.data
                .map(::effectivePro)
                .distinctUntilChanged()
                .collect { _isPro.value = it }
        }
    }

    /** Re-verifies entitlement against the distribution channel — e.g. on app launch. */
    fun refresh() {
        scope.launch { runCatching { entitlementSource.refresh() } }
    }

    /**
     * Debug-only: force Pro on/off without a purchase. No effect in release builds.
     * Fire-and-forget — observe [debugProOverride] for the resulting state.
     * Intended for a developer-mode toggle and for instrumented tests.
     */
    fun setDebugProOverride(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        scope.launch { dataStore.edit { it[DEBUG_OVERRIDE_KEY] = enabled } }
    }

    private fun effectivePro(prefs: Preferences): Boolean {
        val cached = prefs[PRO_CACHE_KEY] ?: false
        val debugOverride = BuildConfig.DEBUG && (prefs[DEBUG_OVERRIDE_KEY] ?: false)
        return cached || debugOverride
    }

    private companion object {
        val PRO_CACHE_KEY = booleanPreferencesKey("pro_entitled_cache")
        val DEBUG_OVERRIDE_KEY = booleanPreferencesKey("pro_debug_override")
    }
}
