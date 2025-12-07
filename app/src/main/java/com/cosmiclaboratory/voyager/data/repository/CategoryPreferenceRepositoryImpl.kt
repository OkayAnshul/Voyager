package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.CategoryPreferenceDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.domain.model.CategoryPreference
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.domain.repository.CategoryPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryPreferenceRepositoryImpl @Inject constructor(
    private val categoryPreferenceDao: CategoryPreferenceDao
) : CategoryPreferenceRepository {

    override fun getAllPreferences(): Flow<List<CategoryPreference>> {
        return categoryPreferenceDao.getAllPreferences().map { it.toDomainModels() }
    }

    override suspend fun getPreferenceForCategory(category: PlaceCategory): CategoryPreference? {
        return categoryPreferenceDao.getPreferenceForCategory(category)?.toDomainModel()
    }

    override fun getPreferenceForCategoryFlow(category: PlaceCategory): Flow<CategoryPreference?> {
        return categoryPreferenceDao.getPreferenceForCategoryFlow(category).map { it?.toDomainModel() }
    }

    override fun getDisabledCategories(): Flow<List<CategoryPreference>> {
        return categoryPreferenceDao.getDisabledCategories().map { it.toDomainModels() }
    }

    override fun getEnabledPreferences(): Flow<List<CategoryPreference>> {
        return categoryPreferenceDao.getEnabledPreferences().map { it.toDomainModels() }
    }

    override fun getPositivePreferences(): Flow<List<CategoryPreference>> {
        return categoryPreferenceDao.getPositivePreferences().map { it.toDomainModels() }
    }

    override fun getNegativePreferences(): Flow<List<CategoryPreference>> {
        return categoryPreferenceDao.getNegativePreferences().map { it.toDomainModels() }
    }

    override suspend fun insertPreference(preference: CategoryPreference): Long {
        return categoryPreferenceDao.insertPreference(preference.toEntity())
    }

    override suspend fun updatePreference(preference: CategoryPreference) {
        categoryPreferenceDao.updatePreference(preference.toEntity())
    }

    override suspend fun deletePreference(preference: CategoryPreference) {
        categoryPreferenceDao.deletePreference(preference.toEntity())
    }

    override suspend fun deletePreferenceForCategory(category: PlaceCategory) {
        categoryPreferenceDao.deletePreferenceForCategory(category)
    }

    override suspend fun setCategoryDisabled(category: PlaceCategory, disabled: Boolean) {
        categoryPreferenceDao.setCategoryDisabled(category, disabled)
    }

    override suspend fun adjustPreferenceScore(category: PlaceCategory, delta: Float) {
        categoryPreferenceDao.adjustPreferenceScore(category, delta)
    }

    override suspend fun incrementAcceptanceCount(category: PlaceCategory, now: LocalDateTime) {
        categoryPreferenceDao.incrementAcceptanceCount(category, now)
    }

    override suspend fun incrementRejectionCount(category: PlaceCategory, now: LocalDateTime) {
        categoryPreferenceDao.incrementRejectionCount(category, now)
    }

    override suspend fun incrementCorrectionCount(category: PlaceCategory, now: LocalDateTime) {
        categoryPreferenceDao.incrementCorrectionCount(category, now)
    }
}
