package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.Achievement
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

/**
 * Gold "New personal record" pills — the private, on-device answer to a Strava leaderboard badge.
 * Renders nothing when there are no achievements.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementChips(achievements: List<Achievement>, modifier: Modifier = Modifier) {
    if (achievements.isEmpty()) return
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "🏆 New personal record" + if (achievements.size > 1) "s" else "",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.Premium,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            achievements.forEach { AchievementPill(it) }
        }
    }
}

@Composable
private fun AchievementPill(achievement: Achievement) {
    Text(
        text = achievement.label,
        style = MaterialTheme.typography.labelMedium,
        color = VoyagerColors.Premium,
        modifier = Modifier
            .background(VoyagerColors.Premium.copy(alpha = 0.15f), RoundedCornerShape(50))
            .border(1.dp, VoyagerColors.Premium.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
