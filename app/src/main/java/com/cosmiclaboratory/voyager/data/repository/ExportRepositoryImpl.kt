package com.cosmiclaboratory.voyager.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.cosmiclaboratory.voyager.BuildConfig
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.ImportSummary
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import com.cosmiclaboratory.voyager.domain.repository.ExportRepository
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.storage.database.entity.displayName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VoyagerDatabase,
    private val movementSegmentDao: MovementSegmentDao,
    private val visitDao: VisitDao,
    private val routeDao: RouteDao,
    private val placeDao: PlaceDao
) : ExportRepository {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun exportDay(dayKey: String, format: ExportFormat): Result<Uri> = runCatching {
        val segments = movementSegmentDao.getByDayKey(dayKey)
        val visits = visitDao.getByDayKey(dayKey)
        val content = when (format) {
            ExportFormat.GPX -> buildGpx(segments, visits)
            ExportFormat.GEOJSON -> buildGeoJson(segments, visits)
            ExportFormat.CSV -> buildCsv(segments)
            ExportFormat.VOYAGER_JSON -> buildVoyagerJson(segments, visits)
        }

        val ext = when (format) {
            ExportFormat.GPX -> "gpx"
            ExportFormat.GEOJSON -> "geojson"
            ExportFormat.CSV -> "csv"
            ExportFormat.VOYAGER_JSON -> "json"
        }

        val file = File(context.cacheDir, "voyager_export_$dayKey.$ext")
        file.writeText(content)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    override suspend fun exportRange(range: DateRange, format: ExportFormat): Result<Uri> = runCatching {
        val dayKeys = generateDayKeys(range.startDay, range.endDay)
        val allSegments = dayKeys.flatMap { movementSegmentDao.getByDayKey(it) }
        val allVisits = dayKeys.flatMap { visitDao.getByDayKey(it) }

        val content = when (format) {
            ExportFormat.GPX -> buildGpx(allSegments, allVisits)
            ExportFormat.GEOJSON -> buildGeoJson(allSegments, allVisits)
            ExportFormat.CSV -> buildCsv(allSegments)
            ExportFormat.VOYAGER_JSON -> buildVoyagerJson(allSegments, allVisits)
        }

        val ext = when (format) {
            ExportFormat.GPX -> "gpx"
            ExportFormat.GEOJSON -> "geojson"
            ExportFormat.CSV -> "csv"
            ExportFormat.VOYAGER_JSON -> "json"
        }

        val file = File(context.cacheDir, "voyager_export_${range.startDay}_${range.endDay}.$ext")
        file.writeText(content)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    override suspend fun importData(uri: Uri, format: ExportFormat): Result<ImportSummary> = runCatching {
        if (format != ExportFormat.VOYAGER_JSON) {
            error("Import is only supported for VoyagerJSON files (got $format).")
        }

        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: error("Could not open import file.")

        val parsed = json.decodeFromString(VoyagerJsonExport.serializer(), raw)
        check(parsed.version <= VoyagerJsonExport.CURRENT_VERSION) {
            "Import file version ${parsed.version} is newer than this app supports (${VoyagerJsonExport.CURRENT_VERSION}). Update Voyager and try again."
        }

        // Place IDs from the export file are remapped to whatever IDs the local
        // database assigns on insert. Same idea for segment IDs (so routes can be
        // attached to the new segment). Visits then reference the remapped place ID.
        val placeIdMap = mutableMapOf<Long, Long>()
        val segmentIdMap = mutableMapOf<Long, Long>()
        var duplicates = 0

        database.withTransaction {
            for (place in parsed.places) {
                val newId = placeDao.insert(
                    PlaceEntity(
                        placeId = 0,
                        centroidLat = place.centroidLat,
                        centroidLng = place.centroidLng,
                        radiusM = place.radiusM,
                        geohash = place.geohash,
                        s2CellId = place.s2CellId,
                        confidence = place.confidence,
                        lifecycleStatus = place.lifecycleStatus,
                        userDisplayName = place.userDisplayName,
                        bestProviderName = place.bestProviderName,
                        bestProviderSource = place.bestProviderSource,
                        category = place.category,
                        categoryConfidence = place.categoryConfidence,
                        userCategory = place.userCategory,
                        createdAt = place.createdAt,
                        lastVisitedAt = place.lastVisitedAt
                    )
                )
                placeIdMap[place.placeId] = newId
            }

            for (segment in parsed.segments) {
                val overlap = movementSegmentDao.getOverlapping(
                    dayKey = segment.dayKey,
                    startAt = segment.startAt,
                    endAt = segment.endAt
                )
                if (overlap.isNotEmpty()) {
                    duplicates++
                    continue
                }
                val newSegmentId = movementSegmentDao.insert(
                    MovementSegmentEntity(
                        segmentId = 0,
                        segmentType = segment.segmentType,
                        startAt = segment.startAt,
                        endAt = segment.endAt,
                        distanceM = segment.distanceM,
                        confidence = segment.confidence,
                        routeId = null,
                        placeId = segment.placeId?.let { placeIdMap[it] },
                        gapReason = segment.gapReason,
                        dayKey = segment.dayKey,
                        isUserCorrected = segment.isUserCorrected
                    )
                )
                segmentIdMap[segment.segmentId] = newSegmentId

                segment.route?.let { route ->
                    routeDao.insert(
                        RouteEntity(
                            routeId = 0,
                            segmentId = newSegmentId,
                            encodedPolyline = route.encodedPolyline,
                            simplifiedPolyline = route.simplifiedPolyline,
                            totalDistanceM = route.totalDistanceM,
                            totalDurationMs = route.totalDurationMs,
                            avgSpeedMps = route.avgSpeedMps,
                            maxSpeedMps = route.maxSpeedMps,
                            transportMode = route.transportMode,
                            sampleCount = route.sampleCount,
                            boundingBoxJson = route.boundingBoxJson
                        )
                    )
                }
            }

            for (visit in parsed.visits) {
                val mappedPlaceId = placeIdMap[visit.placeId] ?: continue
                val overlap = visitDao.getOverlapping(
                    dayKey = visit.dayKey,
                    arrivalAt = visit.arrivalAt,
                    departureAt = visit.departureAt ?: visit.arrivalAt
                )
                if (overlap.isNotEmpty()) {
                    duplicates++
                    continue
                }
                visitDao.insert(
                    VisitEntity(
                        visitId = 0,
                        placeId = mappedPlaceId,
                        arrivalAt = visit.arrivalAt,
                        departureAt = visit.departureAt,
                        dwellMs = visit.dwellMs,
                        source = visit.source,
                        confidence = visit.confidence,
                        isUserCorrected = visit.isUserCorrected,
                        dayKey = visit.dayKey,
                        centroidLat = visit.centroidLat,
                        centroidLng = visit.centroidLng
                    )
                )
            }
        }

        ImportSummary(
            segmentsImported = segmentIdMap.size,
            visitsImported = parsed.visits.size - duplicates.coerceAtMost(parsed.visits.size),
            placesImported = placeIdMap.size,
            duplicatesSkipped = duplicates
        )
    }

    private suspend fun buildGpx(
        segments: List<com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity>,
        visits: List<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Voyager" xmlns="http://www.topografix.com/GPX/1/1">""")

        // Transit segments as tracks
        for (segment in segments) {
            val route = routeDao.getBySegmentId(segment.segmentId) ?: continue
            val points = decodePolyline(route.encodedPolyline)
            if (points.isEmpty()) continue

            sb.appendLine("""  <trk><name>Segment ${segment.segmentId}</name><trkseg>""")
            for ((lat, lng) in points) {
                sb.appendLine("""    <trkpt lat="$lat" lon="$lng"/>""")
            }
            sb.appendLine("""  </trkseg></trk>""")
        }

        // Visits as waypoints
        for (visit in visits) {
            val lat = visit.centroidLat ?: continue
            val lng = visit.centroidLng ?: continue
            val placeName = if (visit.placeId != 0L) {
                placeDao.getById(visit.placeId)?.displayName() ?: "Visit"
            } else "Visit"
            sb.appendLine("""  <wpt lat="$lat" lon="$lng"><name>${escapeXml(placeName)}</name><time>${java.time.Instant.ofEpochMilli(visit.arrivalAt)}</time></wpt>""")
        }

        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private suspend fun buildGeoJson(
        segments: List<com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity>,
        visits: List<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity>
    ): String {
        val features = mutableListOf<String>()

        // Transit segments as LineStrings
        for (segment in segments) {
            val route = routeDao.getBySegmentId(segment.segmentId) ?: continue
            val points = decodePolyline(route.encodedPolyline)
            if (points.isEmpty()) continue

            val coords = points.joinToString(",") { (lat, lng) -> "[$lng,$lat]" }
            features.add("""{"type":"Feature","properties":{"segmentId":${segment.segmentId},"transportMode":"${escapeJson(route.transportMode)}","distanceM":${route.totalDistanceM}},"geometry":{"type":"LineString","coordinates":[$coords]}}""")
        }

        // Visits as Points
        for (visit in visits) {
            val lat = visit.centroidLat ?: continue
            val lng = visit.centroidLng ?: continue
            val placeName = if (visit.placeId != 0L) {
                placeDao.getById(visit.placeId)?.displayName()
            } else null
            features.add("""{"type":"Feature","properties":{"visitId":${visit.visitId},"placeName":${if (placeName != null) "\"${escapeJson(placeName)}\"" else "null"},"arrivalAt":${visit.arrivalAt},"departureAt":${visit.departureAt ?: "null"},"dwellMs":${visit.dwellMs ?: "null"}},"geometry":{"type":"Point","coordinates":[$lng,$lat]}}""")
        }

        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }

    /** Decode Google-encoded polyline to list of (lat, lng) pairs */
    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var shift = 0
            var b: Int
            var value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1F) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (value and 1 != 0) (value shr 1).inv() else value shr 1

            shift = 0
            value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1F) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (value and 1 != 0) (value shr 1).inv() else value shr 1

            result.add(lat / 1e5 to lng / 1e5)
        }
        return result
    }

    private fun buildCsv(segments: List<com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity>): String {
        val header = "segmentId,type,startAt,endAt,distanceM,confidence,dayKey\n"
        val rows = segments.joinToString("\n") {
            "${it.segmentId},${it.segmentType},${it.startAt},${it.endAt},${it.distanceM},${it.confidence},${it.dayKey}"
        }
        return header + rows
    }

    private suspend fun buildVoyagerJson(
        segments: List<MovementSegmentEntity>,
        visits: List<VisitEntity>
    ): String {
        // Collect every place referenced by a segment or visit so import can
        // restore the graph without dangling references.
        val placeIds = buildSet {
            segments.mapNotNullTo(this) { it.placeId }
            visits.mapTo(this) { it.placeId }
        }
        val places = placeIds.mapNotNull { placeDao.getById(it) }.map { it.toWire() }

        val segmentWires = segments.map { segment ->
            val route = routeDao.getBySegmentId(segment.segmentId)?.toWire()
            segment.toWire(route)
        }

        val visitWires = visits.map { it.toWire() }

        val payload = VoyagerJsonExport(
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            places = places,
            segments = segmentWires,
            visits = visitWires
        )
        return json.encodeToString(VoyagerJsonExport.serializer(), payload)
    }

    private fun PlaceEntity.toWire() = PlaceWire(
        placeId = placeId,
        centroidLat = centroidLat,
        centroidLng = centroidLng,
        radiusM = radiusM,
        geohash = geohash,
        s2CellId = s2CellId,
        confidence = confidence,
        lifecycleStatus = lifecycleStatus,
        userDisplayName = userDisplayName,
        bestProviderName = bestProviderName,
        bestProviderSource = bestProviderSource,
        category = category,
        categoryConfidence = categoryConfidence,
        userCategory = userCategory,
        createdAt = createdAt,
        lastVisitedAt = lastVisitedAt
    )

    private fun MovementSegmentEntity.toWire(route: RouteWire?) = SegmentWire(
        segmentId = segmentId,
        segmentType = segmentType,
        startAt = startAt,
        endAt = endAt,
        distanceM = distanceM,
        confidence = confidence,
        placeId = placeId,
        gapReason = gapReason,
        dayKey = dayKey,
        isUserCorrected = isUserCorrected,
        route = route
    )

    private fun RouteEntity.toWire() = RouteWire(
        encodedPolyline = encodedPolyline,
        simplifiedPolyline = simplifiedPolyline,
        totalDistanceM = totalDistanceM,
        totalDurationMs = totalDurationMs,
        avgSpeedMps = avgSpeedMps,
        maxSpeedMps = maxSpeedMps,
        transportMode = transportMode,
        sampleCount = sampleCount,
        boundingBoxJson = boundingBoxJson
    )

    private fun VisitEntity.toWire() = VisitWire(
        visitId = visitId,
        placeId = placeId,
        arrivalAt = arrivalAt,
        departureAt = departureAt,
        dwellMs = dwellMs,
        source = source,
        confidence = confidence,
        isUserCorrected = isUserCorrected,
        dayKey = dayKey,
        centroidLat = centroidLat,
        centroidLng = centroidLng
    )

    /** Escape special XML characters. & must be first to avoid double-escaping. */
    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** Escape special JSON characters for safe string interpolation. */
    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun generateDayKeys(startDay: String, endDay: String): List<String> {
        val start = java.time.LocalDate.parse(startDay)
        val end = java.time.LocalDate.parse(endDay)
        val keys = mutableListOf<String>()
        var current = start
        while (!current.isAfter(end)) {
            keys.add(current.toString())
            current = current.plusDays(1)
        }
        return keys
    }
}
