package com.cosmiclaboratory.voyager.presentation.screen.proof

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.GlassCard
import com.cosmiclaboratory.voyager.presentation.theme.ProBadge
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerShapes
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSpacing
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSurfaces
import com.cosmiclaboratory.voyager.presentation.theme.staggeredEntrance

/**
 * Proof hub — the first-class tab that surfaces Voyager's high-trust, formerly
 * buried features (Mileage, Trips, Export). Frames the Proof pillar with an
 * audit-ready, on-device message and routes into each detailed screen.
 */
@Composable
fun ProofScreen(
    onNavigateToMileage: () -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProofViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Give the flagship Mileage card a live reason to tap: this month's deductible + any backlog.
    val mileageSubtitle = state.mileageDeductible?.let { amount ->
        if (state.unclassifiedCount > 0) "$amount deductible this month · ${state.unclassifiedCount} to classify"
        else "$amount deductible this month — IRS / HMRC-ready"
    } ?: "Turn drives into IRS / HMRC-ready deductions."
    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(VoyagerGradients.screenBackground(size.width, size.height)) }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VoyagerSpacing.screen)
            .padding(top = VoyagerSpacing.lg, bottom = VoyagerSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.xxs)) {
            Text("Proof", style = MaterialTheme.typography.headlineLarge, color = VoyagerColors.OnSurface)
            Text(
                "Evidence-backed records you can defend.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant
            )
        }

        // Audit-ready hero
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(0),
            tint = VoyagerSurfaces.premiumWash
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.md)) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = VoyagerColors.Premium, modifier = Modifier.size(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.xxs)) {
                    Text("Audit-ready by design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = VoyagerColors.OnSurface)
                    Text(
                        "Every drive, trip and export carries its GPS evidence and rule version — computed on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }

        ProofHubCard(
            icon = Icons.Filled.DirectionsCar,
            tint = VoyagerColors.TransportDrive,
            title = "Mileage",
            subtitle = mileageSubtitle,
            index = 1,
            onClick = onNavigateToMileage
        )
        ProofHubCard(
            icon = Icons.Filled.Luggage,
            tint = VoyagerColors.AccentPurple,
            title = "Trips",
            subtitle = "Auto-detected multi-day journeys, ready to share.",
            index = 2,
            onClick = onNavigateToTrips
        )
        // Activities is now its own bottom-nav tab — no duplicate hub card here.
        ProofHubCard(
            icon = Icons.Filled.IosShare,
            tint = VoyagerColors.AccentBlue,
            title = "Export",
            subtitle = "Own your data — GPX, GeoJSON, CSV, JSON.",
            index = 3,
            onClick = onNavigateToExport
        )

        Row(
            modifier = Modifier.padding(top = VoyagerSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.xs)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = VoyagerColors.AccentGreen, modifier = Modifier.size(14.dp))
            Text(
                "Computed on-device. Your records never leave your phone.",
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.AccentGreen
            )
        }
    }
}

@Composable
private fun ProofHubCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    index: Int,
    onClick: () -> Unit,
    isPro: Boolean = false
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.md)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(VoyagerShapes.pill)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VoyagerSpacing.sm)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = VoyagerColors.OnSurface)
                    if (isPro) ProBadge()
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VoyagerColors.OnSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = VoyagerColors.OnSurfaceVariant)
        }
    }
}
