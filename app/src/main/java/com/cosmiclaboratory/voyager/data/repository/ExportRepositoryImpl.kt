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
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.RouteDao
import com.cosmiclaboratory.voyager.storage.database.dao.TrackingSessionDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.RouteEntity
import com.cosmiclaboratory.voyager.storage.database.entity.TrackingSessionEntity
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
    private val placeDao: PlaceDao,
    private val rawLocationSampleDao: RawLocationSampleDao,
    private val trackingSessionDao: TrackingSessionDao,
    private val settingsRepository: SettingsRepository
) : ExportRepository {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun exportDay(dayKey: String, format: ExportFormat): Result<Uri> = runCatching {
        val settings = settingsRepository.observeSettings().value
        val stripCoords = settings.stripExactCoordinatesOnExport
        val segments = movementSegmentDao.getByDayKey(dayKey)
        val visits = visitDao.getByDayKey(dayKey)
        val rawSamples = if (format == ExportFormat.VOYAGER_JSON && settings.exportIncludeRawSamples) {
            rawSamplesForDays(dayKey, dayKey, stripCoords)
        } else emptyList()

        val content = when (format) {
            ExportFormat.GPX -> buildGpx(segments, visits, stripCoords)
            ExportFormat.GEOJSON -> buildGeoJson(segments, visits, stripCoords)
            ExportFormat.CSV -> buildCsv(segments)
            ExportFormat.VOYAGER_JSON -> buildVoyagerJson(segments, visits, rawSamples, stripCoords)
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
        val settings = settingsRepository.observeSettings().value
        val stripCoords = settings.stripExactCoordinatesOnExport
        val dayKeys = generateDayKeys(range.startDay, range.endDay)
        val allSegments = dayKeys.flatMap { movementSegmentDao.getByDayKey(it) }
        val allVisits = dayKeys.flatMap { visitDao.getByDayKey(it) }
        val rawSamples = if (format == ExportFormat.VOYAGER_JSON && settings.exportIncludeRawSamples) {
            rawSamplesForDays(range.startDay, range.endDay, stripCoords)
        } else emptyList()

        val content = when (format) {
            ExportFormat.GPX -> buildGpx(allSegments, allVisits, stripCoords)
            ExportFormat.GEOJSON -> buildGeoJson(allSegments, allVisits, stripCoords)
            ExportFormat.CSV -> buildCsv(allSegments)
            ExportFormat.VOYAGER_JSON -> buildVoyagerJson(allSegments, allVisits, rawSamples, stripCoords)
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
        var rawSamplesImported = 0

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

            // Raw GPS samples (VoyagerJSON v2+). Every sample needs a parent
            // tracking session (non-null FK), so all imported samples are parented
            // to one synthetic "IMPORT" session — no schema change required.
            if (parsed.rawSamples.isNotEmpty()) {
                val first = parsed.rawSamples.first()
                val sessionId = trackingSessionDao.insert(
                    TrackingSessionEntity(
                        startedAt = parsed.rawSamples.minOf { it.capturedAt },
                        endedAt = parsed.rawSamples.maxOf { it.capturedAt },
                        startedBy = "IMPORT",
                        endedBy = "IMPORT",
                        localTimeZone = first.localTimeZone,
                        totalSamples = parsed.rawSamples.size
                    )
                )
                for (sample in parsed.rawSamples) {
                    rawLocationSampleDao.insert(sample.toEntity(sessionId))
                    rawSamplesImported++
                }
            }
        }

        ImportSummary(
            segmentsImported = segmentIdMap.size,
            visitsImported = parsed.visits.size - duplicates.coerceAtMost(parsed.visits.size),
            placesImported = placeIdMap.size,
            duplicatesSkipped = duplicates,
            rawSamplesImported = rawSamplesImported
        )
    }

    private suspend fun buildGpx(
        segments: List<com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity>,
        visits: List<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity>,
        stripCoords: Boolean
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="Voyager" xmlns="http://www.topografix.com/GPX/1/1">""")

        // Transit segments as tracks
        for (segment in segments) {
            val route = routeDao.getBySegmentId(segment.segmentId) ?: continue
            val points = PolylineCodec.decode(route.encodedPolyline)
            if (points.isEmpty()) continue

            sb.appendLine("""  <trk><name>Segment ${segment.segmentId}</name><trkseg>""")
            for ((lat, lng) in points) {
                sb.appendLine("""    <trkpt lat="${lat.stripIf(stripCoords)}" lon="${lng.stripIf(stripCoords)}"/>""")
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
            sb.appendLine("""  <wpt lat="${lat.stripIf(stripCoords)}" lon="${lng.stripIf(stripCoords)}"><name>${escapeXml(placeName)}</name><time>${java.time.Instant.ofEpochMilli(visit.arrivalAt)}</time></wpt>""")
        }

        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private suspend fun buildGeoJson(
        segments: List<com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity>,
        visits: List<com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity>,
        stripCoords: Boolean
    ): String {
        val features = mutableListOf<String>()

        // Transit segments as LineStrings
        for (segment in segments) {
            val route = routeDao.getBySegmentId(segment.segmentId) ?: continue
            val points = PolylineCodec.decode(route.encodedPolyline)
            if (points.isEmpty()) continue

            val coords = points.joinToString(",") { (lat, lng) ->
                "[${lng.stripIf(stripCoords)},${lat.stripIf(stripCoords)}]"
            }
            features.add("""{"type":"Feature","properties":{"segmentId":${segment.segmentId},"transportMode":"${escapeJson(route.transportMode)}","distanceM":${route.totalDistanceM}},"geometry":{"type":"LineString","coordinates":[$coords]}}""")
        }

        // Visits as Points
        for (visit in visits) {
            val lat = visit.centroidLat ?: continue
            val lng = visit.centroidLng ?: continue
            val placeName = if (visit.placeId != 0L) {
                placeDao.getById(visit.placeId)?.displayName()
            } else null
            features.add("""{"type":"Feature","properties":{"visitId":${visit.visitId},"placeName":${if (placeName != null) "\"${escapeJson(placeName)}\"" else "null"},"arrivalAt":${visit.arrivalAt},"departureAt":${visit.departureAt ?: "null"},"dwellMs":${visit.dwellMs ?: "null"}},"geometry":{"type":"Point","coordinates":[${lng.stripIf(stripCoords)},${lat.stripIf(stripCoords)}]}}""")
        }

        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
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
        visits: List<VisitEntity>,
        rawSamples: List<RawSampleWire>,
        stripCoords: Boolean
    ): String {
        // Collect every place referenced by a segment or visit so import can
        // restore the graph without dangling references.
        val placeIds = buildSet {
            segments.mapNotNullTo(this) { it.placeId }
            visits.mapTo(this) { it.placeId }
        }
        val places = placeIds.mapNotNull { placeDao.getById(it) }.map { it.toWire(stripCoords) }

        val segmentWires = segments.map { segment ->
            val route = routeDao.getBySegmentId(segment.segmentId)?.toWire(stripCoords)
            segment.toWire(route)
        }

        val visitWires = visits.map { it.toWire(stripCoords) }

        val payload = VoyagerJsonExport(
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            coordsStripped = stripCoords,
            places = places,
            segments = segmentWires,
            visits = visitWires,
            rawSamples = rawSamples
        )
        return json.encodeToString(VoyagerJsonExport.serializer(), payload)
    }

    private fun PlaceEntity.toWire(stripCoords: Boolean) = PlaceWire(
        placeId = placeId,
        centroidLat = centroidLat.stripIf(stripCoords),
        centroidLng = centroidLng.stripIf(stripCoords),
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

    private fun RouteEntity.toWire(stripCoords: Boolean) = RouteWire(
        encodedPolyline = encodedPolyline.stripPolylineIf(stripCoords),
        simplifiedPolyline = simplifiedPolyline?.stripPolylineIf(stripCoords),
        totalDistanceM = totalDistanceM,
        totalDurationMs = totalDurationMs,
        avgSpeedMps = avgSpeedMps,
        maxSpeedMps = maxSpeedMps,
        transportMode = transportMode,
        sampleCount = sampleCount,
        boundingBoxJson = boundingBoxJson
    )

    private fun VisitEntity.toWire(stripCoords: Boolean) = VisitWire(
        visitId = visitId,
        placeId = placeId,
        arrivalAt = arrivalAt,
        departureAt = departureAt,
        dwellMs = dwellMs,
        source = source,
        confidence = confidence,
        isUserCorrected = isUserCorrected,
        dayKey = dayKey,
        centroidLat = centroidLat?.stripIf(stripCoords),
        centroidLng = centroidLng?.stripIf(stripCoords)
    )

    private fun RawLocationSampleEntity.toWire(stripCoords: Boolean) = RawSampleWire(
        capturedAt = capturedAt,
        receivedAt = receivedAt,
        lat = lat.stripIf(stripCoords),
        lng = lng.stripIf(stripCoords),
        accuracyM = accuracyM,
        verticalAccuracyM = verticalAccuracyM,
        speedMps = speedMps,
        bearingDeg = bearingDeg,
        altitudeM = altitudeM,
        provider = provider,
        isMock = isMock,
        batteryPct = batteryPct,
        isCharging = isCharging,
        deviceIdleMode = deviceIdleMode,
        permissionSnapshot = permissionSnapshot,
        localTimeZone = localTimeZone,
        geohash = geohash
    )

    /** Inverse of [toWire] — rebuilds a raw sample under an imported tracking session. */
    private fun RawSampleWire.toEntity(sessionId: Long) = RawLocationSampleEntity(
        capturedAt = capturedAt,
        receivedAt = receivedAt,
        lat = lat,
        lng = lng,
        accuracyM = accuracyM,
        verticalAccuracyM = verticalAccuracyM,
        speedMps = speedMps,
        bearingDeg = bearingDeg,
        altitudeM = altitudeM,
        provider = provider,
        isMock = isMock,
        batteryPct = batteryPct,
        isCharging = isCharging,
        deviceIdleMode = deviceIdleMode,
        permissionSnapshot = permissionSnapshot,
        trackingSessionId = sessionId,
        localTimeZone = localTimeZone,
        geohash = geohash
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

    /**
     * Rounds a coordinate to ~2 decimal places (~1.1 km) when [strip] is set, so an
     * export can be shared without revealing a precise location. Backs the
     * `stripExactCoordinatesOnExport` setting.
     */
    private fun Double.stripIf(strip: Boolean): Double =
        if (strip) Math.round(this * 100.0) / 100.0 else this

    /** Decodes, rounds, and re-encodes a polyline so coordinate stripping reaches route geometry. */
    private fun String.stripPolylineIf(strip: Boolean): String {
        if (!strip || isEmpty()) return this
        val rounded = PolylineCodec.decode(this).map { (lat, lng) -> lat.stripIf(true) to lng.stripIf(true) }
        return PolylineCodec.encode(rounded)
    }

    /** Raw location samples captured within an inclusive [startDay]..[endDay] window. */
    private suspend fun rawSamplesForDays(
        startDay: String,
        endDay: String,
        stripCoords: Boolean
    ): List<RawSampleWire> {
        val zone = java.time.ZoneId.systemDefault()
        val startMs = java.time.LocalDate.parse(startDay)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = java.time.LocalDate.parse(endDay).plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return rawLocationSampleDao.getByTimeRange(startMs, endMs).map { it.toWire(stripCoords) }
    }

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
