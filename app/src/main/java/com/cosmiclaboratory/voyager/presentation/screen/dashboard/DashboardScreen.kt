package com.cosmiclaboratory.voyager.presentation.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.utils.PermissionStatus
import com.cosmiclaboratory.voyager.domain.model.Place

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Journey Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Live status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isLocationTrackingActive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = Color.Green,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Green,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tracking paused",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
            // Stats Cards - Basic version for now
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Locations", style = MaterialTheme.typography.titleMedium)
                        Text(uiState.totalLocations.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("GPS points tracked", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Places Visited", style = MaterialTheme.typography.titleMedium)
                        Text(uiState.totalPlaces.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Unique locations", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Time", style = MaterialTheme.typography.titleMedium)
                        Text("${uiState.totalTimeTracked / (1000 * 60)}m", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Time spent tracking", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            
            // Real-time current visit status
            if (uiState.isAtPlace && uiState.currentPlace != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Current location", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Currently at", style = MaterialTheme.typography.labelMedium)
                                    Text(uiState.currentPlace!!.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Duration: ${uiState.currentVisitDuration / (1000 * 60)}m", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Location Tracking", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (uiState.isTracking) "Currently tracking" else "Tracking stopped",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isTracking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.isTracking,
                                onCheckedChange = { viewModel.toggleLocationTracking() }
                            )
                        }
                    }
                }
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Place Detection", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (uiState.isDetectingPlaces) "Analyzing locations..." else "Process location data to find places",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = { viewModel.triggerPlaceDetection() },
                                enabled = !uiState.isDetectingPlaces
                            ) {
                                Text(if (uiState.isDetectingPlaces) "Processing..." else "Detect Now")
                            }
                        }
                    }
                }
            }
            
            // Debug section for troubleshooting
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔧 Debug Tools", 
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Troubleshoot place detection issues",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.debugGetDiagnosticInfo() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📊 Diagnostics")
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.debugManualPlaceDetection() },
                                enabled = !uiState.isDetectingPlaces,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🚀 Force Detection")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.debugWorkManagerHealth() },
                                enabled = !uiState.isDetectingPlaces,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔧 Health")
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.debugResetPreferences() },
                                enabled = !uiState.isDetectingPlaces,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Reset")
                            }
                        }
                        
                        // Show diagnostic/error info if available
                        uiState.errorMessage?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = message,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 fun StatsCard(
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 fun LocationTrackingCard(
    isTracking: Boolean,
    onToggleTracking: () -> Unit,
    permissionStatus: PermissionStatus
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Location Tracking",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            permissionStatus != PermissionStatus.ALL_GRANTED -> "Permissions required"
                            isTracking -> "Currently tracking"
                            else -> "Tracking stopped"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            permissionStatus != PermissionStatus.ALL_GRANTED -> MaterialTheme.colorScheme.error
                            isTracking -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                
                Switch(
                    checked = isTracking && permissionStatus == PermissionStatus.ALL_GRANTED,
                    onCheckedChange = { 
                        if (permissionStatus == PermissionStatus.ALL_GRANTED) {
                            onToggleTracking()
                        }
                    },
                    enabled = permissionStatus == PermissionStatus.ALL_GRANTED
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 fun PlaceDetectionCard(
    onDetectNow: () -> Unit,
    isDetecting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Place Detection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isDetecting) "Analyzing locations..." else "Process location data to find places",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = onDetectNow,
                    enabled = !isDetecting
                ) {
                    if (isDetecting) {
                        Text("Processing...")
                    } else {
                        Text("Detect Now")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 fun CurrentVisitCard(
    currentPlace: Place,
    visitDuration: Long,
    isActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Current location",
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = if (isActive) "Currently at" else "Last visit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentPlace.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (isActive) "Live Duration" else "Duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${visitDuration / (1000 * 60)}m",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            if (isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Color.Green,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active visit in progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

 fun formatDuration(milliseconds: Long): String {
    val hours = milliseconds / (1000 * 60 * 60)
    val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "0m"
    }
}
}
}
