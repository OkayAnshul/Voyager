package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.BatteryRequirement
import com.cosmiclaboratory.voyager.domain.model.UserPreferences

@Composable
fun PlaceDetectionAutomationSection(
    preferences: UserPreferences,
    onUpdateFrequency: (Int) -> Unit,
    onUpdateTriggerCount: (Int) -> Unit,
    onUpdateBatteryRequirement: (BatteryRequirement) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Place Detection Automation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Text(
            text = "Control when and how often places are automatically detected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Detection Frequency
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
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Detection Frequency",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Every ${preferences.placeDetectionFrequencyHours}h - Automatic place analysis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = preferences.placeDetectionFrequencyHours.toFloat(),
                    onValueChange = { onUpdateFrequency(it.toInt()) },
                    valueRange = 1f..24f,
                    steps = 22, // Steps of 1 hour
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1h\n(Frequent)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "24h\n(Daily)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Auto-detect Trigger Count
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
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-detect Trigger",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "After ${preferences.autoDetectTriggerCount} locations - Trigger immediate detection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Slider(
                    value = preferences.autoDetectTriggerCount.toFloat(),
                    onValueChange = { onUpdateTriggerCount(it.toInt()) },
                    valueRange = 10f..500f,
                    steps = 48, // Steps of ~10 locations
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "10\n(Immediate)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "500\n(Patient)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Battery Requirements
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
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Battery Requirements",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (preferences.batteryRequirement) {
                                BatteryRequirement.ANY -> "No restrictions - Always run"
                                BatteryRequirement.NOT_LOW -> "Not low battery - Balanced approach"
                                BatteryRequirement.CHARGING -> "Only when charging - Battery friendly"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Battery Requirement Selection
                BatteryRequirement.values().forEach { requirement ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = preferences.batteryRequirement == requirement,
                            onClick = { onUpdateBatteryRequirement(requirement) }
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column {
                            Text(
                                text = when (requirement) {
                                    BatteryRequirement.ANY -> "Any Battery Level"
                                    BatteryRequirement.NOT_LOW -> "Not Low Battery"
                                    BatteryRequirement.CHARGING -> "Only When Charging"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = when (requirement) {
                                    BatteryRequirement.ANY -> "Run detection regardless of battery status"
                                    BatteryRequirement.NOT_LOW -> "Skip detection when battery is low (recommended)"
                                    BatteryRequirement.CHARGING -> "Only run when device is plugged in"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}