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
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TrackingSessionEntity
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

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
                "Click the buttons below to insert realistic test data into your app. " +
                        "This data will persist and appear in your app immediately.",
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

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Full Day in Delhi",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Inserts: 5 places, 7 visits, 300+ locations\n" +
                                "Timeline: 6 AM - 11 PM with realistic movements",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.insertFullDayData() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading && state.currentOperation == "fullDay") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Insert Full Day Data")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
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
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Statistics",
                            style = MaterialTheme.typography.titleMedium
                        )
                        state.stats.forEach { stat ->
                            Text(
                                stat,
                                style = MaterialTheme.typography.bodySmall
                            )
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

    private fun LocalDateTime.toDayKey(): String = this.format(dayKeyFormatter)

    fun insertFullDayData() {
        viewModelScope.launch {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "fullDay")

                val today = LocalDateTime.now().toLocalDate().atStartOfDay()
                val dayKey = today.toDayKey()
                val nowMs = System.currentTimeMillis()

                // Create a tracking session first
                val sessionId = database.trackingSessionDao().insert(
                    TrackingSessionEntity(
                        startedAt = today.plusHours(6).toEpochMs(),
                        endedAt = today.plusHours(23).toEpochMs(),
                        startedBy = "USER",
                        endedBy = "USER",
                        localTimeZone = timeZone
                    )
                )

                val locations = mutableListOf<RawLocationSampleEntity>()
                val placeIds = mutableListOf<Long>()
                val visitIds = mutableListOf<Long>()

                // 1. Morning at Home
                locations.addAll(generateStationaryLocations(
                    28.5672, 77.1580, 24, today.plusHours(6), 300, sessionId
                ))

                val homeId = database.placeDao().insert(PlaceEntity(
                    centroidLat = 28.5672,
                    centroidLng = 77.1580,
                    radiusM = 100f,
                    geohash = GeohashEncoder.encode(28.5672, 77.1580),
                    confidence = 0.95f,
                    lifecycleStatus = "CONFIRMED",
                    userDisplayName = "My Home",
                    category = "HOME",
                    createdAt = nowMs,
                    lastVisitedAt = today.plusHours(6).toEpochMs()
                ))
                placeIds.add(homeId)

                visitIds.add(database.visitDao().insert(VisitEntity(
                    placeId = homeId,
                    arrivalAt = today.plusHours(6).toEpochMs(),
                    departureAt = today.plusHours(8).toEpochMs(),
                    dwellMs = 7200000L,
                    source = "BATCH_DISCOVERY",
                    confidence = 0.95f,
                    dayKey = dayKey
                )))

                // 2. India Gate
                locations.addAll(generateCircularPath(
                    28.6129, 77.2295, 200.0, 40, today.plusHours(8), 90, sessionId
                ))

                val indiaGateId = database.placeDao().insert(PlaceEntity(
                    centroidLat = 28.6129,
                    centroidLng = 77.2295,
                    radiusM = 150f,
                    geohash = GeohashEncoder.encode(28.6129, 77.2295),
                    confidence = 0.88f,
                    lifecycleStatus = "CONFIRMED",
                    userDisplayName = "India Gate",
                    category = "OUTDOOR",
                    createdAt = nowMs,
                    lastVisitedAt = today.plusHours(8).toEpochMs()
                ))
                placeIds.add(indiaGateId)

                visitIds.add(database.visitDao().insert(VisitEntity(
                    placeId = indiaGateId,
                    arrivalAt = today.plusHours(8).toEpochMs(),
                    departureAt = today.plusHours(9).toEpochMs(),
                    dwellMs = 3600000L,
                    source = "BATCH_DISCOVERY",
                    confidence = 0.88f,
                    dayKey = dayKey
                )))

                // 3. Work
                locations.addAll(generateMovementPath(
                    28.6129, 77.2295, 28.6304, 77.2177, 20, today.plusHours(9), 90, sessionId
                ))
                locations.addAll(generateStationaryLocations(
                    28.6304, 77.2177, 42, today.plusHours(9).plusMinutes(30), 300, sessionId
                ))

                val workId = database.placeDao().insert(PlaceEntity(
                    centroidLat = 28.6304,
                    centroidLng = 77.2177,
                    radiusM = 80f,
                    geohash = GeohashEncoder.encode(28.6304, 77.2177),
                    confidence = 0.92f,
                    lifecycleStatus = "CONFIRMED",
                    userDisplayName = "My Office",
                    category = "WORK",
                    createdAt = nowMs,
                    lastVisitedAt = today.plusHours(9).plusMinutes(30).toEpochMs()
                ))
                placeIds.add(workId)

                visitIds.add(database.visitDao().insert(VisitEntity(
                    placeId = workId,
                    arrivalAt = today.plusHours(9).plusMinutes(30).toEpochMs(),
                    departureAt = today.plusHours(13).toEpochMs(),
                    dwellMs = 12600000L,
                    source = "BATCH_DISCOVERY",
                    confidence = 0.92f,
                    dayKey = dayKey
                )))

                // 4. Lunch
                locations.addAll(generateMovementPath(
                    28.6304, 77.2177, 28.5526, 77.2434, 15, today.plusHours(13), 120, sessionId
                ))
                locations.addAll(generateStationaryLocations(
                    28.5526, 77.2434, 12, today.plusHours(13).plusMinutes(30), 300, sessionId
                ))

                val lunchId = database.placeDao().insert(PlaceEntity(
                    centroidLat = 28.5526,
                    centroidLng = 77.2434,
                    radiusM = 50f,
                    geohash = GeohashEncoder.encode(28.5526, 77.2434),
                    confidence = 0.85f,
                    lifecycleStatus = "CONFIRMED",
                    userDisplayName = "Khan Market Food Court",
                    category = "RESTAURANT",
                    createdAt = nowMs,
                    lastVisitedAt = today.plusHours(13).plusMinutes(30).toEpochMs()
                ))
                placeIds.add(lunchId)

                visitIds.add(database.visitDao().insert(VisitEntity(
                    placeId = lunchId,
                    arrivalAt = today.plusHours(13).plusMinutes(30).toEpochMs(),
                    departureAt = today.plusHours(14).toEpochMs(),
                    dwellMs = 1800000L,
                    source = "BATCH_DISCOVERY",
                    confidence = 0.85f,
                    dayKey = dayKey
                )))

                // 5. Back to work
                locations.addAll(generateMovementPath(
                    28.5526, 77.2434, 28.6304, 77.2177, 15, today.plusHours(14), 120, sessionId
                ))
                locations.addAll(generateStationaryLocations(
                    28.6304, 77.2177, 48, today.plusHours(14).plusMinutes(30), 300, sessionId
                ))

                visitIds.add(database.visitDao().insert(VisitEntity(
                    placeId = workId,
                    arrivalAt = today.plusHours(14).plusMinutes(30).toEpochMs(),
                    departureAt = today.plusHours(18).toEpochMs(),
                    dwellMs = 12600000L,
                    source = "BATCH_DISCOVERY",
                    confidence = 0.92f,
                    dayKey = dayKey
                )))

                // 6. Shopping
                locations.addAll(generateMovementPath(
                    28.6304, 77.2177, 28.5244, 77.2066, 20, today.plusHours(18), 90, sessionId
                ))
                locations.addAll(generateStationaryLocations(
                    28.5244, 77.2066, 18, today.plusHours(18).plusMinutes(30), 300, sessionId
                ))

                val mallId = database.placeDao().insert(PlaceEntity(
                    centroidLat = 28.5244,
                    centroidLng = 77.2066,
                    radiusM = 120f,
                    geohash = GeohashEncoder.encode(28.5244, 77.2066),
                    confidence = 0.87f,
                    lifecycleStatus = "CONFIRMED",
                    userDisplayName = "Select Citywalk",
                    category = "SHOPPING",
                    createdAt = nowMs,
                    lastVisitedAt = today.plusHours(18).plusMinutes(30).toEpochMs()
                ))
                placeIds.add(mallId)

                visitIds.add(database.visitDao().insert(VisitEntity(
                    placeId = mallId,
                    arrivalAt = today.plusHours(18).plusMinutes(30).toEpochMs(),
                    departureAt = today.plusHours(19).plusMinutes(30).toEpochMs(),
                    dwellMs = 3600000L,
                    source = "BATCH_DISCOVERY",
                    confidence = 0.87f,
                    dayKey = dayKey
                )))

                // 7. Return home
                locations.addAll(generateMovementPath(
                    28.5244, 77.2066, 28.5672, 77.1580, 20, today.plusHours(19).plusMinutes(30), 90, sessionId
                ))
                locations.addAll(generateStationaryLocations(
                    28.5672, 77.1580, 36, today.plusHours(20), 300, sessionId
                ))

                visitIds.add(database.visitDao().insert(VisitEntity(
                    placeId = homeId,
                    arrivalAt = today.plusHours(20).toEpochMs(),
                    departureAt = today.plusHours(23).toEpochMs(),
                    dwellMs = 10800000L,
                    source = "BATCH_DISCOVERY",
                    confidence = 0.95f,
                    dayKey = dayKey
                )))

                // Insert all locations
                database.rawLocationSampleDao().insertAll(locations)

                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "Successfully inserted full day data!",
                    stats = listOf(
                        "Locations: ${locations.size}",
                        "Places: ${placeIds.size}",
                        "Visits: ${visitIds.size}",
                        "Timeline: 6 AM - 11 PM",
                        "Data is in database and will display in app"
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
        viewModelScope.launch {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "current")

                val now = LocalDateTime.now()
                val thirtyMinutesAgo = now.minusMinutes(30)
                val dayKey = now.toDayKey()
                val nowMs = System.currentTimeMillis()

                val sessionId = database.trackingSessionDao().insert(
                    TrackingSessionEntity(
                        startedAt = thirtyMinutesAgo.toEpochMs(),
                        startedBy = "USER",
                        localTimeZone = timeZone
                    )
                )

                val locations = generateStationaryLocations(
                    28.6315, 77.2167, 30, thirtyMinutesAgo, 60, sessionId
                )

                val coffeeShopId = database.placeDao().insert(PlaceEntity(
                    centroidLat = 28.6315,
                    centroidLng = 77.2167,
                    radiusM = 40f,
                    geohash = GeohashEncoder.encode(28.6315, 77.2167),
                    confidence = 0.90f,
                    lifecycleStatus = "CONFIRMED",
                    userDisplayName = "Cafe Coffee Day - CP",
                    category = "RESTAURANT",
                    createdAt = nowMs,
                    lastVisitedAt = thirtyMinutesAgo.toEpochMs()
                ))

                database.rawLocationSampleDao().insertAll(locations)
                database.visitDao().insert(VisitEntity(
                    placeId = coffeeShopId,
                    arrivalAt = thirtyMinutesAgo.toEpochMs(),
                    departureAt = null,
                    dwellMs = 1800000L,
                    source = "LIVE_DETECTION",
                    confidence = 0.90f,
                    dayKey = dayKey
                ))

                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "Inserted current location data! Check Map and Timeline screens.",
                    stats = listOf(
                        "Location: Cafe Coffee Day",
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
        viewModelScope.launch {
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

    private fun generateCircularPath(
        centerLat: Double, centerLng: Double, radiusMeters: Double, points: Int,
        startTime: LocalDateTime, intervalSeconds: Long, sessionId: Long
    ): List<RawLocationSampleEntity> {
        return (0 until points).map { i ->
            val angle = (i.toDouble() / points) * 2 * Math.PI
            val offsetLat = (cos(angle) * radiusMeters) / 111111.0
            val offsetLng = (sin(angle) * radiusMeters) / (111111.0 * cos(Math.toRadians(centerLat)))
            val lat = centerLat + offsetLat
            val lng = centerLng + offsetLng
            val ts = startTime.plusSeconds(intervalSeconds * i).toEpochMs()
            RawLocationSampleEntity(
                capturedAt = ts,
                receivedAt = ts + 100,
                lat = lat,
                lng = lng,
                accuracyM = 10.0f + (Math.random() * 5).toFloat(),
                speedMps = 1.5f + (Math.random() * 2).toFloat(),
                altitudeM = 50.0 + (Math.random() * 10),
                bearingDeg = ((angle * 180 / Math.PI) % 360).toFloat(),
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
            val progress = i.toDouble() / (points - 1)
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
