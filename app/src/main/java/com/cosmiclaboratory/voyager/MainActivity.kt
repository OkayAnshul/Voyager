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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerDestination
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerNavHost
import com.cosmiclaboratory.voyager.ui.theme.VoyagerTheme
import com.cosmiclaboratory.voyager.utils.PermissionManager
import com.cosmiclaboratory.voyager.utils.PermissionStatus
import com.cosmiclaboratory.voyager.domain.usecase.WorkerManagementUseCases
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var workerManagementUseCases: WorkerManagementUseCases
    
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
        
        setContent {
            VoyagerTheme {
                val currentPermissionStatus = permissionStatus.collectAsState().value
                
                VoyagerApp(
                    permissionStatus = currentPermissionStatus,
                    onPermissionRequest = { handlePermissionRequest() },
                    onOpenSettings = { openAppSettings() },
                    onRequestNotificationPermission = { requestNotificationPermissions() }
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
        _permissionStatus.value = newStatus
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyagerApp(
    permissionStatus: PermissionStatus = PermissionStatus.LOCATION_DENIED,
    onPermissionRequest: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val navController = rememberNavController()
    var selectedDestination by remember { mutableStateOf(VoyagerDestination.Dashboard.route) }
    
    // Show permission gateway if essential permissions are not granted
    // For Voyager to function properly, background location is REQUIRED
    when (permissionStatus) {
        PermissionStatus.LOCATION_DENIED -> {
            com.cosmiclaboratory.voyager.presentation.screen.permission.PermissionGatewayScreen(
                permissionStatus = permissionStatus,
                onRequestPermissions = onPermissionRequest,
                onOpenSettings = onOpenSettings,
                onContinueToApp = { /* This will be handled by permission status change */ }
            )
        }
        else -> {
        // Show main app when permissions are granted
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    VoyagerDestination.bottomNavItems.filterNotNull().forEach { destination ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title) },
                            selected = selectedDestination == destination.route,
                            onClick = {
                                selectedDestination = destination.route
                                navController.navigate(destination.route) {
                                    // Clear back stack to avoid building up a large stack
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
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
            }
        }
        }
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
                    text = "Voyager requires background location access for core functionality:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "✅ Automatic place detection (home, work, etc.)\n" +
                           "✅ Continuous visit tracking and timeline creation\n" +
                           "✅ Movement pattern analysis\n" +
                           "✅ Background location recording",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "⚠️ Without background location, core features will not work properly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Your location data is stored securely on your device and never shared.",
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
