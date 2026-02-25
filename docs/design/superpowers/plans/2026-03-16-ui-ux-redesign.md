# Voyager UI/UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Voyager UI from a 5-tab "Matrix terminal" aesthetic to a polished 4-tab dark UI with enriched data display, merged screens, and unified settings.

**Architecture:** Presentation-layer refactor only. No domain/data layer changes. Replace MatrixComponents with VoyagerComponents, restructure navigation (5 tabs → 4), merge Track into Home, merge AdvancedSettings into Settings tabs, merge Insights/Statistics into one screen. All data sources already exist — this is reorganization and enrichment.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt, Navigation Compose, StateFlow, OpenStreetMap

**Spec:** `docs/superpowers/specs/2026-03-16-ui-ux-redesign-design.md`

**File path prefix:** All paths relative to `app/src/main/java/com/cosmiclaboratory/voyager/` unless noted otherwise.

---

## Chunk 1: Theme & Component Library

Replace the Matrix component library with polished dark VoyagerComponents. This is the foundation — every screen depends on it.

### Task 1: Create VoyagerColors

**Files:**
- Create: `presentation/theme/VoyagerColors.kt`

- [ ] **Step 1: Create the color tokens file**

```kotlin
package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.ui.graphics.Color

// Polished Dark palette — evolved from Matrix teal-on-black
object VoyagerColors {
    // Primary
    val Primary = Color(0xFF26A69A)        // Softer teal
    val PrimaryDim = Color(0xFF1A7A70)     // Muted teal for secondary elements
    val PrimaryContainer = Color(0xFF003D36) // Dark teal container

    // Surfaces
    val Background = Color(0xFF0F0F1A)     // Near-black with blue tint
    val Surface = Color(0xFF1A1A2E)        // Dark blue-grey
    val SurfaceVariant = Color(0xFF252540) // Slightly lighter surface
    val SurfaceBright = Color(0xFF2E2E4A)  // Elevated surface

    // Text
    val OnSurface = Color(0xFFE8E8F0)      // Off-white
    val OnSurfaceVariant = Color(0xFF8888A0) // Muted grey
    val OnPrimary = Color(0xFFFFFFFF)      // White

    // Status
    val Error = Color(0xFFEF5350)          // Warm red
    val ErrorContainer = Color(0xFF3D1A1A) // Dark red
    val Success = Color(0xFF66BB6A)        // Green
    val Warning = Color(0xFFFFA726)        // Amber

    // Severity (for anomalies)
    val SeverityHigh = Error
    val SeverityMedium = Warning
    val SeverityLow = Color(0xFF42A5F5)    // Blue
    val SeverityInfo = Primary
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/presentation/theme/VoyagerColors.kt
git commit -m "feat(theme): add VoyagerColors polished dark palette"
```

### Task 2: Create VoyagerComponents

**Files:**
- Create: `presentation/theme/VoyagerComponents.kt`
- Reference: `presentation/theme/MatrixComponents.kt` (for API compatibility)

- [ ] **Step 1: Create VoyagerCard component**

The primary container. Replaces `MatrixCard`. Key differences: 12dp corners, softer border, optional onClick.

```kotlin
package com.cosmiclaboratory.voyager.presentation.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun VoyagerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .alpha(if (enabled) 1f else 0.5f)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)

    OutlinedCard(
        modifier = cardModifier,
        shape = CardShape,
        border = BorderStroke(1.dp, VoyagerColors.PrimaryDim.copy(alpha = 0.3f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = VoyagerColors.Surface
        )
    ) {
        Column(
            modifier = Modifier.padding(padding),
            content = content
        )
    }
}
```

- [ ] **Step 2: Add VoyagerButton, VoyagerIconButton, VoyagerTextButton**

Append to VoyagerComponents.kt:

```kotlin
@Composable
fun VoyagerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = VoyagerColors.PrimaryContainer,
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

@Composable
fun VoyagerOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, VoyagerColors.Primary.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

@Composable
fun VoyagerIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}

@Composable
fun VoyagerTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = VoyagerColors.Primary
        ),
        content = content
    )
}
```

- [ ] **Step 3: Add utility components (PulsingDot, CollapsibleSection, Badge, SectionHeader, LoadingDots)**

Append to VoyagerComponents.kt. These are carried over from MatrixComponents with updated styling:

```kotlin
/** Pulsing dot indicator for live status */
@Composable
fun PulsingDot(
    size: Dp = 12.dp,
    color: Color = VoyagerColors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

/** Collapsible section with chevron indicator */
@Composable
fun CollapsibleSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.Primary
            )
            Text(
                text = if (isExpanded) "▾" else "▸",
                color = VoyagerColors.PrimaryDim
            )
        }
        if (isExpanded) {
            Column(content = content)
        }
    }
}

/** Small badge for counts or labels */
@Composable
fun VoyagerBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = VoyagerColors.PrimaryContainer,
        contentColor = VoyagerColors.Primary
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Section header text */
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = VoyagerColors.Primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/** Loading animation */
@Composable
fun LoadingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )
    Text(
        text = "Loading" + ".".repeat(dotCount.toInt().coerceIn(0, 3)),
        style = MaterialTheme.typography.bodyMedium,
        color = VoyagerColors.OnSurfaceVariant
    )
}

/** Stat item for grid displays */
@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.Primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/presentation/theme/VoyagerComponents.kt
git commit -m "feat(theme): add VoyagerComponents library (cards, buttons, utilities)"
```

### Task 3: Update Theme.kt to use new colors

**Files:**
- Modify: `ui/theme/Theme.kt` (note: lives in `com.cosmiclaboratory.voyager.ui.theme`, NOT `presentation/theme/`)
- Modify: `ui/theme/Color.kt`

> **Cross-package import required:** VoyagerColors.kt is in `presentation/theme/` while Theme.kt is in `ui/theme/`. Add `import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors` to Theme.kt.

- [ ] **Step 1: Read the current theme file**

Read: `app/src/main/java/com/cosmiclaboratory/voyager/ui/theme/Theme.kt`
Read: `app/src/main/java/com/cosmiclaboratory/voyager/ui/theme/Color.kt`

- [ ] **Step 2: Update the dark color scheme to use VoyagerColors**

Add import at top of Theme.kt:
```kotlin
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
```

Update the `darkColorScheme()` call to use VoyagerColors tokens for `primary`, `onPrimary`, `surface`, `onSurface`, `background`, `error`, etc. Keep the existing `Teal` and `TealDim` exports in Color.kt as aliases to `VoyagerColors.Primary` and `VoyagerColors.PrimaryDim` for backward compatibility during migration.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(theme): update MaterialTheme dark scheme to VoyagerColors"
```

---

## Chunk 2: Navigation Architecture

Change from 5-tab to 4-tab navigation. This is structural — must be done before screen rewrites.

### Task 4: Update VoyagerDestination

**Files:**
- Modify: `presentation/navigation/VoyagerDestination.kt`

- [ ] **Step 1: Rename Dashboard to Home, remove Track and AdvancedSettings**

```kotlin
// RENAME:
object Home : VoyagerDestination(
    route = "home",
    title = "Home",
    icon = Icons.Filled.Home
)

// REMOVE: object Track (entire declaration)
// REMOVE: object AdvancedSettings (entire declaration)

// UPDATE bottomNavItems:
val bottomNavItems = listOf(Home, Map, Timeline, Insights)

// UPDATE menuItems (Settings now accessed via persistent top bar gear icon per spec):
val menuItems = emptyList<VoyagerDestination>()
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: Compilation errors in VoyagerNavHost and any screen referencing `Track` or `AdvancedSettings` or `Dashboard`. This is expected — we fix them next.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/cosmiclaboratory/voyager/presentation/navigation/VoyagerDestination.kt
git commit -m "refactor(nav): rename Dashboard→Home, remove Track/AdvancedSettings destinations"
```

### Task 5: Update VoyagerNavHost

**Files:**
- Modify: `presentation/navigation/VoyagerNavHost.kt`

- [ ] **Step 1: Update NavHost startDestination and routes**

```kotlin
NavHost(
    navController = navController,
    startDestination = VoyagerDestination.Home.route, // was Dashboard
    modifier = modifier
) {
    // RENAME: Dashboard composable → Home
    composable(VoyagerDestination.Home.route) {
        DashboardScreen( // Keep DashboardScreen name for now, rename later
            permissionStatus = permissionStatus,
            onNavigateToReview = {
                navController.navigate(VoyagerDestination.PlaceReview.route)
            },
            onNavigateToSettings = onNavigateToSettings
        )
    }

    // REMOVE: composable(VoyagerDestination.Track.route) { ... }
    // REMOVE: composable(VoyagerDestination.AdvancedSettings.route) { ... }

    // UPDATE Settings: remove onNavigateToAdvancedSettings parameter
    composable(VoyagerDestination.Settings.route) {
        SettingsScreen(
            permissionStatus = permissionStatus,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onNavigateToDebugDataInsertion = {
                navController.navigate(VoyagerDestination.DebugDataInsertion.route)
            },
            onNavigateToCategories = {
                navController.navigate(VoyagerDestination.Categories.route)
            },
            onNavigateToDeveloperProfile = {
                navController.navigate(VoyagerDestination.DeveloperProfile.route)
            }
        )
    }

    // Keep: Map, Timeline, Insights, PlaceReview, Categories, DeveloperProfile, DebugDataInsertion
}
```

- [ ] **Step 2: Update any file referencing `VoyagerDestination.Dashboard`**

Search for `VoyagerDestination.Dashboard` across codebase and replace with `VoyagerDestination.Home`.

Run: `grep -r "VoyagerDestination.Dashboard" app/src/main/java/ --include="*.kt" -l`

Update each file found.

- [ ] **Step 3: Update any file referencing `VoyagerDestination.Track`**

Search: `grep -r "VoyagerDestination.Track" app/src/main/java/ --include="*.kt" -l`
Remove or redirect those references.

- [ ] **Step 4: Update any file referencing `VoyagerDestination.AdvancedSettings`**

Search: `grep -r "VoyagerDestination.AdvancedSettings" app/src/main/java/ --include="*.kt" -l`
Remove those references. Settings screen no longer navigates to AdvancedSettings.

- [ ] **Step 5: Find and update the bottom navigation bar composable**

Search for where `bottomNavItems` is consumed (likely in `MainActivity.kt` or a scaffold composable). Ensure it renders 4 tabs.

Run: `grep -r "bottomNavItems" app/src/main/java/ --include="*.kt" -l`

- [ ] **Step 6: Add persistent top bar with bell and gear icons**

Find the main `Scaffold` composable (likely in `MainActivity.kt` or `VoyagerApp.kt`). Add a `TopAppBar` with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
TopAppBar(
    title = { Text("Voyager", color = VoyagerColors.Primary) },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = VoyagerColors.Background
    ),
    actions = {
        // Bell icon with pending review count badge
        BadgedBox(
            badge = {
                if (pendingReviewCount > 0) {
                    Badge { Text("$pendingReviewCount") }
                }
            }
        ) {
            IconButton(onClick = onNavigateToReview) {
                Icon(Icons.Default.Notifications, "Reviews", tint = VoyagerColors.OnSurface)
            }
        }
        // Gear icon for Settings
        IconButton(onClick = onNavigateToSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = VoyagerColors.OnSurface)
        }
    }
)
```

The `pendingReviewCount` should come from `DashboardViewModel.uiState.pendingReviewCount`, collected at the scaffold level (the same composable that holds the NavHost). Pass it down from the Activity/App composable. Wire `onNavigateToReview` → PlaceReview route and `onNavigateToSettings` → Settings route.

- [ ] **Step 7: Verify full compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL (all references updated)

- [ ] **Step 8: Commit**

```bash
git commit -am "refactor(nav): update NavHost for 4-tab layout, persistent top bar, remove Track/AdvancedSettings routes"
```

---

## Chunk 3: Home Screen (Dashboard + Track Merge)

Complete rewrite of DashboardScreen. Absorb TrackViewModel functionality. Add places lists.

### Task 6: Extend DashboardViewModel with Track + Places data

**Files:**
- Modify: `presentation/screen/dashboard/DashboardViewModel.kt`

- [ ] **Step 1: Add new state fields to DashboardUiState**

Add these fields to the existing `DashboardUiState` data class:

Import types from `com.cosmiclaboratory.voyager.domain.model.*` (PlaceWithVisits, PlacePattern, Place).

```kotlin
// Merged from TrackViewModel
val trackingSessionDuration: String = "",
val sessionLocationCount: Int = 0,
val isDetectingPlaces: Boolean = false,
val detectionStatusMessage: String? = null,

// Today's places
val todaysPlaces: List<PlaceWithVisits> = emptyList(), // domain.model.PlaceWithVisits

// All places
val allPlaces: List<Place> = emptyList(),
val placeSortMode: PlaceSortMode = PlaceSortMode.MOST_VISITED,

// Place patterns (keyed by placeId)
val placePatterns: Map<Long, PlacePattern?> = emptyMap(), // domain.model.PlacePattern
```

Add the sort enum **inside DashboardViewModel.kt** (co-located with the ViewModel):

```kotlin
enum class PlaceSortMode { MOST_VISITED, MOST_TIME, RECENTLY_VISITED, CATEGORY }
```

- [ ] **Step 2: Inject additional dependencies**

Add to constructor injection (verified import paths):
- `com.cosmiclaboratory.voyager.domain.repository.PlaceRepository` (for getAllPlaces, getMostVisitedPlaces, getPlacesWithMostTime, getPlacesVisitedOnDate)
- `com.cosmiclaboratory.voyager.domain.repository.VisitRepository` (for getVisitsBetween)
- `com.cosmiclaboratory.voyager.domain.usecase.AnalyzePlacePatternsUseCase` (for pattern data)
- `com.cosmiclaboratory.voyager.domain.usecase.PlaceDetectionUseCases` or reuse existing detection logic from TrackViewModel

- [ ] **Step 3: Add place data loading**

Add methods:
- `loadTodaysPlaces()` — calls `placeRepository.getPlacesVisitedOnDate(LocalDate.now())` combined with today's visits
- `loadAllPlaces()` — calls appropriate repository method based on `placeSortMode`
- `setPlaceSortMode(mode: PlaceSortMode)` — update sort and reload
- `triggerPlaceDetection()` — migrated from TrackViewModel logic
- `dismissError()` — clear error message

Add session duration formatting from `CurrentState.trackingSessionDuration` and `CurrentState.sessionSavedCount`.

- [ ] **Step 4: Load pattern data**

After places load, call `analyzePlacePatternsUseCase()` and build a `Map<Long, PlacePattern?>` keyed by `placeId` for inline display.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(dashboard): extend DashboardViewModel with Track merge + places data"
```

### Task 7: Rewrite DashboardScreen composable

**Files:**
- Modify: `presentation/screen/dashboard/DashboardScreen.kt`

- [ ] **Step 1: Replace imports — switch from MatrixComponents to VoyagerComponents**

Replace:
```kotlin
import com.cosmiclaboratory.voyager.presentation.theme.*
```
Ensure VoyagerCard, VoyagerButton, etc. are used instead of MatrixCard, MatrixButton.

- [ ] **Step 2: Write the Hero Card (live tracking status)**

```kotlin
@Composable
private fun TrackingHeroCard(
    uiState: DashboardUiState,
    onToggleTracking: () -> Unit,
    currentTime: Long // for live duration ticking
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        // Row: Pulsing dot + "LIVE TRACKING" header
        // Current place name, address, category
        // Live visit duration (ticking every 1s)
        // Tracking toggle switch
        // Session duration + location count
        // Error message if any
    }
}
```

Implement the full composable following the spec Section 3 layout. Use `LocationDisplayFormatter` for formatting.

- [ ] **Step 3: Write the Stat Grid**

```kotlin
@Composable
private fun TodayStatsGrid(uiState: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(value = "${uiState.totalPlaces}", label = "PLACES")
        StatItem(
            value = String.format("%.1f km", uiState.totalTimeTracked.toKm()),
            label = "DISTANCE"
        )
        StatItem(
            value = LocationDisplayFormatter.formatDuration(uiState.totalTimeTracked),
            label = "TRACKED"
        )
    }
}
```

- [ ] **Step 4: Write the Pending Reviews Card**

Conditional card shown when `pendingReviewCount > 0`. Shows count + priority breakdown. Tappable via `onNavigateToReview`.

- [ ] **Step 5: Write Today's Places section**

```kotlin
@Composable
private fun TodaysPlacesSection(
    places: List<PlaceWithVisits>,
    currentPlace: Place?,
    currentVisitDuration: Long,
    onViewOnMap: (Place) -> Unit
) {
    // CollapsibleSection with title "TODAY'S PLACES"
    // For each PlaceWithVisits:
    //   - Place name + category icon
    //   - Address
    //   - Time range (entry → exit or "NOW") + duration
    //   - Visit count today + average stay (totalTimeSpent / visitCount)
    //   - Lifetime visitCount + frequent flag (visitCount >= 10)
    //   - Confidence for active visit
    //   - "View on Map" button
}
```

- [ ] **Step 6: Write All Places section**

```kotlin
@Composable
private fun AllPlacesSection(
    places: List<Place>,
    sortMode: PlaceSortMode,
    patterns: Map<Long, PlacePattern?>,
    onSortModeChange: (PlaceSortMode) -> Unit,
    onViewOnMap: (Place) -> Unit,
    onViewTimeline: (Place) -> Unit
) {
    // CollapsibleSection with title "ALL PLACES (N total)"
    // Sort dropdown
    // For each Place: full card with all metadata per spec
}
```

- [ ] **Step 7: Write Quick Actions section**

Place detection trigger, nav to insights, export. Dev-mode debug tools section.

- [ ] **Step 8: Assemble the full DashboardScreen**

Wire all sections into a `LazyColumn`. Do NOT add a `Scaffold` with top bar here — the persistent top bar lives at the NavHost/Scaffold level (Task 5 Step 6). DashboardScreen should be content-only. Remove all MatrixComponent references.

- [ ] **Step 9: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git commit -am "feat(dashboard): rewrite DashboardScreen with merged Track + places lists"
```

---

## Chunk 4: Map Screen Enhancements

Add search bar, expose map toggles, enhance bottom sheet.

### Task 8: Add search to MapViewModel

**Files:**
- Modify: `presentation/screen/map/MapViewModel.kt`

- [ ] **Step 1: Add search state**

```kotlin
private val _searchQuery = MutableStateFlow("")
val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

fun setSearchQuery(query: String) {
    _searchQuery.value = query
}
```

- [ ] **Step 2: Filter visiblePlaces through search query**

Modify the `visiblePlaces` computation to also filter by search query:

```kotlin
// In the combine/map that produces visiblePlaces:
val filtered = categoryFilteredPlaces.filter { place ->
    val q = searchQuery.value.lowercase()
    if (q.isBlank()) true
    else {
        place.getBestName().lowercase().contains(q) ||
        (place.address?.lowercase()?.contains(q) == true) ||
        place.category.displayName.lowercase().contains(q)
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(map): add search query filtering to MapViewModel"
```

### Task 9: Update MapScreen UI

**Files:**
- Modify: `presentation/screen/map/MapScreen.kt`

- [ ] **Step 1: Add search bar to header**

Add a `TextField` or `SearchBar` composable below the date selector:

```kotlin
val searchQuery by viewModel.searchQuery.collectAsState()

OutlinedTextField(
    value = searchQuery,
    onValueChange = { viewModel.setSearchQuery(it) },
    placeholder = { Text("Search places...") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    leadingIcon = { Icon(Icons.Default.Search, null) },
    trailingIcon = {
        if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                Icon(Icons.Default.Clear, "Clear search")
            }
        }
    },
    // Use VoyagerColors for styling
)
```

- [ ] **Step 2: Expose map toggle controls**

Add a row of toggle chips above the map:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(selected = showRoute, onClick = { showRoute = !showRoute },
        label = { Text("Route") })
    FilterChip(selected = showNumbers, onClick = { showNumbers = !showNumbers },
        label = { Text("Numbers") })
    FilterChip(selected = showCounts, onClick = { showCounts = !showCounts },
        label = { Text("Counts") })
}
```

- [ ] **Step 3: Enhance bottom sheet place details**

Update `EnhancedPlaceBottomSheet` to show additional Place metadata:
- `place.confidence` percentage
- `place.dominantActivity` and `place.dominantSemanticContext`
- `place.isUserRenamed` indicator
- Home/Work/Sleep derived flags
- Frequent derived flag (`visitCount >= 10`)
- "Rename Place" button → show `RenamePlaceDialog`

- [ ] **Step 4: Replace MatrixComponents with VoyagerComponents**

Replace all `MatrixCard`, `MatrixButton`, `MatrixIconButton`, `MatrixTextButton`, `MatrixBadge`, `MatrixDivider` usages with `VoyagerCard`, `VoyagerButton`, `VoyagerIconButton`, `VoyagerTextButton`, `VoyagerBadge`, `HorizontalDivider`.

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git commit -am "feat(map): add search bar, toggle controls, enhanced bottom sheet"
```

---

## Chunk 5: Timeline Screen Enhancements

Compact day summary, enriched segment cards.

### Task 10: Update TimelineScreen

**Files:**
- Modify: `presentation/screen/timeline/TimelineScreen.kt`
- Modify: `presentation/screen/timeline/TimelineViewModel.kt` (add pattern data)

- [ ] **Step 1: Add pattern data to TimelineViewModel**

Inject `AnalyzePlacePatternsUseCase` and add `placePatterns: Map<Long, PlacePattern?>` to `TimelineUiState`. Load patterns when date changes.

- [ ] **Step 2: Replace DaySummaryCard with compact strip**

Replace the existing `DaySummaryCard` composable with a compact single-row summary:

```kotlin
@Composable
private fun DaySummaryStrip(analytics: DayAnalytics, visibleCount: Int, totalCount: Int, onManageCategories: () -> Unit) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        // Row 1: "5 PLACES | 12.4 km | 6h 23m | 3 trips"
        // Row 2: "Longest: HOME 8h 30m" + category breakdown icons
        // Row 3: "3/5 visible [Manage Categories]" (conditional)
    }
}
```

- [ ] **Step 3: Enrich CONFIRMED_PLACE_VISIT cards**

Update the `MovementSegmentCard` for `SegmentType.CONFIRMED_PLACE_VISIT` to show:
- `Visit.confidence` percentage
- `place.visitCount` + derived average stay (`totalTimeSpent / visitCount`)
- Derived frequent flag (`visitCount >= 10`)
- Home/Work/Sleep derived flags (from `place.category` / `place.dominantSemanticContext`)
- Pattern info inline from `placePatterns[place.id]` — e.g., "Weekdays 9am-6pm"
- Rename button (in addition to long-press)

- [ ] **Step 4: Enrich TRANSIT cards**

Update TRANSIT segment rendering to show:
- `segment.averageSpeedKmh` formatted
- Activity icon from `segment.activityHint` (DRIVING→car, WALKING→walk, CYCLING→bike)
- `segment.coordinates.size` as "N GPS points"

- [ ] **Step 5: Replace all MatrixComponents with VoyagerComponents**

Same pattern as Map: replace all Matrix* usages with Voyager* equivalents.

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git commit -am "feat(timeline): compact summary, enriched segment cards, VoyagerComponents"
```

---

## Chunk 6: Insights Screen Merge

Merge InsightsScreen + StatisticsScreen + PlacePatternsScreen into one 6-tab screen.

### Task 11: Update StatisticsTab and StatisticsViewModel

**Files:**
- Modify: `presentation/screen/analytics/StatisticsScreen.kt`
- Modify: `presentation/screen/analytics/StatisticsViewModel.kt`

- [ ] **Step 1: Add OVERVIEW to StatisticsTab enum**

```kotlin
enum class StatisticsTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Lightbulb),  // NEW — first entry
    WEEKLY("Weekly", Icons.Default.DateRange),
    PATTERNS("Patterns", Icons.Default.Place),
    MOVEMENT("Movement", Icons.Default.TrendingUp),
    SOCIAL("Social", Icons.Default.Person),
    ANOMALIES("Anomalies", Icons.Default.Notifications)
}
```

- [ ] **Step 2: Update default selected tab to OVERVIEW**

In StatisticsScreen.kt, change the initial tab state:
```kotlin
// Was: var selectedTab by remember { mutableStateOf(StatisticsTab.WEEKLY) }
var selectedTab by remember { mutableStateOf(StatisticsTab.OVERVIEW) }
```

- [ ] **Step 3: Add Overview data to StatisticsViewModel**

Add to `StatisticsUiState`:

```kotlin
val insights: List<Insight> = emptyList(),                       // domain.model.Insight
val personalizedMessages: List<PersonalizedMessage> = emptyList(), // domain.model.PersonalizedMessage
val movementPatterns: List<MovementPattern> = emptyList(),         // domain.model.MovementPattern
```

Inject these into `StatisticsViewModel`:
- `com.cosmiclaboratory.voyager.domain.usecase.InsightEngine`
- `com.cosmiclaboratory.voyager.domain.usecase.PersonalizedInsightsGenerator`

Load Overview data in the existing `refresh()` method alongside other data.

- [ ] **Step 4: Write OverviewContent composable**

```kotlin
@Composable
private fun OverviewContent(
    insights: List<Insight>,
    personalizedMessages: List<PersonalizedMessage>,
    movementPatterns: List<MovementPattern>,
    periodLabel: String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Personalized insights section
        // Movement patterns section with PatternType icons
    }
}
```

- [ ] **Step 5: Wire OVERVIEW tab into StatisticsScreen composable**

Add the `OVERVIEW` case to the `when(selectedTab)` block:

```kotlin
StatisticsTab.OVERVIEW -> OverviewContent(
    insights = uiState.insights,
    personalizedMessages = uiState.personalizedMessages,
    movementPatterns = uiState.movementPatterns,
    periodLabel = uiState.periodLabel
)
```

- [ ] **Step 6: Replace all MatrixComponents in StatisticsScreen**

Replace Matrix* with Voyager* throughout StatisticsScreen.kt.

- [ ] **Step 7: Verify existing tab VMs still resolve**

Verify that the existing per-tab composables (WeeklyComparisonContent, MovementAnalyticsContent, SocialHealthAnalyticsContent, AnomalyContent) still have working `hiltViewModel()` calls. Each tab lazily creates its own ViewModel — confirm the `@HiltViewModel` classes still exist and are correctly wired.

Run: `grep -r "@HiltViewModel" app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/analytics/ --include="*.kt"`

Expected: WeeklyComparisonViewModel, MovementAnalyticsViewModel, SocialHealthAnalyticsViewModel should all appear.

- [ ] **Step 8: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git commit -am "feat(insights): merge Overview tab into StatisticsScreen with insights/patterns/messages"
```

### Task 12: Remove InsightsScreen navigation

**Files:**
- Modify: `presentation/navigation/VoyagerNavHost.kt`

- [ ] **Step 1: Update Insights route**

The Insights route already points to `StatisticsScreen()`. Verify this is the case. If InsightsScreen was previously the entry point, change the composable to render `StatisticsScreen()` directly.

- [ ] **Step 2: Remove InsightsScreen and PlacePatternsScreen from any remaining navigation**

Search for references:
```bash
grep -r "InsightsScreen\|PlacePatternsScreen" app/src/main/java/ --include="*.kt" -l
```

Remove or redirect any remaining references.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git commit -am "refactor(nav): point Insights route to unified StatisticsScreen"
```

---

## Chunk 7: Settings Screen Unification

Rewrite SettingsScreen with 4-tab layout. Absorb AdvancedSettingsScreen content.

### Task 13: Rewrite SettingsScreen with tabs

**Files:**
- Modify: `presentation/screen/settings/SettingsScreen.kt`

- [ ] **Step 1: Add tab enum and state**

```kotlin
enum class SettingsTab(val title: String) {
    GENERAL("General"),
    DETECTION("Detection"),
    PRIVACY_DATA("Privacy & Data"),
    ADVANCED("Advanced")
}
```

Add `var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }` to the composable.

> **Spec deviation note:** The spec says "SettingsViewModel: Add tab index state." We use composable-local `remember` state instead because tab selection doesn't need to survive process death or be shared across screens. This is intentional — no SettingsViewModel changes needed for tab state.

- [ ] **Step 2: Add ScrollableTabRow**

```kotlin
ScrollableTabRow(
    selectedTabIndex = selectedTab.ordinal,
    modifier = Modifier.fillMaxWidth()
) {
    SettingsTab.entries.forEach { tab ->
        Tab(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            text = { Text(tab.title) }
        )
    }
}
```

- [ ] **Step 3: Write GeneralTabContent**

Contains: Profile selector, Tracking toggle + accuracy mode + intervals, Tracking Quality (3 sliders), Notifications (5 toggles + frequency), Sleep Schedule, Categories link. Use existing `SettingsViewModel` methods — they all already exist.

Each section is a `CollapsibleSection` with VoyagerCard inside.

- [ ] **Step 4: Write DetectionTabContent**

Contains: Place Detection (8 sliders), Categorization Thresholds (5 sliders), Semantic Time Ranges (6 hour pickers), Geocoding Providers (checkboxes + API keys + cache settings), Automation (4 controls), Place Review (6 controls). All fields from spec Section 7.

Reuse existing `GeocodingProvidersSection` composable.

- [ ] **Step 5: Write PrivacyDataTabContent**

Contains: Privacy (4 toggles), Data Stats (3 stat items), Data Management (3 sliders), Export (format picker + 4 toggles + export button), Danger Zone (clear old data, clear all, reset defaults).

Reuse existing export/clear dialogs.

- [ ] **Step 6: Write AdvancedTabContent**

Contains all remaining sections from spec Section 7 Advanced tab. Migrate content from `AdvancedSettingsScreen.kt` — the sections for Quality Filtering, Stationary Detection, Activity Recognition, Timeline Settings, Pattern Analysis, Anomaly Detection, Battery & Performance, Service Config, UI Refresh, Daily Summary.

Also include Debug Tools (gated behind `isDeveloperMode`) and About section with version tap.

- [ ] **Step 7: Wire tabs into main SettingsScreen composable**

```kotlin
when (selectedTab) {
    SettingsTab.GENERAL -> GeneralTabContent(...)
    SettingsTab.DETECTION -> DetectionTabContent(...)
    SettingsTab.PRIVACY_DATA -> PrivacyDataTabContent(...)
    SettingsTab.ADVANCED -> AdvancedTabContent(...)
}
```

- [ ] **Step 8: Remove the `onNavigateToAdvancedSettings` parameter**

It's no longer needed since Advanced is now a tab. Update the composable signature. Fix any callers.

- [ ] **Step 9: Replace all MatrixComponents with VoyagerComponents**

- [ ] **Step 10: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git commit -am "feat(settings): rewrite as 4-tab unified screen with all UserPreferences fields"
```

---

## Chunk 8: Remaining Screens + Cleanup

Update PlaceReview, Categories, and delete old files.

### Task 14: Update PlaceReviewScreen and CategoriesScreen theme

**Files:**
- Modify: `presentation/screen/review/PlaceReviewScreen.kt`
- Modify: `presentation/screen/review/PlaceEditDialog.kt`
- Modify: `presentation/screen/categories/CategoriesScreen.kt`

- [ ] **Step 1: Replace MatrixComponents in PlaceReviewScreen**

Replace all Matrix* with Voyager* equivalents. No structural changes.

- [ ] **Step 2: Replace MatrixComponents in PlaceEditDialog**

Same — theme update only.

- [ ] **Step 3: Replace MatrixComponents in CategoriesScreen**

Same — theme update only.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git commit -am "style: update PlaceReview and Categories screens to VoyagerComponents"
```

### Task 15: Delete obsolete files

**Files:**
- Delete: `presentation/screen/track/TrackScreen.kt`
- Delete: `presentation/screen/track/TrackViewModel.kt`
- Delete: `presentation/screen/settings/AdvancedSettingsScreen.kt`
- Delete: `presentation/screen/insights/InsightsScreen.kt`
- Delete: `presentation/screen/insights/InsightsViewModel.kt`
- Delete: `presentation/screen/insights/PlacePatternsScreen.kt`
- Delete: `presentation/screen/insights/PlacePatternsViewModel.kt`
- Delete: `presentation/theme/GlassmorphismComponents.kt`
- Delete: `presentation/theme/MatrixComponents.kt` (replaced by VoyagerComponents.kt)

- [ ] **Step 1: Verify no remaining references to deleted files**

```bash
grep -r "TrackScreen\|TrackViewModel\|AdvancedSettingsScreen\|InsightsScreen\|InsightsViewModel\|PlacePatternsScreen\|PlacePatternsViewModel\|GlassmorphismComponents\|MatrixCard\|MatrixButton\|MatrixBadge\|MatrixDivider\|MatrixIconButton\|MatrixTextButton" app/src/main/java/ --include="*.kt" -l
```

Expected: No results (all references already updated in previous tasks). If any remain, fix them first.

- [ ] **Step 2: Delete the files**

```bash
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/track/TrackScreen.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/track/TrackViewModel.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/settings/AdvancedSettingsScreen.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/insights/InsightsScreen.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/insights/InsightsViewModel.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/insights/PlacePatternsScreen.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/insights/PlacePatternsViewModel.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/theme/GlassmorphismComponents.kt
git rm app/src/main/java/com/cosmiclaboratory/voyager/presentation/theme/MatrixComponents.kt
```

- [ ] **Step 3: Delete empty track directory if now empty**

```bash
rmdir app/src/main/java/com/cosmiclaboratory/voyager/presentation/screen/track/ 2>/dev/null || true
```

- [ ] **Step 4: Verify full build**

Run: `./gradlew assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git commit -am "chore: delete obsolete Track, AdvancedSettings, Insights, Glassmorphism files"
```

### Task 16: Final integration verification

- [ ] **Step 1: Run full build**

Run: `./gradlew assembleDebug 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run existing tests**

Run: `./gradlew test 2>&1 | tail -15`
Expected: All existing tests pass

- [ ] **Step 3: Verify navigation works**

Manual check (or document for manual QA):
- App launches on Home tab
- 4 bottom tabs visible: Home, Map, Timeline, Insights
- Settings accessible via top-right gear icon
- Place Review accessible via bell icon or Home card
- Map ↔ Timeline date sync works
- "View on Map" from Timeline works
- "View in Timeline" from Map bottom sheet works

- [ ] **Step 4: Final commit with clean state**

```bash
git status  # verify no uncommitted changes
```

---

## Task Dependency Map

```
Chunk 1 (Theme) → Chunk 2 (Nav) → Chunk 3 (Home)
                                 → Chunk 4 (Map)
                                 → Chunk 5 (Timeline)
                                 → Chunk 6 (Insights)
                                 → Chunk 7 (Settings)
                                 → Chunk 8 (Cleanup)
```

Chunks 3-7 can be done in parallel after Chunk 2 completes. Chunk 8 must be last.

## Test Coverage Note

This is a presentation-layer refactor. The existing project has no ViewModel or UI tests. Adding comprehensive test coverage for all new/modified ViewModels is out of scope for this plan but recommended as a follow-up. At minimum, Task 16 verifies:
- Full build passes (`assembleDebug`)
- All existing tests pass (`./gradlew test`)
- Manual verification checklist for navigation and screen rendering

**Recommended follow-up:** Add unit tests for DashboardViewModel (Track merge + places loading) and StatisticsViewModel (Overview data loading) using MockK + Turbine.

## Estimated Task Count
- 16 tasks
- ~85 steps
- 15 commits
