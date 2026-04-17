package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

/**
 * Permission onboarding shown on first launch.
 *
 * Step 1: Request foreground location + activity recognition + notifications (all at once)
 * Step 2: Request background location (Android requires this as a separate step)
 * Step 3: Done → proceed to app
 *
 * Android enforces that background location MUST be requested separately from foreground.
 */
@Composable
fun PermissionOnboardingScreen(
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    // Track which permissions have been responded to
    var foregroundDone by remember { mutableStateOf(false) }
    var backgroundDone by remember { mutableStateOf(false) }

    // Step 1: Foreground location + AR + notifications
    val foregroundPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        foregroundDone = true
        // Move to step 2 (background location) if on Android Q+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            step = 1
        } else {
            onComplete()
        }
    }

    // Step 2: Background location (must be separate on Android 11+)
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        backgroundDone = true
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(VoyagerColors.PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step == 0) Icons.Default.MyLocation else Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = VoyagerColors.Primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Title
            Text(
                text = if (step == 0) "Voyager needs permissions" else "Background location",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface,
                textAlign = TextAlign.Center
            )

            // Description
            Text(
                text = if (step == 0)
                    "To track your journeys, Voyager needs access to your location, activity recognition, and notifications."
                else
                    "For continuous tracking when the app is in the background, Voyager needs \"Allow all the time\" location access.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Permission items list
            if (step == 0) {
                PermissionItem(Icons.Default.MyLocation, "Location", "Track where you go")
                PermissionItem(Icons.Default.DirectionsWalk, "Activity Recognition", "Detect walk, drive, cycle")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionItem(Icons.Default.Notifications, "Notifications", "Tracking status updates")
                }
            } else {
                PermissionItem(Icons.Default.LocationOn, "Background Location", "Track even when app is closed")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grant button
            Button(
                onClick = {
                    if (step == 0) {
                        foregroundLauncher.launch(foregroundPermissions.toTypedArray())
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            onComplete()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoyagerColors.Primary,
                    contentColor = VoyagerColors.OnPrimary
                )
            ) {
                Text(
                    text = if (step == 0) "Grant Permissions" else "Allow Background Location",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Skip button (for step 2 only — foreground is enough to function)
            if (step == 1) {
                TextButton(onClick = onComplete) {
                    Text(
                        text = "Skip for now",
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }

            // Step indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalSteps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 2 else 1
                repeat(totalSteps) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == step) VoyagerColors.Primary
                                else VoyagerColors.SurfaceVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoyagerColors.Surface, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VoyagerColors.Primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}
