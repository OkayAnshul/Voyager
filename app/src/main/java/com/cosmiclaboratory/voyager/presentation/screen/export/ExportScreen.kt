package com.cosmiclaboratory.voyager.presentation.screen.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit = {},
    viewModel: ExportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    val googleImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importGoogleTimeline(uri)
    }

    // When export finishes, fire a share intent and clear the result.
    LaunchedEffect(state.resultUri) {
        val uri = state.resultUri ?: return@LaunchedEffect
        val mime = when (state.format) {
            ExportFormat.GPX -> "application/gpx+xml"
            ExportFormat.GEOJSON -> "application/geo+json"
            ExportFormat.CSV -> "text/csv"
            ExportFormat.VOYAGER_JSON -> "application/json"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Voyager export"))
        viewModel.consumeResult()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & Import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Export ───────────────────────────────────────────────────
            Text("Export your timeline", style = MaterialTheme.typography.titleMedium)
            Text(
                "Save a day or a range of your timeline as a file you can keep, share, or re-import later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Date-range picker
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (state.isSingleDay) "Day" else "Date range",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatRangeLabel(state.startDate, state.endDate),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Format selector
            Text("Format", style = MaterialTheme.typography.labelLarge)
            ExportFormat.entries.forEach { format ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.format == format,
                        onClick = { viewModel.setFormat(format) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(formatLabel(format))
                        Text(
                            formatDescription(format),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.export() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isWorking
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Working…")
                } else {
                    Icon(Icons.Default.FileDownload, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export & Share")
                }
            }

            HorizontalDivider()

            // ── Import ───────────────────────────────────────────────────
            Text("Import a Voyager export", style = MaterialTheme.typography.titleMedium)
            Text(
                "Restore segments, visits, and places from a Voyager JSON file you exported earlier. Overlapping entries are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isWorking
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose file…")
            }

            HorizontalDivider()

            // ── Google Timeline import ───────────────────────────────────
            Text("Import from Google Timeline", style = MaterialTheme.typography.titleMedium)
            Text(
                "Switching from Google Maps Timeline? Import your places and trips. " +
                    "Use the Semantic Location History from Google Takeout, or the " +
                    "Timeline.json exported from your phone. (Records.json — raw GPS — " +
                    "isn't supported.)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { googleImportLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isWorking
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose Google export…")
            }

            state.importSummary?.let { summary ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Import complete", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text("Places imported: ${summary.placesImported}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Segments imported: ${summary.segmentsImported}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Visits imported: ${summary.visitsImported}",
                            style = MaterialTheme.typography.bodySmall)
                        Text("Duplicates skipped: ${summary.duplicatesSkipped}",
                            style = MaterialTheme.typography.bodySmall)
                        if (summary.rawSamplesImported > 0) {
                            Text("Raw samples imported: ${summary.rawSamplesImported}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.consumeResult() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            state.error?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.consumeResult() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        fun LocalDate.toUtcMillis(): Long =
            atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        fun Long.toLocalDate(): LocalDate =
            Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).toLocalDate()

        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.startDate.toUtcMillis(),
            initialSelectedEndDateMillis = state.endDate.toUtcMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMs = pickerState.selectedStartDateMillis
                        if (startMs != null) {
                            val start = startMs.toLocalDate()
                            val end = pickerState.selectedEndDateMillis?.toLocalDate() ?: start
                            viewModel.setDateRange(start, end)
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState)
        }
    }
}

private fun formatRangeLabel(start: LocalDate, end: LocalDate): String {
    val full = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val short = DateTimeFormatter.ofPattern("MMM d")
    return if (start == end) {
        start.format(full)
    } else if (start.year == end.year) {
        "${start.format(short)} – ${end.format(short)}, ${end.year}"
    } else {
        "${start.format(short)}, ${start.year} – ${end.format(short)}, ${end.year}"
    }
}

private fun formatLabel(format: ExportFormat): String = when (format) {
    ExportFormat.VOYAGER_JSON -> "Voyager JSON (recommended)"
    ExportFormat.GPX -> "GPX"
    ExportFormat.GEOJSON -> "GeoJSON"
    ExportFormat.CSV -> "CSV"
}

private fun formatDescription(format: ExportFormat): String = when (format) {
    ExportFormat.VOYAGER_JSON -> "Full fidelity. The only format you can re-import."
    ExportFormat.GPX -> "Tracks + waypoints. For maps and fitness apps."
    ExportFormat.GEOJSON -> "Open spatial format. For QGIS, kepler.gl, etc."
    ExportFormat.CSV -> "Plain segment summary. Spreadsheet-friendly."
}
