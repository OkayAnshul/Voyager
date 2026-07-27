package com.cosmiclaboratory.voyager.presentation.screen.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.DistanceUnit
import com.cosmiclaboratory.voyager.domain.model.MileageRatePreset
import com.cosmiclaboratory.voyager.domain.model.UserSettings
import com.cosmiclaboratory.voyager.domain.model.shortLabel
import com.cosmiclaboratory.voyager.presentation.theme.SectionHeader
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

private val COMMON_CURRENCIES = listOf(
    "USD", "GBP", "EUR", "INR", "CAD", "AUD", "JPY", "SGD", "AED", "ZAR", "NZD", "CHF"
)

/**
 * Mileage rate / currency / unit controls — the "according to user input" surface for the
 * tax-deduction math. Writes straight through [onUpdate] (the settings [String]-key dispatch),
 * so a change here re-derives the money shown on the Mileage screen and in its exports.
 */
@Composable
fun MileageRatesSection(
    settings: UserSettings,
    onUpdate: (String, Any) -> Unit
) {
    val unit = settings.mileageDistanceUnit
    SectionHeader(title = "Mileage rates & units")
    Spacer(Modifier.height(8.dp))
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        // Distance unit
        Label("Distance unit")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = unit == DistanceUnit.MILE,
                onClick = { onUpdate("mileageDistanceUnit", DistanceUnit.MILE.name) },
                label = { Text("Miles") }
            )
            FilterChip(
                selected = unit == DistanceUnit.KM,
                onClick = { onUpdate("mileageDistanceUnit", DistanceUnit.KM.name) },
                label = { Text("Kilometres") }
            )
        }

        Spacer(Modifier.height(12.dp))

        // Currency
        Label("Currency")
        CurrencyDropdown(
            selected = settings.mileageCurrencyCode,
            onSelect = { onUpdate("mileageCurrencyCode", it) }
        )

        Spacer(Modifier.height(12.dp))

        // Rate preset
        Label("Rate preset")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MileageRatePreset.entries.forEach { preset ->
                FilterChip(
                    selected = settings.mileageRatePreset == preset,
                    onClick = { onUpdate("mileageRatePreset", preset.name) },
                    label = { Text(preset.displayName) }
                )
            }
        }

        // Custom rates (only when Custom is chosen)
        if (settings.mileageRatePreset == MileageRatePreset.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Your rate in ${settings.mileageCurrencyCode} per ${unit.shortLabel}, per purpose.",
                style = MaterialTheme.typography.bodySmall,
                color = VoyagerColors.OnSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            RateField("Business", settings.mileageCustomRateBusiness, unit) {
                onUpdate("mileageCustomRateBusiness", it)
            }
            Spacer(Modifier.height(8.dp))
            RateField("Medical", settings.mileageCustomRateMedical, unit) {
                onUpdate("mileageCustomRateMedical", it)
            }
            Spacer(Modifier.height(8.dp))
            RateField("Charitable", settings.mileageCustomRateCharitable, unit) {
                onUpdate("mileageCustomRateCharitable", it)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Rates are estimates — verify the current figure for your tax year and " +
                "jurisdiction before filing.",
            style = MaterialTheme.typography.bodySmall,
            color = VoyagerColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = VoyagerColors.OnSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CurrencyDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(selected) {
        (listOf(selected) + COMMON_CURRENCIES).distinct()
    }
    Box {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = VoyagerColors.SurfaceVariant,
            onClick = { expanded = true }
        ) {
            Text(
                text = selected,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = VoyagerColors.OnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    }
                )
            }
        }
    }
}

@Composable
private fun RateField(
    label: String,
    value: Double,
    unit: DistanceUnit,
    onValue: (Double) -> Unit
) {
    // Local text state so partial input ("0.") doesn't fight the persisted Double.
    var text by remember { mutableStateOf(if (value > 0.0) value.toString() else "") }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.toDoubleOrNull()?.let { onValue(it) }
            if (raw.isBlank()) onValue(0.0)
        },
        label = { Text("$label (per ${unit.shortLabel})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
