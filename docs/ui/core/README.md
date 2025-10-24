# 🔧 Core UI Features (Must Have)

## Overview
This document outlines the essential UI components and features that form the foundation of the Voyager location tracking application. These are **must-have** features required for the minimum viable product.

## Navigation & Structure

### Bottom Navigation Bar
- **Priority**: Must Have ⭐⭐⭐⭐⭐
- **Description**: Main navigation between app sections
- **Components**: 
  - Dashboard (Home icon)
  - Map (Place icon) 
  - Timeline (List icon)
  - Insights (Info icon)
  - Settings (Settings icon)
- **Implementation**: Material 3 NavigationBar
- **File**: `VoyagerDestination.kt`, `VoyagerNavHost.kt`

### Top App Bars
- **Priority**: Must Have ⭐⭐⭐⭐⭐
- **Description**: Screen headers with titles and actions
- **Features**: Title display, action buttons, navigation icons
- **Implementation**: Material 3 TopAppBar
- **Usage**: All main screens

## Core Screens

### 1. Dashboard Screen
- **Priority**: Must Have ⭐⭐⭐⭐⭐
- **File**: `DashboardScreen.kt`
- **Purpose**: Central hub for key metrics and quick actions

#### Essential Components:
- **Stats Cards**: Display key metrics
  - Total Locations tracked
  - Total Places discovered
  - Total Time tracked
  - Places visited today

- **Current Status Display**:
  - Location tracking status (ON/OFF)
  - Current place (if at a known place)
  - Real-time visit duration

- **Quick Action Buttons**:
  - Toggle location tracking
  - Manual place detection
  - View current day summary

#### Data Requirements:
```kotlin
data class DashboardUiState(
    val totalLocations: Int = 0,
    val totalPlaces: Int = 0, 
    val totalTimeTracked: Long = 0L,
    val isTracking: Boolean = false,
    val currentPlace: Place? = null,
    val isAtPlace: Boolean = false,
    val currentVisitDuration: Long = 0L
)
```

### 2. Map Screen  
- **Priority**: Must Have ⭐⭐⭐⭐⭐
- **File**: `MapScreen.kt`
- **Purpose**: Visual representation of places and locations

#### Essential Components:
- **Interactive Map**: OpenStreetMap integration
- **Place Markers**: Visual indicators for saved places
- **User Location**: Current position indicator
- **Map Controls**:
  - Center on user button
  - Zoom controls
  - Layer toggles (places, routes)

#### Core Features:
- Display all saved places as markers
- Show current user location
- Basic zoom and pan functionality
- Place information on marker tap

### 3. Timeline Screen
- **Priority**: Must Have ⭐⭐⭐⭐
- **File**: `TimelineScreen.kt`  
- **Purpose**: Historical view of daily activities

#### Essential Components:
- **Date Navigation**: Browse different days
- **Daily Summary Cards**: Key metrics per day
  - Places visited
  - Time tracked
  - Distance traveled
- **Visit List**: Chronological list of place visits
- **Basic Filtering**: By date range

### 4. Settings Screen
- **Priority**: Must Have ⭐⭐⭐⭐⭐
- **File**: `SettingsScreen.kt`
- **Purpose**: Core app configuration

#### Essential Settings:
- **Location Tracking**: Enable/disable tracking
- **Location Accuracy**: High/Balanced/Low power
- **Place Detection**: Automatic vs manual
- **Data Management**: Clear data, export options
- **Privacy**: Data retention settings

### 5. Permission Gateway Screen
- **Priority**: Must Have ⭐⭐⭐⭐⭐
- **File**: `PermissionGatewayScreen.kt`
- **Purpose**: Handle required permissions

#### Essential Components:
- **Permission Status Display**: Visual indicators
- **Permission Request Cards**: Clear explanations
- **Action Buttons**: Request permissions
- **Fallback Options**: Limited functionality mode

## Essential UI Components

### Stats Card Component
```kotlin
@Composable
fun StatsCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector? = null
)
```
- **Purpose**: Display key metrics consistently
- **Usage**: Dashboard, settings, insights
- **Design**: Material 3 Card with elevation

### Permission Request Card
```kotlin
@Composable  
fun PermissionRequestCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestClick: () -> Unit
)
```
- **Purpose**: Guide users through permission setup
- **Usage**: Permission gateway, settings
- **Design**: Informative card with clear call-to-action

### Loading States
- **Progress Indicators**: For data loading
- **Skeleton Screens**: While fetching analytics
- **Error States**: When operations fail
- **Empty States**: When no data available

## Data Binding Requirements

### Real-time Updates
- Location tracking status changes
- Current place detection
- Visit duration updates  
- Daily statistics refresh

### State Management
- Centralized state via `AppStateManager`
- Reactive UI updates via StateFlow
- Error handling and recovery

## Accessibility Requirements

### Essential A11y Features
- Screen reader support
- Keyboard navigation
- High contrast support
- Text scaling compatibility
- Focus management

### Implementation:
- Semantic content descriptions
- Proper heading hierarchy
- Touch target sizing (48dp minimum)
- Color contrast ratios (4.5:1 minimum)

## Performance Requirements

### Must Meet Benchmarks:
- Screen load time: < 2 seconds
- Navigation transitions: < 300ms
- Data refresh: < 5 seconds
- Memory usage: < 100MB for core screens

## Testing Requirements

### Essential Test Cases:
- Navigation between all screens
- Permission request flows
- Data loading and error states
- Offline functionality
- Configuration persistence

## Implementation Checklist

- [ ] Bottom navigation setup
- [ ] All 5 core screens implemented
- [ ] Essential UI components created
- [ ] Permission handling complete
- [ ] Basic data binding functional
- [ ] Error states handled
- [ ] Loading states implemented
- [ ] Accessibility features added
- [ ] Performance benchmarks met
- [ ] Core functionality tested

## Next Steps
Once core features are complete, proceed to [Advanced UI Features](../advanced/README.md) for enhanced user experience.