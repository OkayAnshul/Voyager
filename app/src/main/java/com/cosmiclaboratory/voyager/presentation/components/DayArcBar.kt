package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.usecase.DayArcSummary
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

/**
 * A slim, proportional bar of the whole day — visits, each transport mode, and quiet
 * gaps — so the shape of the day reads at a glance above the scrolling timeline.
 * Built on the shared [SegmentedProgressBar]; colours come from the transport palette.
 */
@Composable
fun DayArcBar(
    slices: List<DayArcSummary.Slice>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp
) {
    if (slices.isEmpty()) return
    val segments = slices.map { ProgressSegment(fraction = it.fraction, color = it.kind.toColor()) }
    SegmentedProgressBar(
        segments = segments,
        modifier = modifier.fillMaxWidth(),
        height = height
    )
}

private fun DayArcSummary.ArcKind.toColor(): Color = when (this) {
    DayArcSummary.ArcKind.VISIT -> VoyagerColors.Primary
    DayArcSummary.ArcKind.WALK, DayArcSummary.ArcKind.RUN -> VoyagerColors.TransportWalk
    DayArcSummary.ArcKind.CYCLE -> VoyagerColors.TransportCycle
    DayArcSummary.ArcKind.DRIVE -> VoyagerColors.TransportDrive
    DayArcSummary.ArcKind.TRANSIT, DayArcSummary.ArcKind.FLIGHT -> VoyagerColors.TransportTransit
    DayArcSummary.ArcKind.GAP -> VoyagerColors.TransportGap.copy(alpha = 0.4f)
    DayArcSummary.ArcKind.OTHER -> VoyagerColors.OnSurfaceVariant.copy(alpha = 0.5f)
}
