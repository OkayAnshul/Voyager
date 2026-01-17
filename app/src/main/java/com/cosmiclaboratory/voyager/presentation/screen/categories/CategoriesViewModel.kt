package com.cosmiclaboratory.voyager.presentation.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Category Visibility Settings
 *
 * Controls which categories are visible on Map, Timeline, and whether
 * notifications are enabled for each category.
 *
 * Default: All categories HIDDEN until user enables them.
 */
data class CategoryVisibility(
    val showOnMap: Boolean = false,
    val showOnTimeline: Boolean = false,
    val enableNotifications: Boolean = false
) {
    /**
     * Check if category is completely hidden (not shown anywhere)
     */
    fun isCompletelyHidden(): Boolean {
        return !showOnMap && !showOnTimeline && !enableNotifications
    }

    /**
     * Check if category is visible anywhere
     */
    fun isVisible(): Boolean {
        return showOnMap || showOnTimeline
    }
}

/**
 * Categories UI State
 */
data class CategoriesUiState(
    val categorySettings: Map<PlaceCategory, CategoryVisibility> = emptyMap(),
    val categoryPlaces: Map<PlaceCategory, List<Place>> = emptyMap(),
    val allPlaces: List<Place> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    // Stats
    val visibleOnMapCount: Int = 0,
    val visibleOnTimelineCount: Int = 0,
    val notificationsEnabledCount: Int = 0,
    // Assignment dialog state
    val showAssignDialog: Boolean = false,
    val selectedCategoryForAssignment: PlaceCategory? = null
)

/**
 * Categories ViewModel
 *
 * Manages per-category visibility settings.
 * Settings are persisted in UserPreferences.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val placeRepository: PlaceRepository,
    private val logger: ProductionLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategorySettings()
        loadPlaces()
    }

    /**
     * Load category settings from preferences
     */
    private fun loadCategorySettings() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Load preferences
                val preferences = preferencesRepository.getCurrentPreferences()

                // Get category visibility map (all visible by default if not set)
                val categoryMap = PlaceCategory.values().associateWith { category ->
                    // Check if settings exist for this category
                    // For now, default to visible until we add persistence
                    CategoryVisibility(
                        showOnMap = true,
                        showOnTimeline = true,
                        enableNotifications = true
                    )
                }

                // Calculate stats
                val visibleOnMap = categoryMap.count { it.value.showOnMap }
                val visibleOnTimeline = categoryMap.count { it.value.showOnTimeline }
                val notificationsEnabled = categoryMap.count { it.value.enableNotifications }

                _uiState.value = _uiState.value.copy(
                    categorySettings = categoryMap,
                    isLoading = false,
                    visibleOnMapCount = visibleOnMap,
                    visibleOnTimelineCount = visibleOnTimeline,
                    notificationsEnabledCount = notificationsEnabled
                )

                logger.d("CategoriesViewModel", "Loaded settings for ${categoryMap.size} categories")

            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to load category settings", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load settings: ${e.message}"
                )
            }
        }
    }

    /**
     * Update visibility for a specific category
     */
    fun updateCategoryVisibility(category: PlaceCategory, visibility: CategoryVisibility) {
        viewModelScope.launch {
            try {
                logger.d(
                    "CategoriesViewModel",
                    "Updating ${category.displayName}: map=${visibility.showOnMap}, timeline=${visibility.showOnTimeline}, notify=${visibility.enableNotifications}"
                )

                // Update in-memory state
                val updatedMap = _uiState.value.categorySettings.toMutableMap()
                updatedMap[category] = visibility

                // Recalculate stats
                val visibleOnMap = updatedMap.count { it.value.showOnMap }
                val visibleOnTimeline = updatedMap.count { it.value.showOnTimeline }
                val notificationsEnabled = updatedMap.count { it.value.enableNotifications }

                _uiState.value = _uiState.value.copy(
                    categorySettings = updatedMap,
                    visibleOnMapCount = visibleOnMap,
                    visibleOnTimelineCount = visibleOnTimeline,
                    notificationsEnabledCount = notificationsEnabled
                )

                // TODO: Persist to PreferencesRepository
                // This will require adding categoryVisibilitySettings to UserPreferences
                // For now, changes are in-memory only and will reset on app restart

            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to update category visibility", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save: ${e.message}"
                )
            }
        }
    }

    /**
     * Show all categories everywhere (map, timeline, notifications)
     */
    fun showAllCategories() {
        viewModelScope.launch {
            try {
                logger.d("CategoriesViewModel", "Enabling all categories")

                val updatedMap = PlaceCategory.values().associateWith {
                    CategoryVisibility(
                        showOnMap = true,
                        showOnTimeline = true,
                        enableNotifications = true
                    )
                }

                _uiState.value = _uiState.value.copy(
                    categorySettings = updatedMap,
                    visibleOnMapCount = updatedMap.size,
                    visibleOnTimelineCount = updatedMap.size,
                    notificationsEnabledCount = updatedMap.size
                )

            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to show all categories", e)
            }
        }
    }

    /**
     * Hide all categories everywhere
     */
    fun hideAllCategories() {
        viewModelScope.launch {
            try {
                logger.d("CategoriesViewModel", "Hiding all categories")

                val updatedMap = PlaceCategory.values().associateWith {
                    CategoryVisibility(
                        showOnMap = false,
                        showOnTimeline = false,
                        enableNotifications = false
                    )
                }

                _uiState.value = _uiState.value.copy(
                    categorySettings = updatedMap,
                    visibleOnMapCount = 0,
                    visibleOnTimelineCount = 0,
                    notificationsEnabledCount = 0
                )

            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to hide all categories", e)
            }
        }
    }

    /**
     * Reset to defaults (all hidden)
     */
    fun resetToDefaults() {
        hideAllCategories()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Load all places and group by category
     */
    private fun loadPlaces() {
        viewModelScope.launch {
            try {
                placeRepository.getAllPlaces().collect { places ->
                    // Group places by category
                    val categoryPlaces = places.groupBy { it.category }

                    _uiState.value = _uiState.value.copy(
                        allPlaces = places,
                        categoryPlaces = categoryPlaces
                    )

                    logger.d("CategoriesViewModel", "Loaded ${places.size} places")
                }
            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to load places", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load places: ${e.message}"
                )
            }
        }
    }

    /**
     * Show dialog to assign places to a category
     */
    fun showAssignDialog(category: PlaceCategory) {
        _uiState.value = _uiState.value.copy(
            showAssignDialog = true,
            selectedCategoryForAssignment = category
        )
    }

    /**
     * Hide assignment dialog
     */
    fun hideAssignDialog() {
        _uiState.value = _uiState.value.copy(
            showAssignDialog = false,
            selectedCategoryForAssignment = null
        )
    }

    /**
     * Assign a place to a category
     */
    fun assignPlaceToCategory(place: Place, category: PlaceCategory) {
        viewModelScope.launch {
            try {
                logger.d(
                    "CategoriesViewModel",
                    "Assigning place '${place.name}' to category ${category.displayName}"
                )

                val updatedPlace = place.copy(category = category)
                placeRepository.updatePlace(updatedPlace)

                logger.d("CategoriesViewModel", "Successfully assigned place to category")

            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to assign place to category", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to assign place: ${e.message}"
                )
            }
        }
    }

    /**
     * Get places that can be assigned (either UNKNOWN or from other categories)
     */
    fun getAssignablePlaces(targetCategory: PlaceCategory): List<Place> {
        return _uiState.value.allPlaces.filter { place ->
            // Show UNKNOWN places first, then places from other categories
            place.category == PlaceCategory.UNKNOWN || place.category != targetCategory
        }.sortedBy {
            // Sort: UNKNOWN first, then by name
            if (it.category == PlaceCategory.UNKNOWN) "0_${it.name}" else "1_${it.name}"
        }
    }
}
