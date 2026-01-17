package com.cosmiclaboratory.voyager.presentation.state

import com.cosmiclaboratory.voyager.domain.model.Place
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedUiState - Cross-Screen UI State Management
 *
 * Provides shared state across multiple screens for coordinated UX.
 * Uses Hilt Singleton to ensure single instance across app.
 *
 * **Use Cases:**
 * 1. **Date Synchronization**: Map and Timeline share selected date
 *    - Change date on Map → Timeline updates automatically
 *    - Change date on Timeline → Map updates automatically
 *
 * 2. **Place Selection**: Navigate between screens with context
 *    - "View on Map" from Timeline → Map centers on place
 *    - Click place on Map → Can jump to Timeline with context
 *
 * **Injection:**
 * ```kotlin
 * @Composable
 * fun MyScreen(
 *     viewModel: MyViewModel,
 *     sharedUiState: SharedUiState = hiltViewModel() // Inject shared state
 * ) {
 *     val selectedDate by sharedUiState.selectedDate.collectAsState()
 *     // Use selectedDate...
 * }
 * ```
 *
 * **Scope**: Singleton (app-wide)
 */
@Singleton
class SharedUiState @Inject constructor() {

    // ========================================================================
    // DATE SELECTION STATE
    // ========================================================================

    /**
     * Selected date for Timeline and Map screens
     *
     * Default: Today
     * Updates: When user changes date selector on either screen
     * Observ

ers: Map and Timeline screens
     */
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * Update selected date
     *
     * Call this when:
     * - User taps date selector prev/next
     * - User selects specific date from date picker
     * - User clicks "Today" button
     *
     * All observing screens will update automatically.
     */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    /**
     * Select today's date
     *
     * Convenience method for "Today" button
     */
    fun selectToday() {
        _selectedDate.value = LocalDate.now()
    }

    /**
     * Navigate to previous day
     */
    fun selectPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    /**
     * Navigate to next day
     */
    fun selectNextDay() {
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    // ========================================================================
    // PLACE SELECTION STATE
    // ========================================================================

    /**
     * Currently selected place (for cross-screen navigation)
     *
     * Use cases:
     * - User clicks place marker on Map → Store here
     * - Navigate to Timeline → Timeline scrolls to this place
     * - User clicks "View on Map" from Timeline → Map centers on this place
     *
     * Nullable: null when no place selected
     */
    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace.asStateFlow()

    /**
     * Select a place
     *
     * Call this when:
     * - User clicks place marker on Map
     * - User clicks "View on Map" from Timeline
     * - User navigates from place details
     */
    fun selectPlace(place: Place?) {
        _selectedPlace.value = place
    }

    /**
     * Clear place selection
     *
     * Call this when:
     * - User navigates away from place context
     * - User closes place details sheet
     */
    fun clearPlaceSelection() {
        _selectedPlace.value = null
    }

    // ========================================================================
    // MAP-SPECIFIC PLACE SELECTION (FOR "VIEW ON MAP" NAVIGATION)
    // ========================================================================

    /**
     * Place selected for viewing on map (consumed once)
     *
     * Use case: Timeline "View on Map" button
     * - Timeline sets this when user clicks "View on Map"
     * - Map observes and consumes it (centers on place, then clears)
     * - One-time consumption pattern prevents re-triggering
     *
     * Nullable: null when no pending map navigation
     */
    private val _selectedPlaceForMap = MutableStateFlow<Place?>(null)
    val selectedPlaceForMap: StateFlow<Place?> = _selectedPlaceForMap.asStateFlow()

    /**
     * Select a place to view on map
     *
     * Call this when:
     * - User clicks "View on Map" from Timeline
     * - User clicks "View on Map" from place details
     *
     * Map will automatically consume this and center on the place
     */
    fun selectPlaceForMap(place: Place?) {
        _selectedPlaceForMap.value = place
    }

    // ========================================================================
    // NAVIGATION CONTEXT
    // ========================================================================

    /**
     * Navigation source tracking
     *
     * Tracks which screen initiated navigation to provide context.
     * Example: "Came from Timeline" → Map centers on selected place
     *
     * Nullable: null when no navigation context
     */
    private val _navigationSource = MutableStateFlow<NavigationSource?>(null)
    val navigationSource: StateFlow<NavigationSource?> = _navigationSource.asStateFlow()

    /**
     * Set navigation source
     *
     * Call before navigating to provide context to destination screen.
     */
    fun setNavigationSource(source: NavigationSource) {
        _navigationSource.value = source
    }

    /**
     * Clear navigation source
     *
     * Call after consuming navigation context.
     */
    fun clearNavigationSource() {
        _navigationSource.value = null
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Reset all shared state to defaults
     *
     * Use when:
     * - User logs out
     * - App needs to reset to clean state
     */
    fun reset() {
        _selectedDate.value = LocalDate.now()
        _selectedPlace.value = null
        _navigationSource.value = null
    }

    /**
     * Check if selected date is today
     */
    fun isSelectedDateToday(): Boolean {
        return _selectedDate.value == LocalDate.now()
    }

    /**
     * Check if selected date is in the past
     */
    fun isSelectedDateInPast(): Boolean {
        return _selectedDate.value.isBefore(LocalDate.now())
    }

    /**
     * Check if selected date is in the future
     */
    fun isSelectedDateInFuture(): Boolean {
        return _selectedDate.value.isAfter(LocalDate.now())
    }
}

/**
 * NavigationSource - Tracks origin of navigation
 *
 * Use to provide context when navigating between screens.
 */
enum class NavigationSource {
    /**
     * User came from Timeline screen
     *
     * Context: Might have selected a place to view on map
     */
    TIMELINE,

    /**
     * User came from Map screen
     *
     * Context: Might have selected a place to view in timeline
     */
    MAP,

    /**
     * User came from Dashboard screen
     *
     * Context: Likely exploring or checking status
     */
    DASHBOARD,

    /**
     * User came from Place Review screen
     *
     * Context: Might want to see place on map or timeline
     */
    PLACE_REVIEW,

    /**
     * User came from Insights/Analytics screen
     *
     * Context: Exploring patterns or statistics
     */
    INSIGHTS,

    /**
     * User came from Categories screen
     *
     * Context: Managing category visibility
     */
    CATEGORIES,

    /**
     * Direct navigation (no context)
     *
     * Default: No special handling needed
     */
    DIRECT
}
