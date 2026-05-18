package com.cosmiclaboratory.voyager.data.imports

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.cosmiclaboratory.voyager.domain.model.ImportSummary
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import com.cosmiclaboratory.voyager.storage.database.VoyagerDatabase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports a Google Timeline / Location History export into Voyager — the migration
 * wedge for users leaving Google Timeline.
 *
 * Auto-detects the two semantic schemas (legacy Takeout `timelineObjects`, new
 * on-device `semanticSegments`) and maps place visits to [PlaceEntity]+[VisitEntity]
 * and activity segments to [MovementSegmentEntity]. The raw-GPS `Records.json` is
 * deliberately not supported — it carries no semantic structure — and is rejected
 * with guidance.
 *
 * Robustness: the JSON is decoded straight from the input stream (no giant in-memory
 * string), parsing is lenient and ignores unknown keys, and rows are inserted in
 * batched transactions so a failure rolls back only the current batch — earlier
 * batches stay committed as checkpoints.
 */
@Singleton
class GoogleTimelineImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VoyagerDatabase,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val movementSegmentDao: MovementSegmentDao,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Parsed, deduplicated entities ready for insertion. Place ids are assigned on insert. */
    internal data class ParsedTimeline(
        val places: List<ParsedPlace>,
        val visits: List<ParsedVisit>,
        val segments: List<MovementSegmentEntity>,
    )

    /** A unique place; [key] links visits to it before real ids exist. */
    internal data class ParsedPlace(val key: String, val entity: PlaceEntity)

    /** A visit whose [placeKey] resolves to a [ParsedPlace.key]. */
    internal data class ParsedVisit(val placeKey: String, val entity: VisitEntity)

    suspend fun import(uri: Uri): Result<ImportSummary> = runCatching {
        val parsed = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Could not open the selected file.")
            stream.use { parseAndMap(it) }
        }
        check(parsed.places.isNotEmpty() || parsed.segments.isNotEmpty()) {
            "No timeline data found. Export your Semantic Location History or the " +
                "Timeline.json from your phone — the raw Records.json is not supported."
        }
        insert(parsed)
    }

    /**
     * Decodes a Google export stream and maps it to Voyager entities. Pure apart from
     * the stream read — unit-testable with any [InputStream].
     */
    @OptIn(ExperimentalSerializationApi::class)
    internal fun parseAndMap(input: InputStream): ParsedTimeline {
        val root = json.decodeFromStream(GoogleExportRoot.serializer(), input)
        return when {
            !root.timelineObjects.isNullOrEmpty() -> mapLegacy(root.timelineObjects)
            !root.semanticSegments.isNullOrEmpty() -> mapNew(root.semanticSegments)
            else -> ParsedTimeline(emptyList(), emptyList(), emptyList())
        }
    }

    // ── Legacy Semantic Location History ──────────────────────────────

    private fun mapLegacy(objects: List<LegacyTimelineObject>): ParsedTimeline {
        val places = LinkedHashMap<String, PlaceEntity>()
        val visits = mutableListOf<ParsedVisit>()
        val segments = mutableListOf<MovementSegmentEntity>()

        for (obj in objects) {
            obj.placeVisit?.let { pv ->
                val loc = pv.location ?: return@let
                val lat = e7(loc.latitudeE7) ?: return@let
                val lng = e7(loc.longitudeE7) ?: return@let
                val start = parseTimestamp(pv.duration?.startTimestamp) ?: return@let
                val end = parseTimestamp(pv.duration?.endTimestamp)
                val key = loc.placeId ?: GeohashEncoder.encode(lat, lng, GEOHASH_PRECISION)
                accumulatePlace(places, key, lat, lng, start, loc.name, mapCategory(loc.semanticType))
                visits += parsedVisit(key, lat, lng, start, end)
            }
            obj.activitySegment?.let { seg ->
                val start = parseTimestamp(seg.duration?.startTimestamp) ?: return@let
                val end = parseTimestamp(seg.duration?.endTimestamp) ?: return@let
                segments += movementSegment(
                    type = mapActivity(seg.activityType),
                    start = start,
                    end = end,
                    distanceM = seg.distance?.toDouble() ?: 0.0
                )
            }
        }
        return ParsedTimeline(places.toParsedPlaces(), visits, segments)
    }

    // ── New on-device Timeline.json ───────────────────────────────────

    private fun mapNew(segmentsIn: List<NewSemanticSegment>): ParsedTimeline {
        val places = LinkedHashMap<String, PlaceEntity>()
        val visits = mutableListOf<ParsedVisit>()
        val segments = mutableListOf<MovementSegmentEntity>()

        for (s in segmentsIn) {
            val start = parseTimestamp(s.startTime) ?: continue
            val end = parseTimestamp(s.endTime)
            s.visit?.topCandidate?.let { c ->
                val (lat, lng) = parseLatLng(c.placeLocation?.latLng) ?: return@let
                val key = c.placeId ?: GeohashEncoder.encode(lat, lng, GEOHASH_PRECISION)
                accumulatePlace(places, key, lat, lng, start, null, mapCategory(c.semanticType))
                visits += parsedVisit(key, lat, lng, start, end)
            }
            s.activity?.let { a ->
                if (end == null) return@let
                segments += movementSegment(
                    type = mapActivity(a.topCandidate?.type),
                    start = start,
                    end = end,
                    distanceM = a.distanceMeters ?: 0.0
                )
            }
        }
        return ParsedTimeline(places.toParsedPlaces(), visits, segments)
    }

    // ── Entity construction ───────────────────────────────────────────

    private fun accumulatePlace(
        into: LinkedHashMap<String, PlaceEntity>,
        key: String,
        lat: Double,
        lng: Double,
        createdAt: Long,
        name: String?,
        category: String,
    ) {
        val existing = into[key]
        if (existing == null) {
            into[key] = PlaceEntity(
                centroidLat = lat,
                centroidLng = lng,
                geohash = GeohashEncoder.encode(lat, lng, GEOHASH_PRECISION),
                confidence = 0.7f,
                lifecycleStatus = "CONFIRMED",
                bestProviderName = name,
                bestProviderSource = if (name != null) PROVIDER else null,
                category = category,
                categoryConfidence = if (category != "UNKNOWN") 0.6f else 0.0f,
                createdAt = createdAt,
            )
        } else {
            // Keep the earliest first-seen time and fill a name if we lacked one.
            into[key] = existing.copy(
                createdAt = minOf(existing.createdAt, createdAt),
                bestProviderName = existing.bestProviderName ?: name,
                bestProviderSource = existing.bestProviderSource
                    ?: (if (name != null) PROVIDER else null),
            )
        }
    }

    private fun parsedVisit(
        placeKey: String,
        lat: Double,
        lng: Double,
        arrivalAt: Long,
        departureAt: Long?,
    ) = ParsedVisit(
        placeKey = placeKey,
        entity = VisitEntity(
            placeId = 0,
            arrivalAt = arrivalAt,
            departureAt = departureAt,
            dwellMs = departureAt?.let { (it - arrivalAt).coerceAtLeast(0) },
            source = "BATCH_DISCOVERY",
            confidence = 0.7f,
            dayKey = dayKey(arrivalAt),
            centroidLat = lat,
            centroidLng = lng,
        )
    )

    private fun movementSegment(type: String, start: Long, end: Long, distanceM: Double) =
        MovementSegmentEntity(
            segmentType = type,
            startAt = start,
            endAt = end,
            distanceM = distanceM,
            confidence = 0.6f,
            dayKey = dayKey(start),
        )

    private fun LinkedHashMap<String, PlaceEntity>.toParsedPlaces(): List<ParsedPlace> =
        map { (key, entity) -> ParsedPlace(key, entity) }

    // ── Insertion ─────────────────────────────────────────────────────

    private suspend fun insert(parsed: ParsedTimeline): ImportSummary {
        var placesImported = 0
        var visitsImported = 0
        var segmentsImported = 0
        var duplicates = 0

        // Places: one transaction — there are few, and every visit depends on them.
        val keyToId = HashMap<String, Long>(parsed.places.size)
        database.withTransaction {
            for (p in parsed.places) {
                keyToId[p.key] = placeDao.insert(p.entity)
                placesImported++
            }
        }

        // Visits and segments: batched transactions act as checkpoints — a failure
        // rolls back only the current batch, never the whole import.
        for (batch in parsed.visits.chunked(BATCH_SIZE)) {
            database.withTransaction {
                for (v in batch) {
                    val placeId = keyToId[v.placeKey] ?: continue
                    val arrival = v.entity.arrivalAt
                    val departure = v.entity.departureAt ?: arrival
                    if (visitDao.getOverlapping(v.entity.dayKey, arrival, departure).isNotEmpty()) {
                        duplicates++
                        continue
                    }
                    visitDao.insert(v.entity.copy(placeId = placeId))
                    visitsImported++
                }
            }
        }

        for (batch in parsed.segments.chunked(BATCH_SIZE)) {
            database.withTransaction {
                for (s in batch) {
                    if (movementSegmentDao.getOverlapping(s.dayKey, s.startAt, s.endAt).isNotEmpty()) {
                        duplicates++
                        continue
                    }
                    movementSegmentDao.insert(s)
                    segmentsImported++
                }
            }
        }

        return ImportSummary(
            segmentsImported = segmentsImported,
            visitsImported = visitsImported,
            placesImported = placesImported,
            duplicatesSkipped = duplicates,
        )
    }

    // ── Mapping helpers ───────────────────────────────────────────────

    /** Maps a Google activity label (either schema) to a Voyager [SegmentType] name. */
    internal fun mapActivity(raw: String?): String {
        val a = raw?.uppercase()?.replace(' ', '_') ?: return SegmentType.UNKNOWN_MOTION.name
        return when {
            a.contains("WALK") -> SegmentType.WALK.name
            a.contains("RUN") || a.contains("JOG") -> SegmentType.RUN.name
            a.contains("CYCL") || a.contains("BICYCL") || a.contains("BIKE") -> SegmentType.CYCLE.name
            a.contains("FLY") || a.contains("FLIGHT") -> SegmentType.FLIGHT.name
            a.contains("BUS") || a.contains("TRAIN") || a.contains("SUBWAY") ||
                a.contains("TRAM") || a.contains("FERRY") || a.contains("RAIL") ||
                a.contains("TRANSIT") -> SegmentType.TRANSIT.name
            a.contains("VEHICLE") || a.contains("DRIV") || a.contains("CAR") ||
                a.contains("MOTORCYCLE") -> SegmentType.DRIVE.name
            else -> SegmentType.UNKNOWN_MOTION.name
        }
    }

    /** Maps a Google semantic place type to a Voyager place category. */
    internal fun mapCategory(raw: String?): String {
        val s = raw?.uppercase() ?: return "UNKNOWN"
        return when {
            s.contains("HOME") -> "HOME"
            s.contains("WORK") -> "WORK"
            else -> "UNKNOWN"
        }
    }

    private fun e7(value: Long?): Double? = value?.let { it / 1e7 }

    /** Parses a Google `latLng` string such as `"12.345°, 98.765°"`. */
    internal fun parseLatLng(raw: String?): Pair<Double, Double>? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.replace("°", "").split(',')
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lng = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    /** Parses an ISO-8601 timestamp or epoch-millis string to epoch millis. */
    internal fun parseTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        raw.toLongOrNull()?.let { return it }
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (e: Exception) {
                Log.w(TAG, "Unparseable timestamp: $raw")
                null
            }
        }
    }

    private fun dayKey(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private companion object {
        const val TAG = "GoogleTimelineImporter"
        const val GEOHASH_PRECISION = 7
        const val BATCH_SIZE = 200
        const val PROVIDER = "GOOGLE_TIMELINE"
    }
}
