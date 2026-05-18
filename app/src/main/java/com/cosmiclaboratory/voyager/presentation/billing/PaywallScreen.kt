package com.cosmiclaboratory.voyager.presentation.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cosmiclaboratory.voyager.domain.billing.ProProduct
import com.cosmiclaboratory.voyager.domain.billing.ProProductType
import com.cosmiclaboratory.voyager.domain.billing.PurchaseFlowState
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients

/** Pro features advertised on the paywall — kept honest and concrete. */
private val PRO_BENEFITS = listOf(
    "Evidence Cards — why each visit and drive was inferred",
    "Mileage log & IRS/HMRC tax PDF export",
    "Advanced insights, anomalies and trends",
    "Extended export — date ranges, CSV, raw samples",
    "Photo Day Story and travel stories",
    "Premium themes and map styles"
)

@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val purchaseState by viewModel.purchaseFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Close ──────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = VoyagerColors.OnSurfaceVariant)
            }
        }

        // ── Hero ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = VoyagerColors.Premium.copy(alpha = 0.18f)
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = VoyagerColors.Premium,
                    modifier = Modifier.padding(14.dp).size(34.dp)
                )
            }
            Text(
                text = "Voyager Pro",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "Unlock every job your timeline can do.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // ── Benefits ───────────────────────────────────────────────────
        VoyagerCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PRO_BENEFITS.forEach { benefit ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = VoyagerColors.Premium,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = benefit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VoyagerColors.OnSurface
                        )
                    }
                }
            }
        }

        // ── Purchase area ──────────────────────────────────────────────
        when {
            purchaseState is PurchaseFlowState.Purchased -> PurchasedCard(onClose)

            !viewModel.billingAvailable -> InfoCard(
                "Voyager Pro isn't available in this build. The F-Droid edition " +
                    "ships without Google Play billing — install the Play Store build to upgrade."
            )

            products.isEmpty() -> InfoCard("Loading Pro pricing from Google Play…")

            else -> {
                products.forEach { product ->
                    ProductCard(
                        product = product,
                        enabled = purchaseState !is PurchaseFlowState.Working,
                        onChoose = {
                            context.findActivity()?.let { viewModel.purchase(it, product.productId) }
                        }
                    )
                }
            }
        }

        // ── Purchase-flow feedback ─────────────────────────────────────
        when (val state = purchaseState) {
            is PurchaseFlowState.Working -> StatusRow("Contacting Google Play…", spinner = true)
            is PurchaseFlowState.Pending -> StatusRow(
                "Payment pending — Pro unlocks once Google Play confirms it."
            )
            is PurchaseFlowState.Failed -> Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.Error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            else -> Unit
        }

        // ── Honest copy + restore ──────────────────────────────────────
        if (purchaseState !is PurchaseFlowState.Purchased) {
            Text(
                text = "No ads. No cloud. No data selling — ever. Your timeline stays on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (viewModel.billingAvailable) {
                TextButton(
                    onClick = { viewModel.restore() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore purchases", color = VoyagerColors.Primary)
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProProduct,
    enabled: Boolean,
    onChoose: () -> Unit
) {
    val highlighted = product.type == ProProductType.LIFETIME
    VoyagerCard(
        modifier = Modifier.fillMaxWidth(),
        variant = if (highlighted) CardVariant.HIGHLIGHTED else CardVariant.FLAT
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (product.type) {
                        ProProductType.MONTHLY -> "Monthly"
                        ProProductType.YEARLY -> "Yearly"
                        ProProductType.LIFETIME -> "Lifetime"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "${product.formattedPrice} ${product.periodLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurfaceVariant
                )
                if (highlighted) {
                    Text(
                        text = "Pay once — keep Pro forever",
                        style = MaterialTheme.typography.labelMedium,
                        color = VoyagerColors.Premium
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            VoyagerButton(onClick = onChoose, enabled = enabled) {
                Text("Choose")
            }
        }
    }
}

@Composable
private fun PurchasedCard(onClose: () -> Unit) {
    VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.HIGHLIGHTED) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = VoyagerColors.Premium,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "You're on Voyager Pro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VoyagerColors.OnSurface
            )
            Text(
                text = "Thank you for supporting private, on-device location tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
            VoyagerButton(onClick = onClose) { Text("Done") }
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun StatusRow(message: String, spinner: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (spinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = VoyagerColors.Primary
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

/** Walks the [Context] wrapper chain to the hosting [Activity] — needed to launch billing. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
