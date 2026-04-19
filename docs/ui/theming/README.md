# 🎨 Theming System Documentation

## Overview
This document outlines the comprehensive theming system for the Voyager application, including Material 3 integration, custom themes, dark mode support, and dynamic theming capabilities.

## Material 3 Foundation

### Color System
```kotlin
// Core color tokens
@Immutable
data class VoyagerColorScheme(
    // Primary colors
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    
    // Secondary colors
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    
    // Tertiary colors
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    
    // Error colors
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    
    // Surface colors
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceTint: Color,
    
    // Background colors
    val background: Color,
    val onBackground: Color,
    
    // Outline colors
    val outline: Color,
    val outlineVariant: Color,
    
    // Other colors
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    
    // Custom Voyager colors
    val location: Color,
    val place: Color,
    val route: Color,
    val achievement: Color,
    val warning: Color,
    val success: Color
)

// Light theme colors
val VoyagerLightColors = VoyagerColorScheme(
    primary = Color(0xFF2196F3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FD),
    onPrimaryContainer = Color(0xFF001B3E),
    
    secondary = Color(0xFF03DAC6),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFCCF7F3),
    onSecondaryContainer = Color(0xFF002019),
    
    tertiary = Color(0xFF9C27B0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1BEE7),
    onTertiaryContainer = Color(0xFF2E0A32),
    
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceTint = Color(0xFF2196F3),
    
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFF90CAF9),
    
    surfaceDim = Color(0xFFDDD8DD),
    surfaceBright = Color(0xFFFFFBFE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2F7),
    surfaceContainer = Color(0xFFF1ECF1),
    surfaceContainerHigh = Color(0xFFEBE6EB),
    surfaceContainerHighest = Color(0xFFE6E1E5),
    
    // Custom colors
    location = Color(0xFF4CAF50),
    place = Color(0xFFFF9800),
    route = Color(0xFF9C27B0),
    achievement = Color(0xFFFFD700),
    warning = Color(0xFFFF5722),
    success = Color(0xFF4CAF50)
)

// Dark theme colors
val VoyagerDarkColors = VoyagerColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF001B3E),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD3E4FD),
    
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF002019),
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFCCF7F3),
    
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF2E0A32),
    tertiaryContainer = Color(0xFF7B1FA2),
    onTertiaryContainer = Color(0xFFE1BEE7),
    
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceTint = Color(0xFF90CAF9),
    
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E5),
    
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF2196F3),
    
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    
    // Custom colors (adjusted for dark theme)
    location = Color(0xFF81C784),
    place = Color(0xFFFFB74D),
    route = Color(0xFFBA68C8),
    achievement = Color(0xFFFFF176),
    warning = Color(0xFFFF8A65),
    success = Color(0xFF81C784)
)
```

### Typography System
```kotlin
@Immutable
data class VoyagerTypography(
    // Display styles
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    
    // Headline styles
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    
    // Title styles
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    
    // Body styles
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    
    // Label styles
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle
)

val VoyagerTypography = VoyagerTypography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### Shape System
```kotlin
@Immutable
data class VoyagerShapes(
    val extraSmall: CornerBasedShape,
    val small: CornerBasedShape,
    val medium: CornerBasedShape,
    val large: CornerBasedShape,
    val extraLarge: CornerBasedShape
)

val VoyagerShapes = VoyagerShapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
```

---

## Theme Implementation

### Theme Provider
```kotlin
@Composable
fun VoyagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VoyagerDarkColors.toMaterial3ColorScheme()
        else -> VoyagerLightColors.toMaterial3ColorScheme()
    }
    
    val voyagerColors = if (darkTheme) VoyagerDarkColors else VoyagerLightColors
    
    CompositionLocalProvider(
        LocalVoyagerColors provides voyagerColors,
        LocalVoyagerTypography provides VoyagerTypography,
        LocalVoyagerShapes provides VoyagerShapes
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VoyagerTypography.toMaterial3Typography(),
            shapes = VoyagerShapes.toMaterial3Shapes(),
            content = content
        )
    }
}

// Extension functions to convert custom scheme to Material 3
fun VoyagerColorScheme.toMaterial3ColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        // ... map all colors
    )
}

// Local composition providers
val LocalVoyagerColors = staticCompositionLocalOf { VoyagerLightColors }
val LocalVoyagerTypography = staticCompositionLocalOf { VoyagerTypography }
val LocalVoyagerShapes = staticCompositionLocalOf { VoyagerShapes }

// Theme accessors
object VoyagerTheme {
    val colors: VoyagerColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalVoyagerColors.current
    
    val typography: VoyagerTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalVoyagerTypography.current
    
    val shapes: VoyagerShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalVoyagerShapes.current
}
```

### Theme Configuration
```kotlin
data class ThemeConfiguration(
    val isDarkTheme: Boolean,
    val useDynamicColors: Boolean,
    val accentColor: AccentColor,
    val fontScale: Float,
    val animations: AnimationPreference
)

enum class AccentColor(val color: Color) {
    BLUE(Color(0xFF2196F3)),
    GREEN(Color(0xFF4CAF50)),
    PURPLE(Color(0xFF9C27B0)),
    ORANGE(Color(0xFFFF9800)),
    RED(Color(0xFFF44336))
}

enum class AnimationPreference {
    FULL, REDUCED, DISABLED
}

class ThemeManager {
    private val _themeConfiguration = MutableStateFlow(ThemeConfiguration.default())
    val themeConfiguration: StateFlow<ThemeConfiguration> = _themeConfiguration.asStateFlow()
    
    fun updateTheme(configuration: ThemeConfiguration) {
        _themeConfiguration.value = configuration
    }
    
    fun toggleDarkMode() {
        _themeConfiguration.value = _themeConfiguration.value.copy(
            isDarkTheme = !_themeConfiguration.value.isDarkTheme
        )
    }
    
    fun setAccentColor(color: AccentColor) {
        _themeConfiguration.value = _themeConfiguration.value.copy(
            accentColor = color
        )
    }
}
```

---

## Custom Theming

### Category-Based Colors
```kotlin
enum class PlaceCategory(
    val displayName: String,
    val icon: ImageVector,
    val lightColor: Color,
    val darkColor: Color
) {
    HOME("Home", Icons.Default.Home, Color(0xFF4CAF50), Color(0xFF81C784)),
    WORK("Work", Icons.Default.Work, Color(0xFF2196F3), Color(0xFF90CAF9)),
    GYM("Gym", Icons.Default.FitnessCenter, Color(0xFFFF5722), Color(0xFFFF8A65)),
    SHOPPING("Shopping", Icons.Default.ShoppingCart, Color(0xFFE91E63), Color(0xFFF48FB1)),
    RESTAURANT("Restaurant", Icons.Default.Restaurant, Color(0xFFFF9800), Color(0xFFFFB74D)),
    ENTERTAINMENT("Entertainment", Icons.Default.Movie, Color(0xFF9C27B0), Color(0xFFBA68C8)),
    TRANSPORT("Transport", Icons.Default.Train, Color(0xFF607D8B), Color(0xFF90A4AE)),
    HEALTHCARE("Healthcare", Icons.Default.LocalHospital, Color(0xFFF44336), Color(0xFFEF5350)),
    EDUCATION("Education", Icons.Default.School, Color(0xFF3F51B5), Color(0xFF7986CB)),
    TRAVEL("Travel", Icons.Default.Flight, Color(0xFF009688), Color(0xFF4DB6AC)),
    OUTDOOR("Outdoor", Icons.Default.Park, Color(0xFF8BC34A), Color(0xFFAED581)),
    SOCIAL("Social", Icons.Default.Group, Color(0xFFFFEB3B), Color(0xFFFFF176)),
    SERVICES("Services", Icons.Default.Build, Color(0xFF795548), Color(0xFFA1887F)),
    UNKNOWN("Unknown", Icons.Default.Place, Color(0xFF9E9E9E), Color(0xFFBDBDBD)),
    CUSTOM("Custom", Icons.Default.Star, Color(0xFF673AB7), Color(0xFF9575CD));
    
    @Composable
    fun getColor(): Color {
        return if (VoyagerTheme.colors == VoyagerDarkColors) darkColor else lightColor
    }
}

@Composable
fun CategoryColorChip(
    category: PlaceCategory,
    modifier: Modifier = Modifier
) {
    Surface(
        color = category.getColor(),
        shape = CircleShape,
        modifier = modifier.size(24.dp)
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.displayName,
            tint = Color.White,
            modifier = Modifier.padding(4.dp)
        )
    }
}
```

### Dynamic Theming
```kotlin
@Composable
fun DynamicTheme(
    seedColor: Color,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = remember(seedColor, isDarkTheme) {
        if (isDarkTheme) {
            dynamicDarkColorScheme(seedColor)
        } else {
            dynamicLightColorScheme(seedColor)
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// Generate color scheme from seed color
private fun dynamicLightColorScheme(seedColor: Color): ColorScheme {
    val hct = Hct.fromInt(seedColor.toArgb())
    val palette = CorePalette.of(hct.hue, hct.chroma)
    
    return lightColorScheme(
        primary = Color(palette.a1.tone(40)),
        onPrimary = Color(palette.a1.tone(100)),
        primaryContainer = Color(palette.a1.tone(90)),
        onPrimaryContainer = Color(palette.a1.tone(10)),
        // ... generate all colors from palette
    )
}
```

---

## Responsive Theming

### Adaptive Layout
```kotlin
@Composable
fun AdaptiveCard(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = calculateWindowSizeClass()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> VoyagerTheme.shapes.medium
            WindowWidthSizeClass.Medium -> VoyagerTheme.shapes.large
            WindowWidthSizeClass.Expanded -> VoyagerTheme.shapes.extraLarge
            else -> VoyagerTheme.shapes.medium
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Compact -> 4.dp
                WindowWidthSizeClass.Medium -> 6.dp
                WindowWidthSizeClass.Expanded -> 8.dp
                else -> 4.dp
            }
        )
    ) {
        content()
    }
}

@Composable
fun ResponsiveSpacing(): PaddingValues {
    val windowSizeClass = calculateWindowSizeClass()
    
    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> PaddingValues(16.dp)
        WindowWidthSizeClass.Medium -> PaddingValues(24.dp)
        WindowWidthSizeClass.Expanded -> PaddingValues(32.dp)
        else -> PaddingValues(16.dp)
    }
}
```

### Device-Specific Adaptations
```kotlin
@Composable
fun DeviceAdaptiveTheme(
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val adaptedTypography = VoyagerTypography.copy(
        displayLarge = VoyagerTypography.displayLarge.copy(
            fontSize = if (isTablet) 64.sp else 57.sp
        ),
        headlineLarge = VoyagerTypography.headlineLarge.copy(
            fontSize = if (isTablet) 40.sp else 32.sp
        )
    )
    
    CompositionLocalProvider(
        LocalVoyagerTypography provides adaptedTypography
    ) {
        content()
    }
}
```

---

## Theme Customization

### User Theme Preferences
```kotlin
@Composable
fun ThemeCustomizationScreen(
    onThemeChange: (ThemeConfiguration) -> Unit
) {
    var isDarkTheme by remember { mutableStateOf(false) }
    var useDynamicColors by remember { mutableStateOf(true) }
    var selectedAccentColor by remember { mutableStateOf(AccentColor.BLUE) }
    
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ThemeSection(title = "Appearance") {
                SwitchPreference(
                    title = "Dark Theme",
                    subtitle = "Use dark colors throughout the app",
                    checked = isDarkTheme,
                    onCheckedChange = { isDarkTheme = it }
                )
                
                SwitchPreference(
                    title = "Dynamic Colors",
                    subtitle = "Use colors from your wallpaper",
                    checked = useDynamicColors,
                    onCheckedChange = { useDynamicColors = it },
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                )
            }
        }
        
        item {
            ThemeSection(title = "Accent Color") {
                AccentColorPicker(
                    selectedColor = selectedAccentColor,
                    onColorSelected = { selectedAccentColor = it },
                    enabled = !useDynamicColors
                )
            }
        }
        
        item {
            ThemeSection(title = "Preview") {
                ThemePreview(
                    isDarkTheme = isDarkTheme,
                    accentColor = selectedAccentColor,
                    useDynamicColors = useDynamicColors
                )
            }
        }
    }
}

@Composable
fun AccentColorPicker(
    selectedColor: AccentColor,
    onColorSelected: (AccentColor) -> Unit,
    enabled: Boolean = true
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(AccentColor.values()) { color ->
            ColorOption(
                color = color.color,
                isSelected = selectedColor == color,
                onClick = { onColorSelected(color) },
                enabled = enabled
            )
        }
    }
}

@Composable
fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1.0f
    )
    
    Surface(
        color = color,
        shape = CircleShape,
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1.0f else 0.5f),
        border = if (isSelected) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.outline)
        } else null
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
        }
    }
}
```

### Theme Persistence
```kotlin
class ThemePreferencesManager(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        private val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        private val ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val FONT_SCALE = floatPreferencesKey("font_scale")
    }
    
    val themeConfiguration: Flow<ThemeConfiguration> = dataStore.data.map { preferences ->
        ThemeConfiguration(
            isDarkTheme = preferences[IS_DARK_THEME] ?: false,
            useDynamicColors = preferences[USE_DYNAMIC_COLORS] ?: true,
            accentColor = AccentColor.valueOf(
                preferences[ACCENT_COLOR] ?: AccentColor.BLUE.name
            ),
            fontScale = preferences[FONT_SCALE] ?: 1.0f
        )
    }
    
    suspend fun updateTheme(configuration: ThemeConfiguration) {
        dataStore.edit { preferences ->
            preferences[IS_DARK_THEME] = configuration.isDarkTheme
            preferences[USE_DYNAMIC_COLORS] = configuration.useDynamicColors
            preferences[ACCENT_COLOR] = configuration.accentColor.name
            preferences[FONT_SCALE] = configuration.fontScale
        }
    }
}
```

---

## Animation Integration

### Theme-Aware Animations
```kotlin
@Composable
fun ThemeTransition(
    targetTheme: VoyagerColorScheme,
    content: @Composable () -> Unit
) {
    val animatedColors by rememberUpdatedState(targetTheme)
    
    CompositionLocalProvider(
        LocalVoyagerColors provides animatedColors
    ) {
        content()
    }
}

@Composable
fun AnimatedThemeSwitch(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isDarkTheme) {
            VoyagerDarkColors.surface
        } else {
            VoyagerLightColors.surface
        },
        animationSpec = tween(durationMillis = 500)
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable { onThemeToggle() }
    ) {
        // Content with animated background
    }
}
```

---

## Platform Integration

### System Theme Detection
```kotlin
@Composable
fun SystemAwareTheme(
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val systemDarkTheme = isSystemInDarkTheme()
    
    // Monitor system theme changes
    DisposableEffect(systemDarkTheme) {
        // Handle theme change
        onDispose { }
    }
    
    VoyagerTheme(
        darkTheme = systemDarkTheme,
        content = content
    )
}
```

### Edge-to-Edge Support
```kotlin
@Composable
fun EdgeToEdgeTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val darkTheme = VoyagerTheme.colors == VoyagerDarkColors
    
    DisposableEffect(darkTheme) {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.TRANSPARENT.toArgb()
        window.navigationBarColor = Color.TRANSPARENT.toArgb()
        
        WindowInsetsControllerCompat(window, view).let { controller ->
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
        
        onDispose { }
    }
    
    content()
}
```

---

## Testing Themes

### Theme Testing
```kotlin
@Test
fun theme_appliesCorrectColors() {
    composeTestRule.setContent {
        VoyagerTheme(darkTheme = false) {
            Text(
                text = "Test",
                color = VoyagerTheme.colors.primary
            )
        }
    }
    
    // Verify color application
    // Note: Actual color testing requires screenshot testing
}

@Test
fun theme_respondsToSystemChanges() {
    var isDarkTheme by mutableStateOf(false)
    
    composeTestRule.setContent {
        VoyagerTheme(darkTheme = isDarkTheme) {
            Surface(
                color = VoyagerTheme.colors.surface
            ) {
                Text("Theme Test")
            }
        }
    }
    
    // Change theme
    isDarkTheme = true
    
    // Verify theme update
    composeTestRule.onNodeWithText("Theme Test").assertExists()
}
```

### Visual Regression Testing
```kotlin
@Test
fun themePreview_matchesDesignSpecs() {
    composeTestRule.setContent {
        VoyagerTheme {
            ThemeShowcase()
        }
    }
    
    composeTestRule.onRoot().captureToImage()
    // Compare with baseline image
}

@Composable
fun ThemeShowcase() {
    LazyColumn {
        item { ColorShowcase() }
        item { TypographyShowcase() }
        item { ComponentShowcase() }
    }
}
```

This comprehensive theming system provides a solid foundation for consistent, customizable, and accessible design throughout the Voyager application.