package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.LiveWorkoutStats
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.usecase.WorkoutRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the (mockup-pending) Workout Record screen for the Athlete persona.
 *
 * Thin layer over [WorkoutRecorder]: [start] switches to the high-accuracy WORKOUT tier and
 * begins accumulating; the live location stream now feeds the recorder from `PipelineConsumer`
 * (cleaned, deduped, spoof-checked fixes), so [liveStats] ticks while recording. [stop]
 * finalises and surfaces the saved [Activity] for the screen to show / navigate to.
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val recorder: WorkoutRecorder
) : ViewModel() {

    /** Live distance/duration/pace while recording; null when idle. */
    val liveStats: StateFlow<LiveWorkoutStats?> = recorder.liveStats

    /** True while a workout is being recorded (derived from the live-stats stream). */
    val isRecording: StateFlow<Boolean> = recorder.liveStats
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), recorder.isRecording)

    private val _savedActivity = MutableStateFlow<Activity?>(null)
    /** The activity produced by the last [stop]; null until one is saved (or after [consumeSaved]). */
    val savedActivity: StateFlow<Activity?> = _savedActivity.asStateFlow()

    fun start(type: WorkoutType) {
        viewModelScope.launch { recorder.start(type) }
    }

    fun stop() {
        viewModelScope.launch { _savedActivity.value = recorder.stop() }
    }

    /** Clear the saved-activity signal once the screen has handled it. */
    fun consumeSaved() {
        _savedActivity.value = null
    }
}
