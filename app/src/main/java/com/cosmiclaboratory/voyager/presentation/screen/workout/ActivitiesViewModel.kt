package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.PersonalRecords
import com.cosmiclaboratory.voyager.domain.model.WorkoutSuggestion
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.domain.usecase.ImportGpxUseCase
import com.cosmiclaboratory.voyager.domain.usecase.PersonalRecordsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.SuggestWorkoutUseCase
import com.cosmiclaboratory.voyager.domain.usecase.toDomain
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Past recorded workouts for the Activities list, newest first, plus the user's all-time personal
 * records. Reactive — a new workout saved via [WorkoutViewModel] appears (and updates the records)
 * without a manual refresh.
 */
@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    activityDao: ActivityDao,
    personalRecords: PersonalRecordsUseCase,
    private val suggestWorkout: SuggestWorkoutUseCase,
    private val importGpxUseCase: ImportGpxUseCase,
) : ViewModel() {

    private val domainActivities = activityDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    val activities: StateFlow<List<Activity>> = domainActivities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All-time PRs across every recorded activity — computed off the main thread. */
    val records: StateFlow<PersonalRecords> = domainActivities
        .map { personalRecords.compute(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonalRecords())

    private val dismissed = mutableSetOf<Long>()
    private val _suggestions = MutableStateFlow<List<WorkoutSuggestion>>(emptyList())
    /** Passive runs/rides/walks that look like workouts — "save this as an activity?". */
    val suggestions: StateFlow<List<WorkoutSuggestion>> = _suggestions.asStateFlow()

    init { refreshSuggestions() }

    private fun refreshSuggestions() {
        viewModelScope.launch {
            _suggestions.value = suggestWorkout.suggestions().filter { it.segmentId !in dismissed }
        }
    }

    /** Convert a suggestion into a saved activity (the feed + records update reactively). */
    fun saveSuggestion(suggestion: WorkoutSuggestion) {
        viewModelScope.launch {
            suggestWorkout.materialize(suggestion)
            refreshSuggestions()
        }
    }

    /** Hide a suggestion for this session (it ages out of the lookback window anyway). */
    fun dismissSuggestion(segmentId: Long) {
        dismissed += segmentId
        _suggestions.value = _suggestions.value.filterNot { it.segmentId == segmentId }
    }

    /** Import a GPX file's track as a new activity (defaults to Run; the feed updates reactively). */
    fun importGpx(xml: String, type: WorkoutType = WorkoutType.RUN) {
        viewModelScope.launch { importGpxUseCase.import(xml, type) }
    }
}
