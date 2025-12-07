package com.cosmiclaboratory.voyager.data.database.dao

import androidx.room.*
import com.cosmiclaboratory.voyager.data.database.entity.CategoryPreferenceEntity
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryPreferenceDao {

    @Query("SELECT * FROM category_preferences ORDER BY preferenceScore DESC")
    fun getAllPreferences(): Flow<List<CategoryPreferenceEntity>>

    @Query("SELECT * FROM category_preferences WHERE category = :category")
    suspend fun getPreferenceForCategory(category: PlaceCategory): CategoryPreferenceEntity?

    @Query("SELECT * FROM category_preferences WHERE category = :category")
    fun getPreferenceForCategoryFlow(category: PlaceCategory): Flow<CategoryPreferenceEntity?>

    @Query("SELECT * FROM category_preferences WHERE isDisabled = 1")
    fun getDisabledCategories(): Flow<List<CategoryPreferenceEntity>>

    @Query("SELECT * FROM category_preferences WHERE isDisabled = 0 ORDER BY preferenceScore DESC")
    fun getEnabledPreferences(): Flow<List<CategoryPreferenceEntity>>

    @Query("SELECT * FROM category_preferences WHERE preferenceScore > 0 ORDER BY preferenceScore DESC")
    fun getPositivePreferences(): Flow<List<CategoryPreferenceEntity>>

    @Query("SELECT * FROM category_preferences WHERE preferenceScore < 0 ORDER BY preferenceScore ASC")
    fun getNegativePreferences(): Flow<List<CategoryPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: CategoryPreferenceEntity): Long

    @Update
    suspend fun updatePreference(preference: CategoryPreferenceEntity)

    @Delete
    suspend fun deletePreference(preference: CategoryPreferenceEntity)

    @Query("DELETE FROM category_preferences WHERE category = :category")
    suspend fun deletePreferenceForCategory(category: PlaceCategory)

    @Query("UPDATE category_preferences SET isDisabled = :disabled WHERE category = :category")
    suspend fun setCategoryDisabled(category: PlaceCategory, disabled: Boolean)

    @Query("UPDATE category_preferences SET preferenceScore = preferenceScore + :delta WHERE category = :category")
    suspend fun adjustPreferenceScore(category: PlaceCategory, delta: Float)

    @Query("UPDATE category_preferences SET acceptanceCount = acceptanceCount + 1, lastUpdated = :now WHERE category = :category")
    suspend fun incrementAcceptanceCount(category: PlaceCategory, now: java.time.LocalDateTime)

    @Query("UPDATE category_preferences SET rejectionCount = rejectionCount + 1, lastUpdated = :now WHERE category = :category")
    suspend fun incrementRejectionCount(category: PlaceCategory, now: java.time.LocalDateTime)

    @Query("UPDATE category_preferences SET correctionCount = correctionCount + 1, lastUpdated = :now WHERE category = :category")
    suspend fun incrementCorrectionCount(category: PlaceCategory, now: java.time.LocalDateTime)
}
