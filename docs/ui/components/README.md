# 🧩 Component Library Documentation

## Overview
This document provides comprehensive documentation for all reusable UI components in the Voyager application. Components are organized by category and include usage examples, customization options, and implementation guidelines.

## Component Categories

### Basic Components
- [Cards & Containers](#cards--containers)
- [Buttons & Actions](#buttons--actions)
- [Text & Typography](#text--typography)
- [Icons & Images](#icons--images)

### Data Display Components
- [Charts & Visualizations](#charts--visualizations)
- [Lists & Tables](#lists--tables)
- [Progress Indicators](#progress-indicators)
- [Badges & Labels](#badges--labels)

### Input Components
- [Forms & Fields](#forms--fields)
- [Selections & Pickers](#selections--pickers)
- [Search Components](#search-components)
- [Gesture Handlers](#gesture-handlers)

### Layout Components
- [Navigation](#navigation)
- [Containers & Wrappers](#containers--wrappers)
- [Spacing & Dividers](#spacing--dividers)
- [Responsive Layouts](#responsive-layouts)

### Specialized Components
- [Map Components](#map-components)
- [Timeline Components](#timeline-components)
- [Analytics Components](#analytics-components)
- [Permission Components](#permission-components)

---

## Cards & Containers

### StatsCard
**Purpose**: Display key metrics with consistent styling

```kotlin
@Composable
fun StatsCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trend: TrendData? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trend?.let { TrendIndicator(trend = it) }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

**Usage Examples**:
```kotlin
// Basic stats card
StatsCard(
    title = "Total Locations",
    value = "1,234",
    subtitle = "GPS points tracked"
)

// With icon and trend
StatsCard(
    title = "Places Visited",
    value = "42",
    icon = Icons.Default.Place,
    trend = TrendData(value = 5.2f, direction = TrendDirection.UP)
)

// Clickable card
StatsCard(
    title = "Today's Distance",
    value = "12.5 km",
    onClick = { /* Navigate to details */ }
)
```

### EnhancedCard
**Purpose**: Advanced card with multiple content types

```kotlin
@Composable
fun EnhancedCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    image: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp
)
```

---

## Charts & Visualizations

### MiniChart
**Purpose**: Small inline charts for trend visualization

```kotlin
@Composable
fun MiniChart(
    data: List<DataPoint>,
    color: Color = MaterialTheme.colorScheme.primary,
    lineWidth: Dp = 2.dp,
    showDots: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val path = Path()
        val stepX = size.width / (data.size - 1).coerceAtLeast(1)
        val maxY = data.maxOfOrNull { it.value } ?: 1f
        val minY = data.minOfOrNull { it.value } ?: 0f
        val range = (maxY - minY).coerceAtLeast(0.1f)
        
        data.forEachIndexed { index, point ->
            val x = index * stepX
            val y = size.height - ((point.value - minY) / range) * size.height
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = lineWidth.toPx())
        )
        
        if (showDots) {
            data.forEachIndexed { index, point ->
                val x = index * stepX
                val y = size.height - ((point.value - minY) / range) * size.height
                drawCircle(
                    color = color,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}
```

### CircularProgressIndicator
**Purpose**: Animated circular progress with customizable appearance

```kotlin
@Composable
fun CircularProgressIndicator(
    progress: Float,
    text: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 8.dp,
    size: Dp = 120.dp,
    animationDurationMs: Int = 1000,
    modifier: Modifier = Modifier
)
```

---

## Navigation

### BottomNavigationBar
**Purpose**: Main app navigation with Material 3 styling

```kotlin
@Composable
fun VoyagerBottomNavigation(
    currentDestination: VoyagerDestination,
    onNavigate: (VoyagerDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        VoyagerDestination.bottomNavItems.forEach { destination ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.title
                    )
                },
                label = { Text(destination.title) },
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) }
            )
        }
    }
}
```

### TopAppBarWithActions
**Purpose**: Customizable top app bar with actions

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarWithActions(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier
)
```

---

## Form Components

### SearchBar
**Purpose**: Advanced search with suggestions and filters

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyagerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    suggestions: List<SearchSuggestion> = emptyList(),
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    SearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        active = active,
        onActiveChange = onActiveChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear"
                    )
                }
            }
        } else null
    ) {
        LazyColumn {
            items(suggestions) { suggestion ->
                SearchSuggestionItem(
                    suggestion = suggestion,
                    onSelect = { onQueryChange(suggestion.text) }
                )
            }
        }
    }
}
```

---

## Specialized Components

### TimelineEntry
**Purpose**: Individual timeline entry with rich content

```kotlin
@Composable
fun TimelineEntryCard(
    entry: TimelineEntry,
    onEntryClick: (TimelineEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEntryClick(entry) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timeline indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (entry.type) {
                            TimelineEntryType.ARRIVAL -> MaterialTheme.colorScheme.primary
                            TimelineEntryType.DEPARTURE -> MaterialTheme.colorScheme.secondary
                            TimelineEntryType.VISIT -> MaterialTheme.colorScheme.tertiary
                        }
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.timestamp.format(timeFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (entry.duration != null) {
                Chip(
                    onClick = { },
                    label = { Text(formatDuration(entry.duration)) }
                )
            }
        }
    }
}
```

### PlaceMarker
**Purpose**: Map marker for places with customizable appearance

```kotlin
@Composable
fun PlaceMarker(
    place: Place,
    isSelected: Boolean = false,
    onClick: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clickable { onClick(place) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = place.category.icon,
            contentDescription = place.name,
            tint = place.category.color,
            modifier = Modifier.size(24.dp)
        )
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.RadioButtonChecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
```

---

## Component Utilities

### Trend Indicator
**Purpose**: Show data trends with visual indicators

```kotlin
@Composable
fun TrendIndicator(
    trend: TrendData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (trend.direction) {
                TrendDirection.UP -> Icons.Default.TrendingUp
                TrendDirection.DOWN -> Icons.Default.TrendingDown
                TrendDirection.NEUTRAL -> Icons.Default.TrendingFlat
            },
            contentDescription = trend.direction.name,
            tint = when (trend.direction) {
                TrendDirection.UP -> Color.Green
                TrendDirection.DOWN -> Color.Red
                TrendDirection.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = "${if (trend.value >= 0) "+" else ""}${trend.value}%",
            style = MaterialTheme.typography.bodySmall,
            color = when (trend.direction) {
                TrendDirection.UP -> Color.Green
                TrendDirection.DOWN -> Color.Red
                TrendDirection.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

data class TrendData(
    val value: Float,
    val direction: TrendDirection
)

enum class TrendDirection { UP, DOWN, NEUTRAL }
```

### Loading States
**Purpose**: Consistent loading indicators

```kotlin
@Composable
fun LoadingCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .shimmer()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(24.dp)
                    .shimmer()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

fun Modifier.shimmer(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.7f at 500
            },
            repeatMode = RepeatMode.Reverse
        )
    )
    alpha(alpha)
}
```

## Usage Guidelines

### Component Selection
1. **Consistency**: Use existing components before creating new ones
2. **Customization**: Prefer composable parameters over component variants
3. **Performance**: Consider recomposition impact in high-frequency updates
4. **Accessibility**: Ensure all components support screen readers

### Naming Conventions
- **Descriptive**: Component names should describe their purpose
- **Consistent**: Follow established naming patterns
- **Specific**: Avoid generic names like "CustomCard"

### Documentation Requirements
Each component should include:
- Purpose and use cases
- Parameter documentation
- Usage examples
- Accessibility considerations
- Performance notes

### Testing Strategy
- Unit tests for component logic
- Screenshot tests for visual regression
- Accessibility tests for screen readers
- Performance tests for complex components

## Implementation Checklist

### For Each Component:
- [ ] Clear purpose and scope defined
- [ ] Comprehensive parameter documentation
- [ ] Usage examples provided
- [ ] Accessibility features implemented
- [ ] Performance optimizations considered
- [ ] Visual design consistent with theme
- [ ] Error states handled appropriately
- [ ] Loading states implemented where needed
- [ ] Testing coverage adequate
- [ ] Documentation complete and accurate

## Next Steps
Review [Design Patterns](../patterns/README.md) for component composition guidelines and architectural patterns.