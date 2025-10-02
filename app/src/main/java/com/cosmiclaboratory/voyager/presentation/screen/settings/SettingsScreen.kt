package com.cosmiclaboratory.voyager.presentation.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDateTime

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showDeleteOldDataDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = { viewModel.refreshSettings() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Data Statistics Section
                    item {
                        SettingsSection("Data Statistics")
                    }
                    
                    item {
                        DataStatsCard(
                            totalLocations = uiState.totalLocations,
                            totalPlaces = uiState.totalPlaces,
                            totalVisits = uiState.totalVisits
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSection("Location Tracking")
                    }
                    
                    item {
                        SettingsItem(
                            title = "Location Tracking",
                            subtitle = if (uiState.isLocationTrackingEnabled) "Currently tracking" else "Tracking paused",
                            icon = Icons.Filled.LocationOn,
                            trailing = {
                                Switch(
                                    checked = uiState.isLocationTrackingEnabled,
                                    onCheckedChange = { viewModel.toggleLocationTracking() }
                                )
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSection("Data Management")
                    }
                    
                    item {
                        SettingsItem(
                            title = "Export Data",
                            subtitle = "Export your location data to file",
                            icon = Icons.Filled.Info,
                            onClick = { viewModel.exportData() },
                            trailing = if (uiState.isDataExporting) {
                                { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                            } else null
                        )
                    }
                    
                    item {
                        SettingsItem(
                            title = "Delete Old Data",
                            subtitle = "Remove data older than 30 days",
                            icon = Icons.Filled.Delete,
                            onClick = { showDeleteOldDataDialog = true }
                        )
                    }
                    
                    item {
                        SettingsItem(
                            title = "Clear All Data",
                            subtitle = "Delete all tracked locations and places",
                            icon = Icons.Filled.Delete,
                            onClick = { showClearDataDialog = true }
                        )
                    }
                }
            }
        }
        
        // Show messages
        uiState.exportMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearMessage()
            }
        }
        
        uiState.errorMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(5000)
                viewModel.clearMessage()
            }
        }
    }
    
    // Message snackbar
    uiState.exportMessage?.let { message ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
    
    uiState.errorMessage?.let { message ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
    
    // Confirmation Dialogs
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data") },
            text = { Text("This will permanently delete all your location data, places, and visits. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showClearDataDialog = false
                    }
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showDeleteOldDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteOldDataDialog = false },
            title = { Text("Delete Old Data") },
            text = { Text("This will delete all location data older than 30 days. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
                        viewModel.deleteOldData(thirtyDaysAgo)
                        showDeleteOldDataDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteOldDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DataStatsCard(
    totalLocations: Int,
    totalPlaces: Int,
    totalVisits: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Your Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DataStatItem("Locations", totalLocations.toString())
                DataStatItem("Places", totalPlaces.toString())
                DataStatItem("Visits", totalVisits.toString())
            }
        }
    }
}

@Composable
private fun DataStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: { }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            trailing?.invoke()
        }
    }
}