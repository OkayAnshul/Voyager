package com.cosmiclaboratory.voyager.presentation.screen.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.presentation.components.OpenStreetMapView
import com.cosmiclaboratory.voyager.presentation.components.PermissionRequestCard
import com.cosmiclaboratory.voyager.utils.PermissionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    permissionStatus: PermissionStatus = PermissionStatus.ALL_GRANTED,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top bar with controls
        TopAppBar(
            title = { Text("Map") },
            actions = {
                IconButton(
                    onClick = { viewModel.centerOnUser() },
                    enabled = permissionStatus == PermissionStatus.ALL_GRANTED
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Center on user")
                }
                IconButton(onClick = { viewModel.refreshMapData() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(
                    onClick = { viewModel.toggleLocationTracking() },
                    enabled = permissionStatus == PermissionStatus.ALL_GRANTED
                ) {
                    Icon(
                        if (uiState.isTracking && permissionStatus == PermissionStatus.ALL_GRANTED) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = if (uiState.isTracking) "Stop tracking" else "Start tracking"
                    )
                }
            }
        )
        
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = uiState.errorMessage!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            else -> {
                if (permissionStatus != PermissionStatus.ALL_GRANTED) {
                    // Show permission request instead of empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PermissionRequestCard(
                            permissionStatus = permissionStatus,
                            onRequestPermissions = { /* This should be handled by parent */ },
                            onOpenSettings = { /* This should be handled by parent */ }
                        )
                    }
                } else if (uiState.locations.isEmpty() && uiState.places.isEmpty()) {
                    // Show empty state when permissions are granted but no data
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No location data yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start location tracking to see your places and routes on the map",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.toggleLocationTracking() },
                            enabled = permissionStatus == PermissionStatus.ALL_GRANTED
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Tracking")
                        }
                    }
                } else {
                    // Show map and places
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Map view
                        OpenStreetMapView(
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight(),
                            center = uiState.mapCenter,
                            zoomLevel = uiState.zoomLevel,
                            locations = uiState.locations,
                            places = uiState.places,
                            userLocation = uiState.userLocation,
                            onPlaceClick = { place ->
                                viewModel.selectPlace(place)
                            }
                        )
                        
                        // Side panel with places list
                        if (uiState.places.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Places",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    LazyColumn {
                                        items(uiState.places) { place ->
                                            PlaceListItem(
                                                place = place,
                                                isSelected = place == uiState.selectedPlace,
                                                onClick = { viewModel.selectPlace(place) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Selected place details bottom sheet
        uiState.selectedPlace?.let { place ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                PlaceDetails(
                    place = place,
                    onDismiss = { viewModel.selectPlace(null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceListItem(
    place: Place,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = place.category.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${place.visitCount} visits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceDetails(
    place: Place,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Category: ${place.category.name}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Visits: ${place.visitCount}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Coordinates: ${String.format("%.6f", place.latitude)}, ${String.format("%.6f", place.longitude)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (place.confidence < 1.0f) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Confidence: ${(place.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}