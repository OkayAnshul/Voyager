package com.cosmiclaboratory.voyager.presentation.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

/**
 * Gates a Pro feature.
 *
 * When [isPro] is true the [content] is shown unchanged. Otherwise a tasteful
 * locked card explains the feature and offers an "Unlock Pro" call to action —
 * the feature is never silently hidden, so free users always know it exists.
 *
 * Stateless by design: the caller collects [isPro] (typically from
 * [EntitlementViewModel]) and supplies [onUnlock] to open the paywall.
 */
@Composable
fun FeatureGate(
    isPro: Boolean,
    featureName: String,
    description: String,
    modifier: Modifier = Modifier,
    onUnlock: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (isPro) {
        content()
    } else {
        ProLockedCard(
            featureName = featureName,
            description = description,
            onUnlock = onUnlock,
            modifier = modifier
        )
    }
}

@Composable
private fun ProLockedCard(
    featureName: String,
    description: String,
    onUnlock: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    VoyagerCard(
        modifier = modifier.fillMaxWidth(),
        variant = CardVariant.HIGHLIGHTED
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = VoyagerColors.Premium.copy(alpha = 0.18f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = VoyagerColors.Premium,
                    modifier = Modifier.padding(12.dp).size(28.dp)
                )
            }
            Text(
                text = "$featureName is a Pro feature",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onUnlock != null) {
                Spacer(Modifier.height(2.dp))
                VoyagerButton(onClick = onUnlock) {
                    Text("Unlock Pro")
                }
            }
        }
    }
}
