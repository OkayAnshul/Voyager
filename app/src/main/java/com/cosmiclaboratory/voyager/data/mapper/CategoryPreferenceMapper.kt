package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.CategoryPreferenceEntity
import com.cosmiclaboratory.voyager.domain.model.CategoryPreference

fun CategoryPreferenceEntity.toDomainModel(): CategoryPreference {
    return CategoryPreference(
        id = id,
        category = category,
        preferenceScore = preferenceScore,
        correctionCount = correctionCount,
        acceptanceCount = acceptanceCount,
        rejectionCount = rejectionCount,
        isDisabled = isDisabled,
        lastUpdated = lastUpdated,
        timePatternWeight = timePatternWeight,
        durationPatternWeight = durationPatternWeight,
        frequencyPatternWeight = frequencyPatternWeight,
        notes = notes
    )
}

fun CategoryPreference.toEntity(): CategoryPreferenceEntity {
    return CategoryPreferenceEntity(
        id = id,
        category = category,
        preferenceScore = preferenceScore,
        correctionCount = correctionCount,
        acceptanceCount = acceptanceCount,
        rejectionCount = rejectionCount,
        isDisabled = isDisabled,
        lastUpdated = lastUpdated,
        timePatternWeight = timePatternWeight,
        durationPatternWeight = durationPatternWeight,
        frequencyPatternWeight = frequencyPatternWeight,
        notes = notes
    )
}

fun List<CategoryPreferenceEntity>.toDomainModels(): List<CategoryPreference> {
    return map { it.toDomainModel() }
}

fun List<CategoryPreference>.toEntities(): List<CategoryPreferenceEntity> {
    return map { it.toEntity() }
}
