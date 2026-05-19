package com.cosmiclaboratory.voyager.presentation.screen.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TrackingSessionEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TripEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDataInsertionScreen(
    onNavigateBack: () -> Unit,
    viewModel: DebugDataInsertionViewModel = hiltViewModel()
) {
    val state = viewModel.state.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insert Test Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Debug: Insert Test Location Data",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                "Seeds realistic data anchored near your location (Prayagraj) so every " +
                        "screen — Timeline, Insights, Trips, Mileage, Carbon — has something to show. " +
                        "Data persists and appears immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Note:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Test data bypasses normal tracking flow. You may see warnings in logs about " +
                                "\"no current place\" or \"worker stuck\" - this is expected and won't affect the inserted data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "14-Day History (near you)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Clears the database, then inserts 8 places, ~60 visits, ~120 movement " +
                                "segments (drive/transit/cycle), ~1000 GPS samples and one detected " +
                                "multi-day trip to Varanasi. Exercises all free and Pro features.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.insertRichHistory() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading && state.currentOperation == "rich") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Insert 14-Day History")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Current Location",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Inserts: 1 place, 1 active visit, 30 locations\n" +
                                "Simulates being at a cafe right now",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.insertCurrentLocationData() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading && state.currentOperation == "current") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Insert Current Location")
                    }
                }
            }

            if (state.message.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isSuccess)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        state.message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (state.stats.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Statistics",
                            style = MaterialTheme.typography.titleMedium
                        )
                        state.stats.forEach { stat ->
                            Text(stat, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.clearAllData() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear All Test Data")
            }
        }
    }
}

data class DebugDataState(
    val isLoading: Boolean = false,
    val currentOperation: String = "",
    val message: String = "",
    val isSuccess: Boolean = false,
    val stats: List<String> = emptyList()
)

@HiltViewModel
class DebugDataInsertionViewModel @Inject constructor(
    private val database: VoyagerDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(DebugDataState())
    val state: StateFlow<DebugDataState> = _state.asStateFlow()

    private val timeZone = ZoneId.systemDefault().id
    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun LocalDateTime.toEpochMs(): Long =
        this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun LocalDate.toDayKey(): String = this.format(dayKeyFormatter)

    /**
     * A seeded place. Coordinates are real landmarks so reverse-geocoding and the map
     * look believable: a home cluster in Prayagraj plus a Varanasi trip destination.
     */
    private data class Seed(
        val name: String,
        val category: String,
        val lat: Double,
        val lng: Double,
        val radiusM: Float
    )

    private val home = Seed("Home", "HOME", 25.4380, 81.8470, 90f)
    private val work = Seed("Office — Civil Lines", "WORK", 25.4521, 81.8330, 70f)
    private val gym = Seed("Anytime Fitness", "GYM", 25.4405, 81.8410, 45f)
    private val cafe = Seed("El Chico Café", "RESTAURANT", 25.4488, 81.8352, 40f)
    private val mall = Seed("Vinayak City Centre", "SHOPPING", 25.4512, 81.8368, 110f)
    private val hotel = Seed("Hotel Surya, Varanasi", "CUSTOM", 25.2867, 82.9610, 60f)
    private val ghat = Seed("Dashashwamedh Ghat", "OUTDOOR", 25.3076, 83.0103, 130f)
    private val sarnath = Seed("Sarnath", "OUTDOOR", 25.3811, 83.0244, 150f)

    /**
     * Seeds 14 days of believable history near the user: weekday commutes, weekend
     * outings and a 2-day trip to Varanasi (away from home, so trip detection fires).
     * Inserts places, visits, movement segments and GPS samples so every free and Pro
     * surface has data — Timeline, Insights, Carbon, Mileage, Trips.
     */
    fun insertRichHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "rich")
                database.clearAllTables()

                val today = LocalDate.now()
                val nowMs = System.currentTimeMillis()

                val sessionId = database.trackingSessionDao().insert(
                    TrackingSessionEntity(
                        startedAt = today.minusDays(14).atStartOfDay().toEpochMs(),
                        endedAt = nowMs,
                        startedBy = "USER",
                        endedBy = "USER",
                        localTimeZone = timeZone
                    )
                )

                val placeIds = HashMap<String, Long>()
                suspend fun idFor(s: Seed): Long = placeIds.getOrPut(s.name) {
                    database.placeDao().insert(
                        PlaceEntity(
                            centroidLat = s.lat,
                            centroidLng = s.lng,
                            radiusM = s.radiusM,
                            geohash = GeohashEncoder.encode(s.lat, s.lng),
                            confidence = 0.9f,
                            lifecycleStatus = "CONFIRMED",
                            userDisplayName = s.name,
                            category = s.category,
                            categoryConfidence = 0.85f,
                            createdAt = nowMs,
                            lastVisitedAt = nowMs
                        )
                    )
                }

                val locations = ArrayList<RawLocationSampleEntity>()
                var visitCount = 0
                var segmentCount = 0
                var driveMeters = 0.0

                suspend fun stay(s: Seed, arrive: LocalDateTime, depart: LocalDateTime) {
                    val dwell = Duration.between(arrive, depart)
                    val placeId = idFor(s)
                    val dayKey = arrive.toLocalDate().toDayKey()
                    database.visitDao().insert(
                        VisitEntity(
                            placeId = placeId,
                            arrivalAt = arrive.toEpochMs(),
                            departureAt = depart.toEpochMs(),
                            dwellMs = dwell.toMillis(),
                            source = "BATCH_DISCOVERY",
                            confidence = 0.9f,
                            dayKey = dayKey,
                            centroidLat = s.lat,
                            centroidLng = s.lng
                        )
                    )
                    visitCount++
                    // The Timeline renders the movement_segments table — a stop is a
                    // VISIT-type segment linked to a placeId. Without this row the
                    // timeline shows the drives but no named places between them.
                    database.movementSegmentDao().insert(
                        MovementSegmentEntity(
                            segmentType = "VISIT",
                            startAt = arrive.toEpochMs(),
                            endAt = depart.toEpochMs(),
                            distanceM = 0.0,
                            confidence = 0.9f,
                            placeId = placeId,
                            dayKey = dayKey
                        )
                    )
                    segmentCount++
                    val minutes = dwell.toMinutes().coerceAtLeast(1)
                    val n = (minutes / 20).toInt().coerceIn(3, 12)
                    locations += generateStationaryLocations(
                        s.lat, s.lng, n, arrive, minutes * 60 / n, sessionId
                    )
                }

                suspend fun leg(
                    from: Seed, to: Seed, type: String, distanceM: Double,
                    start: LocalDateTime, end: LocalDateTime
                ) {
                    database.movementSegmentDao().insert(
                        MovementSegmentEntity(
                            segmentType = type,
                            startAt = start.toEpochMs(),
                            endAt = end.toEpochMs(),
                            distanceM = distanceM,
                            confidence = 0.86f,
                            dayKey = start.toLocalDate().toDayKey()
                        )
                    )
                    segmentCount++
                    if (type == "DRIVE") driveMeters += distanceM
                    val minutes = Duration.between(start, end).toMinutes().coerceAtLeast(1)
                    val n = if (distanceM > 50_000) 40 else 10
                    locations += generateMovementPath(
                        from.lat, from.lng, to.lat, to.lng, n, start, minutes * 60 / n, sessionId
                    )
                }

                for (offset in 13 downTo 0) {
                    val d = today.minusDays(offset.toLong())
                    fun t(h: Int, m: Int): LocalDateTime = d.atTime(h, m)
                    when (offset) {
                        9 -> { // drive out to Varanasi (morning at home first)
                            stay(home, t(6, 30), t(8, 0))
                            leg(home, hotel, "DRIVE", 126_000.0, t(8, 10), t(12, 40))
                            stay(hotel, t(13, 0), t(22, 30))
                        }
                        8 -> { // away — sightseeing
                            stay(hotel, t(7, 0), t(9, 0))
                            leg(hotel, ghat, "DRIVE", 5_200.0, t(9, 10), t(9, 40))
                            stay(ghat, t(9, 45), t(12, 30))
                            leg(ghat, hotel, "DRIVE", 5_200.0, t(12, 40), t(13, 10))
                            stay(hotel, t(13, 30), t(23, 0))
                        }
                        7 -> { // away — sightseeing
                            stay(hotel, t(7, 30), t(9, 30))
                            leg(hotel, sarnath, "DRIVE", 11_000.0, t(9, 40), t(10, 20))
                            stay(sarnath, t(10, 25), t(13, 30))
                            leg(sarnath, ghat, "DRIVE", 9_500.0, t(15, 30), t(16, 10))
                            stay(ghat, t(16, 15), t(19, 0))
                            leg(ghat, hotel, "DRIVE", 5_200.0, t(19, 10), t(19, 40))
                            stay(hotel, t(20, 0), t(23, 30))
                        }
                        6 -> { // drive home
                            stay(hotel, t(7, 0), t(10, 0))
                            leg(hotel, home, "DRIVE", 126_000.0, t(10, 30), t(15, 0))
                            stay(home, t(16, 0), t(23, 30))
                        }
                        else -> {
                            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY ||
                                d.dayOfWeek == DayOfWeek.SUNDAY
                            if (weekend) {
                                stay(home, t(7, 0), t(11, 0))
                                leg(home, mall, "DRIVE", 3_200.0, t(11, 0), t(11, 22))
                                stay(mall, t(11, 30), t(13, 30))
                                leg(mall, cafe, "DRIVE", 1_900.0, t(13, 35), t(13, 48))
                                stay(cafe, t(13, 50), t(15, 0))
                                leg(cafe, home, "CYCLE", 4_300.0, t(15, 5), t(15, 40))
                                stay(home, t(15, 45), t(23, 30))
                            } else {
                                stay(home, t(6, 30), t(8, 30))
                                val outbound = if (offset % 3 == 0) "TRANSIT" else "DRIVE"
                                leg(home, work, outbound, 3_000.0, t(8, 35), t(9, 0))
                                stay(work, t(9, 0), t(13, 0))
                                leg(work, cafe, "DRIVE", 1_700.0, t(13, 5), t(13, 16))
                                stay(cafe, t(13, 20), t(13, 55))
                                leg(cafe, work, "DRIVE", 1_700.0, t(14, 0), t(14, 12))
                                stay(work, t(14, 15), t(18, 15))
                                if (offset % 2 == 0) {
                                    leg(work, gym, "DRIVE", 1_200.0, t(18, 20), t(18, 33))
                                    stay(gym, t(18, 40), t(19, 40))
                                    leg(gym, home, "DRIVE", 3_400.0, t(19, 45), t(20, 8))
                                } else {
                                    leg(work, home, "DRIVE", 3_000.0, t(18, 20), t(18, 48))
                                }
                                stay(home, t(20, 15), t(23, 30))
                            }
                        }
                    }
                }

                database.rawLocationSampleDao().insertAll(locations)

                // The away run (offsets 8 and 7) is a detectable multi-day trip.
                database.tripDao().insert(
                    TripEntity(
                        startDayKey = today.minusDays(8).toDayKey(),
                        endDayKey = today.minusDays(7).toDayKey(),
                        title = "Trip to Varanasi",
                        placeCount = 3,
                        visitCount = 7,
                        distanceMeters = 36_100.0,
                        isOngoing = false,
                        detectedAt = nowMs
                    )
                )

                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "Inserted 14 days of history near you.",
                    stats = listOf(
                        "Places: ${placeIds.size}",
                        "Visits: $visitCount",
                        "Movement segments: $segmentCount",
                        "GPS samples: ${locations.size}",
                        "Drive distance: ${"%.1f".format(driveMeters / 1000)} km",
                        "Trips: 1 (Varanasi, 2 days)",
                        "Open Timeline, Insights, Trips & Mileage to verify"
                    )
                )
            } catch (e: Exception) {
                _state.value = DebugDataState(
                    message = "Error: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun insertCurrentLocationData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "current")

                val now = LocalDateTime.now()
                val thirtyMinutesAgo = now.minusMinutes(30)
                val dayKey = now.toLocalDate().toDayKey()
                val nowMs = System.currentTimeMillis()

                val sessionId = database.trackingSessionDao().insert(
                    TrackingSessionEntity(
                        startedAt = thirtyMinutesAgo.toEpochMs(),
                        startedBy = "USER",
                        localTimeZone = timeZone
                    )
                )

                val locations = generateStationaryLocations(
                    cafe.lat, cafe.lng, 30, thirtyMinutesAgo, 60, sessionId
                )

                val cafeId = database.placeDao().insert(
                    PlaceEntity(
                        centroidLat = cafe.lat,
                        centroidLng = cafe.lng,
                        radiusM = cafe.radiusM,
                        geohash = GeohashEncoder.encode(cafe.lat, cafe.lng),
                        confidence = 0.90f,
                        lifecycleStatus = "CONFIRMED",
                        userDisplayName = cafe.name,
                        category = cafe.category,
                        createdAt = nowMs,
                        lastVisitedAt = thirtyMinutesAgo.toEpochMs()
                    )
                )

                database.rawLocationSampleDao().insertAll(locations)
                database.visitDao().insert(
                    VisitEntity(
                        placeId = cafeId,
                        arrivalAt = thirtyMinutesAgo.toEpochMs(),
                        departureAt = null,
                        dwellMs = 1800000L,
                        source = "LIVE_DETECTION",
                        confidence = 0.90f,
                        dayKey = dayKey,
                        centroidLat = cafe.lat,
                        centroidLng = cafe.lng
                    )
                )

                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "Inserted current location data! Check Map and Timeline screens.",
                    stats = listOf(
                        "Location: ${cafe.name}",
                        "Locations: ${locations.size}",
                        "Status: Active visit (ongoing)"
                    )
                )
            } catch (e: Exception) {
                _state.value = DebugDataState(
                    message = "Error: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "clear")
                database.clearAllTables()
                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "Cleared all test data!",
                    stats = emptyList()
                )
            } catch (e: Exception) {
                _state.value = DebugDataState(
                    message = "Error: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    // Helper functions

    private fun generateStationaryLocations(
        latitude: Double, longitude: Double, count: Int,
        startTime: LocalDateTime, intervalSeconds: Long, sessionId: Long
    ): List<RawLocationSampleEntity> {
        return (0 until count).map { i ->
            val latVariation = (Math.random() - 0.5) * 0.0001
            val lngVariation = (Math.random() - 0.5) * 0.0001
            val lat = latitude + latVariation
            val lng = longitude + lngVariation
            val ts = startTime.plusSeconds(intervalSeconds * i).toEpochMs()
            RawLocationSampleEntity(
                capturedAt = ts,
                receivedAt = ts + 100,
                lat = lat,
                lng = lng,
                accuracyM = 8.0f + (Math.random() * 4).toFloat(),
                speedMps = 0.0f,
                altitudeM = 50.0 + (Math.random() * 10),
                bearingDeg = 0.0f,
                provider = "fused",
                permissionSnapshot = "fine",
                trackingSessionId = sessionId,
                localTimeZone = timeZone,
                geohash = GeohashEncoder.encode(lat, lng)
            )
        }
    }

    private fun generateMovementPath(
        fromLat: Double, fromLng: Double, toLat: Double, toLng: Double,
        points: Int, startTime: LocalDateTime, intervalSeconds: Long, sessionId: Long
    ): List<RawLocationSampleEntity> {
        return (0 until points).map { i ->
            val progress = i.toDouble() / (points - 1).coerceAtLeast(1)
            val currentLat = fromLat + (toLat - fromLat) * progress
            val currentLng = fromLng + (toLng - fromLng) * progress
            val latVariation = (Math.random() - 0.5) * 0.0002
            val lngVariation = (Math.random() - 0.5) * 0.0002
            val lat = currentLat + latVariation
            val lng = currentLng + lngVariation
            val ts = startTime.plusSeconds(intervalSeconds * i).toEpochMs()
            RawLocationSampleEntity(
                capturedAt = ts,
                receivedAt = ts + 100,
                lat = lat,
                lng = lng,
                accuracyM = 12.0f + (Math.random() * 8).toFloat(),
                speedMps = 5.0f + (Math.random() * 10).toFloat(),
                altitudeM = 50.0 + (Math.random() * 20),
                bearingDeg = Math.toDegrees(Math.atan2(toLng - fromLng, toLat - fromLat)).toFloat(),
                provider = "fused",
                permissionSnapshot = "fine",
                trackingSessionId = sessionId,
                localTimeZone = timeZone,
                geohash = GeohashEncoder.encode(lat, lng)
            )
        }
    }
}
