# Voyager UI/UX Redesign — Design Specification

**Date:** 2026-03-16
**Status:** Approved (Reviewed & Fixed)
**Approach:** B+C Mix — Polished Dark UI with Modern Redesign Patterns

> **Note on file paths:** All file paths in this spec are relative to
> `app/src/main/java/com/cosmiclaboratory/voyager/` unless prefixed with a different root.

## 1. Design Philosophy

- **Theme evolution:** Retain dark mode + teal accent identity, but evolve away from "hacker terminal" aesthetic toward a polished dark UI (Linear/Arc inspired). Softer corners (12dp), reduced ALL-CAPS (section headers only), subtle surface elevation, better typography hierarchy.
- **Grounded in existing capabilities:** Every field, function, and data source referenced in this spec exists in the current codebase. No speculative features.
- **Progressive complexity:** Casual users see a clean dashboard with today's places. Power users can drill into 100+ configurable parameters across 4 settings tabs. Quantified-self enthusiasts get a 6-tab analytics hub.
- **All data surfaced:** Every `UserPreferences` field, every `Place` metadata field, every analytics capability is accessible from the UI.

## 2. Navigation Architecture

### Current
- 5-tab bottom nav: Dashboard, Map, Timeline, Track, Insights
- Settings in overflow menu
- AdvancedSettings as separate nested screen

### Proposed
- **4-tab bottom nav:** Home, Map, Timeline, Insights
- **Persistent top bar:** App title (left), notification bell with pending review count badge (right), settings gear icon (right)
- **Track screen eliminated:** Merged into Home (Dashboard)
- **AdvancedSettings eliminated:** Merged into unified Settings 4-tab screen
- **InsightsScreen + StatisticsScreen merged:** Into single 6-tab Insights screen

### Navigation Map
```
Bottom Nav:
  Home ──────── (start destination)
  Map
  Timeline
  Insights

Top Bar (persistent):
  🔔 Bell → PlaceReview screen
  ⚙ Gear → Settings screen

Settings (4 tabs):
  General │ Detection │ Privacy & Data │ Advanced

Nested Screens (push onto back stack):
  PlaceReview (from bell icon or Home card)
  Categories (from Settings > General > Categories)
  DeveloperProfile (from Settings > About)
  DebugDataInsertion (from Settings > Advanced > Debug, dev mode only)
```

### Route Changes
| Current Route | New Route | Notes |
|---|---|---|
| `dashboard` | `home` | Renamed, merged with Track |
| `track` | **REMOVED** | Merged into Home |
| `map` | `map` | No change |
| `timeline` | `timeline` | No change |
| `insights` | `insights` | Now hosts merged StatisticsScreen |
| `settings` | `settings` | Unified 4-tab screen |
| `advanced_settings` | **REMOVED** | Merged into Settings Advanced tab |
| `place_review` | `place_review` | No change |
| `categories` | `categories` | No change |
| `developer_profile` | `developer_profile` | No change |
| `debug_data_insertion` | `debug_data_insertion` | No change |

### VoyagerDestination Changes
```kotlin
// REMOVE:
object Track
object AdvancedSettings

// RENAME:
object Dashboard → object Home (route = "home")

// bottomNavItems becomes:
listOf(Home, Map, Timeline, Insights)

// menuItems becomes empty (Settings accessed via top bar icon)
```

## 3. Home (Dashboard) Screen

### Overview
The command center. Merges Track functionality. Shows live tracking, today's stats, pending reviews, today's places, all places, and quick actions.

### Data Sources
| UI Element | Source | Field/Method |
|---|---|---|
| Live tracking status | `DashboardViewModel` | `isLocationTrackingActive`, `isTracking` |
| Tracking toggle | `DashboardViewModel` | `toggleLocationTracking()` |
| Current place | `DashboardViewModel` | `currentPlace: Place?` |
| Current visit duration | `DashboardViewModel` | `currentVisitDuration` (live ticking) |
| At place flag | `DashboardViewModel` | `isAtPlace` |
| Tracking session duration | `TrackViewModel` (merge) | `trackingDuration` |
| Session location count | `TrackViewModel` (merge) | from `CurrentState.sessionSavedCount` |
| Frequent flag (derived) | Computed | `place.visitCount >= 10` (threshold-based) |
| Home/Work/Sleep flags (derived) | Computed | `place.category == HOME/WORK` or `place.dominantSemanticContext == RELAXING_HOME` |
| Average stay (derived) | Computed | `place.totalTimeSpent / place.visitCount` |
| Today's places count | `DayAnalytics` | `placesVisited` |
| Today's distance | `DayAnalytics` | `distanceTraveled` |
| Today's tracked time | `DashboardViewModel` | `totalTimeTracked` |
| Pending review count | `DashboardViewModel` | `pendingReviewCount` |
| Pending reviews by priority | `PlaceReviewUseCases` | `getPendingReviewsByPriority()` |
| Today's visited places | `PlaceRepository` | `getPlacesVisitedOnDate(today)` |
| Today's visits | `VisitRepository` | `getVisitsBetween(todayStart, todayEnd)` |
| All places | `PlaceRepository` | `getAllPlaces()`, `getMostVisitedPlaces()`, `getPlacesWithMostTime()` |
| Place patterns | `AnalyzePlacePatternsUseCase` | `invoke()` for inline pattern display |
| Place detection trigger | `DashboardViewModel` | `triggerPlaceDetection()` |
| Export | `SettingsViewModel` | `exportData()` |
| Debug tools | `DashboardViewModel` | `debugGetDiagnosticInfo()`, `debugWorkManagerHealth()`, etc. |
| Developer mode gate | `DeveloperModeManager` | `isDeveloperModeEnabled` |

### Layout (top to bottom)

1. **Hero Card — Live Tracking Status** (always visible)
   - Pulsing green dot when tracking active
   - Current place name, address, category icon
   - Live-ticking visit duration (recompose every 1s)
   - Tracking toggle switch
   - Session duration + location count (merged from Track)
   - Error message display if any (`errorMessage`)

2. **Stat Grid** (3 compact cards in a row)
   - Places today (from `DayAnalytics.placesVisited`)
   - Distance today (from `DayAnalytics.distanceTraveled`, formatted km)
   - Time tracked today (from `totalTimeTracked`, formatted duration)

3. **Pending Reviews Card** (conditional: only if `pendingReviewCount > 0`)
   - Total count + priority breakdown (High/Normal/Low)
   - Tappable → navigates to PlaceReview screen

4. **Today's Places** (collapsible section, expanded by default)
   - List of places visited today, each showing:
     - Place name + category icon
     - Address (from `Place.address`, `Place.locality`)
     - Time range (entry → exit or "NOW") + duration
     - Today's visit count + historical average stay (`Place.totalTimeSpent / Place.visitCount` (derived average stay))
     - Lifetime visit count (`Place.visitCount`) + frequent flag (`place.visitCount >= 10` (derived frequent flag))
     - Confidence for active visit (`Visit.confidence`)
     - "View on Map" button → `sharedUiState.selectPlaceForMap(place)` + nav to Map

5. **All Places** (collapsible section, collapsed by default)
   - Sort dropdown: Most Visited / Most Time / Recently Visited / Category
     - Maps to: `getMostVisitedPlaces()`, `getPlacesWithMostTime()`, `getAllPlaces()` sorted by `lastVisited`, grouped by `category`
   - Each place card shows:
     - Place name + category icon + visit count
     - Category label + last visited date
     - Total time spent (`Place.totalTimeSpent`) + average stay
     - Coordinates (`latitude`, `longitude`)
     - Flags: Home/Work/Sleep (derived from `category`/`dominantSemanticContext`), Frequent (derived from `visitCount >= 10`)
     - OSM name if available (`Place.osmSuggestedName`)
     - User rename indicator (`Place.isUserRenamed`)
     - Pattern info from `PlacePattern` if available (e.g., "Mon/Wed/Fri 6-7:30 PM")
     - "View on Map" + "Timeline" cross-navigation buttons

6. **Quick Actions** (collapsible section)
   - Run Place Detection → `triggerPlaceDetection()`
   - View Statistics → nav to Insights
   - Export Data → `exportData()`

7. **Debug Tools** (collapsible, visible only when `isDeveloperModeEnabled`)
   - All existing debug functions

### ViewModels to Modify
- `DashboardViewModel`: Absorb TrackViewModel functionality. Specifically add to `DashboardUiState`:
  - `trackingSessionDuration: String = ""` — formatted from `CurrentState.trackingSessionDuration`, updated every 1s
  - `sessionLocationCount: Int = 0` — from `CurrentState.sessionSavedCount`
  - `isDetectingPlaces: Boolean = false` — place detection in-progress flag
  - `detectionStatusMessage: String? = null` — detection feedback text
  - `todaysPlaces: List<PlaceWithVisits> = emptyList()` — from `PlaceRepository.getPlacesVisitedOnDate()` + `VisitRepository`
  - `allPlaces: List<Place> = emptyList()` — from `PlaceRepository.getAllPlaces()`
  - `placeSortMode: PlaceSortMode = MOST_VISITED` — enum for sort options
  - `placePatterns: Map<Long, PlacePattern?> = emptyMap()` — pattern per place ID
  - Add functions: `triggerPlaceDetection()`, `dismissError()`, `setPlaceSortMode()` (migrated from TrackViewModel)
- `TrackViewModel`: **Delete** — all functionality absorbed into DashboardViewModel

## 4. Map Screen

### Overview
Date-synced interactive map with place search, category filtering, and rich place detail bottom sheet.

### Data Sources
| UI Element | Source | Field/Method |
|---|---|---|
| GPS trail | `MapViewModel` | `locations: List<Location>` |
| Place markers | `MapViewModel` | `visiblePlaces: List<Place>` (category-filtered) |
| User location | `MapViewModel` | `userLocation: Location?` |
| Selected place | `MapViewModel` | `selectedPlace: Place?` |
| Selected place visits | `MapViewModel` | `selectedPlaceVisits: List<Visit>` |
| Current place | `MapViewModel` | `currentPlace: Place?` |
| Map center/zoom | `MapViewModel` | `mapCenter`, `zoomLevel` |
| Selected date | `SharedUiState` | `selectedDate` (synced with Timeline) |
| Category filters | `MapViewModel` | `categorySettings` |
| All places (for search) | `MapViewModel` | `places: List<Place>` |
| Tracking status | `MapViewModel` | `isTracking` |

### Layout Changes

1. **Header Bar** (replaces current DateSelectorBar)
   - Date navigator: `[<] TODAY (Monday) [>]` synced via `SharedUiState.selectedDate`
   - **Search bar** (NEW): Client-side filter of loaded `places` list by `Place.getBestName()`, `Place.address`, `Place.category.displayName`
   - Status strip: Tracking indicator + visible/total place count + "Categories" link

2. **Map View** (existing `OpenStreetMapView`)
   - All existing features: numbered markers, route polyline, user location, accuracy circle
   - **Toggle controls exposed** (currently internal): Route, Numbers, Visit Counts
   - Center-on-user FAB
   - Color coding: green=current, purple=user-renamed, blue=regular

3. **Place Detail Bottom Sheet** (enhanced from current `EnhancedPlaceBottomSheet`)
   - Triggered by `MapViewModel.selectPlace(place)`
   - Shows all `Place` metadata:
     - Name, address, coordinates
     - Category + confidence
     - Visit count + average stay + frequency flag
     - isUserRenamed indicator
     - Home/Work/Sleep flags (derived from `place.category == HOME/WORK` or `place.dominantSemanticContext`)
   - Recent visits list (max 10) with entry time, duration, confidence, ongoing badge
   - "+N more visits" indicator
   - Actions: "View in Timeline", "Rename Place"

### ViewModels to Modify
- `MapViewModel`: Add `searchQuery: MutableStateFlow<String>("")` and `fun setSearchQuery(query: String)`. Filter `visiblePlaces` through search query matching on `place.getBestName()`, `place.address`, `place.category.displayName`. Search is local composable state fed into VM for filtering.

## 5. Timeline Screen

### Overview
Full movement narrative for a selected date. Shows every segment (visits, transit, stops, gaps) with rich metadata.

### Data Sources
| UI Element | Source | Field/Method |
|---|---|---|
| Movement segments | `TimelineViewModel` | `movementSegments: List<TrackingStateSegment>` |
| Legacy segments | `TimelineViewModel` | `visibleSegments: List<TimelineSegment>` (fallback) |
| Day analytics | `TimelineViewModel` | `dayAnalytics: DayAnalytics?` |
| Selected date | `SharedUiState` | `selectedDate` (synced with Map) |
| Current place | `TimelineViewModel` | `currentPlace: Place?` |
| Current visit duration | `TimelineViewModel` | `currentVisitDuration` |
| Tracking status | `TimelineViewModel` | `isTracking` |
| Category filters | `TimelineViewModel` | `categorySettings` |

### Layout Changes

1. **Date Selector** (streamlined)
   - Same synced date nav: `[<] TODAY [>]` + TODAY button + Jump to Map button

2. **Day Summary Strip** (compact, replaces large DaySummaryCard)
   - Single row: `5 PLACES | 12.4 km | 6h 23m | 3 trips`
   - Longest stay info from `DayAnalytics.longestStay` / `longestStayDuration`
   - Category breakdown: count per category icon
   - Filter status: `3/5 visible [Manage Categories]`

3. **Movement Segments** (enhanced cards per `SegmentType`)

   **CONFIRMED_PLACE_VISIT cards show:**
   - Place name + category icon
   - Address
   - Time range (entry → exit or "NOW") + duration
   - `Visit.confidence` percentage
   - `Place.visitCount` + `Place.totalTimeSpent / Place.visitCount` (derived average stay)
   - Flags: Frequent (derived: `visitCount >= 10`), Home/Work/Sleep (derived from `category`/`dominantSemanticContext`)
   - `PlacePattern` inline if available (e.g., "Weekdays 9am-6pm")
   - "View on Map" button → `sharedUiState.selectPlaceForMap(place)`
   - Rename button / long-press → `RenamePlaceDialog`

   **TRANSIT segments show:**
   - Arrow icon + distance (`totalDistanceMeters`) + duration
   - Time range
   - Speed: `averageSpeedKmh` (formatted)
   - Activity hint: `activityHint` (DRIVING/WALKING/CYCLING icon)
   - Coordinate count from `coordinates` list

   **TRANSIENT_STOP segments show:**
   - Pause icon + duration
   - Time range
   - Nearby address from `addressLabel`

   **NOT_TRACKING segments show:**
   - Location-off icon + duration
   - Time range
   - Reason from `explanation` field (e.g., "Sleep schedule active")

   **UNTRACKED_WHILE_TRACKING segments show:**
   - Signal-off icon + duration
   - Time range
   - Reason from `explanation` (e.g., "No GPS signal")

4. **Category Filter Info** (when categories hidden)
   - Visible/total count + "Manage" link to Categories

### ViewModels to Modify
- `TimelineViewModel`: Add `PlacePattern` data per place for inline display. Rest is already available.

## 6. Insights Screen

### Overview
Unified analytics hub. Merges current InsightsScreen and StatisticsScreen into one 6-tab screen.

### Structural Change
- **Remove** `InsightsScreen` as a separate hub with navigation cards
- **Remove** `PlacePatternsScreen` as a separate destination (content moves into Patterns tab)
- **StatisticsScreen** becomes the Insights screen, gaining an Overview tab

### Tab Structure
| Tab | Data Source | ViewModel |
|---|---|---|
| Overview | `InsightsViewModel` | insights, movementPatterns, personalizedMessages |
| Weekly | `WeeklyComparisonViewModel` + `StatisticsViewModel` | weeklyComparison, ComparisonTab |
| Patterns | `PlacePatternsViewModel` + `StatisticsViewModel` | placePatterns, PatternDetails |
| Movement | `MovementAnalyticsViewModel` + `StatisticsViewModel` | movementStats, trends, distributions, correlations |
| Social | `SocialHealthAnalyticsViewModel` + `StatisticsViewModel` | socialStats, categoryBreakdown, personalizedInsights |
| Anomalies | `StatisticsViewModel` | anomalies, anomalyPatterns |

### Period Selector
- Shared `PeriodSelectorBar` at top, applies to all tabs
- Options: `DateRangePeriod` — Today, ThisWeek, ThisMonth, Last30Days, Custom

### Tab Content Details

**Overview Tab (NEW):**
- AI-generated insights from `InsightEngine.generateInsights()` — `List<Insight>` with type, title, message, confidence, actionable flag
- Personalized messages from `PersonalizedInsightsGenerator.generateMessages()`
- Movement patterns from `AnalyticsUseCases.detectMovementPatterns()` — PatternType (COMMUTE, ROUTINE, etc.)

**Weekly Tab:**
- ComparisonTab toggle (WEEKLY / MONTHLY)
- `WeeklyComparisonData`: places/distance/timeAway for current vs previous period
- Change percentages with `Trend` (UP/DOWN/STABLE) indicators
- `PlaceComparison` list: per-place time changes

**Patterns Tab:**
- `PlacePattern` list from `AnalyzePlacePatternsUseCase`
- Per pattern: place name, `PlacePatternType`, description, confidence bar
- `PatternDetails` variants: DayOfWeekPattern, TimeOfDayPattern, FrequencyPattern, DailyRoutinePattern

**Movement Tab:**
- `MovementStats`: totalDistanceKm, avgSpeedKmh, mostActiveDay
- `TemporalTrend` list: trends over time
- `Distribution` list: median, percentiles for visit duration and daily distance
- `Correlation` list: correlation coefficients between variables

**Social Tab:**
- `SocialHealthStats`: uniquePlaces, varietyScore (progress bar), categoryBreakdown
- `CategoryStats` per category: totalVisits, avgDuration, placeCount
- `socialPlaces` / `healthPlaces` filtered lists
- `PersonalizedMessage` list

**Anomalies Tab:**
- `Anomaly` sealed class rendered by subtype:
  - `MissedPlace`: expected frequency vs actual
  - `UnusualDuration`: % deviation from usual
  - `UnusualTime`: time visited vs usual
  - `UnusualDay`: day visited vs usual
  - `NewPlace`: first visit info
- `AnomalySeverity` color coding: INFO=blue, LOW=green, MEDIUM=yellow, HIGH=red
- `anomalyPatterns`: cross-referenced PlacePattern context

### ViewModels to Modify
- `StatisticsViewModel`: Add Overview tab data (insights, personalized messages, movement patterns). The existing `StatisticsTab` enum in `StatisticsScreen.kt` (line 82-88) should be updated: add `OVERVIEW("Overview", Icons.Default.Lightbulb)` as the first entry before WEEKLY.
- Can potentially consolidate `InsightsViewModel`, `PlacePatternsViewModel`, `WeeklyComparisonViewModel`, `MovementAnalyticsViewModel`, `SocialHealthAnalyticsViewModel` into `StatisticsViewModel` since they all feed one screen now. Alternative: keep separate ViewModels, inject all into the composable.

### Recommendation — ViewModel Strategy
Keep `StatisticsViewModel` as the primary VM for the Insights screen. It already loads most data. Add the Overview data (insights, patterns, messages) to its state.

**VMs to keep as-is** (lazy-loaded per tab via `hiltViewModel()` in each tab composable):
- `WeeklyComparisonViewModel` — used in Weekly tab
- `MovementAnalyticsViewModel` — used in Movement tab
- `SocialHealthAnalyticsViewModel` — used in Social tab

**VMs to delete** (absorbed into StatisticsViewModel):
- `InsightsViewModel` — Overview tab data moves to StatisticsViewModel
- `PlacePatternsViewModel` — Patterns tab data moves to StatisticsViewModel (it already has `placePatterns`)

## 7. Settings Screen

### Overview
Unified 4-tab settings screen. Merges SettingsScreen + AdvancedSettingsScreen. Surfaces all 100+ `UserPreferences` fields.

### Tab Structure

**General Tab** — Day-to-day controls
| Section | Fields |
|---|---|
| Profile | `currentProfile` (SettingsPresetProfile), BatteryImpact, AccuracyLevel, `getEstimatedBatterySavings()` |
| Tracking | `isLocationTrackingEnabled`, `trackingAccuracyMode`, `locationUpdateIntervalMs`, `minDistanceChangeMeters` |
| Tracking Quality | `activityRecognitionConfidence`, `stationaryModeMultiplier`, `maxTrackingGapSeconds` |
| Notifications | `enableArrivalNotifications`, `enableDepartureNotifications`, `enablePatternNotifications`, `enableWeeklySummary`, `reviewNotificationsEnabled`, `notificationUpdateFrequency` |
| Sleep Schedule | `sleepModeEnabled`, `sleepStartHour`, `sleepEndHour`, `sleepModeStrictness`, `motionDetectionEnabled`, `motionSensitivityThreshold` |
| Categories | Link to CategoriesScreen |

**Detection Tab** — Place finding configuration
| Section | Fields |
|---|---|
| Place Detection | `clusteringDistanceMeters`, `minPointsForCluster`, `placeDetectionRadius`, `sessionBreakTimeMinutes`, `minVisitDurationMinutes`, `minDwellTimeSeconds`, `minimumDistanceBetweenPlaces`, `autoConfidenceThreshold` |
| Categorization Thresholds | `homeNightActivityThreshold`, `workHoursActivityThreshold`, `gymActivityThreshold`, `restaurantMealTimeThreshold`, `shoppingMinDurationMinutes`, `shoppingMaxDurationMinutes` |
| Semantic Time Ranges | `workHoursStart/End`, `commuteHoursStart/End`, `commuteEveningStart/End`, `activityTimeRangeStart/End` |
| Geocoding Providers | `enabledGeocodingProviders`, `geocodingApiKeys`, `geocodingCacheDurationDays`, `geocodingCachePrecision` |
| Automation | `placeDetectionFrequencyHours`, `autoDetectTriggerCount`, `batteryRequirement`, `workerEnqueueTimeoutSeconds` |
| Place Review | `autoAcceptStrategy`, `autoAcceptConfidenceThreshold`, `threeVisitAutoAcceptVisitCount`, `reviewPromptMode`, `autoApproveEnabled`, `autoApproveThreshold` |

**Privacy & Data Tab** — Data ownership
| Section | Fields |
|---|---|
| Privacy | `enableLocationHistory`, `enablePlaceDetection`, `enableAnalytics`, `anonymizeExports` |
| Data Stats | `totalLocations`, `totalPlaces`, `totalVisits` (from SettingsUiState) |
| Data Management | `dataRetentionDays`, `maxLocationsToProcess`, `maxRecentLocationsDisplay` |
| Export | `defaultExportFormat` (JSON/CSV/GPX/KML), `includeRawLocations`, `includePlaceData`, `includeVisitData`, `includeAnalytics`, `exportData()` |
| Danger Zone | `deleteOldData()`, `deleteAllData()`, `resetPreferencesToDefaults()` |

**Advanced Tab** — Expert tuning
| Section | Fields |
|---|---|
| Quality Filtering | `maxGpsAccuracyMeters`, `maxSpeedKmh`, `minTimeBetweenUpdatesSeconds` |
| Stationary Detection | `stationaryThresholdMinutes`, `stationaryMovementThreshold` |
| Activity Recognition | `useActivityRecognition` |
| Timeline Settings | `timelineTimeWindowMinutes` (chip selector), `timelineDistanceThresholdMeters` |
| Pattern Analysis | `patternMinVisits`, `patternMinConfidence`, `patternTimeWindowMinutes`, `patternAnalysisDays` |
| Anomaly Detection | `anomalyRecentDays`, `anomalyLookbackDays`, `anomalyDurationThreshold`, `anomalyTimeThresholdHours` |
| Battery & Performance | `autoDetectBatteryThreshold`, `stationaryIntervalMultiplier`, `stationaryDistanceMultiplier`, `forceSaveIntervalMultiplier`, `forceSaveMaxSeconds`, `minimumMovementForTimeSave` |
| Service Config | `serviceHealthCheckIntervalSeconds`, `serviceStopGracePeriodMs`, `permissionCheckIntervalSeconds` |
| UI Refresh | `dashboardRefreshAtPlaceSeconds`, `dashboardRefreshTrackingSeconds`, `dashboardRefreshIdleSeconds`, `analyticsCacheTimeoutSeconds`, `dataProcessingBatchSize` |
| Daily Summary | `dailySummaryEnabled`, `dailySummaryHour` |
| Debug Tools | (dev mode only) DebugDataInsertion link, manual detection, WorkManager health, reset prefs, diagnostics |
| About | Version (tap 7x for dev mode), About Developer link |

### ViewModels to Modify
- `SettingsViewModel`: Already has all 50+ update methods. Add tab index state. Move AdvancedSettings sections content into the same VM (already shares it via `hiltViewModel()`).
- `AdvancedSettingsScreen`: **Delete** — content absorbed into Settings Advanced tab.

## 8. Theme Evolution

### From Matrix to Polished Dark

| Element | Current (Matrix) | Proposed (Polished Dark) |
|---|---|---|
| Corner radius | Sharp/0dp on many cards | Consistent 12dp `RoundedCornerShape` |
| Text casing | ALL CAPS everywhere | Sentence case for body/labels, CAPS for section headers only |
| Color palette | Bright teal (#00BFA5) on pure black | Softer teal with dark blue-grey surfaces |
| Borders | Bright teal 1dp borders | Subtle surface elevation + muted border |
| Typography | `MatrixSectionHeader` monospace feel | Material3 `Typography` with proper hierarchy |
| Cards | `MatrixCard` with bright borders | Material3 `Card` or `OutlinedCard` with softer treatment |
| Buttons | `MatrixButton` with teal outline | Material3 `FilledTonalButton` / `OutlinedButton` |
| Loading | `LoadingDots` custom | Material3 `CircularProgressIndicator` + subtle animation |
| Dividers | `MatrixDivider` (bright teal) | Material3 `HorizontalDivider` (muted) |

### Color Tokens
```
Primary:        Teal (softer, ~#26A69A)
OnPrimary:      White
Surface:        Dark Blue-Grey (#1A1A2E)
SurfaceVariant: Slightly lighter (#252540)
Background:     Near-black (#0F0F1A)
OnSurface:      Off-white (#E8E8F0)
OnSurfaceVariant: Muted grey (#8888A0)
Error:          Warm red (#EF5350)
```

### Component Library Updates
- Replace `MatrixCard` → Polished `VoyagerCard` (12dp corners, subtle border, surface background)
- Replace `MatrixButton` → `VoyagerButton` (filled tonal for primary, outlined for secondary)
- Replace `MatrixBadge` → Material3 `Badge`
- Replace `MatrixDivider` → `HorizontalDivider`
- Keep `PulsingDot` (good UX for live status)
- Keep `CollapsibleSection` (good pattern, update styling)
- Replace `GlassmorphismComponents` → Remove completely (already deprecated)

## 9. Files to Create

| File | Purpose |
|---|---|
| `presentation/theme/VoyagerComponents.kt` | New component library replacing MatrixComponents |
| `presentation/theme/VoyagerColors.kt` | Updated color tokens |

## 10. Files to Modify

| File | Changes |
|---|---|
| `navigation/VoyagerDestination.kt` | Remove Track, AdvancedSettings. Rename Dashboard→Home. Update bottomNavItems. |
| `navigation/VoyagerNavHost.kt` | Remove Track/AdvancedSettings composables. Update `startDestination` from `VoyagerDestination.Dashboard.route` to `VoyagerDestination.Home.route`. Update Home route. Add Settings top bar gear icon at NavHost level. |
| `screen/dashboard/DashboardScreen.kt` | Complete rewrite → Home screen with merged Track, places lists, stats grid |
| `screen/dashboard/DashboardViewModel.kt` | Add TrackVM fields, today's places, all places with sorting, pattern data |
| `screen/map/MapScreen.kt` | Add search bar, expose map toggles, enhance bottom sheet |
| `screen/timeline/TimelineScreen.kt` | Compact day summary, enriched segment cards with confidence/patterns/flags |
| `screen/analytics/StatisticsScreen.kt` | Add Overview tab, merge InsightsScreen content |
| `screen/analytics/StatisticsViewModel.kt` | Add insights/patterns/messages to state, add OVERVIEW to StatisticsTab |
| `screen/settings/SettingsScreen.kt` | Complete rewrite → 4-tab unified screen with all UserPreferences fields |
| `screen/settings/SettingsViewModel.kt` | Add tab index state (minimal changes, already has all methods) |
| `theme/MatrixComponents.kt` | Replace with VoyagerComponents or update in-place |

## 11. Files to Delete

| File | Reason |
|---|---|
| `presentation/screen/track/TrackScreen.kt` | Merged into Home |
| `presentation/screen/track/TrackViewModel.kt` | Merged into DashboardViewModel |
| `presentation/screen/settings/AdvancedSettingsScreen.kt` | Merged into Settings Advanced tab |
| `presentation/screen/insights/InsightsScreen.kt` | Merged into StatisticsScreen Overview tab |
| `presentation/screen/insights/InsightsViewModel.kt` | Absorbed into StatisticsViewModel |
| `presentation/screen/insights/PlacePatternsScreen.kt` | Merged into Insights Patterns tab |
| `presentation/screen/insights/PlacePatternsViewModel.kt` | Absorbed into StatisticsViewModel |
| `presentation/theme/GlassmorphismComponents.kt` | Deprecated, replace with VoyagerComponents |

## 12. Place Review Screen (No Structural Changes)

PlaceReviewScreen is well-designed and stays as-is. Minor theme updates only:
- Apply new VoyagerCard styling
- Apply new typography hierarchy
- Keep: priority grouping, confidence breakdown, suggestions, batch approve, edit dialog

## 13. Categories Screen (No Structural Changes)

CategoriesScreen is well-designed and stays as-is. Minor theme updates only:
- Apply new VoyagerCard styling
- Keep: per-category visibility toggles, assignment dialog, bulk controls, stat chips

## 14. Success Criteria

- [ ] 4-tab bottom nav working (Home, Map, Timeline, Insights)
- [ ] Home screen shows live tracking, stats, reviews, today's places, all places
- [ ] Map has search bar, exposed toggles, enriched bottom sheet
- [ ] Timeline shows enriched segment cards with confidence, patterns, flags
- [ ] Insights has 6 tabs (Overview, Weekly, Patterns, Movement, Social, Anomalies)
- [ ] Settings has 4 tabs (General, Detection, Privacy & Data, Advanced) with ALL UserPreferences fields
- [ ] Theme evolved: 12dp corners, reduced CAPS, softer colors, proper typography
- [ ] Track screen removed, AdvancedSettings removed, InsightsScreen merged
- [ ] All cross-navigation preserved (View on Map, View in Timeline, SharedUiState sync)
- [ ] Developer mode debug tools accessible from Settings > Advanced
- [ ] No new features added — only reorganization and enrichment of existing data
