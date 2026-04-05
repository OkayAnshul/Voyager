package com.cosmiclaboratory.voyager.presentation.state

import android.util.Log
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Shared state holder for Map ↔ Timeline bidirectional sync.
 * @ActivityRetainedScoped so it survives config changes but not process death.
 */
@ActivityRetainedScoped
class DayNavigationStateHolder @Inject constructor() {

    private val _currentDayKey = MutableStateFlow(todayKey())
    val currentDayKey: StateFlow<String> = _currentDayKey.asStateFlow()

    private val _focusedSegmentId = MutableStateFlow<Long?>(null)
    val focusedSegmentId: StateFlow<Long?> = _focusedSegmentId.asStateFlow()

    private val _focusedVisitId = MutableStateFlow<Long?>(null)
    val focusedVisitId: StateFlow<Long?> = _focusedVisitId.asStateFlow()

    fun navigateToDay(dayKey: String) {
        _currentDayKey.value = dayKey
        _focusedSegmentId.value = null
        _focusedVisitId.value = null
    }

    fun focusSegment(segmentId: Long?) {
        _focusedSegmentId.value = segmentId
        _focusedVisitId.value = null
    }

    fun focusVisit(visitId: Long?) {
        _focusedVisitId.value = visitId
        _focusedSegmentId.value = null
    }

    fun navigatePreviousDay() {
        _currentDayKey.value = offsetDay(_currentDayKey.value, -1)
        clearFocus()
    }

    fun navigateNextDay() {
        val next = offsetDay(_currentDayKey.value, 1)
        if (next <= todayKey()) {
            _currentDayKey.value = next
            clearFocus()
        }
    }

    fun clearFocus() {
        _focusedSegmentId.value = null
        _focusedVisitId.value = null
    }

    private fun todayKey(): String {
        val now = java.time.LocalDate.now()
        return now.toString() // YYYY-MM-DD
    }

    private fun offsetDay(dayKey: String, offset: Int): String {
        return try {
            java.time.LocalDate.parse(dayKey).plusDays(offset.toLong()).toString()
        } catch (e: Exception) {
            Log.w("DayNavigation", "Failed to parse dayKey: $dayKey", e)
            dayKey
        }
    }
}
