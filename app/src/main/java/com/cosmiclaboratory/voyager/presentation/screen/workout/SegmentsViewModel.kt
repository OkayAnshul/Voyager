package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.SegmentWithEfforts
import com.cosmiclaboratory.voyager.domain.model.WorkoutSegment
import com.cosmiclaboratory.voyager.domain.usecase.SegmentMatchUseCase
import com.cosmiclaboratory.voyager.domain.usecase.toDomain
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.dao.WorkoutSegmentDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lists saved "race-yourself" segments, each with the efforts found across all recorded activities
 * (fastest first). All matching runs on-device — no leaderboard, no other users.
 */
@HiltViewModel
class SegmentsViewModel @Inject constructor(
    private val workoutSegmentDao: WorkoutSegmentDao,
    activityDao: ActivityDao,
    segmentMatch: SegmentMatchUseCase,
) : ViewModel() {

    val segments: StateFlow<List<SegmentWithEfforts>> = combine(
        workoutSegmentDao.observeAll(),
        activityDao.observeAll(),
    ) { segs, rows ->
        val activities = rows.map { it.toDomain() }
        segs.map { s ->
            val segment = WorkoutSegment(s.segmentId, s.name, s.encodedPolyline, s.distanceMeters, s.createdAt)
            SegmentWithEfforts(segment, segmentMatch.effortsFor(segment, activities))
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(segmentId: Long) {
        viewModelScope.launch { workoutSegmentDao.softDelete(segmentId, System.currentTimeMillis()) }
    }
}
