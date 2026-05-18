package com.cosmiclaboratory.voyager.presentation.screen.daystory

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cosmiclaboratory.voyager.domain.model.DayStory
import com.cosmiclaboratory.voyager.domain.model.DayStoryPlace
import com.cosmiclaboratory.voyager.domain.model.DevicePhoto
import com.cosmiclaboratory.voyager.presentation.billing.EntitlementViewModel
import com.cosmiclaboratory.voyager.presentation.billing.FeatureGate
import com.cosmiclaboratory.voyager.presentation.components.DayNavigator
import com.cosmiclaboratory.voyager.presentation.theme.CardVariant
import com.cosmiclaboratory.voyager.presentation.theme.SectionHeader
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerButton
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerCard
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerGradients
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Photo Day Story — the device's photos for a day, pinned to the places visited.
 *
 * A Pro feature wrapped in [FeatureGate]. The photo-library permission is requested
 * just-in-time: free of any photo access until the user opens this screen and opts in.
 */
@Composable
fun DayStoryScreen(
    onNavigateToPaywall: () -> Unit = {},
    viewModel: DayStoryViewModel = hiltViewModel(),
    entitlementViewModel: EntitlementViewModel = hiltViewModel()
) {
    val isPro by entitlementViewModel.isPro.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // The feature works as long as image access is granted; location is optional.
        if (granted.values.any { it }) {
            viewModel.onAction(DayStoryAction.PermissionGranted)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        FeatureGate(
            isPro = isPro,
            featureName = "Photo Day Story",
            description = "Match the photos on your phone to the places you visited " +
                "each day — all on-device, nothing uploaded.",
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            onUnlock = onNavigateToPaywall
        ) {
            DayStoryContent(
                state = state,
                onAction = viewModel::onAction,
                onRequestPermission = { permissionLauncher.launch(photoPermissions()) }
            )
        }
    }
}

@Composable
private fun DayStoryContent(
    state: DayStoryUiState,
    onAction: (DayStoryAction) -> Unit,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(brush = VoyagerGradients.screenBackground(size.width, size.height)) }
    ) {
        DayNavigator(
            dayLabel = formatDayKey(state.dayKey),
            onPrevious = { onAction(DayStoryAction.PreviousDay) },
            onNext = { onAction(DayStoryAction.NextDay) },
            onTodayClick = { onAction(DayStoryAction.Today) },
            isToday = state.isToday,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            !state.hasPermission -> PermissionPrompt(onRequestPermission)
            state.isLoading -> CenteredMessage("Matching photos to your day…", showSpinner = true)
            state.story?.isEmpty != false -> CenteredMessage(
                "No photos were taken on this day."
            )
            else -> StoryList(state.story)
        }
    }
}

@Composable
private fun StoryList(story: DayStory) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(
                title = "${story.totalPhotoCount} photo(s)",
                trailingAction = { ProBadge() }
            )
        }

        items(story.places, key = { it.placeId }) { place ->
            PlacePhotoCard(place)
        }

        if (story.unplacedPhotos.isNotEmpty()) {
            item {
                PhotoSection(
                    title = "Elsewhere that day",
                    subtitle = "Taken while travelling or away from a known place",
                    emoji = "🧭",
                    photos = story.unplacedPhotos
                )
            }
        }
    }
}

@Composable
private fun PlacePhotoCard(place: DayStoryPlace) {
    val zone = remember { ZoneId.systemDefault() }
    val arrival = remember(place.arrivalAt) {
        TIME_FMT.format(Instant.ofEpochMilli(place.arrivalAt).atZone(zone))
    }
    val departure = remember(place.departureAt) {
        place.departureAt?.let { TIME_FMT.format(Instant.ofEpochMilli(it).atZone(zone)) }
    }
    PhotoSection(
        title = place.displayName,
        subtitle = if (departure != null) "$arrival – $departure" else "from $arrival",
        emoji = place.emoji ?: "📍",
        photos = place.photos
    )
}

@Composable
private fun PhotoSection(
    title: String,
    subtitle: String,
    emoji: String,
    photos: List<DevicePhoto>
) {
    VoyagerCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "$subtitle · ${photos.size} photo(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { it.uri }) { photo ->
                PhotoThumbnail(photo)
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: DevicePhoto) {
    val context = LocalContext.current
    val uri = remember(photo.uri) { Uri.parse(photo.uri) }
    AsyncImage(
        model = uri,
        contentDescription = "Photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(108.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(VoyagerColors.SurfaceVariant)
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
            }
    )
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        VoyagerCard(modifier = Modifier.fillMaxWidth(), variant = CardVariant.HIGHLIGHTED) {
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
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = VoyagerColors.Premium,
                        modifier = Modifier.padding(12.dp).size(28.dp)
                    )
                }
                Text(
                    text = "Allow photo access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = VoyagerColors.OnSurface
                )
                Text(
                    text = "Voyager reads your photos' dates and locations on this device " +
                        "to pin them to the places you visited. They are never uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                VoyagerButton(onClick = onRequestPermission) {
                    Text("Allow photo access")
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String, showSpinner: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = VoyagerColors.Primary)
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = VoyagerColors.OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = VoyagerColors.Premium.copy(alpha = 0.18f)
    ) {
        Text(
            text = "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.Premium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Permissions requested just-in-time: image read + (optional) EXIF location. */
private fun photoPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }
}.toTypedArray()

private fun formatDayKey(dayKey: String): String = runCatching {
    val date = LocalDate.parse(dayKey)
    val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    "$dow, $month ${date.dayOfMonth}, ${date.year}"
}.getOrDefault(dayKey)
