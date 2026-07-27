package com.cosmiclaboratory.voyager.presentation.screen.map

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cosmiclaboratory.voyager.domain.map.MapEngine
import com.cosmiclaboratory.voyager.domain.model.ActiveVisitInfo
import com.cosmiclaboratory.voyager.domain.model.LatLngBounds
import com.cosmiclaboratory.voyager.domain.model.MapRoute
import com.cosmiclaboratory.voyager.domain.model.VisitMarker
import kotlinx.coroutines.delay
import com.cosmiclaboratory.voyager.presentation.di.MapEngineEntryPoint
import com.cosmiclaboratory.voyager.presentation.state.DayNavigationStateHolder
import com.cosmiclaboratory.voyager.presentation.components.*
import com.cosmiclaboratory.voyager.presentation.theme.*
import com.cosmiclaboratory.voyager.ui.theme.MonoData
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.tasks.await

/**
 * Map Screen — Voyager UI (MapLibre via MapEngine)
 *
 * Renders the day's routes and visit markers on a MapLibre map,
 * with bidirectional focus sync via DayNavigationStateHolder.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel(),
    onNavigateToVisit: (Long) -> Unit = {},
    onRecord: () -> Unit = {}
) {
    val context = LocalContext.current
    val mapEngine = remember {
        EntryPointAccessors.fromApplication<MapEngineEntryPoint>(
            context.applicationContext
        ).mapEngine()
    }

    val uiState by viewModel.uiState.collectAsState()
    var showLegend by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoyagerColors.Background)
    ) {
        // Always render the map — even with no data, show the base map
        MapContent(
            mapEngine = mapEngine,
            uiState = uiState,
            onMarkerTapped = { visitId ->
                viewModel.onIntent(MapIntent.TapMarker(visitId))
                onNavigateToVisit(visitId)
            },
            onRouteTapped = { segmentId ->
                viewModel.onIntent(MapIntent.TapRoute(segmentId))
            },
            onMapTapped = {
                viewModel.onIntent(MapIntent.ClearFocus)
            }
        )

        // Floating DayNavigator at top
        DayNavigator(
            dayLabel = formatMapDayKey(uiState.dayKey),
            onPrevious = { viewModel.onIntent(MapIntent.NavigatePreviousDay) },
            onNext = { viewModel.onIntent(MapIntent.NavigateNextDay) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            isToday = uiState.dayKey == java.time.LocalDate.now().toString(),
            summaryText = if (uiState.visitMarkers.isNotEmpty()) {
                "${uiState.visitMarkers.size} places"
            } else null
        )

        // Floating Action Buttons (bottom-right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { showList = true },
                containerColor = VoyagerColors.Surface,
                contentColor = VoyagerColors.Primary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "List places", modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = { showLegend = !showLegend },
                containerColor = if (showLegend) VoyagerColors.PrimaryContainer else VoyagerColors.Surface,
                contentColor = VoyagerColors.Primary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = "Map legend", modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = { viewModel.onIntent(MapIntent.CenterOnUser) },
                containerColor = VoyagerColors.Surface,
                contentColor = VoyagerColors.Primary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My location", modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = { viewModel.onIntent(MapIntent.FitBounds) },
                containerColor = VoyagerColors.Surface,
                contentColor = VoyagerColors.Primary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit bounds", modifier = Modifier.size(20.dp))
            }
        }

        // Prominent Record entry — one tap into the Strava-style workout recorder,
        // without adding a sixth bottom-nav tab.
        ExtendedFloatingActionButton(
            onClick = onRecord,
            containerColor = VoyagerColors.Primary,
            contentColor = VoyagerColors.OnPrimary,
            icon = { Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(20.dp)) },
            text = { Text("Record") },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        // Transport-mode legend (toggleable) — fixes color-only meaning.
        if (showLegend) {
            MapLegendCard(
                onClose = { showLegend = false },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }

        // Accessibility / quick-jump list fallback for the canvas-only map.
        if (showList) {
            MapPlacesListSheet(
                markers = uiState.visitMarkers,
                onPlace = { visitId ->
                    viewModel.onIntent(MapIntent.TapMarker(visitId))
                    showList = false
                },
                onDismiss = { showList = false }
            )
        }

        // Center on user's actual current location when requested
        CenterOnUserEffect(
            requested = uiState.centerOnUserRequested,
            currentLocation = uiState.currentLocation,
            visitMarkers = uiState.visitMarkers,
            mapEngine = mapEngine,
            onConsumed = { viewModel.consumeCenterOnUser() }
        )

        // Fit bounds when requested
        LaunchedEffect(uiState.fitBoundsRequested) {
            if (uiState.fitBoundsRequested) {
                val bounds = uiState.bounds
                if (bounds != null) {
                    mapEngine.fitBounds(
                        listOf(
                            bounds.southWestLat to bounds.southWestLng,
                            bounds.northEastLat to bounds.northEastLng
                        ),
                        paddingPx = 64
                    )
                }
                viewModel.consumeFitBounds()
            }
        }

        // Loading overlay — dim + shimmer over the basemap while the day loads.
        if (uiState.isLoading) {
            MapLoadingOverlay()
        }

        // Empty overlay — honest "No Movement" when the day has no data.
        if (!uiState.isLoading && uiState.routes.isEmpty() && uiState.visitMarkers.isEmpty()) {
            MapEmptyOverlay(modifier = Modifier.align(Alignment.Center))
        }
    }
}

// ---------------------------------------------------------------------------
// Map content with AndroidView
// ---------------------------------------------------------------------------

@Composable
private fun MapContent(
    mapEngine: MapEngine,
    uiState: MapUiState,
    onMarkerTapped: (Long) -> Unit,
    onRouteTapped: (Long) -> Unit,
    onMapTapped: () -> Unit
) {
    // Stable reference maps: engine-assigned id -> domain id
    val markerIdToVisitId = remember { mutableMapOf<String, Long>() }
    val polylineIdToRouteId = remember { mutableMapOf<String, Long>() }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- Native map view ---
        AndroidView(
            factory = { ctx ->
                mapEngine.createMapView(ctx).also {
                    // Wire click listeners once
                    mapEngine.setOnMarkerClickListener { markerId ->
                        markerIdToVisitId[markerId]?.let(onMarkerTapped)
                    }
                    mapEngine.setOnMapClickListener { _, _ -> onMapTapped() }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // --- React to state changes and redraw overlays ---
        RedrawOverlays(
            mapEngine = mapEngine,
            routes = uiState.routes,
            markers = uiState.visitMarkers,
            bounds = uiState.bounds,
            focusedRoute = uiState.focusedRoute,
            focusedSegmentId = uiState.focusedSegmentId,
            currentLocation = uiState.currentLocation,
            markerIdToVisitId = markerIdToVisitId,
            polylineIdToRouteId = polylineIdToRouteId
        )

        // --- Focused segment badge (top-left) ---
        uiState.focusedRoute?.let { route ->
            VoyagerCard(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                padding = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PulsingDot(size = 10.dp, color = VoyagerColors.Primary)
                    Text(
                        text = route.transportMode.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary
                    )
                }
            }
        }

        // --- Focused visit badge (top-left, when a visit is selected from timeline) ---
        val focusedMarker = uiState.focusedVisitMarker
        if (focusedMarker != null && uiState.focusedRoute == null) {
            val marker = focusedMarker
            LaunchedEffect(marker.visitId) {
                mapEngine.animateTo(marker.lat, marker.lng, 16.0)
            }
            VoyagerCard(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                padding = 8.dp
            ) {
                Column {
                    Text(
                        text = "${marker.sequenceNumber}. ${marker.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary,
                        maxLines = 1
                    )
                    Text(
                        text = formatTime(marker.arrivalAt) +
                            (marker.departureAt?.let { " - ${formatTime(it)}" } ?: " - now"),
                        style = MaterialTheme.typography.labelSmall,
                        color = VoyagerColors.OnSurfaceVariant
                    )
                }
            }
        }

        // --- Live location / active visit indicator (top-left, below focused route) ---
        val activeVisitLocation = uiState.activeVisitLocation
        if (activeVisitLocation != null && uiState.focusedRoute == null) {
            ActiveVisitMapBadge(
                visit = activeVisitLocation,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        } else if (uiState.currentLocation != null && uiState.focusedRoute == null) {
            VoyagerCard(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                padding = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PulsingDot(size = 10.dp, color = VoyagerColors.AccentGreen)
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.AccentGreen
                    )
                }
            }
        }

        // --- Day summary chip (top-right) — places visited today ---
        if (uiState.visitMarkers.isNotEmpty()) {
            VoyagerCard(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                variant = CardVariant.GLASS,
                padding = 8.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = VoyagerColors.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${uiState.visitMarkers.size} places",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VoyagerColors.Primary
                    )
                }
            }
        }
    }

    // Forward Activity lifecycle to MapView — required for MapLibre to render
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapEngine.onStart()
                Lifecycle.Event.ON_RESUME -> mapEngine.onResume()
                Lifecycle.Event.ON_PAUSE -> mapEngine.onPause()
                Lifecycle.Event.ON_STOP -> mapEngine.onStop()
                Lifecycle.Event.ON_DESTROY -> mapEngine.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Catch up if already started (composable entered mid-lifecycle)
        mapEngine.onStart()
        mapEngine.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Don't call destroy() — the singleton MapEngine handles cleanup in createMapView()
            // when re-entered. Just clear overlays and pause the map.
            mapEngine.clearAll()
            mapEngine.onPause()
            mapEngine.onStop()
        }
    }
}

// ---------------------------------------------------------------------------
// Overlay synchronisation — runs whenever relevant state keys change
// ---------------------------------------------------------------------------

@Composable
private fun RedrawOverlays(
    mapEngine: MapEngine,
    routes: List<MapRoute>,
    markers: List<VisitMarker>,
    bounds: LatLngBounds?,
    focusedRoute: MapRoute?,
    focusedSegmentId: Long?,
    currentLocation: Pair<Double, Double>?,
    markerIdToVisitId: MutableMap<String, Long>,
    polylineIdToRouteId: MutableMap<String, Long>
) {
    // Track whether we've done initial centering
    val hasCentered = remember { mutableStateOf(false) }

    // Redraw all overlays when routes or markers change (i.e. day changed)
    LaunchedEffect(routes, markers) {
        android.util.Log.d("MapLibreEngine", "RedrawOverlays: routes=${routes.size} markers=${markers.size}")
        mapEngine.clearAll()
        markerIdToVisitId.clear()
        polylineIdToRouteId.clear()

        // Draw route polylines with direction arrows
        for (route in routes) {
            try {
                if (route.polylinePoints.size >= 2) {
                    val id = mapEngine.addDirectionalPolyline(
                        points = route.polylinePoints,
                        color = route.color,
                        width = if (route.segmentId == focusedSegmentId) 8f else 4f
                    )
                    if (id.isNotEmpty()) {
                        polylineIdToRouteId[id] = route.segmentId
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MapLibreEngine", "Route draw failed: segmentId=${route.segmentId}", e)
            }
        }

        // Draw visit markers (numbered with times)
        for (marker in markers) {
            try {
                val timeRange = formatTime(marker.arrivalAt) +
                    (marker.departureAt?.let { " - ${formatTime(it)}" } ?: "")
                val title = "${marker.sequenceNumber}. ${marker.displayName}\n$timeRange"
                val id = mapEngine.addMarker(
                    lat = marker.lat,
                    lng = marker.lng,
                    title = title,
                    number = marker.sequenceNumber
                )
                if (id.isNotEmpty()) {
                    markerIdToVisitId[id] = marker.visitId
                }
            } catch (e: Exception) {
                android.util.Log.e("MapLibreEngine", "Marker draw failed: visitId=${marker.visitId}", e)
            }
        }

        // Draw journey polyline connecting all visits in chronological order
        if (markers.size >= 2) {
            val journeyPoints = markers.sortedBy { it.arrivalAt }.map { it.lat to it.lng }
            mapEngine.addPolyline(
                points = journeyPoints,
                color = 0x80607D8B.toInt(), // semi-transparent grey
                width = 2f
            )
        }

        // Re-add live location indicator after clearAll
        if (currentLocation != null) {
            mapEngine.setCurrentLocationMarker(currentLocation.first, currentLocation.second)
        }
    }

    // Fit bounds when they change (initial load or day switch)
    // If no bounds (no data), center on current location
    LaunchedEffect(bounds, currentLocation) {
        if (bounds != null) {
            val allPoints = listOf(
                bounds.southWestLat to bounds.southWestLng,
                bounds.northEastLat to bounds.northEastLng
            )
            mapEngine.fitBounds(allPoints, paddingPx = 64, maxZoom = 15.0)
            hasCentered.value = true
        } else if (!hasCentered.value && currentLocation != null) {
            // No data for the day — center on user's current location
            mapEngine.animateTo(currentLocation.first, currentLocation.second, 15.0)
            hasCentered.value = true
        }
    }

    // Highlight focused route when selection changes
    LaunchedEffect(focusedSegmentId, focusedRoute) {
        if (focusedRoute != null && focusedRoute.polylinePoints.size >= 2) {
            val id = mapEngine.addPolyline(
                points = focusedRoute.polylinePoints,
                color = focusedRoute.color,
                width = 8f
            )
            if (id.isNotEmpty()) {
                polylineIdToRouteId[id] = focusedRoute.segmentId
            }
            mapEngine.fitBounds(focusedRoute.polylinePoints, paddingPx = 80)
        }
    }

    // Update live location indicator (blue dot) when position changes
    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            mapEngine.setCurrentLocationMarker(currentLocation.first, currentLocation.second)
        } else {
            mapEngine.removeCurrentLocationMarker()
        }
    }
}

// ---------------------------------------------------------------------------
// Center-on-user helper — falls back to FusedLocationProvider if no pipeline loc
// ---------------------------------------------------------------------------

@SuppressLint("MissingPermission")
@Composable
private fun CenterOnUserEffect(
    requested: Boolean,
    currentLocation: Pair<Double, Double>?,
    visitMarkers: List<VisitMarker>,
    mapEngine: MapEngine,
    onConsumed: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect

        if (currentLocation != null) {
            mapEngine.animateTo(currentLocation.first, currentLocation.second, 15.0)
            mapEngine.setCurrentLocationMarker(currentLocation.first, currentLocation.second)
        } else {
            // No pipeline location yet — request last known from FusedLocationProvider
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val loc = client.lastLocation.await()
                if (loc != null) {
                    mapEngine.animateTo(loc.latitude, loc.longitude, 15.0)
                    mapEngine.setCurrentLocationMarker(loc.latitude, loc.longitude, loc.accuracy)
                } else {
                    // Absolute fallback: center on last visit marker
                    val lastMarker = visitMarkers.lastOrNull()
                    if (lastMarker != null) {
                        mapEngine.animateTo(lastMarker.lat, lastMarker.lng, 14.0)
                    }
                }
            } catch (_: SecurityException) {
                // Location permission not granted — fall back to last marker
                val lastMarker = visitMarkers.lastOrNull()
                if (lastMarker != null) {
                    mapEngine.animateTo(lastMarker.lat, lastMarker.lng, 14.0)
                }
            }
        }
        onConsumed()
    }
}

// ---------------------------------------------------------------------------
// Active visit map badge
// ---------------------------------------------------------------------------

@Composable
private fun ActiveVisitMapBadge(
    visit: ActiveVisitInfo,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val dwellMs = (now - visit.arrivalAt).coerceAtLeast(0)

    VoyagerCard(modifier = modifier, padding = 8.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulsingDot(size = 10.dp, color = VoyagerColors.AccentBlue)
            Column {
                Text(
                    text = visit.placeName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = VoyagerColors.AccentBlue,
                    maxLines = 1
                )
                Text(
                    text = formatMapDwellDuration(dwellMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = VoyagerColors.OnSurfaceVariant
                )
            }
        }
    }
}

private fun formatMapDwellDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%dh %dm".format(hours, minutes)
        minutes > 0 -> "%dm %ds".format(minutes, seconds)
        else -> "%ds".format(seconds)
    }
}

// ---------------------------------------------------------------------------
// Loading / Empty states
// ---------------------------------------------------------------------------

/** Dimmed scrim + shimmer skeletons (top day-nav + bottom sheet) over the basemap. */
@Composable
private fun MapLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoyagerColors.Background.copy(alpha = 0.55f))
    ) {
        ShimmerCard(
            height = 40.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .width(180.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerCard(height = 120.dp)
        }
    }
}

/** Honest empty state — "No Movement" with a location-off glyph and mono caption. */
@Composable
private fun MapEmptyOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VoyagerSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(VoyagerColors.Surface.copy(alpha = 0.7f), VoyagerShapes.pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                tint = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = "No Movement",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = VoyagerColors.OnSurface
        )
        Text(
            text = "NO TELEMETRY RECORDED FOR THIS DAY",
            style = MonoData,
            color = VoyagerColors.OnSurfaceVariant,
            textAlign = TextAlign.Center,
            letterSpacing = 1.5.sp
        )
    }
}

private fun formatTime(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val local = java.time.LocalTime.ofInstant(instant, java.time.ZoneId.systemDefault())
    return local.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatMapDayKey(dayKey: String): String {
    if (dayKey.isBlank()) return ""
    return try {
        val date = java.time.LocalDate.parse(dayKey)
        val today = java.time.LocalDate.now()
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        }
    } catch (_: Exception) {
        dayKey
    }
}

// ---------------------------------------------------------------------------
// Legend + accessibility list fallback
// ---------------------------------------------------------------------------

private val LEGEND_MODES = listOf(
    "Walk / Run" to VoyagerColors.TransportWalk,
    "Drive" to VoyagerColors.TransportDrive,
    "Cycle" to VoyagerColors.TransportCycle,
    "Transit" to VoyagerColors.TransportTransit,
    "Gap" to VoyagerColors.TransportGap
)

/** Frosted legend mapping route color → transport mode (never rely on colour alone). */
@Composable
private fun MapLegendCard(onClose: () -> Unit, modifier: Modifier = Modifier) {
    VoyagerCard(modifier = modifier, variant = CardVariant.GLASS, padding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mode Colors", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = VoyagerColors.Primary)
            Icon(
                Icons.Default.Close,
                contentDescription = "Close legend",
                tint = VoyagerColors.OnSurfaceVariant,
                modifier = Modifier.size(16.dp).clickable(onClick = onClose)
            )
        }
        Spacer(Modifier.height(8.dp))
        LEGEND_MODES.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                if (label == "Gap") {
                    // Dashed indicator — a gap is missing data, never a solid leg.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.width(12.dp)
                    ) {
                        repeat(3) {
                            Box(modifier = Modifier.size(width = 3.dp, height = 3.dp).background(color))
                        }
                    }
                } else {
                    Box(modifier = Modifier.size(12.dp).clip(VoyagerShapes.pill).background(color))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = VoyagerColors.OnSurface)
            }
        }
    }
}

/** Text enumeration of the day's places — accessibility + quick-jump for the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapPlacesListSheet(
    markers: List<VisitMarker>,
    onPlace: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VoyagerColors.SurfaceOverlay,
        shape = VoyagerShapes.sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Today's places", style = MaterialTheme.typography.headlineSmall, color = VoyagerColors.OnSurface)
            Spacer(Modifier.height(4.dp))
            if (markers.isEmpty()) {
                Text(
                    "No visits recorded for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoyagerColors.OnSurfaceVariant
                )
            } else {
                markers.sortedBy { it.arrivalAt }.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VoyagerShapes.card)
                            .clickable { onPlace(m.visitId) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(VoyagerShapes.pill)
                                .background(VoyagerColors.Primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${m.sequenceNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = VoyagerColors.Primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                m.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = VoyagerColors.OnSurface,
                                maxLines = 1
                            )
                            Text(
                                formatTime(m.arrivalAt) + (m.departureAt?.let { " – ${formatTime(it)}" } ?: " – now"),
                                style = MaterialTheme.typography.labelSmall,
                                color = VoyagerColors.OnSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
