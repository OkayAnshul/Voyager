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

/**
 * Dialog for renaming a place with custom user-provided name
 *
 * Features:
 * - Quick preset buttons (Home, Work, Gym, etc.)
 * - Custom text input
 * - Option to revert to automatic naming
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                Text("Rename Place")
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
                    text = "Quick presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetChip(
                        icon = Icons.Default.Home,
                        label = "Home",
                        onClick = { customName = "Home" },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        icon = Icons.Default.DateRange,
                        label = "Work",
                        onClick = { customName = "Work" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetChip(
                        icon = Icons.Default.Star,
                        label = "Gym",
                        onClick = { customName = "Gym" },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        icon = Icons.Default.Place,
                        label = "School",
                        onClick = { customName = "School" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetChip(
                        icon = Icons.Default.Favorite,
                        label = "Favorite",
                        onClick = { customName = "Favorite Place" },
                        modifier = Modifier.weight(1f)
                    )
                    PresetChip(
                        icon = Icons.Default.Person,
                        label = "Friend's",
                        onClick = { customName = "Friend's Place" },
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
            Button(
                onClick = {
                    if (customName.isNotBlank()) {
                        onRename(customName)
                        onDismiss()
                    }
                },
                enabled = customName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PresetChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
