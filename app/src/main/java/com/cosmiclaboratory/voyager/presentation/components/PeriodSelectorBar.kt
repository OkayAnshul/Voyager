package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.DateRangePeriod
import androidx.compose.material3.FilterChip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelectorBar(
    selectedPeriod: DateRangePeriod,
    onPeriodSelected: (DateRangePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val presets = listOf(
        DateRangePeriod.Today,
        DateRangePeriod.ThisWeek,
        DateRangePeriod.ThisMonth,
        DateRangePeriod.Last30Days
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.displayLabel()) }
            )
        }

        FilterChip(
            selected = selectedPeriod is DateRangePeriod.Custom,
            onClick = { showDatePicker = true },
            label = { Text(if (selectedPeriod is DateRangePeriod.Custom) selectedPeriod.displayLabel() else "Custom") }
        )
    }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val start = Instant.ofEpochMilli(startMillis)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                            val end = Instant.ofEpochMilli(endMillis)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                            onPeriodSelected(DateRangePeriod.Custom(start, end))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(state = dateRangePickerState)
        }
    }
}
