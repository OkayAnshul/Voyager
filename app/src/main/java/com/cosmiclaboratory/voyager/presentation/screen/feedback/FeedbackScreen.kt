package com.cosmiclaboratory.voyager.presentation.screen.feedback

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.BuildConfig
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

private enum class FeedbackCategory(
    val label: String,
    val icon: ImageVector,
    val subjectTag: String
) {
    BUG("Bug report", Icons.Default.BugReport, "[Bug]"),
    FEATURE("Feature request", Icons.Default.Lightbulb, "[Feature]"),
    GENERAL("General feedback", Icons.Default.SentimentSatisfied, "[Feedback]")
}

private const val FEEDBACK_EMAIL = "anshulisokay@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf(FeedbackCategory.BUG) }
    var description by remember { mutableStateOf("") }
    var includeDeviceInfo by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send feedback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                "This opens your email app with a pre-filled message. Voyager doesn't send anything on its own — you stay in control of what leaves your device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("What kind of feedback?", style = MaterialTheme.typography.labelLarge)

            FeedbackCategory.entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = category == entry,
                        onClick = { category = entry }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(entry.label)
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                label = { Text("Tell me what's on your mind") },
                placeholder = {
                    Text(
                        when (category) {
                            FeedbackCategory.BUG -> "What were you doing? What did you expect? What actually happened?"
                            FeedbackCategory.FEATURE -> "What would make Voyager more useful for you?"
                            FeedbackCategory.GENERAL -> "Anything you want to share — good, bad, or weird."
                        }
                    )
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeDeviceInfo,
                    onCheckedChange = { includeDeviceInfo = it }
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include device info", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "App version, Android version, device model. No location data, ever.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val body = buildEmailBody(category, description, includeDeviceInfo)
                    val subject = "${category.subjectTag} Voyager"
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, "Send feedback"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = description.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open email app")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun buildEmailBody(
    category: FeedbackCategory,
    description: String,
    includeDeviceInfo: Boolean
): String = buildString {
    append(description.trim())
    append("\n\n---\n")
    append("Category: ${category.label}\n")
    if (includeDeviceInfo) {
        append("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
    }
}
