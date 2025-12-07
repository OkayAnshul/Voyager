package com.cosmiclaboratory.voyager.domain.usecase

import android.content.Context
import android.util.Log
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.Visit
import com.cosmiclaboratory.voyager.domain.repository.LocationRepository
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import com.cosmiclaboratory.voyager.domain.repository.VisitRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export format for user data
 */
enum class ExportFormat {
    JSON,
    CSV
}

/**
 * Result of an export operation
 */
sealed class ExportResult {
    data class Success(val filePath: String, val fileSize: Long) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/**
 * Serializable data classes for JSON export
 */
@Serializable
data class LocationExport(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null
)

@Serializable
data class PlaceExport(
    val id: Long,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val streetName: String? = null,
    val locality: String? = null,
    val subLocality: String? = null,
    val postalCode: String? = null,
    val countryCode: String? = null,
    val visitCount: Int,
    val totalTimeSpent: Long,
    val lastVisit: String? = null
)

@Serializable
data class VisitExport(
    val id: Long,
    val placeId: Long,
    val placeName: String,
    val entryTime: String,
    val exitTime: String? = null,
    val duration: Long,
    val confidence: Float
)

@Serializable
data class VoyagerDataExport(
    val exportDate: String,
    val version: String = "1.0",
    val locations: List<LocationExport>,
    val places: List<PlaceExport>,
    val visits: List<VisitExport>,
    val statistics: ExportStatistics
)

@Serializable
data class ExportStatistics(
    val totalLocations: Int,
    val totalPlaces: Int,
    val totalVisits: Int,
    val dateRange: String
)

/**
 * Use case for exporting user data to various formats
 *
 * Features:
 * - Export to JSON (complete data with structure)
 * - Export to CSV (simplified tabular format)
 * - Saves to app's external files directory
 * - Automatic file naming with timestamps
 * - Privacy-friendly (no data sent to servers)
 */
@Singleton
class ExportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val placeRepository: PlaceRepository,
    private val visitRepository: VisitRepository
) {
    private val TAG = "ExportDataUseCase"
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Export all data in the specified format
     */
    suspend operator fun invoke(format: ExportFormat, limit: Int = 10000): ExportResult {
        return try {
            Log.d(TAG, "Starting export in $format format")

            // Fetch all data
            val locations = locationRepository.getRecentLocations(limit).first()
            val places = placeRepository.getAllPlaces().first()
            val visits = visitRepository.getAllVisits().first()

            Log.d(TAG, "Fetched ${locations.size} locations, ${places.size} places, ${visits.size} visits")

            // Create export directory
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Generate filename with timestamp
            val timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val fileName = "voyager_export_$timestamp.${format.name.lowercase()}"
            val exportFile = File(exportDir, fileName)

            // Export based on format
            when (format) {
                ExportFormat.JSON -> exportToJson(exportFile, locations, places, visits)
                ExportFormat.CSV -> exportToCsv(exportFile, locations, places, visits)
            }

            val fileSize = exportFile.length()
            Log.d(TAG, "Export completed: ${exportFile.absolutePath} ($fileSize bytes)")

            ExportResult.Success(exportFile.absolutePath, fileSize)

        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ExportResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Export data to JSON format with full structure
     */
    private fun exportToJson(
        file: File,
        locations: List<Location>,
        places: List<Place>,
        visits: List<Visit>
    ) {
        // Convert domain models to export models
        val locationExports = locations.map { loc ->
            LocationExport(
                latitude = loc.latitude,
                longitude = loc.longitude,
                timestamp = loc.timestamp.format(dateTimeFormatter),
                accuracy = loc.accuracy,
                speed = loc.speed,
                altitude = loc.altitude,
                bearing = loc.bearing
            )
        }

        val placeExports = places.map { place ->
            PlaceExport(
                id = place.id,
                name = place.name,
                category = place.category.name,
                latitude = place.latitude,
                longitude = place.longitude,
                address = place.address,
                streetName = place.streetName,
                locality = place.locality,
                subLocality = place.subLocality,
                postalCode = place.postalCode,
                countryCode = place.countryCode,
                visitCount = place.visitCount,
                totalTimeSpent = place.totalTimeSpent,
                lastVisit = place.lastVisit?.format(dateTimeFormatter)
            )
        }

        // Create place ID to name mapping
        val placeIdToName = places.associate { it.id to it.name }

        val visitExports = visits.map { visit ->
            VisitExport(
                id = visit.id,
                placeId = visit.placeId,
                placeName = placeIdToName[visit.placeId] ?: "Unknown",
                entryTime = visit.entryTime.format(dateTimeFormatter),
                exitTime = visit.exitTime?.format(dateTimeFormatter),
                duration = visit.duration,
                confidence = visit.confidence
            )
        }

        // Calculate date range
        val dateRange = if (locations.isNotEmpty()) {
            val earliest = locations.minByOrNull { it.timestamp }?.timestamp
            val latest = locations.maxByOrNull { it.timestamp }?.timestamp
            "${earliest?.format(dateTimeFormatter)} to ${latest?.format(dateTimeFormatter)}"
        } else {
            "No data"
        }

        val exportData = VoyagerDataExport(
            exportDate = java.time.LocalDateTime.now().format(dateTimeFormatter),
            locations = locationExports,
            places = placeExports,
            visits = visitExports,
            statistics = ExportStatistics(
                totalLocations = locations.size,
                totalPlaces = places.size,
                totalVisits = visits.size,
                dateRange = dateRange
            )
        )

        // Write to file
        file.writeText(json.encodeToString(exportData))
    }

    /**
     * Export data to CSV format (simplified, multiple files)
     */
    private fun exportToCsv(
        file: File,
        locations: List<Location>,
        places: List<Place>,
        visits: List<Visit>
    ) {
        // For CSV, we'll create a ZIP-like structure by using a main file and related files
        // For simplicity, we'll create a combined CSV with locations

        FileOutputStream(file).use { fos ->
            fos.bufferedWriter().use { writer ->
                // Write locations CSV
                writer.write("=== LOCATIONS ===\n")
                writer.write("latitude,longitude,timestamp,accuracy,speed,altitude,bearing\n")
                locations.forEach { loc ->
                    writer.write("${loc.latitude},${loc.longitude},${loc.timestamp.format(dateTimeFormatter)},${loc.accuracy},${loc.speed ?: ""},${loc.altitude ?: ""},${loc.bearing ?: ""}\n")
                }

                writer.write("\n=== PLACES ===\n")
                writer.write("id,name,category,latitude,longitude,address,locality,visitCount,totalTimeSpent\n")
                places.forEach { place ->
                    val address = place.address?.replace(",", ";") ?: ""
                    writer.write("${place.id},${place.name},${place.category.name},${place.latitude},${place.longitude},\"$address\",${place.locality ?: ""},${place.visitCount},${place.totalTimeSpent}\n")
                }

                writer.write("\n=== VISITS ===\n")
                writer.write("id,placeId,entryTime,exitTime,duration,confidence\n")
                visits.forEach { visit ->
                    writer.write("${visit.id},${visit.placeId},${visit.entryTime.format(dateTimeFormatter)},${visit.exitTime?.format(dateTimeFormatter) ?: ""},${visit.duration},${visit.confidence}\n")
                }

                writer.write("\n=== STATISTICS ===\n")
                writer.write("metric,value\n")
                writer.write("totalLocations,${locations.size}\n")
                writer.write("totalPlaces,${places.size}\n")
                writer.write("totalVisits,${visits.size}\n")
            }
        }
    }
}
