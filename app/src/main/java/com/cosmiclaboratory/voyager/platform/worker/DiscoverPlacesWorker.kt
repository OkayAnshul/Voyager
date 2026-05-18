package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "discover_places"
        private const val CLUSTER_RADIUS_M = 80.0
        private const val MIN_CLUSTER_POINTS = 3
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    override suspend fun doWork(): Result {
        // Respect the user's auto-discovery toggle — skip entirely when disabled.
        if (!settingsRepository.observeSettings().value.autoDiscoveryEnabled) {
            logCompletion("Auto-discovery disabled in settings — skipped")
            return Result.success()
        }
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
                    haversineM(centroidLat, centroidLng, existing.centroidLat, existing.centroidLng) < CLUSTER_RADIUS_M
                }

                if (nearbyPlace != null) {
                    // Assign visits to existing place instead of creating a duplicate
                    for (point in cluster) {
                        assignVisitToPlace(point.visitId, nearbyPlace.placeId)
                    }
                } else {
                    // Reverse geocode to get a name for the new place
                    val displayName = try {
                        enrichPlaceUseCase(centroidLat, centroidLng)
                    } catch (_: Exception) { null }

                    val placeId = placeDao.insert(
                        PlaceEntity(
                            centroidLat = centroidLat,
                            centroidLng = centroidLng,
                            radiusM = computeClusterRadius(cluster, centroidLat, centroidLng),
                            geohash = geohash,
                            confidence = (cluster.size.coerceAtMost(10) / 10f).coerceAtLeast(0.3f),
                            lifecycleStatus = PlaceLifecycleStatus.CANDIDATE.name,
                            createdAt = System.currentTimeMillis(),
                            lastVisitedAt = cluster.maxOf { it.arrivalAt },
                            bestProviderName = displayName,
                            bestProviderSource = if (displayName != null) "GEOCODE" else null,
                        )
                    )
                    for (point in cluster) {
                        assignVisitToPlace(point.visitId, placeId)
                    }
                    placesCreated++
                }
            }

            logCompletion("Clustered ${unassignedVisits.size} visits, created $placesCreated new places")
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
            i != idx && haversineM(center.lat, center.lng, points[i].lat, points[i].lng) <= CLUSTER_RADIUS_M
        }
    }

    private fun computeClusterRadius(cluster: List<VisitCentroid>, centLat: Double, centLng: Double): Float {
        val maxDist = cluster.maxOfOrNull { haversineM(centLat, centLng, it.lat, it.lng) } ?: 80.0
        return maxDist.toFloat().coerceIn(30f, 500f)
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
