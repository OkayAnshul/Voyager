package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cosmiclaboratory.voyager.data.database.entity.GeocodingCacheEntity
import java.time.Instant

/**
 * Data Access Object for geocoding cache
 *
 * Cache Strategy:
 * - Insert with REPLACE strategy (updates existing entries)
 * - Query by rounded coordinates for ~100m precision match
 * - Periodic cleanup of expired entries
 */
@Dao
interface GeocodingCacheDao {

    /**
     * Get cached address for coordinates
     * @param lat Latitude (should be rounded to 3 decimal places)
     * @param lng Longitude (should be rounded to 3 decimal places)
     * @return Cached address or null if not found
     */
    @Query("SELECT * FROM geocoding_cache WHERE latitude = :lat AND longitude = :lng LIMIT 1")
    suspend fun getAddress(lat: Double, lng: Double): GeocodingCacheEntity?

    /**
     * Insert or update cached address
     * Uses REPLACE strategy to update existing entries with same coordinates
     * @param cache Geocoding cache entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(cache: GeocodingCacheEntity)

    /**
     * Delete cache entries older than specified timestamp
     * @param expiryDate Instant before which entries should be deleted
     */
    @Query("DELETE FROM geocoding_cache WHERE cachedAt < :expiryDate")
    suspend fun deleteBefore(expiryDate: Instant)

    /**
     * Clear all expired cache entries
     * @param durationDays Number of days to keep cache (default 30)
     */
    suspend fun clearExpiredCache(durationDays: Int = 30) {
        val expiryDate = Instant.now().minusSeconds((durationDays * 24 * 3600).toLong())
        deleteBefore(expiryDate)
    }

    /**
     * Get total number of cached entries
     * Useful for diagnostics and monitoring
     */
    @Query("SELECT COUNT(*) FROM geocoding_cache")
    suspend fun getCacheSize(): Int

    /**
     * Clear all cache entries
     * Use with caution - forces re-geocoding of all places
     */
    @Query("DELETE FROM geocoding_cache")
    suspend fun clearAll()

    /**
     * Get cache statistics for monitoring
     * @return Map of stats (size, oldest entry, etc.)
     */
    @Query("""
        SELECT
            COUNT(*) as total,
            MIN(cachedAt) as oldest,
            MAX(cachedAt) as newest
        FROM geocoding_cache
    """)
    suspend fun getCacheStats(): CacheStats?
}

/**
 * Statistics about geocoding cache
 */
data class CacheStats(
    val total: Int,
    val oldest: Instant?,
    val newest: Instant?
)
