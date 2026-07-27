package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Achievement
import com.cosmiclaboratory.voyager.domain.model.Activity
import com.cosmiclaboratory.voyager.domain.model.DistanceUnit
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.Split
import com.cosmiclaboratory.voyager.domain.usecase.PersonalRecordsUseCase
import com.cosmiclaboratory.voyager.domain.usecase.WorkoutStatsCalculator
import com.cosmiclaboratory.voyager.domain.usecase.toDomain
import com.cosmiclaboratory.voyager.domain.util.LocationUtils
import com.cosmiclaboratory.voyager.platform.export.ActivityGpxExporter
import com.cosmiclaboratory.voyager.storage.database.dao.ActivityDao
import com.cosmiclaboratory.voyager.storage.database.dao.WorkoutSegmentDao
import com.cosmiclaboratory.voyager.storage.database.entity.WorkoutSegmentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for a single recorded activity's detail view. */
data class ActivityDetailState(
    val activity: Activity? = null,
    val route: List<RoutePoint> = emptyList(),
    val splits: List<Split> = emptyList(),
    /** (cumulative-distance-metres, altitude-metres) for the elevation profile; empty if no altitude. */
    val elevationProfile: List<Pair<Double, Double>> = emptyList(),
    /** Records this activity holds vs the rest of the library. */
    val achievements: List<Achievement> = emptyList(),
    val loading: Boolean = true,
    val deleted: Boolean = false,
)

/**
 * Loads one recorded [Activity], reconstructs its full route (lat/lng + time + altitude) from the
 * persisted streams, and derives the splits + elevation profile for the detail screen. Also owns
 * the edit-title/notes, delete, and GPX-export actions.
 */
@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    private val activityDao: ActivityDao,
    private val personalRecords: PersonalRecordsUseCase,
    private val workoutSegmentDao: WorkoutSegmentDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val activityId: Long = savedStateHandle["activityId"] ?: -1L

    private val _state = MutableStateFlow(ActivityDetailState())
    val state: StateFlow<ActivityDetailState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val entity = activityDao.getById(activityId)
            if (entity == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val activity = entity.toDomain()
            val route = WorkoutStatsCalculator.reconstruct(
                activity.encodedPolyline, activity.encodedTimes, activity.encodedAltitudes, activity.startedAt
            )
            val all = activityDao.getAll().map { it.toDomain() }
            _state.value = ActivityDetailState(
                activity = activity,
                route = route,
                splits = WorkoutStatsCalculator.splits(route, DistanceUnit.KM),
                elevationProfile = elevationProfile(route),
                achievements = personalRecords.newAchievements(activity, all),
                loading = false,
            )
        }
    }

    /** Cumulative-distance vs altitude, for the profile chart. Empty when no altitude was captured. */
    private fun elevationProfile(route: List<RoutePoint>): List<Pair<Double, Double>> {
        if (route.none { it.altitudeM != null }) return emptyList()
        val out = ArrayList<Pair<Double, Double>>(route.size)
        var cumulative = 0.0
        var prev: RoutePoint? = null
        for (p in route) {
            prev?.let { cumulative += LocationUtils.calculateDistance(it.lat, it.lng, p.lat, p.lng) }
            prev = p
            p.altitudeM?.let { out.add(cumulative to it) }
        }
        return out
    }

    /** GPX 1.1 (with elevation + timestamps) for sharing; null before the activity has loaded. */
    fun gpx(): String? = _state.value.activity?.let { ActivityGpxExporter.toGpx(it) }

    fun updateUserFields(title: String?, notes: String?) {
        viewModelScope.launch {
            activityDao.updateUserFields(activityId, title?.takeIf { it.isNotBlank() }, notes?.takeIf { it.isNotBlank() }, System.currentTimeMillis())
            load()
        }
    }

    fun delete() {
        viewModelScope.launch {
            activityDao.softDelete(activityId, System.currentTimeMillis())
            _state.update { it.copy(deleted = true) }
        }
    }

    /** Save this activity's route as a reusable "race yourself" segment. */
    fun saveAsSegment(name: String) {
        val activity = _state.value.activity ?: return
        viewModelScope.launch {
            workoutSegmentDao.insert(
                WorkoutSegmentEntity(
                    name = name.ifBlank { activity.displayTitle },
                    encodedPolyline = activity.encodedPolyline,
                    distanceMeters = activity.distanceMeters,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }
}
