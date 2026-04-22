package com.cosmiclaboratory.voyager.presentation.screen.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmiclaboratory.voyager.capture.AdaptiveSamplingPolicy
import com.cosmiclaboratory.voyager.capture.DormantModeManager
import com.cosmiclaboratory.voyager.domain.model.InProgressSegmentSnapshot
import com.cosmiclaboratory.voyager.domain.model.PendingVisitCandidate
import com.cosmiclaboratory.voyager.domain.model.TrackingRuntimeState
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@Composable
fun PipelineDebugScreen(
    viewModel: PipelineDebugViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Pipeline State ────────────────────────────────────────────
        item { SectionLabel("Pipeline State") }
        item {
            DebugCard {
                val state = uiState.runtimeState
                MonoRow("Session", state.activeSessionId?.toString() ?: "none")
                MonoRow("Segment ID", state.currentSegmentId?.toString() ?: "none")
                MonoRow("Last Sample", state.lastAcceptedSampleId?.toString() ?: "none")
                MonoRow("Last Accepted", formatTime(state.lastAcceptedAt))
                MonoRow("Latency", state.lastPipelineLatencyMs?.let { "${it}ms" } ?: "n/a")
                MonoRow("State Version", state.stateVersion.toString())
                MonoRow("Tracking", if (state.isTracking) "ACTIVE" else "STOPPED")
            }
        }

        // ── Motion & Sampling ────────────────────────────────────────
        item { SectionLabel("Motion & Sampling") }
        item {
            DebugCard {
                MonoRow("Motion State", uiState.runtimeState.lastMotionState ?: "unknown")
                MonoRow("Sampling State", uiState.motionState)
                MonoRow("Interval", if (uiState.samplingIntervalMs > 0) "${uiState.samplingIntervalMs}ms" else "GPS OFF")
                MonoRow("Min Distance", "${uiState.minDistanceM}m")
                MonoRow("Dormant", if (uiState.isDormant) "YES" else "no")
                if (uiState.dormantExitedAt > 0) {
                    MonoRow("Dormant Exited", formatTime(uiState.dormantExitedAt))
                }
            }
        }

        // ── Visit Candidate ──────────────────────────────────────────
        item { SectionLabel("Visit Detection") }
        item {
            DebugCard {
                val candidate = uiState.runtimeState.pendingVisitCandidate
                val visitId = uiState.runtimeState.lastConfirmedVisitId
                MonoRow("Confirmed Visit", visitId?.toString() ?: "none")
                if (candidate != null) {
                    MonoRow("Centroid", "%.5f, %.5f".format(candidate.centroidLat, candidate.centroidLng))
                    MonoRow("Samples", candidate.sampleCount.toString())
                    MonoRow("Max Spread", "%.1fm".format(candidate.maxDistanceFromCentroidM))
                    MonoRow("Dwell", formatDuration(System.currentTimeMillis() - candidate.accumulationStartAt))
                    MonoRow("Matched Place", candidate.matchedPlaceId?.toString() ?: "none")
                } else {
                    MonoRow("Candidate", "none")
                }
            }
        }

        // ── In-Progress Segment ──────────────────────────────────────
        item { SectionLabel("In-Progress Segment") }
        item {
            DebugCard {
                val seg = uiState.inProgressSegment
                if (seg != null) {
                    MonoRow("Type", seg.segmentType)
                    MonoRow("Started", formatTime(seg.startAt))
                    MonoRow("Duration", formatDuration(seg.endAt - seg.startAt))
                    MonoRow("Distance", "%.0fm".format(seg.distanceM))
                    MonoRow("Samples", seg.sampleCount.toString())
                } else {
                    MonoRow("Segment", "none")
                }
            }
        }

        // ── Recent Health Logs ───────────────────────────────────────
        item { SectionLabel("Recent Health Logs") }
        if (uiState.healthLogs.isEmpty()) {
            item {
                DebugCard {
                    MonoRow("Logs", "none")
                }
            }
        } else {
            items(uiState.healthLogs) { log ->
                DebugCard {
                    MonoRow(log.eventType, formatTime(log.eventAt))
                    if (log.detailsJson != null) {
                        Text(
                            text = log.detailsJson,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = VoyagerColors.OnSurfaceVariant,
                            maxLines = 3,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = VoyagerColors.Primary
    )
}

@Composable
private fun DebugCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VoyagerColors.Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun MonoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = VoyagerColors.OnSurfaceVariant
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = VoyagerColors.OnSurface
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(epochMs: Long?): String {
    if (epochMs == null || epochMs == 0L) return "n/a"
    return timeFormat.format(Date(epochMs))
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%dh %02dm %02ds".format(h, m, s) else "%dm %02ds".format(m, s)
}

// ── ViewModel ────────────────────────────────────────────────────────────

data class PipelineDebugUiState(
    val runtimeState: TrackingRuntimeState = TrackingRuntimeState(
        activeSessionId = null, currentSegmentId = null, pendingVisitCandidate = null,
        lastAcceptedSampleId = null, lastAcceptedAt = null, stateVersion = 0,
        lastPipelineLatencyMs = null
    ),
    val inProgressSegment: InProgressSegmentSnapshot? = null,
    val motionState: String = "unknown",
    val samplingIntervalMs: Long = 0,
    val minDistanceM: Float = 0f,
    val isDormant: Boolean = false,
    val dormantExitedAt: Long = 0,
    val healthLogs: List<HealthLogEntity> = emptyList()
)

@HiltViewModel
class PipelineDebugViewModel @Inject constructor(
    private val stateStore: TimelineStateStore,
    private val adaptiveSamplingPolicy: AdaptiveSamplingPolicy,
    private val dormantModeManager: DormantModeManager,
    private val healthLogDao: HealthLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PipelineDebugUiState())
    val uiState: StateFlow<PipelineDebugUiState> = _uiState.asStateFlow()

    init {
        // Combine pipeline state + in-progress segment
        viewModelScope.launch {
            combine(
                stateStore.state,
                stateStore.inProgressSegment
            ) { state, segment ->
                state to segment
            }.collect { (state, segment) ->
                refreshSamplingState()
                _uiState.update {
                    it.copy(
                        runtimeState = state,
                        inProgressSegment = segment
                    )
                }
            }
        }

        // Poll sampling policy & dormant state every 2 seconds (non-flow sources)
        viewModelScope.launch {
            while (isActive) {
                refreshSamplingState()
                delay(2000)
            }
        }

        // Load health logs once, refresh on state changes
        viewModelScope.launch {
            stateStore.state.collect {
                val logs = healthLogDao.getRecent(20)
                _uiState.update { it.copy(healthLogs = logs) }
            }
        }
    }

    private fun refreshSamplingState() {
        val policy = adaptiveSamplingPolicy.getCurrentPolicy()
        val motionState = adaptiveSamplingPolicy.getCurrentMotionState()
        _uiState.update {
            it.copy(
                motionState = motionState.name,
                samplingIntervalMs = policy.intervalMs,
                minDistanceM = policy.minDistanceM,
                isDormant = dormantModeManager.isDormant,
                dormantExitedAt = dormantModeManager.dormantExitedAt
            )
        }
    }
}
