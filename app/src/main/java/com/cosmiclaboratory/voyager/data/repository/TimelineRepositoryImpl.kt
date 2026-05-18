package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.*
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.model.enums.SegmentType
import com.cosmiclaboratory.voyager.domain.repository.TimelineRepository
import com.cosmiclaboratory.voyager.domain.usecase.TimelineReconciler
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.*
import com.cosmiclaboratory.voyager.storage.database.entity.displayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepositoryImpl @Inject constructor(
    private val movementSegmentDao: MovementSegmentDao,
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val routeDao: RouteDao,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
    private val geocodeCandidateDao: GeocodeCandidateDao,
    private val stateStore: TimelineStateStore,
    private val rawStepSampleDao: com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao,
    private val reconciler: TimelineReconciler,
    private val settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository,
    private val overpassApiService: com.cosmiclaboratory.voyager.data.api.OverpassApiService
) : TimelineRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val classScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private companion object {
        /** Movement segments below this confidence are hidden when the user
         *  disables low-confidence segments. */
        const val LOW_CONFIDENCE_THRESHOLD = 0.4f
    }

    // Sentinel for "queried but no result" since ConcurrentHashMap doesn't allow null values
    private val NO_POI = com.cosmiclaboratory.voyager.data.api.OverpassResult("", "", 0.0, 0.0, 0.0)
    // Cache Overpass results per place to avoid re-querying on timeline re-renders
    private val overpassCache = java.util.concurrent.ConcurrentHashMap<Long, com.cosmiclaboratory.voyager.data.api.OverpassResult>()

    /**
     * Build up to 3 geocode hints: nearby POI (if available) + stored candidates.
     * Excludes the primary display name to avoid duplication.
     */
    private suspend fun buildGeocodeHints(placeId: Long, primaryName: String?, lat: Double, lng: Double): List<GeocodeHint> {
        val hints = mutableListOf<GeocodeHint>()

        // Nearby POI hint from Overpass
        val cached = overpassCache.getOrPut(placeId) {
            try { overpassApiService.findNearbyPoi(lat, lng) ?: NO_POI } catch (_: Exception) { NO_POI }
        }
        val poi = if (cached === NO_POI) null else cached
        if (poi != null && poi.name != primaryName) {
            val typeLabel = poi.type.substringAfter("=", "place").replaceFirstChar { it.uppercase() }
            hints.add(GeocodeHint(name = "Near ${poi.name}", provider = typeLabel))
        }

        // Alternative geocode candidates
        val candidates = geocodeCandidateDao.getByPlaceId(placeId)
        candidates
            .filter { it.displayName != primaryName && it.displayName.isNotBlank() }
            .take(2)
            .mapTo(hints) { entity ->
                val providerLabel = when (entity.provider) {
                    "ANDROID_GEOCODER" -> "Android Geocoder"
                    "PHOTON" -> "Photon"
                    "NOMINATIM" -> "Nominatim"
                    "GOOGLE" -> "Google"
                    else -> entity.provider
                }
                GeocodeHint(name = entity.displayName, provider = providerLabel)
            }

        return hints.take(3)
    }

    private fun dayBoundsMs(dayKey: String): Pair<Long, Long> {
        val date = java.time.LocalDate.parse(dayKey)
        val zone = java.time.ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return startMs to endMs
    }

    override fun observeDay(dayKey: String): Flow<TimelineDay> {
        val (dayStartMs, dayEndMs) = dayBoundsMs(dayKey)
        return combine(
            movementSegmentDao.observeByDayKey(dayKey),
            rawStepSampleDao.observeSumStepsByTimeRange(dayStartMs, dayEndMs),
            settingsRepository.observeSettings()
        ) { segments, stepSum, settings ->
            val daySteps = stepSum ?: 0
            val timelineSegments = segments.map { segment ->
                val rawSegmentType = try { SegmentType.valueOf(segment.segmentType) } catch (_: Exception) { SegmentType.UNKNOWN_MOTION }
                // Map DWELL (Segmenter's stationary type) to VISIT for display
                val segmentType = if (rawSegmentType == SegmentType.DWELL) SegmentType.VISIT else rawSegmentType
                // For VISIT/DWELL segments, fall back to the visits table for place info
                val place = segment.placeId?.let { placeDao.getById(it) }
                    ?: if (segmentType == SegmentType.VISIT) {
                        visitDao.getVisitOverlappingWindow(segment.startAt, segment.endAt)
                            ?.takeIf { it.placeId > 0 }
                            ?.let { placeDao.getById(it.placeId) }
                    } else null
                // For unlinked visits, build a synthetic place from visit centroid
                val syntheticPlace = if (place == null && segmentType == SegmentType.VISIT) {
                    visitDao.getVisitOverlappingWindow(segment.startAt, segment.endAt)
                        ?.let { visit ->
                            val lat = visit.centroidLat ?: 0.0
                            val lng = visit.centroidLng ?: 0.0
                            if (lat != 0.0 || lng != 0.0) {
                                TimelinePlace(
                                    placeId = 0,
                                    displayName = "%.4f, %.4f".format(lat, lng),
                                    nameSource = "Coordinates",
                                    category = PlaceCategory.UNKNOWN,
                                    confidence = 0f,
                                    lat = lat,
                                    lng = lng
                                )
                            } else null
                        }
                } else null
                val route = segment.routeId?.let { routeDao.getById(it) }
                val evidence = segmentEvidenceDao.getBySegmentId(segment.segmentId)

                TimelineSegment(
                    segmentId = segment.segmentId,
                    type = segmentType,
                    startAt = segment.startAt,
                    endAt = segment.endAt,
                    durationMs = segment.endAt - segment.startAt,
                    distanceM = segment.distanceM,
                    confidence = segment.confidence,
                    evidence = evidence?.let { ev ->
                        EvidenceBlock(
                            sampleCount = ev.sampleCount,
                            avgSpeed = ev.avgSpeedMps,
                            maxSpeed = ev.maxSpeedMps,
                            stepCount = ev.stepCount,
                            headingConsistency = ev.headingConsistency,
                            activityVotes = ev.activityVotesJson?.let {
                                try { json.decodeFromString<Map<String, Int>>(it) } catch (_: Exception) { emptyMap() }
                            } ?: emptyMap(),
                            providerMix = ev.providerMixJson?.let {
                                try { json.decodeFromString<Map<String, Int>>(it) } catch (_: Exception) { emptyMap() }
                            } ?: emptyMap(),
                            explanation = null
                        )
                    },
                    place = place?.let { p ->
                        val displayName = p.displayName()
                        val visitCount = visitDao.countByPlaceId(p.placeId)
                        TimelinePlace(
                            placeId = p.placeId,
                            displayName = displayName,
                            nameSource = when {
                                p.userDisplayName != null -> "Custom name"
                                p.bestProviderName != null -> "via ${p.bestProviderSource ?: "provider"}"
                                else -> "Coordinates"
                            },
                            category = try { PlaceCategory.valueOf(p.category) } catch (_: Exception) { PlaceCategory.UNKNOWN },
                            confidence = p.confidence,
                            lat = p.centroidLat,
                            lng = p.centroidLng,
                            geocodeHints = buildGeocodeHints(p.placeId, displayName, p.centroidLat, p.centroidLng),
                            emoji = p.emoji,
                            visitCount = visitCount
                        )
                    } ?: syntheticPlace,
                    route = route?.let { r ->
                        TimelineRoute(
                            routeId = r.routeId,
                            encodedPolyline = r.encodedPolyline,
                            simplifiedPolyline = r.simplifiedPolyline,
                            totalDistanceM = r.totalDistanceM,
                            avgSpeedMps = r.avgSpeedMps,
                            transportMode = r.transportMode,
                            boundingBoxJson = r.boundingBoxJson
                        )
                    },
                    gapReason = segment.gapReason,
                    isUserCorrected = segment.isUserCorrected
                )
            }

            // Visualization filters — applied to the timeline view only (analytics
            // in observeRange intentionally sees the unfiltered data).
            val reconciled = reconciler.reconcile(timelineSegments, settings.unifyTravelSegments)
                .filter { seg ->
                    val isMovement = seg.type != SegmentType.VISIT && seg.type != SegmentType.GAP
                    when {
                        seg.type == SegmentType.GAP && !settings.showGapSegments -> false
                        isMovement && seg.durationMs < settings.minSegmentDurationMs -> false
                        isMovement && !settings.showLowConfidenceSegments &&
                            seg.confidence < LOW_CONFIDENCE_THRESHOLD -> false
                        else -> true
                    }
                }
            // Assign 1-indexed sequence numbers to VISIT segments
            var visitCounter = 0
            val reconciledSegments = reconciled.map { seg ->
                if (seg.type == SegmentType.VISIT) {
                    visitCounter++
                    seg.copy(sequenceNumber = visitCounter)
                } else seg
            }
            TimelineDay(
                dayKey = dayKey,
                segments = reconciledSegments,
                totalDistanceM = reconciledSegments
                    .filter { it.type != SegmentType.VISIT && it.type != SegmentType.GAP }
                    .sumOf { it.distanceM },
                totalSteps = daySteps,
                firstActivityAt = reconciledSegments.firstOrNull()?.startAt,
                lastActivityAt = reconciledSegments.lastOrNull()?.endAt
            )
        }
    }

    override fun observeRange(startDay: String, endDay: String): Flow<List<TimelineDay>> {
        return movementSegmentDao.observeAllDayKeys().map { dayKeys ->
            dayKeys.filter { it in startDay..endDay }
        }.flatMapLatest { dayKeys ->
            if (dayKeys.isEmpty()) flowOf(emptyList())
            else combine(dayKeys.map { observeDay(it) }) { it.toList() }
        }
    }

    override suspend fun rebuildDay(dayKey: String): Result<Unit> {
        // Rebuilding is done by recomputing from raw samples — deferred to worker
        return Result.success(Unit)
    }

    override suspend fun getSegmentDetails(segmentId: Long): TimelineSegment? {
        val segment = movementSegmentDao.getById(segmentId) ?: return null
        val place = segment.placeId?.let { placeDao.getById(it) }
        val route = segment.routeId?.let { routeDao.getById(it) }

        val evidence = segmentEvidenceDao.getBySegmentId(segmentId)

        val rawType = try { SegmentType.valueOf(segment.segmentType) } catch (_: Exception) { SegmentType.UNKNOWN_MOTION }
        val displayType = if (rawType == SegmentType.DWELL) SegmentType.VISIT else rawType

        // Synthetic place for unlinked visits
        val syntheticPlace = if (place == null && displayType == SegmentType.VISIT) {
            visitDao.getVisitOverlappingWindow(segment.startAt, segment.endAt)
                ?.let { visit ->
                    val lat = visit.centroidLat ?: 0.0
                    val lng = visit.centroidLng ?: 0.0
                    if (lat != 0.0 || lng != 0.0) {
                        TimelinePlace(
                            placeId = 0,
                            displayName = "%.4f, %.4f".format(lat, lng),
                            nameSource = "Coordinates",
                            category = PlaceCategory.UNKNOWN,
                            confidence = 0f,
                            lat = lat,
                            lng = lng
                        )
                    } else null
                }
        } else null

        return TimelineSegment(
            segmentId = segment.segmentId,
            type = displayType,
            startAt = segment.startAt,
            endAt = segment.endAt,
            durationMs = segment.endAt - segment.startAt,
            distanceM = segment.distanceM,
            confidence = segment.confidence,
            evidence = evidence?.let { ev ->
                EvidenceBlock(
                    sampleCount = ev.sampleCount,
                    avgSpeed = ev.avgSpeedMps,
                    maxSpeed = ev.maxSpeedMps,
                    stepCount = ev.stepCount,
                    headingConsistency = ev.headingConsistency,
                    activityVotes = ev.activityVotesJson?.let {
                        try { json.decodeFromString<Map<String, Int>>(it) } catch (_: Exception) { emptyMap() }
                    } ?: emptyMap(),
                    providerMix = ev.providerMixJson?.let {
                        try { json.decodeFromString<Map<String, Int>>(it) } catch (_: Exception) { emptyMap() }
                    } ?: emptyMap(),
                    explanation = null
                )
            },
            place = place?.let { p ->
                val displayName = p.displayName()
                TimelinePlace(
                    placeId = p.placeId,
                    displayName = displayName,
                    nameSource = when {
                        p.userDisplayName != null -> "Custom name"
                        p.bestProviderName != null -> "via ${p.bestProviderSource ?: "provider"}"
                        else -> "Coordinates"
                    },
                    category = try { PlaceCategory.valueOf(p.category) } catch (_: Exception) { PlaceCategory.UNKNOWN },
                    confidence = p.confidence,
                    lat = p.centroidLat,
                    lng = p.centroidLng,
                    geocodeHints = buildGeocodeHints(p.placeId, displayName, p.centroidLat, p.centroidLng)
                )
            } ?: syntheticPlace,
            route = route?.let { r ->
                TimelineRoute(r.routeId, r.encodedPolyline, r.simplifiedPolyline, r.totalDistanceM, r.avgSpeedMps, r.transportMode, r.boundingBoxJson)
            },
            gapReason = segment.gapReason,
            isUserCorrected = segment.isUserCorrected
        )
    }

    /** Emits today's dayKey and re-emits at midnight when the date changes. */
    private fun todayKeyFlow(): Flow<String> = flow {
        while (true) {
            val now = java.time.LocalDate.now()
            emit(now.toString())
            val midnight = now.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
            val msUntilMidnight = midnight.toInstant().toEpochMilli() - System.currentTimeMillis()
            delay(msUntilMidnight.coerceAtLeast(1000))
        }
    }

    private val _liveTimeline: StateFlow<LiveTimelineState> by lazy {
        todayKeyFlow().flatMapLatest { todayKey ->
            combine(
                stateStore.state,
                observeDay(todayKey),
                visitDao.observeActiveVisit(),
                stateStore.inProgressSegment
            ) { runtimeState, todayTimeline, activeVisitEntity, segmentSnapshot ->
                // Build a transient TimelineSegment from the Segmenter's in-memory buffer
                val inProgressSegment = segmentSnapshot?.let { snap ->
                    val snapType = try { SegmentType.valueOf(snap.segmentType) } catch (_: Exception) { SegmentType.UNKNOWN_MOTION }
                    TimelineSegment(
                        segmentId = -1,
                        type = if (snapType == SegmentType.DWELL) SegmentType.VISIT else snapType,
                        startAt = snap.startAt,
                        endAt = snap.endAt,
                        durationMs = snap.endAt - snap.startAt,
                        distanceM = snap.distanceM,
                        confidence = 0.7f,
                        evidence = null,
                        place = null,
                        route = null,
                        gapReason = null,
                        isUserCorrected = false
                    )
                }
                val activeVisit = activeVisitEntity?.let { entity ->
                    var place = if (entity.placeId > 0) placeDao.getById(entity.placeId) else null
                    // When placeId not yet linked, find nearest place by geohash
                    if (place == null) {
                        val lat = entity.centroidLat ?: 0.0
                        val lng = entity.centroidLng ?: 0.0
                        if (lat != 0.0 || lng != 0.0) {
                            val geohash = com.cosmiclaboratory.voyager.domain.util.GeohashEncoder.encode(lat, lng, 6)
                            place = placeDao.getByGeohashPrefix(geohash).minByOrNull {
                                val dLat = it.centroidLat - lat
                                val dLng = it.centroidLng - lng
                                dLat * dLat + dLng * dLng
                            }
                        }
                    }
                    ActiveVisitInfo(
                        visitId = entity.visitId,
                        placeName = place?.displayName()
                            ?: "%.4f, %.4f".format(entity.centroidLat ?: 0.0, entity.centroidLng ?: 0.0),
                        category = try { PlaceCategory.valueOf(place?.category ?: "UNKNOWN") }
                            catch (_: Exception) { PlaceCategory.UNKNOWN },
                        arrivalAt = entity.arrivalAt,
                        centroidLat = entity.centroidLat ?: place?.centroidLat ?: 0.0,
                        centroidLng = entity.centroidLng ?: place?.centroidLng ?: 0.0
                    )
                }
                LiveTimelineState(
                    currentDay = todayTimeline,
                    inProgressSegment = inProgressSegment,
                    isTracking = runtimeState.activeSessionId != null,
                    activeVisit = activeVisit,
                    pendingCandidate = runtimeState.pendingVisitCandidate
                )
            }
        }.stateIn(
            classScope,
            SharingStarted.Eagerly,
            LiveTimelineState(null, null, false)
        )
    }

    override fun observeLiveTimeline(): StateFlow<LiveTimelineState> = _liveTimeline

    override fun observeDayKeys(): Flow<List<String>> =
        movementSegmentDao.observeAllDayKeys().map { keys ->
            val today = java.time.LocalDate.now().toString()
            if (today !in keys) listOf(today) + keys else keys
        }
}
