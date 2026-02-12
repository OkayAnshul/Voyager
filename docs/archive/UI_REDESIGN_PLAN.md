# Voyager UI Redesign - Complete Documentation

**Status**: In Progress
**Start Date**: December 12, 2025
**Target Completion**: 3 weeks (80 hours estimated)
**Design Theme**: "Matrix Terminal" - Black/Green Cyberpunk Aesthetic

---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Current State Analysis](#current-state-analysis)
3. [Design Philosophy](#design-philosophy)
4. [Visual Design System](#visual-design-system)
5. [Architecture Changes](#architecture-changes)
6. [Implementation Plan](#implementation-plan)
7. [Critical Issues & Solutions](#critical-issues--solutions)
8. [Screen-by-Screen Redesign](#screen-by-screen-redesign)
9. [New Features](#new-features)
10. [Testing Strategy](#testing-strategy)
11. [Migration Guide](#migration-guide)
12. [Success Metrics](#success-metrics)

---

## Executive Summary

### Overview
Complete transformation of the Voyager location tracking app from a glassmorphism purple/pink theme to a sleek, high-tech "Matrix Terminal" aesthetic with black background and green highlights. This redesign addresses critical performance issues, improves user control, and modernizes the visual identity.

### Key Objectives
1. **Visual Identity**: Transform from soft glassmorphism to sharp cyberpunk aesthetic
2. **Performance**: Eliminate periodic UI refreshes causing battery drain
3. **User Control**: Implement per-category visibility controls
4. **Integration**: Cross-screen date synchronization and inline workflows
5. **Polish**: Smooth animations, better Scaffold behavior, improved onboarding

### High-Level Changes
- **Theme**: Purple/Pink → Black/Green with Matrix aesthetic
- **Typography**: Default → JetBrains Mono (monospace/terminal style)
- **Components**: Glassmorphism → Sharp-cornered Matrix components
- **Navigation**: 5 bottom tabs (Dashboard, Map, Timeline, Categories, Insights)
- **Architecture**: Event-driven updates (removing periodic refresh timers)
- **New Screen**: Categories management with visibility controls
- **Enhanced Screens**: Timeline with inline reviews, Map with date selector

---

## Current State Analysis

### Existing UI Implementation

#### Screens (10 total)
1. **DashboardScreen** (479 lines) - Home with tracking stats
2. **MapScreen** (348 lines) - OSM-based map visualization
3. **TimelineScreen** (300+ lines) - Chronological visit history
4. **InsightsScreen** (300+ lines) - Analytics dashboard
5. **SettingsScreen** (500+ lines) - Configuration (81+ parameters)
6. **PlaceReviewScreen** (448 lines) - Place approval workflow
7. **PlacePatternsScreen** - Pattern analysis
8. **StatisticsScreen** - Detailed analytics
9. **DebugDataInsertionScreen** (655 lines) - Developer tools
10. **PermissionScreen** - Permission requests

#### Current Theme (Material 3 + Glassmorphism)
```kotlin
// Existing Colors
Purple80 (#D0BCFF), PurpleGrey80 (#CCC2DC), Pink80 (#EFB8C8)
Purple40 (#6650a4), PurpleGrey40 (#625b71), Pink40 (#7D5260)

// Glassmorphism Components (8 total)
- GlassCard: 10% white opacity, 16dp rounded corners
- GlassButton: 15% white opacity, gradient overlay
- GlassTextField: Frosted background
- GlassChip, GlassDivider, GlassSurface, etc.
```

#### Navigation Structure
- **Bottom Nav**: Dashboard, Map, Timeline, Insights, Settings
- **Nested Routes**: 13 additional destinations
- **Navigation**: Jetpack Compose NavHost with single Activity

#### State Management
- **11 ViewModels** managing separate screen states
- **AppStateManager** for centralized app state
- **StateEventDispatcher** for event-driven updates
- **EventListener** interface for reactive components

### Critical Issues Identified

#### Issue #1: Periodic UI Refresh
**Location**: `DashboardViewModel.kt:281-309`
**Problem**: `startPeriodicRefresh()` runs infinite loop with 30-120s intervals
- Causes unnecessary recompositions
- Drains battery with constant wake-ups
- Triggers analytics recalculation every 30s
- Redundant with event-driven updates already in place

**Impact**: High - affects performance and battery life

#### Issue #2: Scaffold Screen Shortening
**Problem**: Screens shorten when keyboard appears or navigation changes
- Missing `.imePadding()` modifier
- Incorrect window insets consumption
- Causes UI layout shifts and poor UX

**Impact**: Medium - affects user experience

#### Issue #3: Settings Overwhelm
**Problem**: 13+ component sections on single Settings screen
- Overwhelming for new users
- Difficult to find specific settings
- No progressive disclosure

**Impact**: Medium - affects usability

#### Issue #4: No Category Control
**Problem**: Categories always visible on map and timeline
- No user control over visibility
- Clutters UI for users who don't use categories
- All-or-nothing approach

**Impact**: Medium - affects user control and clarity

---

## Design Philosophy

### "Matrix Terminal" Theme Principles

#### 1. Technical Precision
- Sharp corners (4dp) instead of rounded
- Monospace typography for terminal aesthetic
- Green-on-black color scheme
- Minimal decorative elements

#### 2. Information Density Control
- Collapsible sections for progressive disclosure
- Hidden by default (categories, advanced options)
- Inline actions instead of navigation
- Animated visibility transitions

#### 3. Event-Driven Architecture
- Remove all periodic timers
- React to StateEventDispatcher events only
- Update only affected UI components
- Efficient StateFlow collection

#### 4. User Autonomy
- Per-category visibility controls
- Per-screen preferences
- Opt-in for advanced features
- Clear control hierarchy

#### 5. Cross-Screen Integration
- Shared date selection state
- Quick navigation between related views
- Contextual actions (View on Map, View in Timeline)
- Unified workflows

---

## Visual Design System

### Color Palette

#### Primary Colors
```kotlin
val MatrixGreen = Color(0xFF00FF41)        // Primary - bright Matrix green
val MatrixGreenDark = Color(0xFF00AA2B)    // Secondary - darker green accent
val MatrixGreenDim = Color(0xFF00FF41)     // Borders (0.3f alpha)
    .copy(alpha = 0.3f)
```

#### Background Colors
```kotlin
val MatrixBlack = Color(0xFF000000)        // Pure black (OLED optimized)
val MatrixGray = Color(0xFF1A1A1A)         // Surface color (subtle contrast)
val MatrixDarkGray = Color(0xFF0D0D0D)     // Elevated surfaces
```

#### Semantic Colors
```kotlin
val MatrixSuccess = MatrixGreen            // Success states
val MatrixWarning = Color(0xFFFFAA00)      // Warning states
val MatrixError = Color(0xFFFF0000)        // Error states
val MatrixInfo = MatrixGreenDark           // Informational
```

#### Color Application
- **Background**: Pure black (#000000) for all screens
- **Surface**: Dark gray (#1A1A1A) for cards and elevated components
- **Primary**: Matrix green for text, icons, highlights
- **Borders**: Green with 30% opacity for subtle outlines
- **Interactive Elements**: Full green on hover/focus, dim when inactive

### Typography

#### Font Family: JetBrains Mono
Professional monospace font designed for developers and terminal applications.

**Weights Used:**
- Regular (400) - Body text, labels
- Medium (500) - Headings, emphasis
- Bold (700) - Titles, strong emphasis

**Font Files** (to be added to `app/src/main/res/font/`):
- `jetbrainsmono_regular.ttf`
- `jetbrainsmono_medium.ttf`
- `jetbrainsmono_bold.ttf`

#### Type Scale
```kotlin
val MatrixTypography = Typography(
    // Large titles
    displayLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp
    ),

    // Screen titles
    headlineLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),

    // Section headers
    titleLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // Body text
    bodyLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),

    // Small text (captions, labels)
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### Shapes & Spacing

#### Corner Radius
- **Cards**: 4dp (sharp, minimal rounding)
- **Buttons**: 4dp (consistent with cards)
- **Text Fields**: 4dp (uniform across UI)
- **Chips**: 4dp (rectangular, not pill-shaped)

**Rationale**: Sharp corners convey technical precision and modern digital aesthetic, contrasting with the soft glassmorphism of the previous design.

#### Spacing Scale (8dp Grid System)
```kotlin
val SpacingXXS = 2.dp
val SpacingXS = 4.dp
val SpacingSM = 8.dp
val SpacingMD = 16.dp
val SpacingLG = 24.dp
val SpacingXL = 32.dp
val SpacingXXL = 48.dp
```

**Application**:
- Card padding: 16.dp (MD)
- Section spacing: 8.dp (SM) vertically
- Screen padding: 16.dp (MD) horizontal
- Large gaps: 24.dp (LG) between major sections

#### Elevation & Borders
- **Elevation**: Minimal (2dp max) - rely on borders instead
- **Border Width**: 1dp for all components
- **Border Color**: MatrixGreenDim (30% opacity)
- **Border Style**: Solid lines, no dashed or dotted

### Component Design

#### MatrixCard
**Visual Properties**:
- Background: MatrixGray (#1A1A1A)
- Border: 1dp MatrixGreenDim
- Corner radius: 4dp
- Padding: 16.dp
- Elevation: 2.dp (subtle shadow)

**Usage**: Primary container for grouped content

#### MatrixButton
**Variants**:
1. **Primary** (outlined): Transparent background, green border
2. **Filled**: Green background, black text
3. **Text**: No background, green text

**States**:
- Default: Green border, transparent
- Hover: Slight green tint (10% opacity)
- Pressed: Darker green tint (20% opacity)
- Disabled: Dim green (50% opacity)

#### MatrixTextField
**Visual Properties**:
- Background: Transparent
- Border: 1dp green (focused), dim green (unfocused)
- Text color: MatrixGreen
- Cursor: MatrixGreen with pulse animation
- Placeholder: MatrixGreenDim

**Behavior**:
- Focus: Border brightens, cursor appears
- Error: Border turns red, error text below
- Disabled: Grayed out, no interaction

#### MatrixChip
**Visual Properties**:
- Background: Transparent (unselected), MatrixGreenDark (selected)
- Border: 1dp MatrixGreenDim
- Padding: 12.dp horizontal, 6.dp vertical
- Text: Uppercase for emphasis

**Usage**: Categories, tags, filter options

#### MatrixDivider
**Visual Properties**:
- Color: MatrixGreenDim
- Thickness: 1.dp
- Full-width by default

**Usage**: Section separators, visual hierarchy

### Iconography

#### Icon Library
**Primary**: Material Icons Extended (androidx.compose.material:material-icons-extended:1.6.0)
- 2000+ icons
- Consistent style
- Optimized for Compose

#### Icon Sizing
- **Extra Small**: 16.dp (inline with text)
- **Small**: 20.dp (list items)
- **Medium**: 24.dp (standard, toolbar)
- **Large**: 32.dp (featured icons)
- **Extra Large**: 48.dp (empty states)

#### Icon Colors
- **Active**: MatrixGreen (full opacity)
- **Inactive**: MatrixGreenDim (30% opacity)
- **Disabled**: Gray (20% opacity)

### Animation Guidelines

#### Transition Timing
- **Fast**: 150ms (micro-interactions, button presses)
- **Medium**: 300ms (content changes, expansions)
- **Slow**: 500ms (screen transitions, emphasis)

#### Easing Functions
- **Standard**: FastOutSlowInEasing (most transitions)
- **Entrance**: FastOutLinearInEasing (appearing elements)
- **Exit**: LinearOutSlowInEasing (disappearing elements)

#### Animated Elements
1. **Collapsible Sections**: AnimatedVisibility with slideIn/slideOut
2. **Live Status Badge**: Pulsing green dot (infinite sine wave)
3. **Screen Transitions**: Slide + fade crossfade
4. **Loading States**: Shimmer effect (green tint)
5. **Success Feedback**: Scale up + fade in green checkmark

---

## Architecture Changes

### State Management Improvements

#### Remove Periodic Refresh Pattern
**Before** (DashboardViewModel.kt):
```kotlin
private fun startPeriodicRefresh() {
    viewModelScope.launch {
        while (true) {
            val refreshInterval = if (currentAppState.currentPlace != null) {
                30000L // 30 seconds at place
            } else if (currentAppState.locationTracking.isActive) {
                60000L // 60 seconds tracking
            } else {
                120000L // 2 minutes idle
            }

            kotlinx.coroutines.delay(refreshInterval)
            loadDashboardData() // Full refresh every cycle
        }
    }
}
```

**After** (Event-Driven):
```kotlin
// Removed startPeriodicRefresh() entirely

// Enhanced event handling with granular updates
override suspend fun onStateEvent(event: StateEvent) {
    when (event.type) {
        EventTypes.LOCATION_UPDATE -> {
            // Update only duration, not full refresh
            if (_uiState.value.isAtPlace) {
                _uiState.update { it.copy(
                    currentVisitDuration = calculateCurrentVisitDuration()
                )}
            }
        }
        EventTypes.PLACE_ENTERED, EventTypes.PLACE_EXITED -> {
            // Full refresh only on significant events
            loadDashboardData()
        }
        EventTypes.TRACKING_STARTED, EventTypes.TRACKING_STOPPED -> {
            // Update single field
            _uiState.update { it.copy(
                isTracking = event.type == EventTypes.TRACKING_STARTED
            )}
        }
    }
}
```

**Benefits**:
- ✅ No unnecessary wake-ups (battery savings)
- ✅ Updates only when data actually changes
- ✅ Granular updates (single field vs full state)
- ✅ Leverages existing event infrastructure

#### Shared UI State for Cross-Screen Coordination

**New**: `SharedUiState.kt`
```kotlin
@Singleton
class SharedUiState @Inject constructor() {
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _selectedPlace = MutableStateFlow<Place?>(null)
    val selectedPlace: StateFlow<Place?> = _selectedPlace.asStateFlow()

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun selectPlace(place: Place?) {
        _selectedPlace.value = place
    }

    fun clearSelection() {
        _selectedPlace.value = null
    }
}
```

**Usage**:
- Map and Timeline share selectedDate
- Changing date on Map updates Timeline automatically
- "View on Map" from Timeline preserves selected place
- Centralized navigation context

### Navigation Architecture Updates

#### Before (5 bottom nav items):
```kotlin
val bottomNavItems = listOf(
    Dashboard, Map, Timeline, Insights, Settings
)
```

#### After (Categories replaces Settings):
```kotlin
val bottomNavItems = listOf(
    Dashboard, Map, Timeline, Categories, Insights
)

// Settings moved to top-right menu
```

#### New Destination: Categories
```kotlin
object Categories : VoyagerDestination(
    route = "categories",
    title = "Categories",
    icon = Icons.Default.Category // Material Icons Extended
)
```

### Data Model Extensions

#### Category Visibility Settings
**New data structures** (add to `UserPreferences` or create separate):

```kotlin
@Entity(tableName = "category_visibility")
data class CategoryVisibilitySettings(
    @PrimaryKey val categoryId: String,
    val category: PlaceCategory,
    val showOnMap: Boolean = false,
    val showOnTimeline: Boolean = false,
    val enableNotifications: Boolean = false,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
```

**Repository methods**:
```kotlin
interface CategoryVisibilityRepository {
    fun getCategoryVisibility(category: PlaceCategory): Flow<CategoryVisibility>
    fun getAllCategoryVisibility(): Flow<Map<PlaceCategory, CategoryVisibility>>
    suspend fun updateCategoryVisibility(category: PlaceCategory, visibility: CategoryVisibility)
    suspend fun resetAllVisibility()
}
```

**ViewModel integration**:
```kotlin
// MapViewModel filters places based on category visibility
val visiblePlaces = combine(
    placeRepository.getAllPlaces(),
    categoryVisibilityRepository.getAllCategoryVisibility()
) { places, visibilityMap ->
    places.filter { place ->
        visibilityMap[place.category]?.showOnMap == true
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

---

## Implementation Plan

### Phase 1: Foundation (Week 1 - 25 hours)

#### Task 1.1: Theme System Redesign (6 hours)

**Step 1: Download JetBrains Mono Font**
1. Visit [Google Fonts - JetBrains Mono](https://fonts.google.com/specimen/JetBrains+Mono)
2. Download Regular (400), Medium (500), Bold (700) weights
3. Convert to `.ttf` format if needed
4. Place in `app/src/main/res/font/` directory

**Step 2: Create Color.kt**
```kotlin
// File: app/src/main/java/com/cosmiclaboratory/voyager/ui/theme/Color.kt
package com.cosmiclaboratory.voyager.ui.theme

import androidx.compose.ui.graphics.Color

// Matrix Green Palette
val MatrixGreen = Color(0xFF00FF41)
val MatrixGreenDark = Color(0xFF00AA2B)
val MatrixGreenDim = Color(0xFF00FF41).copy(alpha = 0.3f)

// Background Palette
val MatrixBlack = Color(0xFF000000)
val MatrixGray = Color(0xFF1A1A1A)
val MatrixDarkGray = Color(0xFF0D0D0D)

// Semantic Colors
val MatrixSuccess = MatrixGreen
val MatrixWarning = Color(0xFFFFAA00)
val MatrixError = Color(0xFFFF0000)
val MatrixInfo = MatrixGreenDark

// Legacy colors (keep for gradual migration)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

**Step 3: Create Type.kt**
```kotlin
// File: app/src/main/java/com/cosmiclaboratory/voyager/ui/theme/Type.kt
package com.cosmiclaboratory.voyager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cosmiclaboratory.voyager.R

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold)
)

val MatrixTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

**Step 4: Update Theme.kt**
```kotlin
// File: app/src/main/java/com/cosmiclaboratory/voyager/ui/theme/Theme.kt
package com.cosmiclaboratory.voyager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Matrix Dark Color Scheme (primary theme)
private val MatrixDarkColorScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = MatrixBlack,
    primaryContainer = MatrixGreenDark,
    onPrimaryContainer = MatrixGreen,

    secondary = MatrixGreenDark,
    onSecondary = MatrixBlack,
    secondaryContainer = MatrixGray,
    onSecondaryContainer = MatrixGreen,

    tertiary = MatrixGreenDim,
    onTertiary = MatrixBlack,

    background = MatrixBlack,
    onBackground = MatrixGreen,

    surface = MatrixGray,
    onSurface = MatrixGreen,
    surfaceVariant = MatrixDarkGray,
    onSurfaceVariant = MatrixGreenDim,

    outline = MatrixGreenDim,
    outlineVariant = MatrixGreenDim,

    error = MatrixError,
    onError = MatrixBlack,
    errorContainer = MatrixError.copy(alpha = 0.2f),
    onErrorContainer = MatrixError
)

// Legacy color schemes (keep for rollback)
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

@Composable
fun VoyagerTheme(
    darkTheme: Boolean = true, // Force dark theme (Matrix is always dark)
    dynamicColor: Boolean = false, // Disable dynamic color
    content: @Composable () -> Unit
) {
    // Always use Matrix color scheme
    val colorScheme = MatrixDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MatrixTypography,
        content = content
    )
}
```

**Step 5: Update build.gradle**
```kotlin
// File: app/build.gradle.kts
dependencies {
    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // Existing dependencies...
}
```

#### Task 1.2: Create Matrix Components (8 hours)

**Full implementation** in `MatrixComponents.kt` - see plan file for complete code.

Key components to implement:
1. `MatrixCard` - Primary container
2. `MatrixButton` - Action buttons
3. `MatrixTextField` - Input fields
4. `MatrixDivider` - Separators
5. `MatrixChip` - Tags/categories
6. `MatrixIconButton` - Icon-only buttons
7. Helper composables (CollapsibleSection, LiveStatusBadge, etc.)

#### Task 1.3-1.5: Navigation, Scaffold, ViewModel Fixes
See implementation plan in main plan file.

### Phase 2 & 3: Screen Redesigns and Polish
Detailed in main plan file - see `/home/anshul/.claude/plans/adaptive-giggling-aurora.md`

---

## Critical Issues & Solutions

### Issue 1: Periodic Refresh Performance

**Problem**: DashboardViewModel (and other ViewModels) use infinite while loops with delays to periodically refresh data every 30-120 seconds. This causes:
- Unnecessary battery drain
- Redundant analytics calculations
- UI recompositions even when data hasn't changed
- Conflicts with existing event-driven updates

**Root Cause**: Attempted solution for "keeping UI fresh" but duplicates StateEventDispatcher functionality.

**Solution**:
1. Remove `startPeriodicRefresh()` function entirely
2. Remove call from ViewModel init
3. Enhance `onStateEvent()` handler with granular updates:
   - `LOCATION_UPDATE` → Update duration only (if at place)
   - `PLACE_ENTERED/EXITED` → Full data refresh
   - `TRACKING_STARTED/STOPPED` → Update tracking status only
4. Leverage existing AppStateManager StateFlow collection
5. Apply same pattern to MapViewModel, TimelineViewModel, etc.

**Verification**:
- Monitor with Android Profiler (CPU usage should drop)
- Check battery stats (less wake-ups)
- Verify UI still updates on actual events

### Issue 2: Scaffold Screen Shortening

**Problem**: When keyboard appears or navigation changes, screen content gets shortened and layout shifts occur.

**Root Cause**:
- Missing `.imePadding()` on Scaffold
- Incorrect window insets consumption
- Padding values not properly consumed

**Solution**:
```kotlin
Scaffold(
    modifier = Modifier
        .fillMaxSize()
        .imePadding(), // Add this
    // ... other params
) { paddingValues ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues) // Add this
    ) {
        // Content
    }
}
```

**Verification**:
- Open keyboard in search fields
- Navigate between screens
- Verify content doesn't shift or shorten

### Issue 3: Category Visibility Control

**Problem**: Categories always shown on map and timeline with no user control.

**Solution**: New Categories screen with per-category toggles for:
- Show on Map
- Show on Timeline
- Enable Notifications

Implementation details in Screen Redesign section.

---

## Screen-by-Screen Redesign

### Dashboard Screen

**Before**: Glassmorphism cards, periodic refresh, manual place detection button
**After**: Matrix cards, live status badge, collapsible sections, inline reviews

**Key Changes**:
1. **Live Status Badge**: Pulsing green dot + status text
2. **Collapsible Sections**: Hide/show stats with AnimatedVisibility
3. **Inline Reviews**: Show pending count with quick navigation
4. **Event-Driven**: No periodic refresh

**New Components**:
- `LiveStatusBadge`: Real-time tracking status
- `CollapsibleSection`: Animated expand/collapse wrapper
- `InlineReviewCard`: Quick review access
- `QuickActionsRow`: Toggle tracking, trigger detection

### Categories Screen (NEW)

**Purpose**: Per-category visibility management

**Layout**:
- Header with explanation
- List of all categories (14 predefined + custom)
- Each category has:
  - Icon + name
  - Show on Map toggle
  - Show on Timeline toggle
  - Enable Notifications toggle

**Data Model**:
```kotlin
data class CategoryVisibility(
    val showOnMap: Boolean = false,
    val showOnTimeline: Boolean = false,
    val enableNotifications: Boolean = false
)
```

**Integration**:
- MapViewModel filters places by `showOnMap`
- TimelineViewModel filters segments by `showOnTimeline`
- Default: All categories hidden

### Map Screen

**Before**: Static map, no date selection, all places shown
**After**: Date selector, filtered by category visibility, cross-links to Timeline

**Key Changes**:
1. **Date Selector**: Synced with Timeline via SharedUiState
2. **Category Filtering**: Only show places with `showOnMap = true`
3. **Green Markers**: Replace default colors
4. **Bottom Sheet**: Enhanced with "View in Timeline" action

### Timeline Screen

**Before**: Date selector, visit list, navigate to review screen
**After**: Synced date selector, inline reviews, category filtering, "View on Map" links

**Key Changes**:
1. **Inline Reviews**: Approve/reject without navigation
2. **Category Filtering**: Only show visits with `showOnTimeline = true`
3. **Quick Actions**: "View on Map" button for each segment
4. **Date Sync**: Share selected date with Map

---

## New Features

### 1. Category Visibility System
- Per-category toggles (map, timeline, notifications)
- Hidden by default
- Persistent user preferences
- Real-time filtering

### 2. Cross-Screen Date Synchronization
- SharedUiState holds selected date
- Map and Timeline observe and update
- Seamless navigation between views

### 3. Inline Review Workflow
- Review pending places from Timeline
- Quick approve/reject actions
- No navigation required
- Reduces friction

### 4. Live Status Indicator
- Pulsing green dot when tracking
- Status text (tracking/at place/disabled)
- Real-time updates via AppStateManager

### 5. Collapsible Sections
- Progressive disclosure
- Reduce information overload
- Smooth animations

---

## Testing Strategy

### Manual Testing Checklist

#### Visual Consistency
- [ ] All screens use black background
- [ ] All text is Matrix green or dim green
- [ ] All components have 4dp corners
- [ ] JetBrains Mono font applied everywhere
- [ ] No glassmorphism effects remain

#### Functional Testing
- [ ] No periodic refreshes (monitor for 5+ minutes)
- [ ] Categories hidden by default
- [ ] Toggling category visibility filters map/timeline
- [ ] Date changes on Map update Timeline
- [ ] Date changes on Timeline update Map
- [ ] Inline reviews approve/reject correctly
- [ ] Keyboard doesn't shorten screens
- [ ] Navigation doesn't cause layout shifts

#### Performance Testing
- [ ] CPU usage lower than before (Android Profiler)
- [ ] Battery usage reduced (check wake locks)
- [ ] Smooth 60fps animations
- [ ] No lag on screen transitions
- [ ] Event-driven updates only

#### Integration Testing
- [ ] "View on Map" from Timeline works
- [ ] "View in Timeline" from Map works
- [ ] Category changes reflect immediately
- [ ] Review approval updates timeline
- [ ] Permission flow completes smoothly

---

## Migration Guide

### For Users

#### Visual Changes
- App now uses black/green color scheme
- Text appears in monospace font
- Sharper, more angular design
- Categories hidden by default (enable in Categories screen)

#### Behavioral Changes
- Location tracking starts automatically after permissions
- Categories must be enabled to show on map/timeline
- Reviews can be approved directly from Timeline
- Date selection synced between Map and Timeline

#### No Data Loss
- All existing places, visits, and preferences preserved
- Category visibility starts as "hidden" (user must enable)
- Tracking continues as before

### For Developers

#### Code Changes
- Replace `GlassCard` → `MatrixCard` globally
- Replace `GlassButton` → `MatrixButton` globally
- Remove `startPeriodicRefresh()` from all ViewModels
- Add `.imePadding()` to Scaffold
- Use SharedUiState for cross-screen state

#### New Dependencies
```kotlin
implementation("androidx.compose.material:material-icons-extended:1.6.0")
```

#### Database Migration
Add `CategoryVisibility` table (if using Room):
```sql
CREATE TABLE category_visibility (
    categoryId TEXT PRIMARY KEY,
    category TEXT NOT NULL,
    showOnMap INTEGER NOT NULL DEFAULT 0,
    showOnTimeline INTEGER NOT NULL DEFAULT 0,
    enableNotifications INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL
);
```

---

## Success Metrics

### Visual Quality
- ✅ 100% of screens use Matrix theme
- ✅ 0 glassmorphism components remain
- ✅ Consistent 4dp corner radius
- ✅ JetBrains Mono font across all text

### Performance
- 🎯 70%+ reduction in periodic wake-ups
- 🎯 50%+ reduction in unnecessary recompositions
- 🎯 Smooth 60fps animations on mid-range devices
- 🎯 Battery usage improvement (measured over 24h)

### User Control
- ✅ Per-category visibility toggles working
- ✅ All categories hidden by default
- ✅ User can enable/disable independently

### Integration
- ✅ Date sync between Map and Timeline
- ✅ Inline reviews functional
- ✅ Cross-navigation links working
- ✅ No layout shifts on keyboard/navigation

---

## Timeline & Resources

### Estimated Effort
- **Phase 1** (Foundation): 25 hours
- **Phase 2** (Screens): 30 hours
- **Phase 3** (Polish): 25 hours
- **Total**: 80 hours (2-3 weeks at 30-40 hours/week)

### Critical Path
1. Theme + Components (14 hours) - MUST complete first
2. Fix DashboardViewModel (6 hours) - Critical for performance
3. Categories Screen (10 hours) - Blocking Map/Timeline filtering
4. Map/Timeline enhancements (12 hours) - Depends on Categories
5. Testing & Polish (5 hours) - Final phase

### Resources Required
- Android developer (1) - Full implementation
- Designer (optional) - Icon selection, visual QA
- QA tester (optional) - Manual testing

---

## Appendix

### File Structure
```
app/src/main/java/com/cosmiclaboratory/voyager/
├── ui/theme/
│   ├── Color.kt (MODIFIED - Matrix colors)
│   ├── Theme.kt (MODIFIED - Matrix theme)
│   └── Type.kt (NEW - JetBrains Mono typography)
├── presentation/
│   ├── theme/
│   │   ├── MatrixComponents.kt (NEW - Component library)
│   │   └── GlassmorphismComponents.kt (DEPRECATED - keep for migration)
│   ├── state/
│   │   └── SharedUiState.kt (NEW - Cross-screen state)
│   ├── screen/
│   │   ├── dashboard/ (MODIFIED - Matrix redesign)
│   │   ├── categories/ (NEW - Category management)
│   │   ├── map/ (MODIFIED - Date selector, filtering)
│   │   ├── timeline/ (MODIFIED - Inline reviews, filtering)
│   │   ├── settings/ (MODIFIED - Reorganized)
│   │   └── onboarding/ (NEW - Onboarding flow)
│   └── navigation/
│       └── VoyagerDestination.kt (MODIFIED - Categories added)
└── MainActivity.kt (MODIFIED - Scaffold fixes)
```

### Reference Links
- [JetBrains Mono Font](https://fonts.google.com/specimen/JetBrains+Mono)
- [Material Icons Extended](https://fonts.google.com/icons)
- [Material 3 Design Guidelines](https://m3.material.io/)
- [Compose Animation Documentation](https://developer.android.com/jetpack/compose/animation)

---

**Document Version**: 1.0
**Last Updated**: December 12, 2025
**Maintained By**: Voyager Development Team
