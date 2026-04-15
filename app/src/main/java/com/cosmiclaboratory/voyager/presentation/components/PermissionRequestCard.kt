package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.enums.PermissionState
import com.cosmiclaboratory.voyager.presentation.theme.*

@Composable
fun PermissionRequestCard(
    permissionState: PermissionState,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    VoyagerCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon based on permission state
            Icon(
                imageVector = when (permissionState) {
                    PermissionState.FINE_LOCATION -> Icons.Default.CheckCircle
                    PermissionState.COARSE_LOCATION -> Icons.Default.Warning
                    else -> Icons.Default.Close
                },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = when (permissionState) {
                    PermissionState.FINE_LOCATION -> MaterialTheme.colorScheme.primary
                    PermissionState.COARSE_LOCATION -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (permissionState) {
                PermissionState.FULL,
                PermissionState.FINE_LOCATION -> {
                    Text(
                        text = "PERMISSIONS GRANTED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "All required permissions are granted. Location tracking is ready!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                PermissionState.COARSE_LOCATION -> {
                    Text(
                        text = "FINE LOCATION REQUIRED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Voyager requires precise location for place detection, visit tracking, and timeline creation.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    VoyagerButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ENABLE PRECISE LOCATION")
                    }
                }

                PermissionState.NO_LOCATION_WITH_AR,
                PermissionState.NOTHING,
                PermissionState.BACKGROUND_RESTRICTED -> {
                    Text(
                        text = "LOCATION ACCESS REQUIRED",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Voyager needs location access to track your movements and detect places.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Your data stays private and secure on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    VoyagerButton(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GRANT PERMISSION")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    VoyagerButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SETTINGS")
                    }
                }
            }
        }
    }
}
