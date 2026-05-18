package com.cosmiclaboratory.voyager.presentation.screen.reliability

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.SectionHeader
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients

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

        // ── Sample-gap self-test ──────────────────────────────────────────
        VoyagerCard(
            modifier = Modifier.fillMaxWidth(),
            variant = if (state.hasRecentGap) CardVariant.HIGHLIGHTED else CardVariant.FLAT
        ) {
            Text(
                text = if (state.hasRecentGap) "Tracking gap detected" else "Tracking looks healthy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (state.hasRecentGap) VoyagerColors.Warning else VoyagerColors.Success
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    state.hoursSinceLastSample == null ->
                        "No location samples yet — start tracking to begin your timeline."
                    state.hasRecentGap ->
                        "Last location was ${state.hoursSinceLastSample}h ago. The app was " +
                            "likely stopped by the system. Re-enabling autostart (below) usually fixes this."
                    else ->
                        "Last location was ${state.hoursSinceLastSample}h ago — within normal range."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant
            )
        }

        // ── OEM autostart guidance ────────────────────────────────────────
        VoyagerCard(
            modifier = Modifier.fillMaxWidth(),
            variant = if (state.isAggressiveOem) CardVariant.HIGHLIGHTED else CardVariant.FLAT
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
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/"))
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
