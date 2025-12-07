package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.CategoryPreference
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface CategoryPreferenceRepository {

    fun getAllPreferences(): Flow<List<CategoryPreference>>

    suspend fun getPreferenceForCategory(category: PlaceCategory): CategoryPreference?

    fun getPreferenceForCategoryFlow(category: PlaceCategory): Flow<CategoryPreference?>

    fun getDisabledCategories(): Flow<List<CategoryPreference>>

    fun getEnabledPreferences(): Flow<List<CategoryPreference>>

    fun getPositivePreferences(): Flow<List<CategoryPreference>>

    fun getNegativePreferences(): Flow<List<CategoryPreference>>

    suspend fun insertPreference(preference: CategoryPreference): Long

    suspend fun updatePreference(preference: CategoryPreference)

    suspend fun deletePreference(preference: CategoryPreference)

    suspend fun deletePreferenceForCategory(category: PlaceCategory)

    suspend fun setCategoryDisabled(category: PlaceCategory, disabled: Boolean)

    suspend fun adjustPreferenceScore(category: PlaceCategory, delta: Float)

    suspend fun incrementAcceptanceCount(category: PlaceCategory, now: LocalDateTime)

    suspend fun incrementRejectionCount(category: PlaceCategory, now: LocalDateTime)

    suspend fun incrementCorrectionCount(category: PlaceCategory, now: LocalDateTime)
}
