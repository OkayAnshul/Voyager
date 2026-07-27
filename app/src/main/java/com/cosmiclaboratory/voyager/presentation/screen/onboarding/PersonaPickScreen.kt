package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerPrimaryButton

/**
 * Onboarding persona pick — one question: choose a [Job]. Each job maps to a
 * sensible starting tracking preset behind the scenes, so completing it configures
 * the whole app (preset applied, job recorded). Presets stay tunable in Settings.
 */
@Composable
fun PersonaPickScreen(
    viewModel: PersonaPickViewModel = hiltViewModel(),
    onComplete: () -> Unit = {}
) {
    var selectedJob by remember { mutableStateOf<Job?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height))
            },
        // Inset for the system bars so the "Start" button clears the gesture nav bar
        // (it's the last item in the list) and the title clears the status bar.
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Which of these sounds most like you?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "Pick the one that fits — we'll tune tracking and put the right things up front. You can change all of it later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Private by default — no account, no cloud. Everything stays on your device.",
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.AccentGreen
            )
            Spacer(Modifier.height(4.dp))
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) VoyagerColors.Primary else VoyagerColors.OnSurface
                )
                Text(
                    text = job.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = job.forWho,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = VoyagerColors.OnSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = job.whatYouSee,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.Primary
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            VoyagerPrimaryButton(
                onClick = {
                    selectedJob?.let { job ->
                        viewModel.choosePersona(job, onComplete)
                    }
                },
                enabled = selectedJob != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start capturing")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tracking starts now — running privately on your device. You can pause it any time.",
                style = MaterialTheme.typography.labelSmall,
                color = VoyagerColors.OnSurfaceVariant
            )
        }
    }
}
