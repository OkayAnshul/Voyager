package com.cosmiclaboratory.voyager.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.presentation.theme.*

@Composable
fun RenamePlaceDialog(
    place: Place,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRevertToAutomatic: () -> Unit
) {
    var customName by remember {
        mutableStateOf(if (place.isUserRenamed) place.name else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("RENAME PLACE")
                if (place.isUserRenamed) {
                    IconButton(
                        onClick = {
                            onRevertToAutomatic()
                            onDismiss()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Revert to automatic",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current automatic name
                if (!place.isUserRenamed) {
                    Text(
                        text = "Current: ${place.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Quick preset buttons
                Text(
                    text = "QUICK PRESETS:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = customName == "Home",
                        onClick = { customName = "Home" },
                        label = { Text("Home") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = customName == "Work",
                        onClick = { customName = "Work" },
                        label = { Text("Work") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = customName == "Gym",
                        onClick = { customName = "Gym" },
                        label = { Text("Gym") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = customName == "School",
                        onClick = { customName = "School" },
                        label = { Text("School") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = customName == "Favorite Place",
                        onClick = { customName = "Favorite Place" },
                        label = { Text("Favorite") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = customName == "Friend's Place",
                        onClick = { customName = "Friend's Place" },
                        label = { Text("Friend's") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Custom name input
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Custom name") },
                    placeholder = { Text("Enter custom name...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (customName.isNotBlank()) {
                                onRename(customName)
                                onDismiss()
                            }
                        }
                    )
                )

                if (place.isUserRenamed) {
                    Text(
                        text = "Tip: Tap the refresh icon to revert to automatic naming",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            VoyagerButton(
                onClick = {
                    if (customName.isNotBlank()) {
                        onRename(customName)
                        onDismiss()
                    }
                },
                enabled = customName.isNotBlank()
            ) {
                Text("SAVE")
            }
        },
        dismissButton = {
            VoyagerButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
