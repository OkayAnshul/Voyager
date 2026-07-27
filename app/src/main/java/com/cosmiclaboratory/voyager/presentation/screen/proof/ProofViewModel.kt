package com.cosmiclaboratory.voyager.presentation.screen.proof

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import com.cosmiclaboratory.voyager.domain.model.formatMoney
import com.cosmiclaboratory.voyager.domain.model.toMileageRateConfig
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.usecase.BuildMileageLogUseCase
import com.cosmiclaboratory.voyager.domain.usecase.MileageDeduction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Live figures the Proof hub surfaces so its cards give a reason to tap, not just a label. */
data class ProofUiState(
    /** Formatted deductible amount for the current month, or null when there are no drives yet. */
    val mileageDeductible: String? = null,
    val unclassifiedCount: Int = 0
)

@HiltViewModel
class ProofViewModel @Inject constructor(
    buildMileageLog: BuildMileageLogUseCase,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<ProofUiState> = combine(
        buildMileageLog.observeLog(DateRangePeriod.ThisMonth),
        settingsRepository.observeSettings().map { it.toMileageRateConfig() }
    ) { log, config ->
        val estimate = MileageDeduction.estimate(log, config)
        ProofUiState(
            mileageDeductible = if (log.entries.isEmpty()) null
            else formatMoney(estimate.totalAmount, estimate.currencyCode),
            unclassifiedCount = log.unclassifiedCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProofUiState())
}
