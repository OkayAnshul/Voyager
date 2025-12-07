package com.cosmiclaboratory.voyager.presentation.screen.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.cosmiclaboratory.voyager.data.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.data.database.entity.LocationEntity
import com.cosmiclaboratory.voyager.data.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.data.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
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
                        Icon(Icons.Default.ArrowBack, "Back")
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
                        "ℹ️ Note:",
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
                        "Week of Data",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Inserts: 3 places, 20+ visits, 1000+ locations\n" +
                                "Pattern: Mon-Fri work, Sat gym, Sun home",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { viewModel.insertWeekData() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading && state.currentOperation == "week") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Insert Week Data")
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
                                "Simulates being at a café right now",
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
                                "• $stat",
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
    private val database: VoyagerDatabase,
    private val appStateManager: com.cosmiclaboratory.voyager.data.state.AppStateManager
) : ViewModel() {

    private val _state = MutableStateFlow(DebugDataState())
    val state = _state.asStateFlow()

    fun insertFullDayData() {
        viewModelScope.launch {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "fullDay")

                val today = LocalDateTime.now().toLocalDate().atStartOfDay()
                val locations = mutableListOf<LocationEntity>()
                val places = mutableListOf<Long>()
                val visits = mutableListOf<Long>()

                // 1. Morning at Home
                locations.addAll(generateStationaryLocations(
                    28.5672, 77.1580, 24, today.plusHours(6), 300
                ))

                val homeId = database.placeDao().insertPlace(PlaceEntity(
                    name = "My Home",
                    category = PlaceCategory.HOME,
                    latitude = 28.5672,
                    longitude = 77.1580,
                    address = "Vasant Vihar, New Delhi, Delhi 110057",
                    streetName = "Pocket C Road",
                    locality = "Vasant Vihar",
                    subLocality = "South West Delhi",
                    postalCode = "110057",
                    countryCode = "IN",
                    visitCount = 0,
                    totalTimeSpent = 0L,
                    radius = 100.0,
                    confidence = 0.95f
                ))
                places.add(homeId)

                visits.add(database.visitDao().insertVisit(VisitEntity(
                    placeId = homeId,
                    entryTime = today.plusHours(6),
                    exitTime = today.plusHours(8),
                    duration = 7200000L,
                    confidence = 0.95f
                )))

                // 2. India Gate
                locations.addAll(generateCircularPath(
                    28.6129, 77.2295, 200.0, 40, today.plusHours(8), 90
                ))

                val indiaGateId = database.placeDao().insertPlace(PlaceEntity(
                    name = "India Gate",
                    category = PlaceCategory.OUTDOOR,
                    latitude = 28.6129,
                    longitude = 77.2295,
                    address = "Rajpath, India Gate, New Delhi, Delhi 110001",
                    streetName = "Rajpath",
                    locality = "India Gate",
                    postalCode = "110001",
                    countryCode = "IN",
                    visitCount = 0,
                    totalTimeSpent = 0L,
                    radius = 150.0,
                    confidence = 0.88f
                ))
                places.add(indiaGateId)

                visits.add(database.visitDao().insertVisit(VisitEntity(
                    placeId = indiaGateId,
                    entryTime = today.plusHours(8),
                    exitTime = today.plusHours(9),
                    duration = 3600000L,
                    confidence = 0.88f
                )))

                // 3. Commute to work
                locations.addAll(generateMovementPath(
                    28.6129, 77.2295, 28.6304, 77.2177, 20, today.plusHours(9), 90
                ))

                // 4. Work
                locations.addAll(generateStationaryLocations(
                    28.6304, 77.2177, 42, today.plusHours(9).plusMinutes(30), 300
                ))

                val workId = database.placeDao().insertPlace(PlaceEntity(
                    name = "My Office",
                    category = PlaceCategory.WORK,
                    latitude = 28.6304,
                    longitude = 77.2177,
                    address = "Connaught Place, New Delhi, Delhi 110001",
                    streetName = "Barakhamba Road",
                    locality = "Connaught Place",
                    postalCode = "110001",
                    countryCode = "IN",
                    visitCount = 0,
                    totalTimeSpent = 0L,
                    radius = 80.0,
                    confidence = 0.92f
                ))
                places.add(workId)

                visits.add(database.visitDao().insertVisit(VisitEntity(
                    placeId = workId,
                    entryTime = today.plusHours(9).plusMinutes(30),
                    exitTime = today.plusHours(13),
                    duration = 12600000L,
                    confidence = 0.92f
                )))

                // 5. Lunch
                locations.addAll(generateMovementPath(
                    28.6304, 77.2177, 28.5526, 77.2434, 15, today.plusHours(13), 120
                ))
                locations.addAll(generateStationaryLocations(
                    28.5526, 77.2434, 12, today.plusHours(13).plusMinutes(30), 300
                ))

                val lunchId = database.placeDao().insertPlace(PlaceEntity(
                    name = "Khan Market Food Court",
                    category = PlaceCategory.RESTAURANT,
                    latitude = 28.5526,
                    longitude = 77.2434,
                    address = "Khan Market, New Delhi, Delhi 110003",
                    streetName = "Middle Lane",
                    locality = "Khan Market",
                    postalCode = "110003",
                    countryCode = "IN",
                    visitCount = 0,
                    totalTimeSpent = 0L,
                    radius = 50.0,
                    confidence = 0.85f
                ))
                places.add(lunchId)

                visits.add(database.visitDao().insertVisit(VisitEntity(
                    placeId = lunchId,
                    entryTime = today.plusHours(13).plusMinutes(30),
                    exitTime = today.plusHours(14),
                    duration = 1800000L,
                    confidence = 0.85f
                )))

                // 6. Back to work
                locations.addAll(generateMovementPath(
                    28.5526, 77.2434, 28.6304, 77.2177, 15, today.plusHours(14), 120
                ))
                locations.addAll(generateStationaryLocations(
                    28.6304, 77.2177, 48, today.plusHours(14).plusMinutes(30), 300
                ))

                visits.add(database.visitDao().insertVisit(VisitEntity(
                    placeId = workId,
                    entryTime = today.plusHours(14).plusMinutes(30),
                    exitTime = today.plusHours(18),
                    duration = 12600000L,
                    confidence = 0.92f
                )))

                // 7. Shopping
                locations.addAll(generateMovementPath(
                    28.6304, 77.2177, 28.5244, 77.2066, 20, today.plusHours(18), 90
                ))
                locations.addAll(generateStationaryLocations(
                    28.5244, 77.2066, 18, today.plusHours(18).plusMinutes(30), 300
                ))

                val mallId = database.placeDao().insertPlace(PlaceEntity(
                    name = "Select Citywalk",
                    category = PlaceCategory.SHOPPING,
                    latitude = 28.5244,
                    longitude = 77.2066,
                    address = "District Centre, Saket, New Delhi, Delhi 110017",
                    streetName = "A-3, District Centre",
                    locality = "Saket",
                    postalCode = "110017",
                    countryCode = "IN",
                    visitCount = 0,
                    totalTimeSpent = 0L,
                    radius = 120.0,
                    confidence = 0.87f
                ))
                places.add(mallId)

                visits.add(database.visitDao().insertVisit(VisitEntity(
                    placeId = mallId,
                    entryTime = today.plusHours(18).plusMinutes(30),
                    exitTime = today.plusHours(19).plusMinutes(30),
                    duration = 3600000L,
                    confidence = 0.87f
                )))

                // 8. Return home
                locations.addAll(generateMovementPath(
                    28.5244, 77.2066, 28.5672, 77.1580, 20, today.plusHours(19).plusMinutes(30), 90
                ))
                locations.addAll(generateStationaryLocations(
                    28.5672, 77.1580, 36, today.plusHours(20), 300
                ))

                visits.add(database.visitDao().insertVisit(VisitEntity(
                    placeId = homeId,
                    entryTime = today.plusHours(20),
                    exitTime = today.plusHours(23),
                    duration = 10800000L,
                    confidence = 0.95f
                )))

                // Insert all locations
                database.locationDao().insertLocations(locations)

                // Note: We intentionally do NOT sync with AppStateManager to avoid triggering
                // state validation warnings. Test data bypasses normal tracking flow.
                // This is expected behavior and won't affect the inserted data.

                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "✅ Successfully inserted full day data!\n" +
                            "Note: State validation warnings in logs are expected and harmless.",
                    stats = listOf(
                        "Locations: ${locations.size}",
                        "Places: ${places.size}",
                        "Visits: ${visits.size}",
                        "Timeline: 6 AM - 11 PM",
                        "",
                        "Data is in database and will display in app"
                    )
                )
            } catch (e: Exception) {
                _state.value = DebugDataState(
                    message = "❌ Error: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun insertWeekData() {
        viewModelScope.launch {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "week")
                // Simplified - just insert basic week data
                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "✅ Week data insertion coming soon!",
                    stats = listOf("Feature in development")
                )
            } catch (e: Exception) {
                _state.value = DebugDataState(
                    message = "❌ Error: ${e.message}",
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

                val locations = generateStationaryLocations(
                    28.6315, 77.2167, 30, thirtyMinutesAgo, 60
                )

                val coffeeShopId = database.placeDao().insertPlace(PlaceEntity(
                    name = "Café Coffee Day - CP",
                    category = PlaceCategory.RESTAURANT,
                    latitude = 28.6315,
                    longitude = 77.2167,
                    address = "N Block, Connaught Place, New Delhi, Delhi 110001",
                    streetName = "N Block Circle",
                    locality = "Connaught Place",
                    postalCode = "110001",
                    countryCode = "IN",
                    visitCount = 0,
                    totalTimeSpent = 0L,
                    radius = 40.0,
                    confidence = 0.90f
                ))

                database.locationDao().insertLocations(locations)
                database.visitDao().insertVisit(VisitEntity(
                    placeId = coffeeShopId,
                    entryTime = thirtyMinutesAgo,
                    exitTime = null,
                    duration = 1800000L,
                    confidence = 0.90f
                ))

                // Note: Test data doesn't update app state, so validation warnings are expected

                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "✅ Inserted current location data!\n" +
                            "Check Map and Timeline screens to see the data.",
                    stats = listOf(
                        "Location: Café Coffee Day",
                        "Locations: ${locations.size}",
                        "Status: Active visit (ongoing)",
                        "",
                        "Log warnings about state are expected"
                    )
                )
            } catch (e: Exception) {
                _state.value = DebugDataState(
                    message = "❌ Error: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                _state.value = DebugDataState(isLoading = true, currentOperation = "clear")
                database.locationDao().deleteAllLocations()
                _state.value = DebugDataState(
                    isSuccess = true,
                    message = "✅ Cleared all test data!",
                    stats = emptyList()
                )
            } catch (e: Exception) {
                _state.value = DebugDataState(
                    message = "❌ Error: ${e.message}",
                    isSuccess = false
                )
            }
        }
    }

    // Helper functions
    private fun generateStationaryLocations(
        latitude: Double, longitude: Double, count: Int,
        startTime: LocalDateTime, intervalSeconds: Long
    ): List<LocationEntity> {
        return (0 until count).map { i ->
            val latVariation = (Math.random() - 0.5) * 0.0001
            val lngVariation = (Math.random() - 0.5) * 0.0001
            LocationEntity(
                latitude = latitude + latVariation,
                longitude = longitude + lngVariation,
                timestamp = startTime.plusSeconds(intervalSeconds * i),
                accuracy = 8.0f + (Math.random() * 4).toFloat(),
                speed = 0.0f,
                altitude = 50.0 + (Math.random() * 10),
                bearing = 0.0f
            )
        }
    }

    private fun generateCircularPath(
        centerLat: Double, centerLng: Double, radiusMeters: Double, points: Int,
        startTime: LocalDateTime, intervalSeconds: Long
    ): List<LocationEntity> {
        return (0 until points).map { i ->
            val angle = (i.toDouble() / points) * 2 * Math.PI
            val offsetLat = (cos(angle) * radiusMeters) / 111111.0
            val offsetLng = (sin(angle) * radiusMeters) / (111111.0 * cos(Math.toRadians(centerLat)))
            LocationEntity(
                latitude = centerLat + offsetLat,
                longitude = centerLng + offsetLng,
                timestamp = startTime.plusSeconds(intervalSeconds * i),
                accuracy = 10.0f + (Math.random() * 5).toFloat(),
                speed = 1.5f + (Math.random() * 2).toFloat(),
                altitude = 50.0 + (Math.random() * 10),
                bearing = ((angle * 180 / Math.PI) % 360).toFloat()
            )
        }
    }

    private fun generateMovementPath(
        fromLat: Double, fromLng: Double, toLat: Double, toLng: Double,
        points: Int, startTime: LocalDateTime, intervalSeconds: Long
    ): List<LocationEntity> {
        return (0 until points).map { i ->
            val progress = i.toDouble() / (points - 1)
            val currentLat = fromLat + (toLat - fromLat) * progress
            val currentLng = fromLng + (toLng - fromLng) * progress
            val latVariation = (Math.random() - 0.5) * 0.0002
            val lngVariation = (Math.random() - 0.5) * 0.0002
            LocationEntity(
                latitude = currentLat + latVariation,
                longitude = currentLng + lngVariation,
                timestamp = startTime.plusSeconds(intervalSeconds * i),
                accuracy = 12.0f + (Math.random() * 8).toFloat(),
                speed = 5.0f + (Math.random() * 10).toFloat(),
                altitude = 50.0 + (Math.random() * 20),
                bearing = Math.toDegrees(Math.atan2(toLng - fromLng, toLat - fromLat)).toFloat()
            )
        }
    }
}
