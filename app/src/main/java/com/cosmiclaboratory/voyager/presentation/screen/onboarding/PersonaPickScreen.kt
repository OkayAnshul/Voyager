package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.cosmiclaboratory.voyager.domain.model.Job
import com.cosmiclaboratory.voyager.domain.model.SettingsPresets
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients

/**
 * Onboarding persona pick — choose a [Job] and a starting tracking preset.
 * Completing it configures the whole app (preset applied, job recorded).
 */
@Composable
fun PersonaPickScreen(
    viewModel: PersonaPickViewModel = hiltViewModel(),
    onComplete: () -> Unit = {}
) {
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    var selectedPresetId by remember { mutableStateOf("DAILY_COMMUTER") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height))
            },
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Make Voyager yours",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "Pick what you want it for and how you travel — we'll tune everything to match.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            Text(
                text = "1 · What do you want Voyager for?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
        }
        items(Job.entries) { job ->
            val selected = selectedJob == job
            VoyagerCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { selectedJob = job },
                variant = if (selected) CardVariant.HIGHLIGHTED else CardVariant.FLAT
            ) {
                Text(
                    text = job.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) VoyagerColors.Primary else VoyagerColors.OnSurface
                )
                Text(
                    text = job.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "2 · Pick a starting profile",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "You can fine-tune or switch this any time in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
        items(SettingsPresets.all) { preset ->
            val selected = selectedPresetId == preset.id
            VoyagerCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { selectedPresetId = preset.id },
                variant = if (selected) CardVariant.HIGHLIGHTED else CardVariant.FLAT
            ) {
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) VoyagerColors.Primary else VoyagerColors.OnSurface
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            VoyagerButton(
                onClick = {
                    selectedJob?.let { job ->
                        viewModel.choosePersona(job, selectedPresetId, onComplete)
                    }
                },
                enabled = selectedJob != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start")
            }
        }
    }
}
