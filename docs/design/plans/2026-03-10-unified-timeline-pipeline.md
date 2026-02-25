# Unified Timeline Pipeline Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the dual timeline pipeline with a single canonical `TrackingStateSegment` pipeline that drives Timeline, Map, and Insights — with confidence-ranked naming, travel mode inference, and quality metrics.

**Architecture:** Eliminate the legacy `TimelineSegment` path entirely. `MovementSegmentationUseCase` becomes the single source of truth. Add a `PlaceNameResolver` scoring module. Map consumes the same segment list as Timeline. Quality metrics are computed per-render and exposed via a diagnostics surface.

**Tech Stack:** Kotlin, Room, Hilt, Jetpack Compose, MockK, JUnit 4, coroutines, OSM/Nominatim/Overpass

---

## Phase 1: Naming Resolver (Confidence-Ranked)

### Task 1: Create `PlaceNameResolver` with scoring logic

**Files:**
- Create: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/PlaceNameResolver.kt`
- Create: `app/src/test/java/com/cosmiclaboratory/voyager/domain/PlaceNameResolverTest.kt`

**Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/cosmiclaboratory/voyager/domain/PlaceNameResolverTest.kt
package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.usecase.PlaceNameResolver
import com.cosmiclaboratory.voyager.domain.usecase.ResolvedName
import com.cosmiclaboratory.voyager.domain.usecase.NameSource
import com.cosmiclaboratory.voyager.fixtures.TestDataFactory
import org.junit.Assert.*
import org.junit.Test

class PlaceNameResolverTest {

    private val resolver = PlaceNameResolver()

    @Test
    fun `user-renamed place always wins`() {
        val place = TestDataFactory.createPlace(
            name = "My Favorite Cafe",
            address = "123 Main St"
        ).copy(
            isUserRenamed = true,
            osmSuggestedName = "Starbucks"
        )
        val result = resolver.resolve(place)
        assertEquals("My Favorite Cafe", result.displayName)
        assertEquals(NameSource.USER_CUSTOM, result.source)
        assertTrue(result.confidence >= 0.99)
    }

    @Test
    fun `strong OSM POI name beats street address`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = "Starbucks Coffee",
            streetName = "Market St",
            address = "456 Market St, San Francisco"
        )
        val result = resolver.resolve(place)
        assertEquals("Starbucks Coffee", result.displayName)
        assertEquals(NameSource.OSM_POI, result.source)
    }

    @Test
    fun `structured address used when no POI`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = null,
            streetName = "Market St",
            locality = "San Francisco"
        )
        val result = resolver.resolve(place)
        assertEquals("Market St, San Francisco", result.displayName)
        assertEquals(NameSource.STRUCTURED_ADDRESS, result.source)
    }

    @Test
    fun `street-only name when no locality`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = null,
            streetName = "Oak Avenue",
            locality = null,
            subLocality = null
        )
        val result = resolver.resolve(place)
        assertEquals("Oak Avenue", result.displayName)
        assertEquals(NameSource.STRUCTURED_ADDRESS, result.source)
    }

    @Test
    fun `area or subLocality used as fallback`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = null,
            streetName = null,
            subLocality = "Downtown",
            locality = "San Francisco"
        )
        val result = resolver.resolve(place)
        assertEquals("Downtown, San Francisco", result.displayName)
        assertEquals(NameSource.AREA, result.source)
    }

    @Test
    fun `coordinates used as last resort`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = null,
            streetName = null,
            locality = null,
            subLocality = null,
            address = null
        )
        val result = resolver.resolve(place)
        assertTrue(result.displayName.contains(place.latitude.toString().take(7)))
        assertEquals(NameSource.COORDINATES, result.source)
        assertTrue(result.confidence < 0.3)
    }

    @Test
    fun `generic OSM names are rejected`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = "Building",
            streetName = "Elm St"
        )
        val result = resolver.resolve(place)
        // Should skip "Building" and fall to street name
        assertNotEquals("Building", result.displayName)
        assertEquals(NameSource.STRUCTURED_ADDRESS, result.source)
    }

    @Test
    fun `numeric house numbers are rejected as names`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = "42B",
            streetName = "Elm St"
        )
        val result = resolver.resolve(place)
        assertNotEquals("42B", result.displayName)
    }

    @Test
    fun `never returns Unknown Place when any candidate exists`() {
        val place = TestDataFactory.createPlace(
            name = "Unknown Place",
            category = PlaceCategory.UNKNOWN
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = null,
            streetName = "Any Street"
        )
        val result = resolver.resolve(place)
        assertFalse(
            "Should never show 'Unknown Place' when street name exists",
            result.displayName.contains("Unknown", ignoreCase = true)
        )
    }

    @Test
    fun `subtitle is populated with address when name is POI`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = "Starbucks Coffee",
            streetName = "Market St",
            locality = "San Francisco"
        )
        val result = resolver.resolve(place)
        assertEquals("Starbucks Coffee", result.displayName)
        assertEquals("Market St, San Francisco", result.subtitle)
    }

    @Test
    fun `subtitle is null when name IS the address`() {
        val place = TestDataFactory.createPlace(
            name = "auto-generated"
        ).copy(
            isUserRenamed = false,
            osmSuggestedName = null,
            streetName = "Market St",
            locality = "San Francisco"
        )
        val result = resolver.resolve(place)
        assertNull(result.subtitle)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cosmiclaboratory.voyager.domain.PlaceNameResolverTest" --info`
Expected: FAIL — classes `PlaceNameResolver`, `ResolvedName`, `NameSource` don't exist yet.

**Step 3: Write minimal implementation**

```kotlin
// app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/PlaceNameResolver.kt
package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Place
import javax.inject.Inject
import javax.inject.Singleton

enum class NameSource {
    USER_CUSTOM,
    OSM_POI,
    STRUCTURED_ADDRESS,
    AREA,
    COORDINATES
}

data class ResolvedName(
    val displayName: String,
    val subtitle: String? = null,
    val source: NameSource,
    val confidence: Double
)

@Singleton
class PlaceNameResolver @Inject constructor() {

    fun resolve(place: Place): ResolvedName {
        // Priority 1: User custom name
        if (place.isUserRenamed && place.name.isNotBlank()) {
            val subtitle = buildSubtitle(place)
            return ResolvedName(
                displayName = place.name,
                subtitle = subtitle,
                source = NameSource.USER_CUSTOM,
                confidence = 1.0
            )
        }

        // Priority 2: Strong OSM POI name
        val osmName = place.osmSuggestedName
        if (!osmName.isNullOrBlank() && !isGenericName(osmName) && !isNumericOrHouseNumber(osmName)) {
            val subtitle = buildSubtitle(place)
            return ResolvedName(
                displayName = osmName,
                subtitle = subtitle,
                source = NameSource.OSM_POI,
                confidence = 0.9
            )
        }

        // Priority 3: Structured address (street + locality)
        val streetName = place.streetName?.takeIf { it.isNotBlank() && !isNumericOrHouseNumber(it) }
        val locality = place.locality?.takeIf { it.isNotBlank() }
        val subLocality = place.subLocality?.takeIf { it.isNotBlank() }

        if (streetName != null) {
            val displayName = listOfNotNull(streetName, locality).joinToString(", ")
            return ResolvedName(
                displayName = displayName,
                subtitle = null, // name IS the address, no subtitle needed
                source = NameSource.STRUCTURED_ADDRESS,
                confidence = 0.7
            )
        }

        // Priority 4: Area / intersection (subLocality + locality)
        if (subLocality != null || locality != null) {
            val displayName = listOfNotNull(subLocality, locality).joinToString(", ")
            return ResolvedName(
                displayName = displayName,
                subtitle = null,
                source = NameSource.AREA,
                confidence = 0.5
            )
        }

        // Priority 5: Coordinates fallback
        return ResolvedName(
            displayName = "${String.format("%.4f", place.latitude)}, ${String.format("%.4f", place.longitude)}",
            subtitle = null,
            source = NameSource.COORDINATES,
            confidence = 0.1
        )
    }

    private fun buildSubtitle(place: Place): String? {
        val parts = listOfNotNull(
            place.streetName?.takeIf { it.isNotBlank() && !isNumericOrHouseNumber(it) },
            place.locality?.takeIf { it.isNotBlank() }
        )
        return if (parts.isNotEmpty()) parts.joinToString(", ") else null
    }

    private fun isGenericName(name: String): Boolean {
        val lower = name.lowercase()
        val generic = setOf("building", "place", "location", "unnamed", "null", "untitled", "unknown")
        return generic.any { lower.contains(it) }
    }

    private fun isNumericOrHouseNumber(name: String): Boolean {
        if (name.all { it.isDigit() }) return true
        if (name.matches(Regex("^\\d+[A-Za-z]?$"))) return true
        if (name.matches(Regex("^\\d+[/\\-]\\d*[A-Za-z]?$"))) return true
        return false
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cosmiclaboratory.voyager.domain.PlaceNameResolverTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/PlaceNameResolver.kt \
       app/src/test/java/com/cosmiclaboratory/voyager/domain/PlaceNameResolverTest.kt
git commit -m "feat: add PlaceNameResolver with confidence-ranked naming"
```

---

### Task 2: Wire `PlaceNameResolver` into `EnrichPlaceWithDetailsUseCase`

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/EnrichPlaceWithDetailsUseCase.kt`
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/di/UseCasesModule.kt` (if needed for DI)

**Step 1: Update `EnrichPlaceWithDetailsUseCase` to use `PlaceNameResolver`**

Replace the `generateSmartName()` method body with a delegation to `PlaceNameResolver.resolve()`. Keep the method signature for backward compatibility but delegate internally.

```kotlin
// In EnrichPlaceWithDetailsUseCase constructor, add:
private val nameResolver: PlaceNameResolver

// Replace generateSmartName() body:
private fun generateSmartName(
    place: Place,
    address: AddressResult?,
    details: PlaceDetails?
): String {
    // Build a temporary Place with all the enrichment data applied
    val enrichedPlace = place.copy(
        osmSuggestedName = details?.name?.takeIf { it.isNotBlank() && it != "null" },
        streetName = address?.streetName,
        locality = address?.locality,
        subLocality = address?.subLocality
    )
    return nameResolver.resolve(enrichedPlace).displayName
}
```

**Step 2: Add `PlaceNameResolver` to constructor injection**

`PlaceNameResolver` has `@Inject constructor()` so Hilt will auto-provide it. Just add it to `EnrichPlaceWithDetailsUseCase`'s constructor:

```kotlin
@Singleton
class EnrichPlaceWithDetailsUseCase @Inject constructor(
    private val geocodingRepository: GeocodingRepository,
    private val nameResolver: PlaceNameResolver
)
```

**Step 3: Run existing tests**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: PASS (no tests directly test `EnrichPlaceWithDetailsUseCase` currently)

**Step 4: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/EnrichPlaceWithDetailsUseCase.kt
git commit -m "refactor: delegate place naming to PlaceNameResolver"
```

---

### Task 3: Wire `PlaceNameResolver` into `TrackingStateSegment.formatSummary()` and `MovementSegmentationUseCase`

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/MovementSegmentationUseCase.kt`
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TrackingStateSegment.kt`

**Step 1: Add `resolvedName` field to `TrackingStateSegment`**

Add to `TrackingStateSegment`:
```kotlin
/** Resolved display name + subtitle from PlaceNameResolver */
val resolvedName: ResolvedName? = null,
```

**Step 2: Update `MovementSegmentationUseCase` to inject `PlaceNameResolver` and populate `resolvedName`**

Add `PlaceNameResolver` to constructor. In the visit segment creation (line ~101), resolve the name:

```kotlin
// In the visitSegments mapping:
val visitSegments = visits.map { visit ->
    val place = placeMap[visit.placeId]
    val exitTime = visit.exitTime ?: rangeEnd
    val resolved = place?.let { nameResolver.resolve(it) }
    TrackingStateSegment(
        type = SegmentType.CONFIRMED_PLACE_VISIT,
        timeRange = TimeRange(visit.entryTime, exitTime),
        place = place,
        visits = listOf(visit),
        addressLabel = resolved?.subtitle ?: place?.address ?: place?.name,
        resolvedName = resolved,
        confidence = 1.0
    )
}
```

**Step 3: Update `formatSummary()` to use `resolvedName`**

```kotlin
SegmentType.CONFIRMED_PLACE_VISIT -> {
    val name = resolvedName?.displayName ?: place?.name ?: "Unknown place"
    "$name - ${formatDuration()}"
}
```

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cosmiclaboratory.voyager.domain.MovementSegmentationTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TrackingStateSegment.kt \
       app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/MovementSegmentationUseCase.kt
git commit -m "feat: integrate PlaceNameResolver into segment pipeline"
```

---

## Phase 2: Eliminate Legacy Timeline Pipeline

### Task 4: Remove legacy `TimelineSegment` from `TimelineViewModel` and UI

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineViewModel.kt`
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineScreen.kt`

**Step 1: Remove legacy fields from `TimelineUiState`**

Remove `timelineSegments`, `visibleSegments` from `TimelineUiState`. The `movementSegments` field becomes the sole segment list:

```kotlin
data class TimelineUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val segments: List<TrackingStateSegment> = emptyList(),
    val dayAnalytics: DayAnalytics? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentPlace: Place? = null,
    val currentVisitDuration: Long = 0L,
    val isTracking: Boolean = false,
    val categorySettings: Map<PlaceCategory, CategoryVisibility> = emptyMap()
)
```

**Step 2: Remove legacy `TimelineLoadResult` and legacy pipeline call**

In `loadTimelineForDate()`:
- Remove the `generateTimelineSegmentsUseCase(date)` call (legacy path)
- Remove `placeRepository.getAllPlaces()` call (unbounded query)
- Keep only `generateTimelineSegmentsUseCase.generateMovementTimeline(date)`
- Apply category filtering to `movementSegments` instead of legacy segments

```kotlin
private fun loadTimelineForDate(date: LocalDate) {
    viewModelScope.launch {
        try {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val categorySettings = loadCategorySettings()

                // Generate unified movement segments (single pipeline)
                val segments = try {
                    generateTimelineSegmentsUseCase.generateMovementTimeline(date)
                } catch (e: Exception) {
                    android.util.Log.e("TimelineViewModel", "Failed to generate segments", e)
                    emptyList()
                }

                // Filter by category visibility
                val visibleSegments = segments.filter { segment ->
                    val place = segment.place ?: return@filter true // non-place segments always visible
                    val visibility = categorySettings[place.category] ?: CategoryVisibility()
                    visibility.showOnTimeline
                }

                // Analytics
                val cacheKey = CacheKey(date, categorySettings.hashCode())
                val currentTime = System.currentTimeMillis()
                val dayAnalytics = if (analyticsCache.containsKey(cacheKey) &&
                    cacheTimestamps[cacheKey]?.let { (currentTime - it) < cacheTimeoutMs } == true) {
                    analyticsCache[cacheKey]!!
                } else {
                    val analytics = analyticsUseCases.generateDayAnalytics(date)
                    analyticsCache[cacheKey] = analytics
                    cacheTimestamps[cacheKey] = currentTime
                    analytics
                }

                Triple(visibleSegments, categorySettings, dayAnalytics)
            }

            _uiState.value = _uiState.value.copy(
                segments = result.first,
                categorySettings = result.second,
                dayAnalytics = result.third,
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Failed to load timeline: ${e.message}"
            )
        }
    }
}
```

**Step 3: Update `TimelineScreen` to use `uiState.segments` only**

- Replace `uiState.visibleSegments.isEmpty() && uiState.movementSegments.isEmpty()` with `uiState.segments.isEmpty()`
- Remove the legacy fallback branch (`else { items(uiState.visibleSegments) ... }`)
- Remove the category filter info card that compares `visibleSegments.size` vs `timelineSegments.size`
- Use `uiState.segments` directly:

```kotlin
items(uiState.segments) { segment ->
    MovementSegmentCard(
        segment = segment,
        onLongPress = { place -> placeToRename = place },
        onViewOnMap = { place ->
            sharedUiState.selectPlaceForMap(place)
            navController?.navigateToTab(VoyagerDestination.Map.route)
        }
    )
}
```

**Step 4: Remove `filterSegmentsByCategory()` method (legacy, no longer used)**

**Step 5: Remove unused imports for `TimelineSegment` in `TimelineScreen.kt`**

**Step 6: Run build**

Run: `./gradlew :app:assembleDebug --info`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineViewModel.kt \
       app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineScreen.kt
git commit -m "refactor: remove legacy TimelineSegment pipeline from Timeline UI"
```

---

### Task 5: Remove legacy `invoke(date)` from `GenerateTimelineSegmentsUseCase`

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/GenerateTimelineSegmentsUseCase.kt`
- Modify: `app/src/test/java/com/cosmiclaboratory/voyager/domain/GenerateTimelineSegmentsTest.kt`

**Step 1: Delete legacy methods from `GenerateTimelineSegmentsUseCase`**

Remove:
- `operator fun invoke(date: LocalDate): List<TimelineSegment>` (lines 71-140)
- `fillGaps()` (lines 142-197)
- `groupVisitsByProximity()` (lines 202-231)
- Remove imports for `TimelineSegment`, `LocationUtils`

Keep only `generateMovementTimeline(date)`.

**Step 2: Update tests in `GenerateTimelineSegmentsTest.kt`**

Remove any tests that call the legacy `invoke()` method. Update remaining tests to use `generateMovementTimeline()` instead.

**Step 3: Check for other callers of the legacy `invoke()` method**

Search codebase for `generateTimelineSegmentsUseCase(` — should now be zero callers after Task 4.

Run: `grep -r "generateTimelineSegmentsUseCase(" app/src/main/` to confirm.

**Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/GenerateTimelineSegmentsUseCase.kt \
       app/src/test/java/com/cosmiclaboratory/voyager/domain/GenerateTimelineSegmentsTest.kt
git commit -m "refactor: remove legacy timeline generation, keep only movement pipeline"
```

---

### Task 6: Delete `TimelineSegment` model (if no remaining callers)

**Files:**
- Delete or clean: `app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TimelineSegment.kt`

**Step 1: Search for remaining references**

Run: `grep -r "TimelineSegment" app/src/main/ --include="*.kt" -l`

If any files still reference `TimelineSegment`, update them to use `TrackingStateSegment`. The `TimeRange` class in the same file must NOT be deleted — it's used by `TrackingStateSegment`.

**Step 2: Move `TimeRange` to its own file or keep in `TimelineSegment.kt` renamed**

Since `TimeRange` is used everywhere, move it:
- Create: `app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TimeRange.kt` with just the `TimeRange` class
- Remove `TimelineSegment` class from the old file
- Delete the old file

**Step 3: Update imports across codebase**

The import path changes from `com.cosmiclaboratory.voyager.domain.model.TimeRange` — if it was in `TimelineSegment.kt`, update all imports. (If `TimeRange` was already importable from the package, no change needed since both files are in the same package.)

**Step 4: Run build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add -A  # careful: review what's staged
git commit -m "refactor: remove TimelineSegment, extract TimeRange to own file"
```

---

## Phase 3: Map Consumes Segments

### Task 7: Add segment data to `MapViewModel` for parity with Timeline

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/map/MapViewModel.kt`

**Step 1: Add `GenerateTimelineSegmentsUseCase` to `MapViewModel` constructor**

```kotlin
@HiltViewModel
class MapViewModel @Inject constructor(
    // ... existing deps ...
    private val generateTimelineSegmentsUseCase: GenerateTimelineSegmentsUseCase
) : ViewModel(), EventListener {
```

**Step 2: Add `segments` to `MapUiState`**

```kotlin
data class MapUiState(
    // ... existing fields ...
    val segments: List<TrackingStateSegment> = emptyList()
)
```

**Step 3: Load segments in `loadMapDataForDate()`**

After loading locations and places, also generate segments:

```kotlin
// In loadMapDataForDate(), after existing data loading:
val segments = try {
    generateTimelineSegmentsUseCase.generateMovementTimeline(date)
} catch (e: Exception) {
    logger.e("MapViewModel", "Failed to generate segments for map", e)
    emptyList()
}

_uiState.value = _uiState.value.copy(
    // ... existing fields ...
    segments = segments,
    isLoading = false
)
```

**Step 4: Run build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/map/MapViewModel.kt
git commit -m "feat: map now consumes same segment pipeline as timeline"
```

---

### Task 8: Render transit polylines from segments on Map

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/map/MapScreen.kt`
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/components/OpenStreetMapView.kt`

**Step 1: Pass transit segment coordinates to the map composable**

In `MapScreen.kt`, extract transit coordinates from `uiState.segments`:

```kotlin
val transitPolylines = uiState.segments
    .filter { it.type == SegmentType.TRANSIT && it.coordinates.isNotEmpty() }
    .map { it.coordinates }
```

Pass `transitPolylines` to `OpenStreetMapView`.

**Step 2: Render polylines in `OpenStreetMapView`**

Add a `transitPolylines: List<List<Pair<Double, Double>>>` parameter. For each polyline, create an osmdroid `Polyline`:

```kotlin
transitPolylines.forEach { coords ->
    val polyline = Polyline(mapView)
    polyline.setPoints(coords.map { (lat, lng) -> GeoPoint(lat, lng) })
    polyline.outlinePaint.color = Color.parseColor("#00BFA5") // Teal
    polyline.outlinePaint.strokeWidth = 4f
    mapView.overlayManager.add(polyline)
}
```

**Step 3: Run build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/map/MapScreen.kt \
       app/src/main/java/com/cosmiclaboratory/voyager/presentation/components/OpenStreetMapView.kt
git commit -m "feat: render transit polylines on map from unified segments"
```

---

## Phase 4: Enhanced Travel Segments

### Task 9: Add travel mode inference to `MovementSegmentationUseCase`

**Files:**
- Create: `app/src/test/java/com/cosmiclaboratory/voyager/domain/TravelModeInferenceTest.kt`
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TrackingStateSegment.kt`
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/MovementSegmentationUseCase.kt`

**Step 1: Add `TravelMode` enum and `inferredMode` field**

```kotlin
// In TrackingStateSegment.kt, add before the class:
enum class TravelMode(val displayName: String) {
    WALKING("Walking"),
    RUNNING("Running"),
    CYCLING("Cycling"),
    VEHICLE("Vehicle"),
    UNKNOWN("Traveling")
}

// In TrackingStateSegment data class, add field:
val inferredMode: TravelMode? = null,
val modeConfidence: Double? = null,
```

**Step 2: Write failing test**

```kotlin
// app/src/test/java/com/cosmiclaboratory/voyager/domain/TravelModeInferenceTest.kt
package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.usecase.MovementSegmentationUseCase
import com.cosmiclaboratory.voyager.fixtures.TestDataFactory
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TravelModeInferenceTest {

    private val locationRepository = mockk<com.cosmiclaboratory.voyager.domain.repository.LocationRepository>()
    private val visitRepository = mockk<com.cosmiclaboratory.voyager.domain.repository.VisitRepository>()
    private val placeRepository = mockk<com.cosmiclaboratory.voyager.domain.repository.PlaceRepository>()
    private val currentStateRepository = mockk<com.cosmiclaboratory.voyager.domain.repository.CurrentStateRepository>()
    private val preferencesRepository = mockk<com.cosmiclaboratory.voyager.domain.repository.PreferencesRepository>()
    private val nameResolver = mockk<com.cosmiclaboratory.voyager.domain.usecase.PlaceNameResolver>(relaxed = true)

    private lateinit var useCase: MovementSegmentationUseCase

    private val startOfDay = LocalDate.of(2026, 3, 10).atStartOfDay()
    private val endOfDay = startOfDay.plusDays(1)

    @Before
    fun setup() {
        useCase = MovementSegmentationUseCase(
            locationRepository, visitRepository, placeRepository,
            currentStateRepository, preferencesRepository, nameResolver
        )
        coEvery { preferencesRepository.getCurrentPreferences() } returns TestDataFactory.createUserPreferences()
        coEvery { currentStateRepository.getCurrentStateSync() } returns null
        coEvery { visitRepository.getVisitsBetween(any(), any()) } returns flowOf(emptyList())
    }

    @Test
    fun `walking speed locations produce WALKING mode`() = runTest {
        // Walking: 1-2 m/s (3.6-7.2 km/h)
        val locations = (0 until 20).map { i ->
            Location(
                id = i.toLong(), latitude = 37.7749 + i * 0.0001,
                longitude = -122.4194, timestamp = startOfDay.plusHours(8).plusSeconds(i * 30L),
                accuracy = 10f, speed = 1.5f // m/s = walking
            )
        }
        coEvery { locationRepository.getLocationsBetween(any(), any()) } returns flowOf(locations)

        val segments = useCase.generateSegments(startOfDay, endOfDay)
        val transit = segments.filter { it.type == SegmentType.TRANSIT }
        assertTrue(transit.isNotEmpty())
        transit.forEach { seg ->
            assertEquals(TravelMode.WALKING, seg.inferredMode)
            assertNotNull(seg.modeConfidence)
        }
    }

    @Test
    fun `vehicle speed locations produce VEHICLE mode`() = runTest {
        // Vehicle: > 25 m/s (90+ km/h)
        val locations = (0 until 20).map { i ->
            Location(
                id = i.toLong(), latitude = 37.7749 + i * 0.005,
                longitude = -122.4194, timestamp = startOfDay.plusHours(8).plusSeconds(i * 30L),
                accuracy = 15f, speed = 30f // m/s = vehicle
            )
        }
        coEvery { locationRepository.getLocationsBetween(any(), any()) } returns flowOf(locations)

        val segments = useCase.generateSegments(startOfDay, endOfDay)
        val transit = segments.filter { it.type == SegmentType.TRANSIT }
        assertTrue(transit.isNotEmpty())
        transit.forEach { seg ->
            assertEquals(TravelMode.VEHICLE, seg.inferredMode)
        }
    }
}
```

**Step 3: Implement mode inference in `MovementSegmentationUseCase`**

Add a private method:

```kotlin
private fun inferTravelMode(
    avgSpeedKmh: Double?,
    activityHint: UserActivity?
): Pair<TravelMode, Double> {
    // GPS speed is primary signal
    val speed = avgSpeedKmh ?: return Pair(TravelMode.UNKNOWN, 0.3)

    val (speedMode, speedConfidence) = when {
        speed < 2.0 -> Pair(TravelMode.WALKING, 0.5) // Very slow, might be stationary drift
        speed < 7.5 -> Pair(TravelMode.WALKING, 0.85)
        speed < 15.0 -> Pair(TravelMode.RUNNING, 0.7) // Could be fast walking or slow cycling
        speed < 30.0 -> Pair(TravelMode.CYCLING, 0.7)
        else -> Pair(TravelMode.VEHICLE, 0.9)
    }

    // Activity recognition as secondary confirmation
    val activityMode = when (activityHint) {
        UserActivity.WALKING -> TravelMode.WALKING
        UserActivity.RUNNING -> TravelMode.RUNNING
        UserActivity.CYCLING -> TravelMode.CYCLING
        UserActivity.DRIVING -> TravelMode.VEHICLE
        else -> null
    }

    // Boost confidence if both agree
    val finalConfidence = if (activityMode == speedMode) {
        (speedConfidence + 0.1).coerceAtMost(0.95)
    } else {
        speedConfidence
    }

    return Pair(speedMode, finalConfidence)
}
```

Call it in the transit segment creation block, set `inferredMode` and `modeConfidence` on the segment.

**Step 4: Update `formatSummary()` for transit to show mode**

```kotlin
SegmentType.TRANSIT -> {
    val mode = inferredMode?.displayName ?: "Traveled"
    val dist = totalDistanceMeters?.let { formatDistance(it) } ?: ""
    val speed = averageSpeedKmh?.let { "at avg ${it.toInt()} km/h" } ?: ""
    listOfNotNull(
        mode,
        dist.ifEmpty { null },
        speed.ifEmpty { null },
        "- ${formatDuration()}"
    ).joinToString(" ")
}
```

**Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cosmiclaboratory.voyager.domain.TravelModeInferenceTest" --info`
Expected: PASS

**Step 6: Run all tests**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: PASS

**Step 7: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TrackingStateSegment.kt \
       app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/MovementSegmentationUseCase.kt \
       app/src/test/java/com/cosmiclaboratory/voyager/domain/TravelModeInferenceTest.kt
git commit -m "feat: add GPS-first travel mode inference with confidence scoring"
```

---

## Phase 5: Transient Stop Enrichment

### Task 10: Reverse-geocode transient stops for address labels

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/MovementSegmentationUseCase.kt`

**Step 1: Add `GeocodingRepository` to `MovementSegmentationUseCase` constructor**

```kotlin
@Singleton
class MovementSegmentationUseCase @Inject constructor(
    // ... existing deps ...
    private val geocodingRepository: GeocodingRepository
)
```

**Step 2: In `classifyLocationWindow()`, when creating `TRANSIENT_STOP` segments, reverse-geocode the center**

```kotlin
// After computing centerLat/centerLng for transient stop:
val addressLabel = try {
    val address = geocodingRepository.getAddressForCoordinates(centerLat, centerLng)
    listOfNotNull(
        address?.streetName?.takeIf { it.isNotBlank() },
        address?.subLocality?.takeIf { it.isNotBlank() },
        address?.locality?.takeIf { it.isNotBlank() }
    ).joinToString(", ").ifEmpty { null }
} catch (e: Exception) {
    null
}
```

Set `addressLabel` on the `TRANSIENT_STOP` segment.

**Step 3: Run build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/MovementSegmentationUseCase.kt
git commit -m "feat: reverse-geocode transient stops for address labels"
```

---

## Phase 6: Quality Metrics

### Task 11: Create `TimelineQualityMetrics` data class and computation

**Files:**
- Create: `app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TimelineQualityMetrics.kt`
- Create: `app/src/test/java/com/cosmiclaboratory/voyager/domain/TimelineQualityMetricsTest.kt`

**Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/cosmiclaboratory/voyager/domain/TimelineQualityMetricsTest.kt
package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.usecase.NameSource
import com.cosmiclaboratory.voyager.domain.usecase.ResolvedName
import com.cosmiclaboratory.voyager.fixtures.TestDataFactory
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TimelineQualityMetricsTest {

    @Test
    fun `metrics correctly compute unknown name rate`() {
        val segments = listOf(
            makeVisitSegment(NameSource.COORDINATES),
            makeVisitSegment(NameSource.OSM_POI),
            makeVisitSegment(NameSource.COORDINATES)
        )
        val metrics = TimelineQualityMetrics.compute(segments)
        assertEquals(2.0 / 3.0, metrics.coordinateFallbackRate, 0.01)
    }

    @Test
    fun `metrics correctly compute untracked gap ratio`() {
        val now = LocalDateTime.of(2026, 3, 10, 0, 0)
        val segments = listOf(
            TrackingStateSegment(
                type = SegmentType.CONFIRMED_PLACE_VISIT,
                timeRange = TimeRange(now.plusHours(8), now.plusHours(12)),
                place = TestDataFactory.createPlace()
            ),
            TrackingStateSegment(
                type = SegmentType.UNTRACKED_WHILE_TRACKING,
                timeRange = TimeRange(now.plusHours(12), now.plusHours(16))
            )
        )
        val metrics = TimelineQualityMetrics.compute(segments)
        assertEquals(0.5, metrics.untrackedGapRatio, 0.01)
    }

    @Test
    fun `empty segments produce zero metrics`() {
        val metrics = TimelineQualityMetrics.compute(emptyList())
        assertEquals(0.0, metrics.coordinateFallbackRate, 0.001)
        assertEquals(0.0, metrics.untrackedGapRatio, 0.001)
        assertEquals(0, metrics.totalSegments)
    }

    private fun makeVisitSegment(nameSource: NameSource): TrackingStateSegment {
        val now = LocalDateTime.of(2026, 3, 10, 9, 0)
        return TrackingStateSegment(
            type = SegmentType.CONFIRMED_PLACE_VISIT,
            timeRange = TimeRange(now, now.plusHours(1)),
            place = TestDataFactory.createPlace(),
            resolvedName = ResolvedName(
                displayName = "Test",
                source = nameSource,
                confidence = 0.5
            )
        )
    }
}
```

**Step 2: Implement `TimelineQualityMetrics`**

```kotlin
// app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TimelineQualityMetrics.kt
package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.usecase.NameSource

data class TimelineQualityMetrics(
    val totalSegments: Int,
    val placeVisitCount: Int,
    val transitCount: Int,
    val transientStopCount: Int,
    val untrackedCount: Int,
    val coordinateFallbackRate: Double,   // % of place visits using coordinate names
    val untrackedGapRatio: Double,        // % of total time that is untracked
    val averageNameConfidence: Double     // mean confidence of resolved names
) {
    companion object {
        fun compute(segments: List<TrackingStateSegment>): TimelineQualityMetrics {
            if (segments.isEmpty()) return TimelineQualityMetrics(
                totalSegments = 0, placeVisitCount = 0, transitCount = 0,
                transientStopCount = 0, untrackedCount = 0,
                coordinateFallbackRate = 0.0, untrackedGapRatio = 0.0,
                averageNameConfidence = 0.0
            )

            val placeVisits = segments.filter { it.type == SegmentType.CONFIRMED_PLACE_VISIT }
            val coordinateFallbacks = placeVisits.count {
                it.resolvedName?.source == NameSource.COORDINATES
            }
            val coordinateFallbackRate = if (placeVisits.isNotEmpty())
                coordinateFallbacks.toDouble() / placeVisits.size else 0.0

            val totalMs = segments.sumOf { it.durationMs }.coerceAtLeast(1)
            val untrackedMs = segments
                .filter { it.type == SegmentType.UNTRACKED_WHILE_TRACKING || it.type == SegmentType.NOT_TRACKING }
                .sumOf { it.durationMs }
            val untrackedGapRatio = untrackedMs.toDouble() / totalMs

            val nameConfidences = placeVisits.mapNotNull { it.resolvedName?.confidence }
            val avgConfidence = if (nameConfidences.isNotEmpty()) nameConfidences.average() else 0.0

            return TimelineQualityMetrics(
                totalSegments = segments.size,
                placeVisitCount = placeVisits.size,
                transitCount = segments.count { it.type == SegmentType.TRANSIT },
                transientStopCount = segments.count { it.type == SegmentType.TRANSIENT_STOP },
                untrackedCount = segments.count {
                    it.type == SegmentType.UNTRACKED_WHILE_TRACKING || it.type == SegmentType.NOT_TRACKING
                },
                coordinateFallbackRate = coordinateFallbackRate,
                untrackedGapRatio = untrackedGapRatio,
                averageNameConfidence = avgConfidence
            )
        }
    }
}
```

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cosmiclaboratory.voyager.domain.TimelineQualityMetricsTest" --info`
Expected: PASS

**Step 4: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/domain/model/TimelineQualityMetrics.kt \
       app/src/test/java/com/cosmiclaboratory/voyager/domain/TimelineQualityMetricsTest.kt
git commit -m "feat: add TimelineQualityMetrics with unknown-name and gap-ratio tracking"
```

---

### Task 12: Expose quality metrics in Timeline UI (developer mode)

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineViewModel.kt`
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineScreen.kt`

**Step 1: Add metrics to `TimelineUiState`**

```kotlin
val qualityMetrics: TimelineQualityMetrics? = null
```

**Step 2: Compute metrics in `loadTimelineForDate()`**

After generating segments:
```kotlin
val metrics = TimelineQualityMetrics.compute(segments)
```

Set `qualityMetrics = metrics` in the UI state update.

**Step 3: Add metrics card to `TimelineScreen` (shown only in developer mode or behind a toggle)**

At the bottom of the `LazyColumn`, add a collapsible metrics card:

```kotlin
uiState.qualityMetrics?.let { metrics ->
    item {
        MatrixCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("QUALITY METRICS", style = MaterialTheme.typography.labelMedium, color = Teal)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Segments: ${metrics.totalSegments} (${metrics.placeVisitCount} visits, ${metrics.transitCount} transits)")
                Text("Coordinate fallback: ${String.format("%.0f%%", metrics.coordinateFallbackRate * 100)}")
                Text("Untracked ratio: ${String.format("%.0f%%", metrics.untrackedGapRatio * 100)}")
                Text("Avg name confidence: ${String.format("%.0f%%", metrics.averageNameConfidence * 100)}")
            }
        }
    }
}
```

**Step 4: Run build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineViewModel.kt \
       app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineScreen.kt
git commit -m "feat: display timeline quality metrics in UI"
```

---

## Phase 7: Cleanup and Code Quality

### Task 13: Remove duplicated utility methods

**Files:**
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/domain/usecase/EnrichPlaceWithDetailsUseCase.kt` — remove `isGenericName()`, `isNumericOrHouseNumber()` (now in `PlaceNameResolver`)
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/data/repository/GeocodingRepositoryImpl.kt` — remove duplicate `isGenericName()` if present
- Modify: `app/src/main/java/com/cosmiclaboratory/voyager/data/api/OverpassApiService.kt` — remove duplicate `isGenericName()` if present

**Step 1: Search for duplicates**

Run: `grep -rn "isGenericName" app/src/main/ --include="*.kt"`

**Step 2: For each duplicate, either:**
- Delete the local copy and import from `PlaceNameResolver`
- Or extract to a shared `NamingUtils` object if `PlaceNameResolver` is too coupled

Since `PlaceNameResolver.isGenericName()` is private, make it `internal` or extract to a top-level function in the same file:

```kotlin
// At bottom of PlaceNameResolver.kt
internal fun isGenericPlaceName(name: String): Boolean {
    val lower = name.lowercase()
    val generic = setOf("building", "place", "location", "unnamed", "null", "untitled", "unknown")
    return generic.any { lower.contains(it) }
}

internal fun isNumericOrHouseNumber(name: String): Boolean {
    if (name.all { it.isDigit() }) return true
    if (name.matches(Regex("^\\d+[A-Za-z]?$"))) return true
    if (name.matches(Regex("^\\d+[/\\-]\\d*[A-Za-z]?$"))) return true
    return false
}
```

Update all call sites to use these shared functions.

**Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: PASS

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: deduplicate isGenericName/isNumericOrHouseNumber to PlaceNameResolver"
```

---

### Task 14: Remove `placeRepository.getAllPlaces()` call from `TimelineViewModel`

This was already done in Task 4. Verify it's gone:

**Step 1: Verify**

Run: `grep -n "getAllPlaces" app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/timeline/TimelineViewModel.kt`
Expected: No matches

If still present, remove the call. The segments already carry their `Place` references from the pipeline.

---

### Task 15: Run full test suite and verify build

**Step 1: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest --info`
Expected: ALL PASS

**Step 2: Run full debug build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Final commit if any remaining fixups**

```bash
git add -A
git commit -m "chore: final cleanup for unified timeline pipeline"
```

---

## Summary of Changes

| What | Before | After |
|------|--------|-------|
| Timeline segments | Dual: `TimelineSegment` + `TrackingStateSegment` | Single: `TrackingStateSegment` only |
| Place naming | `generateSmartName()` inline, duplicated | `PlaceNameResolver` with confidence ranking |
| Name quality | "Unknown Place" possible | Never when any address candidate exists |
| Travel mode | All transit = `TRANSIT` | `WALKING`/`RUNNING`/`CYCLING`/`VEHICLE` with confidence |
| Map data | Raw locations + place markers | Same segments as Timeline + transit polylines |
| Quality metrics | None | `coordinateFallbackRate`, `untrackedGapRatio`, `avgNameConfidence` |
| `getAllPlaces()` in Timeline | Called every refresh (unbounded) | Removed — places come from segments |
| Duplicate utils | `isGenericName()` in 3+ files | Single shared implementation |

## Files Created
- `domain/usecase/PlaceNameResolver.kt`
- `domain/model/TimelineQualityMetrics.kt`
- `domain/model/TimeRange.kt` (extracted from `TimelineSegment.kt`)
- `test/.../PlaceNameResolverTest.kt`
- `test/.../TravelModeInferenceTest.kt`
- `test/.../TimelineQualityMetricsTest.kt`

## Files Modified
- `domain/usecase/EnrichPlaceWithDetailsUseCase.kt` — delegates to `PlaceNameResolver`
- `domain/usecase/MovementSegmentationUseCase.kt` — travel mode, geocoding for transient stops, name resolver
- `domain/usecase/GenerateTimelineSegmentsUseCase.kt` — legacy path removed
- `domain/model/TrackingStateSegment.kt` — new fields: `resolvedName`, `inferredMode`, `modeConfidence`, `TravelMode` enum
- `presentation/screen/timeline/TimelineViewModel.kt` — single pipeline, quality metrics
- `presentation/screen/timeline/TimelineScreen.kt` — legacy UI removed, metrics card added
- `presentation/screen/map/MapViewModel.kt` — consumes segments
- `presentation/screen/map/MapScreen.kt` — transit polylines
- `presentation/components/OpenStreetMapView.kt` — polyline rendering

## Files Deleted
- `domain/model/TimelineSegment.kt` (after extracting `TimeRange`)
