package com.cosmiclaboratory.voyager.presentation.screen.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.MatrixCard
import com.cosmiclaboratory.voyager.ui.theme.Teal
import com.cosmiclaboratory.voyager.ui.theme.TealDim

/**
 * Developer Profile Screen
 *
 * Shows information about the developer (Anshul) with social links and a personal note.
 * Matrix-themed design matching the app aesthetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperProfileScreen(
    onNavigateBack: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About Developer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Picture Placeholder (gradient circle)
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Teal,
                                        Color(0xFF00BFA5),
                                        Color(0xFF1DE9B6)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Anshul",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Code Enthusiast | Tech Tinkerer | Passionate Builder",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Teal,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "\"Turning coffee into code, bugs into features, and ideas into reality.\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // About Section
            item {
                MatrixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "ABOUT VOYAGER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }

                        Text(
                            text = "Hey there! 👋 You found the secret 'About Developer' section. Welcome to my little corner of the app!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Voyager started as a fun side project and evolved into... well, a slightly bigger fun side project. Built with:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "• Way too much coffee ☕ (seriously, I should buy stocks)\n• Late-night coding marathons 🌙\n• A genuine love for clean code\n• The hope that good UX makes life easier\n• Teal accents because they look awesome 💚",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "\"If it works, ship it. If it breaks, fix it. If it's ugly, refactor it. Repeat.\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Teal.copy(alpha = 0.9f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Developer Philosophy Section
            item {
                MatrixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "DEVELOPER MANIFESTO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }

                        Text(
                            text = "\"Code is like humor. When you have to explain it, it's probably bad.\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "What I enjoy:\n\n→ Making things that actually work\n→ UI that doesn't make users cry\n→ Performance that feels snappy\n→ Learning new tech (even if I break things)\n→ Experimenting with ideas\n→ Shipping things and iterating\n→ Coffee. Lots of coffee.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "\"My code works... I have no idea why. My code doesn't work... I have no idea why. Developer life in a nutshell.\" 😅",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Tech Stack Section
            item {
                MatrixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "BUILT WITH",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }

                        Text(
                            text = "⚡ Kotlin – Modern, concise, fun to write\n🎨 Jetpack Compose – UI that makes sense\n🗺️ Location APIs – Because maps are cool\n📊 Room Database – SQLite but friendlier\n⚙️ Hilt/Dagger – DI made manageable\n🔄 Coroutines & Flow – Async without tears\n🎯 Material 3 – Design that scales\n💚 Custom theming – Personal touch",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Connect With Me Section
            item {
                MatrixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "CONNECT WITH ME",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }

                        // GitHub
                        SocialLinkCard(
                            icon = Icons.Default.Code,
                            iconTint = Color(0xFF6e5494),
                            label = "GitHub",
                            handle = "@OkayAnshul",
                            onClick = {
                                uriHandler.openUri("https://github.com/OkayAnshul")
                            }
                        )

                        // LinkedIn
                        SocialLinkCard(
                            icon = Icons.Default.Work,
                            iconTint = Color(0xFF0077B5),
                            label = "LinkedIn",
                            handle = "anshulisworking",
                            onClick = {
                                uriHandler.openUri("https://www.linkedin.com/in/anshulisworking")
                            }
                        )

                        // Instagram
                        SocialLinkCard(
                            icon = Icons.Default.PhotoCamera,
                            iconTint = Color(0xFFE4405F),
                            label = "Instagram",
                            handle = "@axshzl.bin",
                            onClick = {
                                uriHandler.openUri("https://www.instagram.com/axshzl.bin/")
                            }
                        )

                        // Twitter/X
                        SocialLinkCard(
                            icon = Icons.Default.Tag,
                            iconTint = Color(0xFF1DA1F2),
                            label = "Twitter/X",
                            handle = "@ern404errFate",
                            onClick = {
                                uriHandler.openUri("https://x.com/ern404errFate")
                            }
                        )
                    }
                }
            }

            // Get In Touch Section
            item {
                MatrixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "GET IN TOUCH",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Teal
                            )
                        }

                        // Send Feedback Button
                        Button(
                            onClick = {
                                uriHandler.openUri("mailto:anshulisokay@gmail.com?subject=Voyager%20Feedback")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Feedback", fontWeight = FontWeight.Medium)
                        }

                        // Report an Issue Button
                        OutlinedButton(
                            onClick = {
                                uriHandler.openUri("https://github.com/OkayAnshul")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Teal
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Report an Issue", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Footer
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Made with ❤️ and lots of ☕",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CosmicLabs © 2025",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealDim,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Social Link Card Component
 *
 * Displays a clickable card for social media links.
 */
@Composable
private fun SocialLinkCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    handle: String,
    onClick: () -> Unit
) {
    MatrixCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = Teal,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
