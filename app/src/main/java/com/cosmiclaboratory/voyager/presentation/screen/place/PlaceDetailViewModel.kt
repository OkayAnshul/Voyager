package com.cosmiclaboratory.voyager.presentation.screen.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.AnalyticsRepository
import com.cosmiclaboratory.voyager.domain.repository.EvidenceRepository
import com.cosmiclaboratory.voyager.domain.repository.GeocodingRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val place: TimelinePlace? = null,
    val analytics: PlaceAnalytics? = null,
    val evidence: ConfidenceBlock? = null,
    val geocodeCandidates: List<GeocodeCandidate> = emptyList(),
    val isLoading: Boolean = true
)

sealed interface PlaceDetailIntent {
    data class Rename(val name: String) : PlaceDetailIntent
    data class SetCategory(val category: PlaceCategory) : PlaceDetailIntent
    data class SetEmoji(val emoji: String?) : PlaceDetailIntent
    data object Confirm : PlaceDetailIntent
    data class MergeWith(val targetPlaceId: Long) : PlaceDetailIntent
    data object RefreshGeocode : PlaceDetailIntent
}

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val placeRepository: PlaceRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val evidenceRepository: EvidenceRepository,
    private val geocodingRepository: GeocodingRepository
) : ViewModel() {

    private val placeId: Long = savedStateHandle["placeId"] ?: -1L

    private val _uiState = MutableStateFlow(PlaceDetailUiState())
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    init {
        if (placeId > 0) {
            viewModelScope.launch {
                placeRepository.observePlace(placeId).collect { place ->
                    _uiState.update { it.copy(place = place, isLoading = false) }
                }
            }
            viewModelScope.launch {
                val analytics = analyticsRepository.getPlaceAnalytics(placeId)
                val evidence = evidenceRepository.getPlaceEvidence(placeId)
                val candidates = geocodingRepository.getCandidatesForPlace(placeId)
                _uiState.update {
                    it.copy(analytics = analytics, evidence = evidence, geocodeCandidates = candidates)
                }
            }
        }
    }

    fun onIntent(intent: PlaceDetailIntent) {
        viewModelScope.launch {
            when (intent) {
                is PlaceDetailIntent.Rename -> placeRepository.renamePlace(placeId, intent.name)
                is PlaceDetailIntent.SetCategory -> placeRepository.setPlaceCategory(placeId, intent.category)
                is PlaceDetailIntent.SetEmoji -> placeRepository.setPlaceEmoji(placeId, intent.emoji)
                is PlaceDetailIntent.Confirm -> placeRepository.confirmPlace(placeId)
                is PlaceDetailIntent.MergeWith -> placeRepository.mergePlaces(
                    sourceIds = listOf(placeId), targetId = intent.targetPlaceId
                )
                is PlaceDetailIntent.RefreshGeocode -> {
                    geocodingRepository.refreshGeocodeForPlace(placeId)
                    val candidates = geocodingRepository.getCandidatesForPlace(placeId)
                    _uiState.update { it.copy(geocodeCandidates = candidates) }
                }
            }
        }
    }
}
