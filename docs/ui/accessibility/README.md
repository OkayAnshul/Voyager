# ♿ Accessibility Guidelines

## Overview
This document outlines comprehensive accessibility requirements and implementation guidelines for the Voyager application, ensuring usability for all users regardless of their abilities.

## Accessibility Standards

### WCAG 2.1 Compliance
- **Level AA Compliance**: Target standard for all features
- **Level AAA**: Aspirational for critical user flows
- **Platform Guidelines**: Follow Android and iOS accessibility standards

### Core Principles
1. **Perceivable**: Information must be presentable in ways users can perceive
2. **Operable**: Interface components must be operable by all users
3. **Understandable**: Information and UI operation must be understandable
4. **Robust**: Content must be robust enough for interpretation by assistive technologies

---

## Visual Accessibility

### Color & Contrast
```kotlin
object AccessibilityColors {
    // High contrast color pairs (7:1 ratio)
    val highContrastPrimary = Color(0xFF000080)
    val highContrastOnPrimary = Color(0xFFFFFFFF)
    
    // Standard contrast color pairs (4.5:1 ratio)
    val standardPrimary = Color(0xFF2196F3)
    val standardOnPrimary = Color(0xFFFFFFFF)
    
    // Focus indicators
    val focusRing = Color(0xFF0066CC)
    val focusBackground = Color(0x1A0066CC)
}

@Composable
fun AccessibleText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    highContrast: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = style,
        color = if (highContrast) {
            AccessibilityColors.highContrastPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
    )
}
```

### Text Scaling Support
```kotlin
@Composable
fun ScalableContent(
    content: @Composable (Float) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val maxScale = 2.0f // Support up to 200% scaling
    val clampedScale = fontScale.coerceAtMost(maxScale)
    
    content(clampedScale)
}

@Composable
fun AccessibleCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    ScalableContent { fontScale ->
        Card(
            modifier = modifier
                .fillMaxWidth()
                .sizeIn(
                    minHeight = (48 * fontScale).dp // Maintain minimum touch target
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    (16 * fontScale.coerceAtMost(1.5f)).dp // Scale padding appropriately
                )
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

### Motion & Animation
```kotlin
@Composable
fun AccessibleAnimation(
    targetValue: Float,
    content: @Composable (Float) -> Unit
) {
    val prefersReducedMotion = LocalAccessibilityManager.current.isReduceMotionEnabled
    
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = if (prefersReducedMotion) {
            snap() // No animation
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        }
    )
    
    content(animatedValue)
}

@Composable
fun ReducedMotionTransition(
    targetState: Boolean,
    content: @Composable (Boolean) -> Unit
) {
    val prefersReducedMotion = LocalAccessibilityManager.current.isReduceMotionEnabled
    
    if (prefersReducedMotion) {
        content(targetState)
    } else {
        AnimatedVisibility(
            visible = targetState,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            content(true)
        }
    }
}
```

---

## Screen Reader Support

### Content Descriptions
```kotlin
@Composable
fun AccessibleStatsCard(
    title: String,
    value: String,
    trend: TrendData? = null,
    onClick: (() -> Unit)? = null
) {
    val contentDescription = buildString {
        append("$title: $value")
        trend?.let { 
            append(", ${it.direction.name.lowercase()} by ${it.value}%")
        }
        if (onClick != null) {
            append(", double tap to view details")
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                onClickLabel = "View $title details",
                role = Role.Button
            ) { onClick?.invoke() }
            .semantics {
                this.contentDescription = contentDescription
                if (onClick != null) {
                    stateDescription = "Clickable"
                }
            }
    ) {
        // Card content
    }
}
```

### Semantic Structure
```kotlin
@Composable
fun AccessibleScreen(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                heading()
            }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics {
                heading()
                role = Role.Heading
            }
        )
        
        content()
    }
}

@Composable
fun AccessibleList(
    items: List<String>,
    onItemClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.semantics {
            role = Role.List
        }
    ) {
        itemsIndexed(items) { index, item ->
            ListItem(
                headlineContent = { Text(item) },
                modifier = Modifier
                    .clickable { onItemClick(item) }
                    .semantics {
                        role = Role.ListItem
                        contentDescription = "Item ${index + 1} of ${items.size}: $item"
                    }
            )
        }
    }
}
```

### Screen Reader Navigation
```kotlin
@Composable
fun AccessibleNavigation(
    destinations: List<VoyagerDestination>,
    currentDestination: VoyagerDestination,
    onNavigate: (VoyagerDestination) -> Unit
) {
    NavigationBar(
        modifier = Modifier.semantics {
            role = Role.TabList
            contentDescription = "Main navigation"
        }
    ) {
        destinations.forEachIndexed { index, destination ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null // Handled by item semantics
                    )
                },
                label = { Text(destination.title) },
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) },
                modifier = Modifier.semantics {
                    role = Role.Tab
                    contentDescription = "${destination.title} tab, ${index + 1} of ${destinations.size}"
                    if (currentDestination == destination) {
                        stateDescription = "Selected"
                    }
                }
            )
        }
    }
}
```

---

## Motor Accessibility

### Touch Targets
```kotlin
object TouchTargets {
    val minimum = 48.dp // WCAG minimum
    val recommended = 56.dp // Recommended size
    val comfortable = 64.dp // Comfortable for motor impairments
}

@Composable
fun AccessibleButton(
    onClick: () -> Unit,
    text: String,
    size: TouchTargetSize = TouchTargetSize.RECOMMENDED,
    modifier: Modifier = Modifier
) {
    val buttonSize = when (size) {
        TouchTargetSize.MINIMUM -> TouchTargets.minimum
        TouchTargetSize.RECOMMENDED -> TouchTargets.recommended
        TouchTargetSize.COMFORTABLE -> TouchTargets.comfortable
    }
    
    Button(
        onClick = onClick,
        modifier = modifier
            .sizeIn(
                minWidth = buttonSize,
                minHeight = buttonSize
            ),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp
        )
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center
        )
    }
}

enum class TouchTargetSize { MINIMUM, RECOMMENDED, COMFORTABLE }
```

### Gesture Alternatives
```kotlin
@Composable
fun AccessibleSwipeableCard(
    content: @Composable () -> Unit,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    showAlternativeButtons: Boolean = true
) {
    Column {
        // Main card with swipe gestures
        SwipeableCard(
            content = content,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight
        )
        
        // Alternative buttons for users who can't swipe
        if (showAlternativeButtons && (onSwipeLeft != null || onSwipeRight != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                onSwipeLeft?.let { action ->
                    AccessibleButton(
                        onClick = action,
                        text = "Previous"
                    )
                }
                onSwipeRight?.let { action ->
                    AccessibleButton(
                        onClick = action,
                        text = "Next"
                    )
                }
            }
        }
    }
}
```

### Keyboard Navigation
```kotlin
@Composable
fun KeyboardNavigableList(
    items: List<String>,
    onItemClick: (String) -> Unit,
    onItemDelete: ((String) -> Unit)? = null
) {
    val focusRequesters = remember { List(items.size) { FocusRequester() } }
    
    LazyColumn {
        itemsIndexed(items) { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index])
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        when {
                            keyEvent.key == Key.Enter || keyEvent.key == Key.Spacebar -> {
                                onItemClick(item)
                                true
                            }
                            keyEvent.key == Key.Delete && onItemDelete != null -> {
                                onItemDelete(item)
                                true
                            }
                            keyEvent.key == Key.DirectionDown && index < items.size - 1 -> {
                                focusRequesters[index + 1].requestFocus()
                                true
                            }
                            keyEvent.key == Key.DirectionUp && index > 0 -> {
                                focusRequesters[index - 1].requestFocus()
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable { onItemClick(item) }
                    .padding(16.dp)
            ) {
                Text(
                    text = item,
                    modifier = Modifier.weight(1f)
                )
                
                onItemDelete?.let { deleteAction ->
                    IconButton(
                        onClick = { deleteAction(item) },
                        modifier = Modifier.semantics {
                            contentDescription = "Delete $item"
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }
    }
}
```

---

## Cognitive Accessibility

### Simple Language & Clear Instructions
```kotlin
@Composable
fun AccessibleOnboarding(
    steps: List<OnboardingStep>,
    currentStep: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = (currentStep + 1).toFloat() / steps.size,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Step ${currentStep + 1} of ${steps.size}"
                }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Simple, clear instruction
        Text(
            text = steps[currentStep].title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = steps[currentStep].description,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp // Increased line height for readability
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Clear, labeled buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                AccessibleButton(
                    onClick = onPrevious,
                    text = "Previous"
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            
            AccessibleButton(
                onClick = if (currentStep == steps.size - 1) onComplete else onNext,
                text = if (currentStep == steps.size - 1) "Complete" else "Next"
            )
        }
    }
}
```

### Error Prevention & Recovery
```kotlin
@Composable
fun AccessibleForm(
    formState: FormState,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Clear form title
        Text(
            text = "Location Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        
        // Field with clear validation
        AccessibleTextField(
            value = formState.locationName,
            onValueChange = formState::updateLocationName,
            label = "Location Name",
            helpText = "Enter a memorable name for this location",
            error = formState.locationNameError,
            isRequired = true
        )
        
        // Clear submission with confirmation
        AccessibleButton(
            onClick = {
                if (formState.hasChanges) {
                    // Show confirmation dialog
                    showConfirmationDialog(onSubmit)
                } else {
                    onSubmit()
                }
            },
            text = "Save Location",
            size = TouchTargetSize.COMFORTABLE
        )
    }
}

@Composable
fun AccessibleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helpText: String? = null,
    error: String? = null,
    isRequired: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { 
                Text(label + if (isRequired) " *" else "")
            },
            isError = error != null,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (isRequired) {
                        stateDescription = "Required field"
                    }
                    helpText?.let {
                        contentDescription = "$label. $it"
                    }
                }
        )
        
        // Help text
        helpText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
        
        // Error message
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp)
                    .semantics {
                        role = Role.Alert
                        liveRegion = LiveRegionMode.Polite
                    }
            )
        }
    }
}
```

---

## Testing Accessibility

### Automated Testing
```kotlin
@Test
fun screen_meetsBasicAccessibilityRequirements() {
    composeTestRule.setContent {
        DashboardScreen()
    }
    
    // Test minimum touch targets
    composeTestRule
        .onAllNodesWithRole(Role.Button)
        .assertAll(
            hasMinTouchTarget(48.dp)
        )
    
    // Test content descriptions
    composeTestRule
        .onAllNodes(hasClickAction())
        .assertAll(
            hasContentDescription()
        )
    
    // Test heading structure
    composeTestRule
        .onNodeWithRole(Role.Heading)
        .assertExists()
}

@Test
fun navigation_supportsKeyboardNavigation() {
    composeTestRule.setContent {
        VoyagerBottomNavigation(
            currentDestination = VoyagerDestination.Dashboard,
            onNavigate = { }
        )
    }
    
    // Test tab navigation
    composeTestRule
        .onNodeWithRole(Role.TabList)
        .assertExists()
    
    composeTestRule
        .onAllNodesWithRole(Role.Tab)
        .assertCountEquals(5)
        .onFirst()
        .performKeyInput {
            pressKey(Key.Tab)
        }
}

fun hasMinTouchTarget(size: Dp): SemanticsMatcher {
    return SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)
        .and(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
}

fun hasContentDescription(): SemanticsMatcher {
    return SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
}
```

### Manual Testing Checklist
```markdown
## Screen Reader Testing
- [ ] All interactive elements have appropriate labels
- [ ] Content is announced in logical order
- [ ] Error messages are announced immediately
- [ ] Loading states are announced
- [ ] Success confirmations are announced

## Keyboard Navigation Testing
- [ ] All interactive elements are reachable via keyboard
- [ ] Focus order is logical and intuitive
- [ ] Focus indicators are clearly visible
- [ ] No keyboard traps exist
- [ ] Shortcuts work as expected

## Motor Accessibility Testing
- [ ] All touch targets meet 48dp minimum
- [ ] Gestures have alternative interactions
- [ ] Controls can be operated with one hand
- [ ] No precise timing requirements exist
- [ ] Drag and drop has alternatives

## Visual Accessibility Testing
- [ ] Text meets 4.5:1 contrast ratio (AA)
- [ ] Interactive elements meet 3:1 contrast ratio
- [ ] Content works without color alone
- [ ] Text scales to 200% without horizontal scrolling
- [ ] Motion can be disabled
```

---

## Platform-Specific Guidelines

### Android Accessibility
```kotlin
// TalkBack support
@Composable
fun TalkBackOptimizedComponent() {
    val accessibilityManager = LocalAccessibilityManager.current
    
    if (accessibilityManager.isEnabled) {
        // TalkBack-optimized layout
        Column {
            // Simplified content for screen readers
        }
    } else {
        // Standard visual layout
        Row {
            // Visual content
        }
    }
}

// Live regions for dynamic content
@Composable
fun LiveUpdateComponent(
    status: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = status,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            role = Role.Status
        }
    )
}
```

### iOS Accessibility (if applicable)
```kotlin
// VoiceOver support
@Composable
fun VoiceOverOptimizedComponent() {
    // iOS-specific accessibility patterns
}
```

---

## Resources & Tools

### Testing Tools
- **Android**: Accessibility Scanner, TalkBack, Switch Access
- **Development**: Accessibility Test Framework, Compose semantics testing
- **Manual**: Keyboard-only navigation, high contrast mode

### Guidelines & Standards
- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Android Accessibility Guidelines](https://developer.android.com/guide/topics/ui/accessibility)
- [Material Design Accessibility](https://material.io/design/usability/accessibility.html)

### Implementation Priorities
1. **Critical**: Screen reader support, keyboard navigation, minimum touch targets
2. **Important**: High contrast support, motion preferences, error handling
3. **Enhanced**: Voice control, advanced keyboard shortcuts, personalization

This accessibility framework ensures the Voyager app is usable by everyone, creating an inclusive experience that meets international standards and platform best practices.