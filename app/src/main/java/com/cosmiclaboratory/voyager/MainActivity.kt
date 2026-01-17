package com.cosmiclaboratory.voyager

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import com.exyte.animatednavbar.AnimatedNavigationBar
import com.exyte.animatednavbar.animation.balltrajectory.Parabolic
import com.exyte.animatednavbar.animation.indendshape.Height
import com.exyte.animatednavbar.animation.indendshape.shapeCornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerDestination
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerNavHost
import com.cosmiclaboratory.voyager.ui.theme.VoyagerTheme
import com.cosmiclaboratory.voyager.ui.theme.GreatVibesFontFamily
import com.cosmiclaboratory.voyager.ui.theme.Teal
import com.cosmiclaboratory.voyager.utils.PermissionManager
import com.cosmiclaboratory.voyager.utils.PermissionStatus
import com.cosmiclaboratory.voyager.domain.usecase.WorkerManagementUseCases
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Extension function for clickable without ripple effect
@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() }
) {
    onClick()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var workerManagementUseCases: WorkerManagementUseCases

    @Inject
    lateinit var locationUseCases: com.cosmiclaboratory.voyager.domain.usecase.LocationUseCases

    // State for permission management using proper flow for Activity context
    private val _permissionStatus = MutableStateFlow(PermissionStatus.LOCATION_DENIED)
    private val permissionStatus = _permissionStatus.asStateFlow()
    
    // UI state for permission education dialogs
    private var showBackgroundLocationEducation by mutableStateOf(false)
    private var permissionDenialMessage by mutableStateOf<String?>(null)
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Location permission result: $permissions")
        
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        // Always update permission status first
        updatePermissionStatus()
        
        if (fineLocationGranted && coarseLocationGranted) {
            Log.d("MainActivity", "Foreground location permissions granted")
            // DO NOT automatically request background location here - let user trigger it through UI
            // This ensures compliance with Android guidelines for background location requests
            Log.d("MainActivity", "User can now access the app and request background location if desired")
        } else {
            Log.d("MainActivity", "Location permissions denied")
            // Handle denial case - check if permanently denied
            handleLocationPermissionDenial(permissions)
        }
    }
    
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainActivity", "Background location permission result: $isGranted")
        
        // Always update permission status
        updatePermissionStatus()
        
        if (isGranted) {
            Log.d("MainActivity", "Background location permission granted")
        } else {
            Log.d("MainActivity", "Background location permission denied")
            // Check if we need notification permissions on Android 13+
            if (!PermissionManager.hasNotificationPermissions(this)) {
                requestNotificationPermissions()
            }
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainActivity", "Notification permission result: $isGranted")
        
        // Always update permission status - this could be the final permission needed
        updatePermissionStatus()
        
        if (isGranted) {
            Log.d("MainActivity", "All permissions granted - ready to use app")
        } else {
            Log.d("MainActivity", "Notification permission denied - app will work with limited functionality")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize permission status
        updatePermissionStatus()

        // Initialize background workers now that Hilt is ready
        lifecycleScope.launch {
            try {
                workerManagementUseCases.initializeBackgroundWorkers()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize background workers", e)
                // App continues to work without background workers
            }
        }

        // Phase 1 UX: Check for deep link navigation from notifications
        val navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            VoyagerTheme {
                val currentPermissionStatus = permissionStatus.collectAsState().value

                VoyagerApp(
                    permissionStatus = currentPermissionStatus,
                    onPermissionRequest = { handlePermissionRequest() },
                    onOpenSettings = { openAppSettings() },
                    onRequestNotificationPermission = { requestNotificationPermissions() },
                    initialDestination = navigateTo
                )
                
                // Show background location education dialog
                if (showBackgroundLocationEducation) {
                    BackgroundLocationEducationDialog(
                        onRequestPermission = { requestBackgroundLocationWithEducation() },
                        onSkip = { skipBackgroundLocation() },
                        onDismiss = { showBackgroundLocationEducation = false }
                    )
                }
                
                // Show permission denial message
                permissionDenialMessage?.let { message ->
                    AlertDialog(
                        onDismissRequest = { permissionDenialMessage = null },
                        title = { Text("Permission Required") },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(
                                onClick = { 
                                    permissionDenialMessage = null
                                    if (message.contains("Settings")) {
                                        openAppSettings()
                                    }
                                }
                            ) {
                                Text(if (message.contains("Settings")) "Open Settings" else "OK")
                            }
                        },
                        dismissButton = if (!message.contains("Settings")) {
                            {
                                TextButton(onClick = { permissionDenialMessage = null }) {
                                    Text("Cancel")
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }
    
    private fun updatePermissionStatus() {
        val newStatus = PermissionManager.getPermissionStatus(this)
        Log.d("MainActivity", "Permission status updated: $newStatus")
        val previousStatus = _permissionStatus.value
        _permissionStatus.value = newStatus

        // Auto-start tracking when all permissions are granted
        if (newStatus == PermissionStatus.ALL_GRANTED && previousStatus != PermissionStatus.ALL_GRANTED) {
            Log.d("MainActivity", "All permissions granted - starting location tracking automatically")
            lifecycleScope.launch {
                try {
                    locationUseCases.startLocationTracking()
                    Log.d("MainActivity", "Location tracking started successfully")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to start location tracking", e)
                }
            }
        }
    }
    
    private fun requestLocationPermissions() {
        Log.d("MainActivity", "Requesting basic location permissions: ${PermissionManager.LOCATION_PERMISSIONS.toList()}")
        locationPermissionLauncher.launch(PermissionManager.LOCATION_PERMISSIONS)
    }
    
    private fun requestBackgroundLocationPermission() {
        Log.d("MainActivity", "Requesting background location permission - showing education dialog")
        
        // Always show education dialog first for proper Android compliance
        showBackgroundLocationEducation = true
    }
    
    private fun requestNotificationPermissions() {
        Log.d("MainActivity", "Requesting notification permissions")
        if (PermissionManager.NOTIFICATION_PERMISSIONS.isNotEmpty()) {
            Log.d("MainActivity", "Launching notification permission: ${PermissionManager.NOTIFICATION_PERMISSIONS[0]}")
            notificationPermissionLauncher.launch(PermissionManager.NOTIFICATION_PERMISSIONS[0])
        } else {
            Log.d("MainActivity", "No notification permissions needed for this Android version")
        }
    }
    
    private fun handlePermissionRequest() {
        val currentStatus = _permissionStatus.value
        Log.d("MainActivity", "Handling permission request for status: $currentStatus")
        
        when (currentStatus) {
            PermissionStatus.LOCATION_DENIED -> {
                Log.d("MainActivity", "Location denied - requesting basic location permissions")
                requestLocationPermissions()
            }
            PermissionStatus.LOCATION_BASIC_ONLY -> {
                Log.d("MainActivity", "Basic location only - requesting background location")
                requestBackgroundLocationPermission()
            }
            PermissionStatus.LOCATION_GRANTED_BACKGROUND_NEEDED -> {
                Log.d("MainActivity", "Background location needed - requesting background location")
                requestBackgroundLocationPermission()
            }
            PermissionStatus.LOCATION_FULL_ACCESS -> {
                Log.d("MainActivity", "Location full access - requesting notifications")
                requestNotificationPermissions()
            }
            PermissionStatus.PARTIAL_GRANTED -> {
                // Request next needed permission
                val nextPermission = PermissionManager.getNextPermissionToRequest(this)
                Log.d("MainActivity", "Partial permissions granted - next needed: $nextPermission")
                when (nextPermission) {
                    Manifest.permission.ACCESS_FINE_LOCATION -> requestLocationPermissions()
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> requestBackgroundLocationPermission()
                    Manifest.permission.POST_NOTIFICATIONS -> requestNotificationPermissions()
                    null -> Log.d("MainActivity", "No additional permissions needed")
                }
            }
            PermissionStatus.ALL_GRANTED -> {
                Log.d("MainActivity", "All permissions already granted")
            }
        }
    }
    
    private fun openAppSettings() {
        PermissionManager.openAppSettings(this)
    }
    
    private fun handleLocationPermissionDenial(permissions: Map<String, Boolean>) {
        val fineLocationDenied = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == false
        val coarseLocationDenied = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == false
        
        if (fineLocationDenied || coarseLocationDenied) {
            // Check if permanently denied
            val isPermanentlyDenied = PermissionManager.isPermissionPermanentlyDenied(
                this, 
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            
            if (isPermanentlyDenied) {
                permissionDenialMessage = "Location permission was denied. Please enable it in Settings to use Voyager."
            } else {
                permissionDenialMessage = "Location permission is required for Voyager to work. Please try again."
            }
        }
    }
    
    private fun requestBackgroundLocationWithEducation() {
        Log.d("MainActivity", "Background location permission requested from education dialog")
        showBackgroundLocationEducation = false
        backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    
    private fun skipBackgroundLocation() {
        showBackgroundLocationEducation = false
        // Continue to notification permissions if needed
        if (!PermissionManager.hasNotificationPermissions(this)) {
            requestNotificationPermissions()
        }
    }
}

/**
 * Animated Voyager Title for Top App Bar
 *
 * Displays "Voyager - CosmicLabs by Anshul" with:
 * - "Voyager" in elegant Great Vibes font with animated color
 * - " - CosmicLabs by " in regular font
 * - "Anshul" clickable to navigate to developer profile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedVoyagerTitle(
    onDeveloperProfileClick: () -> Unit = {}
) {
    // Animate color between Teal and Purple/Gold
    val infiniteTransition = rememberInfiniteTransition(label = "titleColorAnimation")

    val animatedColor by infiniteTransition.animateColor(
        initialValue = Teal,
        targetValue = Color(0xFFC0C0C0), // Silver color
        animationSpec = infiniteRepeatable<Color>(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "titleColor"
    )

    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val annotatedString = buildAnnotatedString {
                    // "Voyager" in signature font with animated color
                    pushStringAnnotation(tag = "VOYAGER", annotation = "voyager")
                    withStyle(
                        style = SpanStyle(
                            color = animatedColor,
                            fontFamily = GreatVibesFontFamily,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append("Voyager")
                    }
                    pop()

                    // " - CosmicLabs by " in regular font
                    withStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    ) {
                        append(" - CosmicLabs by ")
                    }

                    // "Anshul" in regular font with animated color - CLICKABLE
                    val anshulStart = length
                    pushStringAnnotation(tag = "ANSHUL", annotation = "developer")
                    withStyle(
                        style = SpanStyle(
                            color = animatedColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append("Anshul")
                    }
                    pop()
                }

                ClickableText(
                    text = annotatedString,
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(
                            tag = "ANSHUL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let {
                            onDeveloperProfileClick()
                        }
                    }
                )
            }
        },
        actions = {
            IconButton(onClick = onDeveloperProfileClick) {
                Icon(
                    imageVector = Icons.Filled.ode,
                    contentDescription = "Developer Profile",
                    tint = Teal
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyagerApp(
    permissionStatus: PermissionStatus = PermissionStatus.LOCATION_DENIED,
    onPermissionRequest: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    initialDestination: String? = null
) {
    val navController = rememberNavController()
    var selectedDestination by remember { mutableStateOf(VoyagerDestination.Dashboard.route) }

    // Track selected index for AnimatedNavigationBar
    val bottomNavItems = VoyagerDestination.bottomNavItems.filterNotNull()
    val selectedIndex = bottomNavItems.indexOfFirst { it.route == selectedDestination }.takeIf { it >= 0 } ?: 0

    // Update selected destination when navigation happens (e.g., via "View on Map" button)
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val route = backStackEntry.destination.route
            // Only update if it's a bottom nav item
            if (route != null && bottomNavItems.any { it.route == route }) {
                selectedDestination = route
            }
        }
    }

    // Phase 1 UX: Handle deep link navigation from notifications
    LaunchedEffect(initialDestination) {
        initialDestination?.let { destination ->
            when (destination) {
                "place_review" -> {
                    navController.navigate(VoyagerDestination.PlaceReview.route)
                    selectedDestination = VoyagerDestination.PlaceReview.route
                }
            }
        }
    }

    // Show permission gateway if essential permissions are not granted
    // For Voyager to function properly, background location is REQUIRED
    when (permissionStatus) {
        PermissionStatus.LOCATION_DENIED -> {
            // Full-screen permission request
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.cosmiclaboratory.voyager.presentation.components.PermissionRequestCard(
                    permissionStatus = permissionStatus,
                    onRequestPermissions = onPermissionRequest,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        }
        else -> {
        // Show main app when permissions are granted
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(), // FIX: Prevent screen shortening when keyboard appears
            topBar = {
                AnimatedVoyagerTitle(
                    onDeveloperProfileClick = {
                        navController.navigate(VoyagerDestination.DeveloperProfile.route)
                    }
                )
            },
            bottomBar = {
                AnimatedNavigationBar(
                    selectedIndex = selectedIndex,
                    modifier = Modifier.height(64.dp),
                    cornerRadius = shapeCornerRadius(cornerRadius = 34.dp),
                    ballAnimation = Parabolic(tween(300)),
                    indentAnimation = Height(tween(300)),
                    barColor = MaterialTheme.colorScheme.surfaceContainer,
                    ballColor = MaterialTheme.colorScheme.primary
                ) {
                    bottomNavItems.forEachIndexed { index, destination ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .noRippleClickable {
                                    selectedDestination = destination.route
                                    navController.navigate(destination.route) {
                                        // Clear back stack to avoid building up a large stack
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.title,
                                tint = if (selectedIndex == index)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            // FIX: Use Box with consumeWindowInsets to prevent layout shifts
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                // Show permission upgrade banner for required and enhanced functionality
                when (permissionStatus) {
                    PermissionStatus.LOCATION_BASIC_ONLY -> {
                        PermissionUpgradeBanner(
                            permissionStatus = permissionStatus,
                            onRequestPermissions = onPermissionRequest,
                            onDismiss = { /* Could add dismiss logic if needed */ }
                        )
                    }
                    PermissionStatus.LOCATION_FULL_ACCESS -> {
                        // Show notification enhancement banner
                        PermissionUpgradeBanner(
                            permissionStatus = PermissionStatus.PARTIAL_GRANTED, // Use for notifications
                            onRequestPermissions = onPermissionRequest,
                            onDismiss = { /* Could add dismiss logic if needed */ }
                        )
                    }
                    else -> { /* No banner needed */ }
                }
                
                VoyagerNavHost(
                    navController = navController,
                    permissionStatus = permissionStatus,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    modifier = Modifier.weight(1f)
                )
            } // End Column
        } // End Box (consumeWindowInsets)
        } // End Scaffold
        } // End when else
    }
}

@Composable
private fun BackgroundLocationEducationDialog(
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Background Location Access",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Core Features Require Background Location:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Automatic place detection\n" +
                           "• Continuous visit tracking\n" +
                           "• Timeline creation\n" +
                           "• Movement pattern analysis",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📍 IMPORTANT: Choose 'Allow all the time'",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "On the next screen, select 'Allow all the time' for full functionality.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "🔒 Your data stays private and secure on your device, never shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Enable Background Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Use Limited Mode")
            }
        }
    )
}

@Composable
private fun PermissionUpgradeBanner(
    permissionStatus: PermissionStatus,
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, description, buttonText) = when (permissionStatus) {
        PermissionStatus.LOCATION_BASIC_ONLY -> Triple(
            "Background Location Required",
            "Voyager requires background location for core features: place detection, visit tracking, and timeline creation. Limited functionality without it.",
            "Enable Background Location"
        )
        PermissionStatus.LOCATION_GRANTED_BACKGROUND_NEEDED -> Triple(
            "Background Location Required",
            "Voyager requires background location for core features: place detection, visit tracking, and timeline creation. Limited functionality without it.",
            "Enable Background Location"
        )
        PermissionStatus.PARTIAL_GRANTED -> Triple(
            "Enable Notifications (Optional)",
            "Get notified when you arrive at or leave places for better insights",
            "Enable Notifications"
        )
        else -> return // Don't show banner for other states
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
            ) {
                Text(buttonText)
            }
        }
    }
}
