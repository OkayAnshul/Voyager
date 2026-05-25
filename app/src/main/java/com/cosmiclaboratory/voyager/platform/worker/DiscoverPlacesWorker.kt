package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.model.enums.GeocodingProviderId
import com.cosmiclaboratory.voyager.domain.model.enums.PlaceLifecycleStatus
import com.cosmiclaboratory.voyager.domain.model.enums.VisitSource
import com.cosmiclaboratory.voyager.domain.util.GeohashEncoder
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import com.cosmiclaboratory.voyager.storage.database.entity.PlaceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.*

/**
 * Runs HDBSCAN-like density clustering on unassigned visit centroids to discover new places.
 * Uniquely enqueued every 6 hours via WorkerScheduler.
 *
 * Rough-timeline mode: when the user granted only "Approximate" location, GPS samples
 * land on a ~1–3 km grid, so the precise 80 m cluster radius would scatter visits to one
 * real place across many tiny clusters (or merge unrelated places). In that mode the
 * worker clusters at a city scale instead, producing honest broad "area" places.
 */
@HiltWorker
class DiscoverPlacesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
    private val healthLogDao: HealthLogDao,
    private val enrichPlaceUseCase: com.cosmiclaboratory.voyager.domain.usecase.EnrichPlaceWithDetailsUseCase,
    private val settingsRepository: com.cosmiclaboratory.voyager.domain.repository.SettingsRepository,
    private val permissionMonitor: com.cosmiclaboratory.voyager.platform.coordinator.PermissionMonitor,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "discover_places"
        private const val CLUSTER_RADIUS_M = 80.0
        /** City-scale cluster radius used when only approximate location is granted. */
        private const val ROUGH_CLUSTER_RADIUS_M = 2_000.0
        private const val MIN_CLUSTER_POINTS = 3
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Cluster radius for the current permission grant — exposed for unit testing. */
        fun clusterRadiusFor(roughMode: Boolean): Double =
            if (roughMode) ROUGH_CLUSTER_RADIUS_M else CLUSTER_RADIUS_M

        /** Confidence floor for a place whose centroid geocodes to a named OSM POI. */
        const val POI_PRIOR_CONFIDENCE = 0.85f

        /**
         * Confidence for a newly-discovered place. A centroid that geocodes to a
         * named OSM POI (Overpass) is strong evidence the place is real, so it lifts
         * confidence to at least [POI_PRIOR_CONFIDENCE] — except in rough/approximate
         * mode, where the centroid is too coarse to trust a POI match (so it stays
         * capped). Exposed for unit testing.
         */
        fun resolvePlaceConfidence(
            baseConfidence: Float,
            isPoiPrior: Boolean,
            roughMode: Boolean
        ): Float = when {
            roughMode -> baseConfidence.coerceAtMost(0.5f)
            isPoiPrior -> maxOf(baseConfidence, POI_PRIOR_CONFIDENCE)
            else -> baseConfidence
        }
    }

    /** Set once per run from the live permission snapshot; drives the cluster radius. */
    private var roughMode = false
    private var activeClusterRadiusM = CLUSTER_RADIUS_M

    override suspend fun doWork(): Result {
        val settings = settingsRepository.observeSettings().value
        // Respect the user's auto-discovery toggle — skip entirely when disabled.
        if (!settings.autoDiscoveryEnabled) {
            logCompletion("Auto-discovery disabled in settings — skipped")
            return Result.success()
        }
        roughMode = permissionMonitor.snapshot.value.isApproximateLocationOnly
        activeClusterRadiusM = clusterRadiusFor(roughMode)
        return try {
            val unassignedVisits = collectUnassignedVisitCentroids()
            if (unassignedVisits.isEmpty()) {
                logCompletion("No unassigned visits to cluster")
                return Result.success()
            }

            val clusters = densityCluster(unassignedVisits)
            var placesCreated = 0

            for (cluster in clusters) {
                val centroidLat = cluster.map { it.lat }.average()
                val centroidLng = cluster.map { it.lng }.average()
                val geohash = GeohashEncoder.encode(centroidLat, centroidLng)

                // Check for existing nearby place to avoid duplicates
                val existingPlaces = placeDao.getByGeohashPrefix(geohash.take(5))
                val nearbyPlace = existingPlaces.firstOrNull { existing ->
                    haversineM(centroidLat, centroidLng, existing.centroidLat, existing.centroidLng) < activeClusterRadiusM
                }

                if (nearbyPlace != null) {
                    // Assign visits to existing place instead of creating a duplicate
                    for (point in cluster) {
                        assignVisitToPlace(point.visitId, nearbyPlace.placeId)
                    }
                } else {
                    // Reverse geocode for a name + provenance — skipped when
                    // auto-geocoding is off (place keeps its coordinate name).
                    val enrichment = if (settings.autoGeocodeNewPlaces) {
                        try {
                            enrichPlaceUseCase.enrichWithSource(centroidLat, centroidLng)
                        } catch (_: Exception) { null }
                    } else null
                    val displayName = enrichment?.displayName
                    // POI prior: the centroid resolved to a named OSM POI — strong
                    // evidence this is a real place, so confidence is lifted below.
                    val isPoiPrior = enrichment?.providerSource == GeocodingProviderId.OVERPASS.name

                    // Coarse-location places are broad areas — never let one look
                    // as trustworthy as a precisely-clustered place.
                    val baseConfidence = (cluster.size.coerceAtMost(10) / 10f).coerceAtLeast(0.3f)
                    val placeId = placeDao.insert(
                        PlaceEntity(
                            centroidLat = centroidLat,
                            centroidLng = centroidLng,
                            radiusM = computeClusterRadius(cluster, centroidLat, centroidLng),
                            geohash = geohash,
                            confidence = resolvePlaceConfidence(baseConfidence, isPoiPrior, roughMode),
                            lifecycleStatus = PlaceLifecycleStatus.CANDIDATE.name,
                            createdAt = System.currentTimeMillis(),
                            lastVisitedAt = cluster.maxOf { it.arrivalAt },
                            bestProviderName = displayName,
                            bestProviderSource = when {
                                isPoiPrior -> "OVERPASS"
                                displayName != null -> "GEOCODE"
                                else -> null
                            },
                        )
                    )
                    for (point in cluster) {
                        assignVisitToPlace(point.visitId, placeId)
                    }
                    placesCreated++
                }
            }

            val modeNote = if (roughMode) " (rough/city-level mode)" else ""
            logCompletion("Clustered ${unassignedVisits.size} visits, created $placesCreated new places$modeNote")
            Result.success()
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private suspend fun collectUnassignedVisitCentroids(): List<VisitCentroid> {
        val allPlaces = placeDao.getAllActive().associateBy { it.placeId }
        val now = System.currentTimeMillis()
        val recentVisits = mutableListOf<VisitEntity>()

        for (daysAgo in 0..30) {
            val dayMs = now - daysAgo.toLong() * 24 * 60 * 60 * 1000
            val dayKey = millisToDayKey(dayMs)
            recentVisits.addAll(visitDao.getByDayKey(dayKey))
        }

        return recentVisits.filter { visit ->
            visit.placeId == 0L || !allPlaces.containsKey(visit.placeId)
        }.mapNotNull { visit ->
            val lat = visit.centroidLat
            val lng = visit.centroidLng
            if (lat != null && lng != null) {
                VisitCentroid(
                    visitId = visit.visitId,
                    lat = lat,
                    lng = lng,
                    arrivalAt = visit.arrivalAt
                )
            } else null
        }
    }

    private suspend fun assignVisitToPlace(visitId: Long, placeId: Long) {
        val visit = visitDao.getById(visitId) ?: return
        visitDao.update(visit.copy(placeId = placeId))
        placeDao.updateLastVisitedAt(placeId, visit.arrivalAt)
    }

    private fun densityCluster(points: List<VisitCentroid>): List<List<VisitCentroid>> {
        if (points.isEmpty()) return emptyList()

        val visited = BooleanArray(points.size)
        val clusters = mutableListOf<List<VisitCentroid>>()

        for (i in points.indices) {
            if (visited[i]) continue
            val neighbors = regionQuery(points, i)
            if (neighbors.size < MIN_CLUSTER_POINTS) continue
            val cluster = mutableListOf<VisitCentroid>()
            expandCluster(points, visited, i, neighbors.toMutableList(), cluster)
            clusters.add(cluster)
        }
        return clusters
    }

    private fun expandCluster(
        points: List<VisitCentroid>,
        visited: BooleanArray,
        pointIdx: Int,
        neighbors: MutableList<Int>,
        cluster: MutableList<VisitCentroid>,
    ) {
        visited[pointIdx] = true
        cluster.add(points[pointIdx])

        var i = 0
        while (i < neighbors.size) {
            val nIdx = neighbors[i]
            if (!visited[nIdx]) {
                visited[nIdx] = true
                cluster.add(points[nIdx])
                val newNeighbors = regionQuery(points, nIdx)
                if (newNeighbors.size >= MIN_CLUSTER_POINTS) {
                    for (nn in newNeighbors) {
                        if (nn !in neighbors) neighbors.add(nn)
                    }
                }
            }
            i++
        }
    }

    private fun regionQuery(points: List<VisitCentroid>, idx: Int): List<Int> {
        val center = points[idx]
        return points.indices.filter { i ->
            i != idx && haversineM(center.lat, center.lng, points[i].lat, points[i].lng) <= activeClusterRadiusM
        }
    }

    private fun computeClusterRadius(cluster: List<VisitCentroid>, centLat: Double, centLng: Double): Float {
        val maxDist = cluster.maxOfOrNull { haversineM(centLat, centLng, it.lat, it.lng) } ?: 80.0
        // Rough mode allows a far wider place footprint so coarse visits still match.
        val upperBound = if (roughMode) 3_000f else 500f
        return maxDist.toFloat().coerceIn(30f, upperBound)
    }

    private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    private fun millisToDayKey(ms: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    private suspend fun logCompletion(details: String) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","details":"$details"}""",
            )
        )
    }

    private suspend fun logFailure(e: Exception) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HealthEventTypeWorkerFailure,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","error":"${e.message?.take(200)}"}""",
            )
        )
    }

    private data class VisitCentroid(
        val visitId: Long,
        val lat: Double,
        val lng: Double,
        val arrivalAt: Long,
    )
}
