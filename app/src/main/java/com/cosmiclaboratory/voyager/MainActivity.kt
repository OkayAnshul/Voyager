package com.cosmiclaboratory.voyager

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerDestination
import com.cosmiclaboratory.voyager.presentation.navigation.VoyagerNavHost
import com.cosmiclaboratory.voyager.ui.theme.VoyagerTheme
import com.cosmiclaboratory.voyager.utils.PermissionHandler
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (fineLocationGranted && coarseLocationGranted) {
            // Permissions granted, can start location tracking
        } else {
            // Permissions denied, show explanation
        }
    }
    
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle background location permission result
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check and request location permissions
        checkLocationPermissions()
        
        setContent {
            VoyagerTheme {
                VoyagerApp()
            }
        }
    }
    
    private fun checkLocationPermissions() {
        when {
            PermissionHandler.hasLocationPermissions(this) -> {
                // Check background location permission if needed
                if (!PermissionHandler.hasBackgroundLocationPermission(this)) {
                    // Show rationale for background location if needed
                }
            }
            else -> {
                // Request location permissions
                locationPermissionLauncher.launch(PermissionHandler.LOCATION_PERMISSIONS)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyagerApp() {
    val navController = rememberNavController()
    var selectedDestination by remember { mutableStateOf(VoyagerDestination.Dashboard.route) }
    
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
        VoyagerNavHost(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}
