package com.cosmiclaboratory.voyager.presentation.screen.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.cosmiclaboratory.voyager.presentation.theme.*

/**
 * Developer Profile Screen
 *
 * Shows information about the developer (Anshul) with social links and a personal note.
 * Matrix-themed design matching the app aesthetic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperProfileScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    var voyagerTapCount by remember { mutableStateOf(0) }
    var showVoyagerStory by remember { mutableStateOf(false) }

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
                                        VoyagerColors.Primary,
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
                        color = VoyagerColors.Primary,
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
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = VoyagerColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "About Voyager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VoyagerColors.Primary,
                                modifier = Modifier.clickable {
                                    voyagerTapCount++
                                    if (voyagerTapCount >= 3) {
                                        voyagerTapCount = 0
                                        showVoyagerStory = true
                                    }
                                }
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
                            color = VoyagerColors.Primary.copy(alpha = 0.9f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // A Note from Anshul
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = VoyagerColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "A note from Anshul",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VoyagerColors.Primary
                            )
                        }

                        Text(
                            text = "I started Voyager because I wanted to know where my time actually went — not in the cloud, not on someone else's server, just on my phone, where my life already lives.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "The apps I tried all wanted my data more than I wanted their features. So I built one that doesn't. Voyager runs entirely on your device. There's no account to make, no server to trust, no analytics watching me build it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "This is a side project. I built it to learn Android deeply — sensor fusion, background services, location pipelines, encryption, Compose, the whole stack. But I also built it because I think small, honest tools deserve to exist alongside the big ones. If Voyager is useful to you, that's the part that matters most.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Thanks to the OpenStreetMap community, MapLibre, Photon and Nominatim, and the open-source Android ecosystem — none of this would exist without them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "— Anshul",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VoyagerColors.Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Developer Philosophy Section
            item {
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = VoyagerColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Developer Manifesto",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VoyagerColors.Primary
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
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = VoyagerColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Built With",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VoyagerColors.Primary
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
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = VoyagerColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Connect With Me",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VoyagerColors.Primary
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
                            icon = Icons.Default.Label,
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
                VoyagerCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = VoyagerColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Get In Touch",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = VoyagerColors.Primary
                            )
                        }

                        // Send Feedback Button
                        VoyagerButton(
                            onClick = {
                                uriHandler.openUri("mailto:anshulisokay@gmail.com?subject=Voyager%20Feedback")
                            },
                            modifier = Modifier.fillMaxWidth()
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
                        VoyagerButton(
                            onClick = {
                                uriHandler.openUri("https://github.com/OkayAnshul")
                            },
                            modifier = Modifier.fillMaxWidth()
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
                    TextButton(onClick = onNavigateToLicenses) {
                        Text(
                            text = "Open-source licenses",
                            style = MaterialTheme.typography.labelMedium,
                            color = VoyagerColors.Primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Made with ❤️ and lots of ☕",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "CosmicLabs © ${java.time.Year.now().value}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VoyagerColors.PrimaryDim,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showVoyagerStory) {
        ModalBottomSheet(
            onDismissRequest = { showVoyagerStory = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Why \"Voyager\"?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VoyagerColors.Primary
                )
                Text(
                    text = "Named after the Voyager 1 and Voyager 2 space probes — the farthest human-made objects from Earth. They left in 1977 carrying the Golden Record: a snapshot of humanity, sent into the unknown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This app is the inverse. Instead of broadcasting, it listens — quietly recording your own small voyages and keeping them right here, on your device. Your Golden Record stays with you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Every journey is an experiment in the Cosmic Laboratory.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.Primary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(8.dp))
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
    VoyagerCard(
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
                tint = VoyagerColors.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
