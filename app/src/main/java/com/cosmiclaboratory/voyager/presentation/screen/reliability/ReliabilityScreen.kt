package com.cosmiclaboratory.voyager.presentation.screen.reliability

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.PulsingDot
import com.cosmiclaboratory.voyager.presentation.theme.SectionHeader
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerSurfaces

/** Human-friendly "time ago" from whole hours — avoids the odd "0h ago" for sub-hour gaps. */
private fun agoLabel(hours: Long): String = when {
    hours <= 0L -> "less than an hour"
    hours < 24L -> "${hours}h"
    else -> "${hours / 24}d"
}

/**
 * Reliability check — explains why background tracking can stop on aggressive
 * OEMs and surfaces a self-test for recent sample gaps. Pre-empts the
 * "this app is broken" review.
 */
@Composable
fun ReliabilityScreen(
    viewModel: ReliabilityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(title = "Tracking health")

        // ── Sample-gap self-test — tracking-state hero ────────────────────
        val healthy = !state.hasRecentGap && state.hoursSinceLastSample != null
        VoyagerCard(
            modifier = Modifier.fillMaxWidth(),
            variant = CardVariant.GLASS,
            tint = if (healthy) VoyagerSurfaces.auroraActive else null
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PulsingDot(
                    size = 10.dp,
                    color = if (state.hasRecentGap) VoyagerColors.Warning else VoyagerColors.Success
                )
                Text(
                    text = if (state.hasRecentGap) "Tracking gap detected" else "Tracking looks healthy",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.hasRecentGap) VoyagerColors.Warning else VoyagerColors.Success
                )
            }
            Spacer(Modifier.height(4.dp))
            // Hoist into a local so the null-check smart-casts across the branches
            // (a property access can't smart-cast, which is why this used to need `!!`).
            val hoursSinceLastSample = state.hoursSinceLastSample
            Text(
                text = when {
                    hoursSinceLastSample == null ->
                        "No location samples yet — start tracking to begin your timeline."
                    state.hasRecentGap ->
                        "Last location was ${agoLabel(hoursSinceLastSample)} ago. The app was " +
                            "likely stopped by the system. Re-enabling autostart (below) usually fixes this."
                    else ->
                        "Last location was ${agoLabel(hoursSinceLastSample)} ago — within normal range."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant
            )
        }

        // ── OEM autostart guidance ────────────────────────────────────────
        VoyagerCard(
            modifier = Modifier.fillMaxWidth(),
            variant = if (state.isAggressiveOem) CardVariant.HIGHLIGHTED else CardVariant.GLASS
        ) {
            Text(
                text = "Your device: ${state.manufacturer}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.isAggressiveOem) {
                    "${state.manufacturer} devices aggressively close background apps. " +
                        "To keep your timeline complete, allow Voyager to autostart and " +
                        "disable battery optimisation for it."
                } else {
                    "Your device generally allows background apps. If tracking still " +
                        "stops, check battery optimisation settings for Voyager."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            VoyagerButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(state.dontKillMyAppUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open setup guide for ${state.manufacturer}")
            }
        }

        Text(
            text = "dontkillmyapp.com is a community guide with per-device steps. " +
                "Voyager never sends your data anywhere — this only opens the guide.",
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}
