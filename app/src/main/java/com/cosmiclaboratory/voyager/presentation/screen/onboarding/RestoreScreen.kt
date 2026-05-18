package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerOutlinedButton

/**
 * First-launch restore step.
 *
 * A returning user — reinstalling, or moving to a new device — can restore their
 * timeline from a Voyager backup before building a fresh one. A brand-new user just
 * starts fresh. Shown once (see [RestorePreferences]); both paths call [onComplete].
 */
@Composable
fun RestoreScreen(
    viewModel: RestoreViewModel = hiltViewModel(),
    onComplete: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restore(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = VoyagerColors.Primary.copy(alpha = 0.15f)
        ) {
            Icon(
                imageVector = Icons.Default.SettingsBackupRestore,
                contentDescription = null,
                tint = VoyagerColors.Primary,
                modifier = Modifier.padding(18.dp).size(40.dp)
            )
        }

        Text(
            text = "Restore your timeline",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.OnSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Reinstalling, or moving to a new device? Restore your places, " +
                "visits and trips from a Voyager backup file. New to Voyager? Just start fresh.",
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        when {
            state.summary != null -> {
                val summary = state.summary!!
                VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.HIGHLIGHTED) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = VoyagerColors.Success,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Backup restored",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VoyagerColors.OnSurface
                        )
                        Text(
                            text = "${summary.placesImported} places · " +
                                "${summary.visitsImported} visits · " +
                                "${summary.segmentsImported} trips" +
                                if (summary.duplicatesSkipped > 0) {
                                    " · ${summary.duplicatesSkipped} duplicates skipped"
                                } else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                VoyagerButton(
                    onClick = {
                        viewModel.markPromptSeen()
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue")
                }
            }

            else -> {
                state.error?.let { error ->
                    VoyagerCard(modifier = Modifier.fillMaxWidth(), tintColor = VoyagerColors.Error.copy(alpha = 0.12f)) {
                        Text(
                            text = "Couldn't restore that file: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.Error
                        )
                    }
                }

                VoyagerButton(
                    onClick = { pickFile.launch(arrayOf("application/json", "*/*")) },
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = VoyagerColors.Primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Restoring…")
                    } else {
                        Text("Restore from a backup file")
                    }
                }

                VoyagerOutlinedButton(
                    onClick = {
                        viewModel.markPromptSeen()
                        onComplete()
                    },
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start fresh")
                }
            }
        }
    }
}
