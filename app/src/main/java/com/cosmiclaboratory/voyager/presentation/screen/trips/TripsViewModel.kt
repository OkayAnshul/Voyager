package com.cosmiclaboratory.voyager.presentation.screen.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Trip
import com.cosmiclaboratory.voyager.domain.usecase.DetectTripsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.toDomain
import com.cosmiclaboratory.voyager.storage.database.dao.TripDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripsUiState(
    val trips: List<Trip> = emptyList(),
    val hasHomeAnchor: Boolean = true,
    val isLoading: Boolean = true,
    val isDetecting: Boolean = false
)

sealed interface TripsAction {
    /** Re-run detection now instead of waiting for the nightly worker. */
    data object Refresh : TripsAction
}

/**
 * Backs the Trips list. Trips are produced by the nightly [DetectTripsUseCase] worker;
 * this also re-runs detection once on open so a new user sees trips without waiting.
 */
@HiltViewModel
class TripsViewModel @Inject constructor(
    tripDao: TripDao,
    private val detectTrips: DetectTripsUseCase
) : ViewModel() {

    private val hasHomeAnchor = MutableStateFlow(true)
    private val isDetecting = MutableStateFlow(false)

    val uiState: StateFlow<TripsUiState> = combine(
        tripDao.observeAll().map { rows -> rows.map { it.toDomain() } },
        hasHomeAnchor,
        isDetecting
    ) { trips, hasHome, detecting ->
        TripsUiState(
            trips = trips,
            hasHomeAnchor = hasHome,
            isLoading = false,
            isDetecting = detecting
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripsUiState())

    init {
        refresh()
    }

    fun onAction(action: TripsAction) {
        when (action) {
            TripsAction.Refresh -> refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            isDetecting.value = true
            hasHomeAnchor.value = detectTrips.hasHomeAnchor()
            runCatching { detectTrips.detectAndStore() }
            isDetecting.value = false
        }
    }
}
