package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.UserCorrectionEntity
import com.cosmiclaboratory.voyager.domain.model.UserCorrection

fun UserCorrectionEntity.toDomainModel(): UserCorrection {
    return UserCorrection(
        id = id,
        placeId = placeId,
        correctionTime = correctionTime,
        correctionType = correctionType,
        oldValue = oldValue,
        newValue = newValue,
        confidence = confidence,
        locationCount = locationCount,
        visitCount = visitCount,
        wasAppliedToLearning = wasAppliedToLearning,
        similarCorrectionCount = similarCorrectionCount
    )
}

fun UserCorrection.toEntity(): UserCorrectionEntity {
    return UserCorrectionEntity(
        id = id,
        placeId = placeId,
        correctionTime = correctionTime,
        correctionType = correctionType,
        oldValue = oldValue,
        newValue = newValue,
        confidence = confidence,
        locationCount = locationCount,
        visitCount = visitCount,
        wasAppliedToLearning = wasAppliedToLearning,
        similarCorrectionCount = similarCorrectionCount
    )
}

fun List<UserCorrectionEntity>.toDomainModels(): List<UserCorrection> {
    return map { it.toDomainModel() }
}

fun List<UserCorrection>.toEntities(): List<UserCorrectionEntity> {
    return map { it.toEntity() }
}
