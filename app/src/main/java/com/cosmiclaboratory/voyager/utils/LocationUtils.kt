package com.cosmiclaboratory.voyager.utils

import kotlin.math.*

object LocationUtils {
    
    private const val EARTH_RADIUS_KM = 6371.0
    
    data class Bounds(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double
    )
    
    /**
     * Calculate distance between two points using Haversine formula
     * @return distance in meters
     */
    fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_KM * c * 1000 // Convert to meters
    }
    
    /**
     * Calculate bounding box for a given point and radius
     */
    fun calculateBounds(
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): Bounds {
        val latRadian = Math.toRadians(latitude)
        val deltaLat = radiusKm / EARTH_RADIUS_KM
        val deltaLng = asin(sin(deltaLat) / cos(latRadian))
        
        return Bounds(
            minLat = Math.toDegrees(latRadian - deltaLat),
            maxLat = Math.toDegrees(latRadian + deltaLat),
            minLng = Math.toDegrees(Math.toRadians(longitude) - deltaLng),
            maxLng = Math.toDegrees(Math.toRadians(longitude) + deltaLng)
        )
    }
    
    /**
     * Check if a location is within a geofence
     */
    fun isWithinGeofence(
        currentLat: Double,
        currentLng: Double,
        geofenceLat: Double,
        geofenceLng: Double,
        radiusMeters: Double
    ): Boolean {
        val distance = calculateDistance(currentLat, currentLng, geofenceLat, geofenceLng)
        return distance <= radiusMeters
    }
    
    /**
     * Calculate bearing between two points
     * @return bearing in degrees (0-360)
     */
    fun calculateBearing(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = sin(dLng) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLng)
        
        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360) % 360
    }
    
    /**
     * Check if location has significant change (for filtering redundant updates)
     */
    fun hasSignificantChange(
        oldLat: Double,
        oldLng: Double,
        newLat: Double,
        newLng: Double,
        minDistanceMeters: Double = 10.0
    ): Boolean {
        val distance = calculateDistance(oldLat, oldLng, newLat, newLng)
        return distance >= minDistanceMeters
    }
    
    /**
     * Cluster nearby locations using DBSCAN algorithm
     */
    fun clusterLocations(
        locations: List<Pair<Double, Double>>,
        maxDistanceMeters: Double = 50.0,
        minPoints: Int = 3
    ): List<List<Pair<Double, Double>>> {
        if (locations.isEmpty()) return emptyList()
        
        val clusters = mutableListOf<MutableList<Pair<Double, Double>>>()
        val visited = BooleanArray(locations.size)
        val noise = BooleanArray(locations.size)
        
        for (i in locations.indices) {
            if (visited[i]) continue
            
            visited[i] = true
            val neighbors = getNeighbors(locations, i, maxDistanceMeters)
            
            if (neighbors.size < minPoints) {
                noise[i] = true
            } else {
                val cluster = mutableListOf<Pair<Double, Double>>()
                expandCluster(locations, i, neighbors, cluster, visited, maxDistanceMeters, minPoints)
                if (cluster.isNotEmpty()) {
                    clusters.add(cluster)
                }
            }
        }
        
        return clusters.filter { it.size >= minPoints }
    }
    
    private fun getNeighbors(
        locations: List<Pair<Double, Double>>,
        pointIndex: Int,
        maxDistanceMeters: Double
    ): MutableList<Int> {
        val neighbors = mutableListOf<Int>()
        val point = locations[pointIndex]
        
        for (i in locations.indices) {
            if (i == pointIndex) continue
            
            val distance = calculateDistance(
                point.first, point.second,
                locations[i].first, locations[i].second
            )
            
            if (distance <= maxDistanceMeters) {
                neighbors.add(i)
            }
        }
        
        return neighbors
    }
    
    private fun expandCluster(
        locations: List<Pair<Double, Double>>,
        pointIndex: Int,
        neighbors: MutableList<Int>,
        cluster: MutableList<Pair<Double, Double>>,
        visited: BooleanArray,
        maxDistanceMeters: Double,
        minPoints: Int
    ) {
        cluster.add(locations[pointIndex])
        
        var i = 0
        while (i < neighbors.size) {
            val neighborIndex = neighbors[i]
            
            if (!visited[neighborIndex]) {
                visited[neighborIndex] = true
                val neighborNeighbors = getNeighbors(locations, neighborIndex, maxDistanceMeters)
                
                if (neighborNeighbors.size >= minPoints) {
                    neighbors.addAll(neighborNeighbors.filter { it !in neighbors })
                }
            }
            
            if (cluster.none { it == locations[neighborIndex] }) {
                cluster.add(locations[neighborIndex])
            }
            
            i++
        }
    }
}