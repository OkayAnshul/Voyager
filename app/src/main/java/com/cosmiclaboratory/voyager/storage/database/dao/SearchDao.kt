package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.SearchIndexEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SearchMetadataEntity

/**
 * A single FTS hit joined with its source metadata. Carries the index row's [dayKey] and
 * [placeDisplayName] so DAY matches can be resolved without a second lookup, and the
 * [relevanceBoost] used to rank results.
 */
data class SearchHit(
    val sourceTable: String,
    val sourceId: Long,
    val relevanceBoost: Float,
    val dayKey: String?,
    val placeDisplayName: String?,
)

@Dao
interface SearchDao {
    @Insert
    suspend fun insertSearchEntry(entry: SearchIndexEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: SearchMetadataEntity)

    @Query("SELECT rowid, * FROM search_index WHERE search_index MATCH :query")
    suspend fun search(query: String): List<SearchIndexEntity>

    @Query("SELECT * FROM search_metadata WHERE searchRowId = :rowId")
    suspend fun getMetadata(rowId: Long): SearchMetadataEntity?

    @Query("""
        SELECT m.* FROM search_metadata m
        INNER JOIN search_index i ON m.searchRowId = i.rowid
        WHERE search_index MATCH :query
    """)
    suspend fun searchWithMetadata(query: String): List<SearchMetadataEntity>

    /**
     * Full-text search returning both the source metadata and the index row's [SearchHit.dayKey] /
     * [SearchHit.placeDisplayName], ranked by relevance boost. Used by the search repository so
     * that DAY matches surface (date search) and results come back most-relevant first.
     */
    @Query("""
        SELECT m.sourceTable AS sourceTable, m.sourceId AS sourceId, m.relevanceBoost AS relevanceBoost,
               i.dayKey AS dayKey, i.placeDisplayName AS placeDisplayName
        FROM search_metadata m
        INNER JOIN search_index i ON m.searchRowId = i.rowid
        WHERE search_index MATCH :query
        ORDER BY m.relevanceBoost DESC
    """)
    suspend fun searchHits(query: String): List<SearchHit>

    @Query("DELETE FROM search_index")
    suspend fun clearIndex()

    @Query("DELETE FROM search_metadata")
    suspend fun clearMetadata()

    @Transaction
    suspend fun rebuildIndex(entries: List<Pair<SearchIndexEntity, SearchMetadataEntity>>) {
        clearIndex()
        clearMetadata()
        for ((entry, metadata) in entries) {
            val rowId = insertSearchEntry(entry)
            insertMetadata(metadata.copy(searchRowId = rowId))
        }
    }
}
