package com.cosmiclaboratory.voyager.presentation.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.TimelinePlace
import com.cosmiclaboratory.voyager.domain.model.ids.PlaceId
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.utils.ProductionLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * Category Visibility Settings
 *
 * Controls which categories are visible on Map, Timeline, and whether
 * notifications are enabled for each category.
 *
 * Default: All categories VISIBLE until user customizes them.
 */
data class CategoryVisibility(
    val showOnMap: Boolean = true,
    val showOnTimeline: Boolean = true,
    val enableNotifications: Boolean = true
) {
    fun isCompletelyHidden(): Boolean {
        return !showOnMap && !showOnTimeline && !enableNotifications
    }

    fun isVisible(): Boolean {
        return showOnMap || showOnTimeline
    }
}

/**
 * Categories UI State
 */
data class CategoriesUiState(
    val categorySettings: Map<PlaceCategory, CategoryVisibility> = emptyMap(),
    val categoryPlaces: Map<PlaceCategory, List<TimelinePlace>> = emptyMap(),
    val allPlaces: List<TimelinePlace> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val visibleOnMapCount: Int = 0,
    val visibleOnTimelineCount: Int = 0,
    val notificationsEnabledCount: Int = 0,
    val showAssignDialog: Boolean = false,
    val selectedCategoryForAssignment: PlaceCategory? = null
)

/**
 * Categories ViewModel
 *
 * Manages per-category visibility settings. Visibility is persisted in DataStore
 * (see [persistVisibility]) — kept separate from [UserSettings] because it's a
 * dynamic per-category map rather than a fixed schema.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val logger: ProductionLogger,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        private val CATEGORY_VISIBILITY_KEY = stringPreferencesKey("category_visibility")
    }

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategorySettings()
        loadPlaces()
    }

    private fun loadCategorySettings() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Load persisted visibility from DataStore
                val prefs = dataStore.data.first()
                val savedJson = prefs[CATEGORY_VISIBILITY_KEY]
                val categoryMap = if (savedJson != null) {
                    deserializeVisibility(savedJson)
                } else {
                    // Default: all categories visible
                    PlaceCategory.values().associateWith {
                        CategoryVisibility(showOnMap = true, showOnTimeline = true, enableNotifications = true)
                    }
                }

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

    fun updateCategoryVisibility(category: PlaceCategory, visibility: CategoryVisibility) {
        viewModelScope.launch {
            try {
                logger.d(
                    "CategoriesViewModel",
                    "Updating ${category.displayName}: map=${visibility.showOnMap}, timeline=${visibility.showOnTimeline}, notify=${visibility.enableNotifications}"
                )

                val updatedMap = _uiState.value.categorySettings.toMutableMap()
                updatedMap[category] = visibility

                val visibleOnMap = updatedMap.count { it.value.showOnMap }
                val visibleOnTimeline = updatedMap.count { it.value.showOnTimeline }
                val notificationsEnabled = updatedMap.count { it.value.enableNotifications }

                _uiState.value = _uiState.value.copy(
                    categorySettings = updatedMap,
                    visibleOnMapCount = visibleOnMap,
                    visibleOnTimelineCount = visibleOnTimeline,
                    notificationsEnabledCount = notificationsEnabled
                )
                persistVisibility(updatedMap)
            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to update category visibility", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save: ${e.message}"
                )
            }
        }
    }

    fun showAllCategories() {
        viewModelScope.launch {
            try {
                logger.d("CategoriesViewModel", "Enabling all categories")

                val updatedMap = PlaceCategory.values().associateWith {
                    CategoryVisibility(showOnMap = true, showOnTimeline = true, enableNotifications = true)
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

    fun hideAllCategories() {
        viewModelScope.launch {
            try {
                logger.d("CategoriesViewModel", "Hiding all categories")

                val updatedMap = PlaceCategory.values().associateWith {
                    CategoryVisibility(showOnMap = false, showOnTimeline = false, enableNotifications = false)
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

    fun resetToDefaults() {
        showAllCategories()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun loadPlaces() {
        viewModelScope.launch {
            try {
                placeRepository.observePlaces().collect { places ->
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

    fun showAssignDialog(category: PlaceCategory) {
        _uiState.value = _uiState.value.copy(
            showAssignDialog = true,
            selectedCategoryForAssignment = category
        )
    }

    fun hideAssignDialog() {
        _uiState.value = _uiState.value.copy(
            showAssignDialog = false,
            selectedCategoryForAssignment = null
        )
    }

    fun assignPlaceToCategory(place: TimelinePlace, category: PlaceCategory) {
        viewModelScope.launch {
            try {
                logger.d(
                    "CategoriesViewModel",
                    "Assigning place '${place.displayName}' to category ${category.displayName}"
                )
                placeRepository.setPlaceCategory(PlaceId(place.placeId), category)
                logger.d("CategoriesViewModel", "Successfully assigned place to category")
            } catch (e: Exception) {
                logger.e("CategoriesViewModel", "Failed to assign place to category", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to assign place: ${e.message}"
                )
            }
        }
    }

    fun getAssignablePlaces(targetCategory: PlaceCategory): List<TimelinePlace> {
        return _uiState.value.allPlaces.filter { place ->
            place.category == PlaceCategory.UNKNOWN || place.category != targetCategory
        }.sortedBy {
            if (it.category == PlaceCategory.UNKNOWN) "0_${it.displayName}" else "1_${it.displayName}"
        }
    }

    private suspend fun persistVisibility(map: Map<PlaceCategory, CategoryVisibility>) {
        val json = serializeVisibility(map)
        dataStore.edit { prefs ->
            prefs[CATEGORY_VISIBILITY_KEY] = json
        }
    }

    private fun serializeVisibility(map: Map<PlaceCategory, CategoryVisibility>): String {
        val root = JSONObject()
        map.forEach { (category, vis) ->
            val obj = JSONObject()
            obj.put("map", vis.showOnMap)
            obj.put("timeline", vis.showOnTimeline)
            obj.put("notifications", vis.enableNotifications)
            root.put(category.name, obj)
        }
        return root.toString()
    }

    private fun deserializeVisibility(json: String): Map<PlaceCategory, CategoryVisibility> {
        val result = mutableMapOf<PlaceCategory, CategoryVisibility>()
        val root = JSONObject(json)
        PlaceCategory.values().forEach { category ->
            val obj = root.optJSONObject(category.name)
            result[category] = if (obj != null) {
                CategoryVisibility(
                    showOnMap = obj.optBoolean("map", true),
                    showOnTimeline = obj.optBoolean("timeline", true),
                    enableNotifications = obj.optBoolean("notifications", true)
                )
            } else {
                CategoryVisibility()
            }
        }
        return result
    }
}
