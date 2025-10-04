package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.UserPreferences
import com.cosmiclaboratory.voyager.utils.PermissionManager

@Composable
fun NotificationSettingsSection(
    preferences: UserPreferences,
    onUpdateNotificationSettings: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onUpdateNotificationFrequency: (Int) -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasNotificationPermission = PermissionManager.hasNotificationPermissions(context)
    Column(modifier = modifier) {
        Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // Permission Status Banner
        if (!hasNotificationPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Grant notification permission to receive alerts when you arrive at or leave places.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Notification Permission")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Notification Types
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Notification Types",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Arrival Notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Arrival Notifications",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Get notified when you arrive at places",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = preferences.enableArrivalNotifications && hasNotificationPermission,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotificationPermission) {
                                onRequestNotificationPermission()
                            } else if (hasNotificationPermission) {
                                onUpdateNotificationSettings(
                                    enabled,
                                    preferences.enableDepartureNotifications,
                                    preferences.enablePatternNotifications,
                                    preferences.enableWeeklySummary
                                )
                            }
                        },
                        enabled = hasNotificationPermission || !preferences.enableArrivalNotifications
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Departure Notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Departure Notifications",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Get notified when you leave places",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = preferences.enableDepartureNotifications && hasNotificationPermission,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotificationPermission) {
                                onRequestNotificationPermission()
                            } else if (hasNotificationPermission) {
                                onUpdateNotificationSettings(
                                    preferences.enableArrivalNotifications,
                                    enabled,
                                    preferences.enablePatternNotifications,
                                    preferences.enableWeeklySummary
                                )
                            }
                        },
                        enabled = hasNotificationPermission || !preferences.enableDepartureNotifications
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Pattern Notifications
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pattern Notifications",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Get notified about unusual patterns",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = preferences.enablePatternNotifications && hasNotificationPermission,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotificationPermission) {
                                onRequestNotificationPermission()
                            } else if (hasNotificationPermission) {
                                onUpdateNotificationSettings(
                                    preferences.enableArrivalNotifications,
                                    preferences.enableDepartureNotifications,
                                    enabled,
                                    preferences.enableWeeklySummary
                                )
                            }
                        },
                        enabled = hasNotificationPermission || !preferences.enablePatternNotifications
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Weekly Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Weekly Summary",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Get weekly movement and place insights",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = preferences.enableWeeklySummary && hasNotificationPermission,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotificationPermission) {
                                onRequestNotificationPermission()
                            } else if (hasNotificationPermission) {
                                onUpdateNotificationSettings(
                                    preferences.enableArrivalNotifications,
                                    preferences.enableDepartureNotifications,
                                    preferences.enablePatternNotifications,
                                    enabled
                                )
                            }
                        },
                        enabled = hasNotificationPermission || !preferences.enableWeeklySummary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Notification Frequency
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update Frequency",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Update notification every ${preferences.notificationUpdateFrequency} locations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = preferences.notificationUpdateFrequency.toFloat(),
                    onValueChange = { onUpdateNotificationFrequency(it.toInt()) },
                    valueRange = 1f..100f, // Every 1 to 100 locations
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}