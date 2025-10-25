package com.cosmiclaboratory.voyager.storage.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.storage.database.entity.SearchIndexEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SearchMetadataEntity

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
