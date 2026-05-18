package com.cosmiclaboratory.voyager.presentation.billing

import androidx.lifecycle.ViewModel
import com.cosmiclaboratory.voyager.domain.billing.ProEntitlementManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes the app-wide Pro entitlement to any screen that gates a feature.
 *
 * A screen collects [isPro] and passes it to [com.cosmiclaboratory.voyager.presentation.billing.FeatureGate].
 */
@HiltViewModel
class EntitlementViewModel @Inject constructor(
    proEntitlementManager: ProEntitlementManager
) : ViewModel() {

    /** Whether the user has Voyager Pro — see [ProEntitlementManager.isPro]. */
    val isPro: StateFlow<Boolean> = proEntitlementManager.isPro
}
