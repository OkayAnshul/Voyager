# 🎨 Design Patterns & Guidelines

## Overview
This document establishes design patterns, architectural guidelines, and best practices for building consistent, maintainable, and user-friendly interfaces in the Voyager application.

## Design System Foundation

### Material Design 3 Principles
- **Accessible**: Usable by everyone, regardless of ability
- **Adaptive**: Flexible across platforms and contexts  
- **Human**: Expressive and relatable
- **Distinctive**: Memorable and differentiated

### Voyager Design Language
```kotlin
object VoyagerDesignTokens {
    // Color System
    val primaryColor = Color(0xFF2196F3)
    val secondaryColor = Color(0xFF03DAC6)
    val surfaceColor = Color(0xFFFFFBFE)
    val errorColor = Color(0xFFBA1A1A)
    
    // Typography Scale
    val headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp)
    val bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    
    // Spacing System
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 16.dp
    val spaceLg = 24.dp
    val spaceXl = 32.dp
    
    // Corner Radius
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
}
```

---

## Layout Patterns

### Screen Layout Pattern
**Purpose**: Consistent screen structure across the application

```kotlin
@Composable
fun ScreenLayout(
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = topBar ?: {},
        bottomBar = bottomBar ?: {},
        floatingActionButton = floatingActionButton ?: {},
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        content(paddingValues)
    }
}
```

**Usage Pattern**:
```kotlin
@Composable
fun DashboardScreen() {
    ScreenLayout(
        topBar = {
            TopAppBar(title = { Text("Dashboard") })
        },
        bottomBar = {
            VoyagerBottomNavigation(/* ... */)
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier.fillMaxSize()
        ) {
            // Screen content
        }
    }
}
```

### Content Container Pattern
**Purpose**: Consistent content spacing and organization

```kotlin
@Composable
fun ContentContainer(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = VoyagerDesignTokens.spaceMd,
    verticalSpacing: Dp = VoyagerDesignTokens.spaceMd,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content
    )
}
```

---

## Navigation Patterns

### Screen Navigation Pattern
**Purpose**: Consistent navigation handling

```kotlin
class NavigationManager {
    fun navigateToScreen(
        navController: NavController,
        destination: VoyagerDestination,
        clearBackStack: Boolean = false
    ) {
        if (clearBackStack) {
            navController.navigate(destination.route) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate(destination.route)
        }
    }
    
    fun navigateBack(navController: NavController): Boolean {
        return navController.popBackStack()
    }
}
```

### Deep Link Pattern
**Purpose**: Handle external navigation

```kotlin
sealed class DeepLink(val route: String) {
    object PlaceDetails : DeepLink("place/{placeId}")
    object DateTimeline : DeepLink("timeline/{date}")
    object Analytics : DeepLink("analytics?period={period}")
    
    companion object {
        fun fromUri(uri: Uri): DeepLink? {
            return when {
                uri.path?.startsWith("/place/") == true -> PlaceDetails
                uri.path?.startsWith("/timeline/") == true -> DateTimeline
                uri.path?.startsWith("/analytics") == true -> Analytics
                else -> null
            }
        }
    }
}
```

---

## Data Loading Patterns

### Loading State Pattern
**Purpose**: Consistent loading state management

```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val exception: Throwable) : UiState<Nothing>()
    object Empty : UiState<Nothing>()
}

@Composable
fun <T> UiStateHandler(
    uiState: UiState<T>,
    onRetry: (() -> Unit)? = null,
    loadingContent: @Composable () -> Unit = { DefaultLoadingContent() },
    emptyContent: @Composable () -> Unit = { DefaultEmptyContent() },
    errorContent: @Composable (Throwable) -> Unit = { DefaultErrorContent(it, onRetry) },
    successContent: @Composable (T) -> Unit
) {
    when (uiState) {
        is UiState.Loading -> loadingContent()
        is UiState.Success -> successContent(uiState.data)
        is UiState.Error -> errorContent(uiState.exception)
        is UiState.Empty -> emptyContent()
    }
}
```

### Pagination Pattern
**Purpose**: Handle large data sets efficiently

```kotlin
@Composable
fun <T> PaginatedList(
    items: LazyPagingItems<T>,
    itemContent: @Composable (T) -> Unit,
    loadingContent: @Composable () -> Unit = { DefaultLoadingItem() },
    errorContent: @Composable (Throwable) -> Unit = { DefaultErrorItem(it) }
) {
    LazyColumn {
        items(items) { item ->
            item?.let { itemContent(it) }
        }
        
        item {
            when (val loadState = items.loadState.append) {
                is LoadState.Loading -> loadingContent()
                is LoadState.Error -> errorContent(loadState.error)
                else -> Unit
            }
        }
    }
}
```

---

## Form Patterns

### Form Validation Pattern
**Purpose**: Consistent form validation handling

```kotlin
data class FormField<T>(
    val value: T,
    val error: String? = null,
    val isValid: Boolean = error == null
)

class FormState<T> {
    var fields by mutableStateOf(mapOf<String, FormField<Any>>())
        private set
    
    fun updateField(name: String, value: Any) {
        fields = fields.toMutableMap().apply {
            put(name, FormField(value = value))
        }
    }
    
    fun validateField(name: String, validator: (Any) -> String?) {
        fields[name]?.let { field ->
            val error = validator(field.value)
            fields = fields.toMutableMap().apply {
                put(name, field.copy(error = error, isValid = error == null))
            }
        }
    }
    
    val isValid: Boolean
        get() = fields.values.all { it.isValid }
}

@Composable
fun ValidatedTextField(
    field: FormField<String>,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = field.value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = !field.isValid,
        supportingText = field.error?.let { { Text(it) } },
        modifier = modifier
    )
}
```

---

## Animation Patterns

### Transition Pattern
**Purpose**: Consistent animations across the app

```kotlin
object VoyagerAnimations {
    val defaultEnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(300)
    ) + fadeIn(animationSpec = tween(300))
    
    val defaultExitTransition = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(300)
    ) + fadeOut(animationSpec = tween(300))
    
    val modalEnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    val modalExitTransition = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(250)
    )
}

@Composable
fun AnimatedScreenTransition(
    targetState: VoyagerDestination,
    content: @Composable (VoyagerDestination) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            VoyagerAnimations.defaultEnterTransition with 
            VoyagerAnimations.defaultExitTransition
        }
    ) { destination ->
        content(destination)
    }
}
```

### Value Animation Pattern
**Purpose**: Smooth value transitions

```kotlin
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    animationSpec: AnimationSpec<Float> = spring()
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = animationSpec
    )
    
    Text(
        text = animatedValue.toInt().toString(),
        modifier = modifier
    )
}

@Composable
fun AnimatedProgress(
    progress: Float,
    color: Color = MaterialTheme.colorScheme.primary,
    animationDurationMs: Int = 1000
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = animationDurationMs)
    )
    
    LinearProgressIndicator(
        progress = animatedProgress,
        color = color
    )
}
```

---

## Error Handling Patterns

### Error Display Pattern
**Purpose**: Consistent error presentation

```kotlin
@Composable
fun ErrorState(
    error: Throwable,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(VoyagerDesignTokens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(VoyagerDesignTokens.spaceMd))
        
        Text(
            text = error.localizedMessage ?: "An error occurred",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        onRetry?.let {
            Spacer(modifier = Modifier.height(VoyagerDesignTokens.spaceMd))
            Button(onClick = it) {
                Text("Retry")
            }
        }
    }
}
```

### Error Snackbar Pattern
**Purpose**: Non-intrusive error notifications

```kotlin
@Composable
fun ErrorSnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    ) { data ->
        Snackbar(
            action = data.visuals.actionLabel?.let { actionLabel ->
                {
                    TextButton(onClick = { data.performAction() }) {
                        Text(actionLabel)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Text(data.visuals.message)
        }
    }
}
```

---

## Accessibility Patterns

### Screen Reader Pattern
**Purpose**: Consistent accessibility support

```kotlin
@Composable
fun AccessibleCard(
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = onClick != null,
                onClickLabel = "Open $title",
                role = Role.Button
            ) { onClick?.invoke() }
            .semantics {
                contentDescription = "$title. $description"
                if (onClick != null) {
                    stateDescription = "Clickable"
                }
            }
    ) {
        content()
    }
}
```

### Focus Management Pattern
**Purpose**: Keyboard and screen reader navigation

```kotlin
@Composable
fun FocusableList(
    items: List<String>,
    onItemClick: (String) -> Unit
) {
    val focusRequesters = remember { List(items.size) { FocusRequester() } }
    
    LazyColumn {
        itemsIndexed(items) { index, item ->
            Button(
                onClick = { onItemClick(item) },
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { keyEvent ->
                        when (keyEvent.key) {
                            Key.DirectionDown -> {
                                if (index < items.size - 1) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (index > 0) {
                                    focusRequesters[index - 1].requestFocus()
                                }
                                true
                            }
                            else -> false
                        }
                    }
            ) {
                Text(item)
            }
        }
    }
}
```

---

## Performance Patterns

### Lazy Loading Pattern
**Purpose**: Efficient content loading

```kotlin
@Composable
fun LazyLoadingImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = { DefaultImagePlaceholder() },
    error: @Composable () -> Unit = { DefaultImageError() }
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
        error = error
    )
}
```

### Memory Optimization Pattern
**Purpose**: Efficient resource management

```kotlin
@Composable
fun OptimizedChart(
    data: List<DataPoint>,
    modifier: Modifier = Modifier
) {
    val sampledData = remember(data) {
        if (data.size > MAX_CHART_POINTS) {
            data.sampleEvenly(MAX_CHART_POINTS)
        } else {
            data
        }
    }
    
    val chartPath = remember(sampledData) {
        generateChartPath(sampledData)
    }
    
    Canvas(modifier = modifier) {
        drawPath(chartPath, Color.Blue)
    }
}

private fun List<DataPoint>.sampleEvenly(targetSize: Int): List<DataPoint> {
    if (size <= targetSize) return this
    val step = size.toFloat() / targetSize
    return (0 until targetSize).map { i ->
        this[(i * step).toInt()]
    }
}
```

---

## Testing Patterns

### Component Testing Pattern
**Purpose**: Consistent component testing approach

```kotlin
@Test
fun statsCard_displaysCorrectValues() {
    composeTestRule.setContent {
        StatsCard(
            title = "Test Title",
            value = "123",
            subtitle = "Test Subtitle"
        )
    }
    
    composeTestRule
        .onNodeWithText("Test Title")
        .assertIsDisplayed()
    
    composeTestRule
        .onNodeWithText("123")
        .assertIsDisplayed()
    
    composeTestRule
        .onNodeWithText("Test Subtitle")
        .assertIsDisplayed()
}

@Test
fun statsCard_handlesClickEvents() {
    var clicked = false
    
    composeTestRule.setContent {
        StatsCard(
            title = "Clickable Card",
            value = "456",
            onClick = { clicked = true }
        )
    }
    
    composeTestRule
        .onNodeWithText("Clickable Card")
        .performClick()
    
    assertTrue(clicked)
}
```

### Accessibility Testing Pattern
**Purpose**: Ensure accessibility compliance

```kotlin
@Test
fun screen_hasProperAccessibilityStructure() {
    composeTestRule.setContent {
        DashboardScreen()
    }
    
    // Test heading hierarchy
    composeTestRule
        .onNodeWithTag("screen_title")
        .assertHasClickAction(false)
        .assertIsDisplayed()
    
    // Test button accessibility
    composeTestRule
        .onAllNodesWithRole(Role.Button)
        .assertCountEquals(4)
        .onFirst()
        .assert(hasClickAction())
    
    // Test content descriptions
    composeTestRule
        .onNodeWithContentDescription("Location tracking status")
        .assertIsDisplayed()
}
```

---

## Code Organization Patterns

### File Structure Pattern
```
presentation/
├── screen/
│   ├── dashboard/
│   │   ├── DashboardScreen.kt
│   │   ├── DashboardViewModel.kt
│   │   └── components/
│   │       ├── StatsSection.kt
│   │       └── QuickActions.kt
│   └── ...
├── components/
│   ├── common/
│   │   ├── Cards.kt
│   │   ├── Buttons.kt
│   │   └── Charts.kt
│   └── specialized/
│       ├── TimelineComponents.kt
│       └── MapComponents.kt
├── navigation/
│   ├── VoyagerNavHost.kt
│   └── VoyagerDestination.kt
└── theme/
    ├── Theme.kt
    ├── Color.kt
    └── Typography.kt
```

### Naming Conventions
- **Screens**: `[Feature]Screen.kt` (e.g., `DashboardScreen.kt`)
- **ViewModels**: `[Feature]ViewModel.kt` (e.g., `DashboardViewModel.kt`)
- **Components**: Descriptive names (e.g., `StatsCard.kt`, `TimelineEntry.kt`)
- **Composables**: PascalCase matching component purpose

## Implementation Guidelines

### Best Practices
1. **Single Responsibility**: Each component should have one clear purpose
2. **Composition over Inheritance**: Prefer composable functions over class hierarchies
3. **State Hoisting**: Lift state to the appropriate level
4. **Immutable Data**: Use immutable data classes for UI state
5. **Preview Functions**: Include `@Preview` for all components

### Performance Guidelines
1. **Stable Parameters**: Use stable types for composable parameters
2. **Key Usage**: Provide keys for dynamic lists
3. **Derivation**: Use `derivedStateOf` for computed values
4. **Side Effects**: Use appropriate side effect APIs

### Accessibility Guidelines
1. **Content Descriptions**: Provide meaningful descriptions
2. **Semantic Roles**: Use appropriate roles for interactive elements
3. **Focus Order**: Ensure logical focus navigation
4. **Touch Targets**: Maintain minimum 48dp touch targets

---

## Design Tokens

### Spacing Scale
```kotlin
object Spacing {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
```

### Typography Scale
```kotlin
object VoyagerTypography {
    val displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp)
    val displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp)
    val displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp)
    
    val headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp)
    val headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp)
    val headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp)
    
    val titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp)
    val titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    
    val bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
}
```

### Animation Tokens
```kotlin
object AnimationTokens {
    val durationShort = 150
    val durationMedium = 300
    val durationLong = 500
    
    val easingStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val easingEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
}
```

These patterns and guidelines ensure consistency, maintainability, and excellent user experience across the Voyager application.