# UI Enhancement Roadmap

**Last Updated:** December 11, 2025
**Version:** 1.0.0

This document outlines the UI/UX improvement plan for Voyager, prioritized by impact and effort.

---

## Current State Assessment

### Screens Implemented (10 total)

✅ **Production Ready:**
1. DashboardScreen - Overview and stats
2. MapScreen - Interactive OSM visualization
3. TimelineScreen - Chronological visit history
4. SettingsScreen - 81+ configuration parameters
5. PlaceReviewScreen - Place review workflow
6. PlacePatternsScreen - Pattern analysis
7. StatisticsScreen - Detailed statistics
8. DebugScreen - Developer tools
9. PermissionScreen - Permission requests

⚠️ **Needs Work:**
10. InsightsScreen - Basic analytics (missing advanced features)

---

## Priority 1: Missing Functionality (8 hours)

### 1.1 Export/Import UI (30 minutes)

**Location:** SettingsScreen → Data Management section

**Components to Add:**
```kotlin
@Composable
fun DataManagementSection(
    onExportJson: () → Unit,
    onExportCsv: () → Unit,
    onImport: () → Unit
) {
    Card {
        Column {
            Text("Data Export & Import")

            Row {
                Button(onClick = onExportJson) {
                    Icon(Icons.Default.Download)
                    Text("Export JSON")
                }

                Button(onClick = onExportCsv) {
                    Icon(Icons.Default.Download)
                    Text("Export CSV")
                }
            }

            Button(onClick = onImport) {
                Icon(Icons.Default.Upload)
                Text("Import Backup")
            }
        }
    }
}
```

**Backend:** Already implemented (ExportDataUseCase)

**Estimated Time:** 30 minutes

---

### 1.2 Advanced Analytics Screen (4 hours)

**New Screen:** `presentation/screen/analytics/AdvancedAnalyticsScreen.kt`

**Features:**
- Statistical insights display
- Correlation charts (which places visited together)
- Temporal trend analysis (weekday vs weekend)
- Predictive analytics
- Distribution graphs

**Backend Required:**
- Wire StatisticalAnalyticsUseCase in DI
- Create AdvancedAnalyticsViewModel
- Optimize performance for large datasets

**Design Mockup:**
```
┌─────────────────────────────────────┐
│ Advanced Analytics                  │
├─────────────────────────────────────┤
│                                     │
│ 📊 Statistical Insights             │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ • You visit work 5.2 times/week     │
│ • Average visit duration: 7h 15m    │
│ • Most common pattern: Home→Work    │
│                                     │
│ 📈 Temporal Trends                  │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ [Chart: Weekday vs Weekend]         │
│                                     │
│ 🔗 Correlations                     │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ • 80% of gym visits followed by     │
│   restaurant (within 2 hours)       │
│                                     │
└─────────────────────────────────────┘
```

**Estimated Time:** 4 hours

---

### 1.3 Personalized Insights Display (2 hours)

**Location:** DashboardScreen → Insights Card

**Components:**
```kotlin
@Composable
fun InsightsCard(
    insights: List<PersonalizedMessage>,
    onDismiss: (String) → Unit
) {
    Card {
        Column {
            Text("Insights for You", style = MaterialTheme.typography.titleMedium)

            insights.forEach { insight →
                InsightItem(
                    icon = insight.icon,
                    message = insight.message,
                    onDismiss = { onDismiss(insight.id) }
                )
            }

            TextButton(onClick = { /* Refresh */ }) {
                Text("Refresh Insights")
            }
        }
    }
}
```

**Backend Required:**
- Wire PersonalizedInsightsGenerator in DI
- Add to DashboardViewModel

**Estimated Time:** 2 hours

---

### 1.4 Anomaly Alerts UI (1.5 hours)

**New Tab:** "Alerts" or integrate into Insights

**Features:**
- List of detected anomalies
- Severity indicators (⚠️ Warning, ❌ Error)
- Action buttons (Review Place, Dismiss, Fix)
- Explanation of why it's anomalous

**Example:**
```
┌─────────────────────────────────────┐
│ ⚠️ Anomalies Detected (3)            │
├─────────────────────────────────────┤
│                                     │
│ ⚠️ Potential Missed Place            │
│ You have 50 locations near "123     │
│ Main St" that aren't a place yet.   │
│ [Review] [Dismiss]                  │
│                                     │
│ ❌ Unusual Duration                  │
│ You spent 12h at work on Saturday - │
│ much longer than usual.             │
│ [Mark Correct] [Fix]                │
│                                     │
└─────────────────────────────────────┘
```

**Backend:** DetectAnomaliesUseCase (already wired, just hidden)

**Estimated Time:** 1.5 hours

---

## Priority 2: UX Improvements (12 hours)

### 2.1 Onboarding Flow (4 hours)

**Screens:** 4-step wizard on first launch

**Flow:**
```
Step 1: Welcome
  - App introduction
  - Privacy promise
  - Value proposition

Step 2: Permissions
  - Why we need each permission
  - Visual explanation
  - Grant buttons

Step 3: Quick Setup
  - Choose preset profile:
    • Balanced (recommended)
    • Battery Saver
    • Maximum Accuracy
  - Customize later in Settings

Step 4: First Tracking
  - "Let's start tracking!"
  - Shows notification explanation
  - Starts LocationTrackingService
```

**Implementation:**
```kotlin
@Composable
fun OnboardingFlow(onComplete: () → Unit) {
    var currentStep by remember { mutableStateOf(0) }

    HorizontalPager(count = 4, state = remember { PagerState() }) { page →
        when (page) {
            0 → WelcomeStep()
            1 → PermissionsStep()
            2 → QuickSetupStep()
            3 → FirstTrackingStep(onComplete = onComplete)
        }
    }
}
```

**Estimated Time:** 4 hours

---

### 2.2 Empty States (2 hours)

**Improvements Needed:**

| Screen | Current | Improved |
|--------|---------|----------|
| Dashboard | Blank | "Enable tracking to get started" + illustration |
| Map | Empty map | "Visit places to see them here" + icon |
| Timeline | Blank list | "No visits today. Check another date?" |
| Review | Empty | "All caught up! 🎉 No places need review" |

**Design Pattern:**
```kotlin
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionButton: (@Composable () → Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        actionButton?.invoke()
    }
}
```

**Estimated Time:** 2 hours

---

### 2.3 Error States (2 hours)

**Improved Error Messages:**

| Error | Current | Improved |
|-------|---------|----------|
| Detection fails | "Failed to detect places" | "Not enough location data. Track for 2+ days to detect places." |
| Permission denied | "Permission denied" | "Location permission denied. Grant in Settings → Location." |
| Low accuracy | "Error" | "GPS signal too weak. Move outdoors for better accuracy." |

**Implementation:**
```kotlin
data class ErrorState(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val actions: List<ErrorAction>
)

data class ErrorAction(
    val label: String,
    val onClick: () → Unit
)

@Composable
fun ErrorStateDisplay(error: ErrorState) {
    Column {
        Icon(error.icon)
        Text(error.title)
        Text(error.description)

        error.actions.forEach { action →
            Button(onClick = action.onClick) {
                Text(action.label)
            }
        }
    }
}
```

**Estimated Time:** 2 hours

---

### 2.4 Loading States (2 hours)

**Replace:** Circular progress indicators

**With:** Skeleton screens (shimmering placeholders)

**Example:**
```kotlin
@Composable
fun PlaceCardSkeleton() {
    Card(modifier = Modifier.shimmer()) {
        Column {
            Box(Modifier.size(40.dp).background(Color.Gray.copy(alpha = 0.3f)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(20.dp).background(Color.Gray.copy(alpha = 0.3f)))
            Box(Modifier.fillMaxWidth(0.5f).height(16.dp).background(Color.Gray.copy(alpha = 0.3f)))
        }
    }
}
```

**Library:** `com.valentinilk.shimmer:compose-shimmer:1.0.5`

**Estimated Time:** 2 hours

---

### 2.5 Permission Flow Enhancement (2 hours)

**Improvements:**
- Education screens before permission request
- Visual explanation with screenshots
- Clear value proposition
- Deep link to settings if denied

**Estimated Time:** 2 hours

---

## Priority 3: Accessibility (8 hours)

### 3.1 Screen Reader Support (3 hours)

**Tasks:**
- Add contentDescription to all Icon/Image composables
- Add semantic properties for groups
- Test with TalkBack
- Ensure logical reading order

**Example:**
```kotlin
Icon(
    imageVector = Icons.Default.Home,
    contentDescription = "Home place indicator"
)

Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
    Icon(...)
    Text("Home")
}
```

**Estimated Time:** 3 hours

---

### 3.2 Dynamic Text Sizing (2 hours)

**Tasks:**
- Test with 200% font scaling
- Fix text truncation issues
- Use sp units consistently
- Test with system font settings

**Estimated Time:** 2 hours

---

### 3.3 High Contrast Mode (2 hours)

**Tasks:**
- Ensure 4.5:1 contrast ratio (WCAG AA)
- Create high-contrast theme variant
- Add border outlines for buttons
- Test with Android accessibility scanner

**Estimated Time:** 2 hours

---

### 3.4 Touch Target Sizes (1 hour)

**Tasks:**
- Ensure minimum 48dp touch targets
- Increase small button sizes
- Add spacing between clickable elements
- Test with touch exploration

**Estimated Time:** 1 hour

---

## Priority 4: Polish (12 hours)

### 4.1 Animations & Transitions (4 hours)

**Add:**
- Screen navigation animations (slide/fade)
- Card expand/collapse animations
- List item entry animations
- Chart animations (smooth transitions)

**Example:**
```kotlin
AnimatedVisibility(
    visible = isExpanded,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut()
) {
    DetailedContent()
}
```

**Estimated Time:** 4 hours

---

### 4.2 Haptic Feedback (2 hours)

**Add:**
- Button press feedback
- Success/error vibrations
- Long-press confirmations
- Contextual haptics

**Example:**
```kotlin
val haptic = LocalHapticFeedback.current

Button(onClick = {
    haptic.performHapticFeedback(HapticFeedbackType.Click)
    doAction()
}) {
    Text("Action")
}
```

**Estimated Time:** 2 hours

---

### 4.3 Dark Mode Optimization (3 hours)

**Tasks:**
- Review all colors for dark theme
- Use OLED-friendly pure blacks
- Ensure consistent contrast
- Test all screens in both themes

**Estimated Time:** 3 hours

---

### 4.4 Icon Consistency (2 hours)

**Tasks:**
- Audit all icons
- Use Material Icons consistently
- Ensure appropriate sizes (24dp default)
- Semantic meaning clear

**Estimated Time:** 2 hours

---

### 4.5 Typography Refinement (1 hour)

**Tasks:**
- Apply Material Design 3 type scale
- Consistent hierarchy
- Improved readability
- Better line spacing

**Estimated Time:** 1 hour

---

## Design System Documentation (4 hours)

### Components to Document

1. **Color Palette**
   - Primary/secondary/tertiary
   - Light/dark variants
   - Semantic colors

2. **Typography Scale**
   - Display, headline, title, body, label
   - Font weights
   - Line heights

3. **Spacing System**
   - 4dp/8dp grid
   - Consistent margins
   - Component spacing

4. **Component Library**
   - Reusable components
   - Usage guidelines
   - Code examples

**Tool:** Create in Markdown or use Figma

**Estimated Time:** 4 hours

---

## Total Effort Summary

| Priority | Category | Hours |
|----------|----------|-------|
| 1 | Missing Functionality | 8 |
| 2 | UX Improvements | 12 |
| 3 | Accessibility | 8 |
| 4 | Polish | 12 |
| - | Design System Docs | 4 |
| **Total** | | **44 hours** |

---

## Implementation Order

### Week 1 (16 hours)
1. Export/Import UI (0.5h) - Quick win
2. Onboarding Flow (4h)
3. Empty States (2h)
4. Error States (2h)
5. Loading States (2h)
6. Advanced Analytics Screen (4h)
7. Personalized Insights (1.5h remaining)

### Week 2 (16 hours)
1. Personalized Insights (0.5h finish)
2. Anomaly Alerts UI (1.5h)
3. Screen Reader Support (3h)
4. Dynamic Text Sizing (2h)
5. High Contrast Mode (2h)
6. Touch Target Sizes (1h)
7. Animations & Transitions (4h)
8. Haptic Feedback (2h)

### Week 3 (12 hours)
1. Dark Mode Optimization (3h)
2. Icon Consistency (2h)
3. Typography Refinement (1h)
4. Permission Flow Enhancement (2h)
5. Design System Documentation (4h)

---

## Success Criteria

- [ ] All 10 screens have empty states
- [ ] All error messages are user-friendly
- [ ] Onboarding flow implemented
- [ ] Export/import buttons added
- [ ] Advanced analytics displayed
- [ ] Anomaly alerts visible
- [ ] TalkBack works correctly
- [ ] 200% font scaling supported
- [ ] 4.5:1 contrast ratio met
- [ ] All touch targets >= 48dp
- [ ] Smooth animations throughout
- [ ] Haptic feedback on key actions
- [ ] Dark mode optimized
- [ ] Icons consistent
- [ ] Design system documented

---

## Next Steps After UI Enhancement

1. **User Testing** - Test with real users
2. **Performance Optimization** - Profile and optimize
3. **Accessibility Audit** - Third-party evaluation
4. **Beta Release** - Limited release to testers

---

**Prepared for:** Next development phase
**Reference:** Main VOYAGER_COMPLETE_GUIDE.md
**Dependencies:** Backend features must be wired in DI first
