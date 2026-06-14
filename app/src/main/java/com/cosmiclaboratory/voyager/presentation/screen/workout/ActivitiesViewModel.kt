package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.usecase.toDomain
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Past recorded workouts for the (mockup-pending) Activities list, newest first.
 * Reactive — a new workout saved via [WorkoutViewModel] appears without a manual refresh.
 */
@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    activityDao: ActivityDao
) : ViewModel() {

    val activities: StateFlow<List<Activity>> = activityDao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
