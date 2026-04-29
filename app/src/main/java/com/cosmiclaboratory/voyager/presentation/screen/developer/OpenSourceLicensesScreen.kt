package com.cosmiclaboratory.voyager.presentation.screen.developer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors

private data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String
)

private val LICENSES = listOf(
    LicenseEntry("Kotlin", "Apache 2.0", "https://github.com/JetBrains/kotlin"),
    LicenseEntry("Jetpack Compose", "Apache 2.0", "https://developer.android.com/jetpack/compose"),
    LicenseEntry("Material 3", "Apache 2.0", "https://m3.material.io"),
    LicenseEntry("Hilt / Dagger", "Apache 2.0", "https://dagger.dev/hilt/"),
    LicenseEntry("Room", "Apache 2.0", "https://developer.android.com/training/data-storage/room"),
    LicenseEntry("AndroidX Navigation", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    LicenseEntry("AndroidX WorkManager", "Apache 2.0", "https://developer.android.com/topic/libraries/architecture/workmanager"),
    LicenseEntry("AndroidX DataStore", "Apache 2.0", "https://developer.android.com/topic/libraries/architecture/datastore"),
    LicenseEntry("AndroidX Security Crypto", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/security"),
    LicenseEntry("kotlinx.coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    LicenseEntry("kotlinx.serialization", "Apache 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    LicenseEntry("Retrofit", "Apache 2.0", "https://square.github.io/retrofit/"),
    LicenseEntry("OkHttp", "Apache 2.0", "https://square.github.io/okhttp/"),
    LicenseEntry("Ktor Client", "Apache 2.0", "https://ktor.io/"),
    LicenseEntry("Gson", "Apache 2.0", "https://github.com/google/gson"),
    LicenseEntry("Accompanist Permissions", "Apache 2.0", "https://google.github.io/accompanist/"),
    LicenseEntry("Lottie for Compose", "Apache 2.0", "https://github.com/airbnb/lottie-android"),
    LicenseEntry("MapLibre GL Native", "BSD 2-Clause", "https://maplibre.org/"),
    LicenseEntry("SQLCipher for Android", "BSD-style", "https://www.zetetic.net/sqlcipher/"),
    LicenseEntry("Google Play services (Location, Activity Recognition)", "Google Play Services Terms", "https://developers.google.com/android/guides/terms"),
    LicenseEntry("Photon", "Apache 2.0 (data: ODbL)", "https://photon.komoot.io/"),
    LicenseEntry("Nominatim", "Server: GPL-2.0 (data: ODbL)", "https://nominatim.org/"),
    LicenseEntry("Overpass API", "Server: AGPL-3.0 (data: ODbL)", "https://overpass-api.de/"),
    LicenseEntry("OpenStreetMap data", "Open Database License (ODbL)", "https://www.openstreetmap.org/copyright"),
    LicenseEntry("Inter font", "SIL Open Font License 1.1", "https://rsms.me/inter/"),
    LicenseEntry("JetBrains Mono font", "SIL Open Font License 1.1", "https://www.jetbrains.com/lp/mono/"),
    LicenseEntry("Great Vibes font", "SIL Open Font License 1.1", "https://fonts.google.com/specimen/Great+Vibes"),
    LicenseEntry("MockK / Turbine / Truth (test)", "Apache 2.0", "https://mockk.io")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onNavigateBack: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open-source licenses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Voyager stands on the shoulders of the open-source community. Tap any item to visit its project page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(LICENSES) { entry ->
                VoyagerCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { uriHandler.openUri(entry.url) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = entry.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = VoyagerColors.Primary
                        )
                    }
                }
            }
        }
    }
}

