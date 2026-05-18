package com.cosmiclaboratory.voyager.presentation.screen.daystory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.DayStory
import com.cosmiclaboratory.voyager.domain.usecase.BuildDayStoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DayStoryUiState(
    val dayKey: String,
    val hasPermission: Boolean = false,
    val isLoading: Boolean = true,
    val story: DayStory? = null,
    val error: String? = null
) {
    val isToday: Boolean get() = dayKey == LocalDate.now().toString()
}

sealed interface DayStoryAction {
    data object PreviousDay : DayStoryAction
    data object NextDay : DayStoryAction
    data object Today : DayStoryAction
    /** The just-in-time photo permission was granted — reload the story. */
    data object PermissionGranted : DayStoryAction
}

/**
 * Drives the Photo Day Story screen.
 *
 * The day to show comes from the optional `dayKey` nav argument (the Timeline shortcut
 * supplies it; the standalone entry defaults to today). The screen requests the photo
 * permission just-in-time and signals [DayStoryAction.PermissionGranted] once granted.
 */
@HiltViewModel
class DayStoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val buildDayStory: BuildDayStoryUseCase
) : ViewModel() {

    private val initialDay: String =
        savedStateHandle.get<String>("dayKey")?.takeIf { it.isNotBlank() }
            ?: LocalDate.now().toString()

    private val _uiState = MutableStateFlow(DayStoryUiState(dayKey = initialDay))
    val uiState: StateFlow<DayStoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onAction(action: DayStoryAction) {
        when (action) {
            DayStoryAction.PreviousDay -> shiftDay(-1)
            DayStoryAction.NextDay -> shiftDay(1)
            DayStoryAction.Today -> {
                _uiState.update { it.copy(dayKey = LocalDate.now().toString()) }
                refresh()
            }
            DayStoryAction.PermissionGranted -> refresh()
        }
    }

    private fun shiftDay(deltaDays: Long) {
        val target = LocalDate.parse(_uiState.value.dayKey).plusDays(deltaDays)
        if (target.isAfter(LocalDate.now())) return // no future days
        _uiState.update { it.copy(dayKey = target.toString()) }
        refresh()
    }

    private fun refresh() {
        val hasPermission = buildDayStory.hasPhotoPermission()
        _uiState.update {
            it.copy(hasPermission = hasPermission, isLoading = hasPermission, error = null)
        }
        if (!hasPermission) return

        val day = _uiState.value.dayKey
        viewModelScope.launch {
            val result = runCatching { buildDayStory.build(day) }
            _uiState.update { state ->
                // Drop a stale load if the user changed day while it was in flight.
                if (state.dayKey != day) return@update state
                state.copy(
                    isLoading = false,
                    story = result.getOrNull() ?: DayStory.empty(day),
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }
}
