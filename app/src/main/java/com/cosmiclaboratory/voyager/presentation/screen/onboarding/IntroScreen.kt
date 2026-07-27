package com.cosmiclaboratory.voyager.presentation.screen.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerOutlinedButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerPrimaryButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerWordmark
import com.cosmiclaboratory.voyager.ui.theme.JetBrainsMonoFontFamily

/**
 * First-run intro — one clean screen that says what Voyager is, everything it does
 * (grouped by the three pillars that also drive the persona pick), and how it's
 * different from Google Timeline. Migrating your history (Google Timeline import /
 * backup restore) is a quiet **corner button**, not a blocking card — the same import
 * also lives in Settings → Export & Import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroScreen(
    googleImportViewModel: GoogleTimelineImportViewModel = hiltViewModel(),
    restoreViewModel: RestoreViewModel = hiltViewModel(),
    onComplete: () -> Unit = {}
) {
    val googleState by googleImportViewModel.uiState.collectAsStateWithLifecycle()
    val restoreState by restoreViewModel.uiState.collectAsStateWithLifecycle()

    val pickGoogleExport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) googleImportViewModel.import(uri) }

    val pickBackupFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) restoreViewModel.restore(uri) }

    val working = googleState.isWorking || restoreState.isWorking
    val summary = googleState.summary ?: restoreState.summary
    val error = googleState.error ?: restoreState.error

    var showImport by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 12.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Corner affordance: bring your history (import / restore) ──────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showImport = true }) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Bring history",
                        style = MaterialTheme.typography.labelLarge,
                        color = VoyagerColors.Primary
                    )
                }
            }

            // ── Hero ──────────────────────────────────────────────────────────────
            VoyagerWordmark(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 26.sp,
                letterSpacing = 5.sp
            )
            Text(
                text = "Your life, mapped.",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Private by design · provable on demand",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = VoyagerColors.Primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            MonoKicker(text = "WHAT VOYAGER DOES", color = VoyagerColors.OnSurfaceVariant)

            // ── The three pillars (each maps to a persona) ────────────────────────
            PillarCard(
                icon = Icons.Filled.Place,
                accent = VoyagerColors.Primary,
                name = "MEMORY",
                tagline = "A private timeline of where life happened",
                features = listOf(
                    "Scrollable timeline of every place & trip",
                    "A map of everywhere you've been",
                    "Photo Day Story from your gallery",
                    "Search your whole history"
                )
            )
            PillarCard(
                icon = Icons.Filled.Verified,
                accent = VoyagerColors.AccentAmber,
                name = "PROOF",
                tagline = "Evidence-grade logs you can export",
                features = listOf(
                    "Tax- & audit-ready mileage",
                    "Every visit backed by its evidence",
                    "Export to GPX, CSV, PDF & JSON"
                )
            )
            PillarCard(
                icon = Icons.Filled.Insights,
                accent = VoyagerColors.AccentPurple,
                name = "HABITS",
                tagline = "Patterns and routines, honestly",
                features = listOf(
                    "Insights into how you spend your days",
                    "Routines, commutes & sleep rhythm",
                    "Activities & fitness stats"
                )
            )

            // ── How it's different ────────────────────────────────────────────────
            VoyagerCard(
                modifier = Modifier.fillMaxWidth(),
                variant = CardVariant.HIGHLIGHTED
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = VoyagerColors.AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Unlike Google Timeline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.OnSurface
                    )
                }
                Spacer(Modifier.height(10.dp))
                DiffLine("Everything stays on your phone — encrypted")
                DiffLine("No account, no cloud, no ads")
                DiffLine("Every trip keeps tax- & audit-ready proof")
            }

            Text(
                text = "Built as a small, honest tool that runs entirely on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(2.dp))

            VoyagerPrimaryButton(
                onClick = onComplete,
                enabled = !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }

        if (showImport) {
            ModalBottomSheet(
                onDismissRequest = { showImport = false },
                sheetState = sheetState,
                containerColor = VoyagerColors.SurfaceVariant
            ) {
                BringHistorySheet(
                    working = working,
                    summary = summary,
                    error = error,
                    onImportGoogle = { pickGoogleExport.launch(arrayOf("application/json", "*/*")) },
                    onRestoreBackup = { pickBackupFile.launch(arrayOf("application/json", "*/*")) }
                )
            }
        }
    }
}

@Composable
private fun MonoKicker(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@Composable
private fun PillarCard(
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    name: String,
    tagline: String,
    features: List<String>
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.15f)) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = name,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        features.forEach { feature ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurface
                )
            }
        }
    }
}

@Composable
private fun DiffLine(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = VoyagerColors.AccentGreen,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = VoyagerColors.OnSurface
        )
    }
}

@Composable
private fun BringHistorySheet(
    working: Boolean,
    summary: com.cosmiclaboratory.voyager.domain.model.ImportSummary?,
    error: String?,
    onImportGoogle: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Coming from Google Timeline?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.OnSurface
        )
        Text(
            text = "Bring your places & trips across, or restore a Voyager backup. Optional — you can also do this any time in Settings → Export & Import.",
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        when {
            summary != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = VoyagerColors.Success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Imported ${summary.placesImported} places · " +
                            "${summary.visitsImported} visits · ${summary.segmentsImported} trips" +
                            if (summary.duplicatesSkipped > 0) " · ${summary.duplicatesSkipped} duplicates skipped" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurface
                    )
                }
            }

            working -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = VoyagerColors.Primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Importing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }

            else -> {
                if (error != null) {
                    Text(
                        text = "Couldn't import that file: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = VoyagerColors.Error
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VoyagerOutlinedButton(
                        onClick = onImportGoogle,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Google Timeline")
                    }
                    VoyagerOutlinedButton(
                        onClick = onRestoreBackup,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Restore backup")
                    }
                }
            }
        }
    }
}
