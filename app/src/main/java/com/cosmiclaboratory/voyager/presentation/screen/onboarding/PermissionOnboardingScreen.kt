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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerPrimaryButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSurfaces

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
            .drawBehind { drawRect(VoyagerGradients.screenBackground(size.width, size.height)) }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            // Icon — aurora glow
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(VoyagerSurfaces.auroraSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step == 0) Icons.Default.MyLocation else Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = VoyagerColors.Primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Title — value-first
            Text(
                text = if (step == 0) "Your journeys, captured privately" else "Keep capturing in the background",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface,
                textAlign = TextAlign.Center
            )

            // Description
            Text(
                text = if (step == 0)
                    "Voyager remembers where you've been so you can revisit it, prove it, and see your patterns — all on your device. To do that, it needs a few permissions."
                else
                    "One more step. Tap \"Allow Background Location\" below, then choose \"Allow all the time\" on the screen that opens. This is what lets Voyager keep recording your route while the screen is off — without it, your timeline will have gaps. Your location never leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Permission items list — honest "why we ask" rationale
            if (step == 0) {
                PermissionItem(Icons.Default.MyLocation, "Location", "So your timeline knows where you've been")
                PermissionItem(Icons.Default.DirectionsWalk, "Activity Recognition", "To tell walking from driving — computed on-device")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionItem(Icons.Default.Notifications, "Notifications", "A quiet status while tracking runs")
                }
            } else {
                PermissionItem(Icons.Default.LocationOn, "Background Location", "Capture your route even when the screen's off")
            }

            // Privacy reassurance — the moat, stated plainly
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VoyagerColors.AccentGreen, modifier = Modifier.size(14.dp))
                Text(
                    text = "No account, no cloud. Everything stays on your device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.AccentGreen,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grant button — aurora CTA
            VoyagerPrimaryButton(
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
                    .height(52.dp)
            ) {
                Text(
                    text = if (step == 0) "Grant Permissions" else "Allow Background Location",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // No skip on the background step — "Allow all the time" is essential for the
            // timeline to be accurate, so the only forward action is to grant it. (The OS
            // dialog result proceeds either way; we can't force a grant, but we don't offer
            // an easy opt-out that quietly breaks capture.)

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
    GlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(
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
}
