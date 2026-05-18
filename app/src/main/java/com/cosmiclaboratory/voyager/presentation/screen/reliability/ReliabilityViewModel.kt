package com.cosmiclaboratory.voyager.presentation.screen.reliability

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.repository.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReliabilityUiState(
    val manufacturer: String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
    /** OEMs known to aggressively kill background apps (dontkillmyapp.com). */
    val isAggressiveOem: Boolean = false,
    val lastSampleAt: Long? = null,
    /** Whole hours since the last accepted sample, null if never sampled. */
    val hoursSinceLastSample: Long? = null,
    val hasRecentGap: Boolean = false
)

@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    trackingRepository: TrackingRepository
) : ViewModel() {

    companion object {
        private val AGGRESSIVE_OEMS = listOf(
            "xiaomi", "redmi", "poco", "huawei", "honor", "oppo",
            "vivo", "oneplus", "realme", "samsung", "meizu", "asus"
        )
        private const val GAP_THRESHOLD_MS = 24L * 60 * 60 * 1000
    }

    val uiState: StateFlow<ReliabilityUiState> = trackingRepository.observeHealth()
        .map { health ->
            val manufacturer = Build.MANUFACTURER
            val now = System.currentTimeMillis()
            val last = health.lastSampleAt
            ReliabilityUiState(
                manufacturer = manufacturer.replaceFirstChar { it.uppercase() },
                isAggressiveOem = AGGRESSIVE_OEMS.any { manufacturer.contains(it, ignoreCase = true) },
                lastSampleAt = last,
                hoursSinceLastSample = last?.let { (now - it) / (60L * 60 * 1000) },
                hasRecentGap = last != null && last > 0L && now - last > GAP_THRESHOLD_MS
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReliabilityUiState())
}
