package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.VisitReviewEntity
import com.cosmiclaboratory.voyager.domain.model.VisitReview

fun VisitReviewEntity.toDomainModel(): VisitReview {
    return VisitReview(
        id = id,
        visitId = visitId,
        placeId = placeId,
        placeName = placeName,
        entryTime = entryTime,
        exitTime = exitTime,
        duration = duration,
        confidence = confidence,
        reviewReason = reviewReason,
        status = status,
        alternativePlaceId = alternativePlaceId,
        alternativePlaceName = alternativePlaceName,
        userConfirmedPlaceId = userConfirmedPlaceId,
        reviewedAt = reviewedAt,
        notes = notes
    )
}

fun VisitReview.toEntity(): VisitReviewEntity {
    return VisitReviewEntity(
        id = id,
        visitId = visitId,
        placeId = placeId,
        placeName = placeName,
        entryTime = entryTime,
        exitTime = exitTime,
        duration = duration,
        confidence = confidence,
        reviewReason = reviewReason,
        status = status,
        alternativePlaceId = alternativePlaceId,
        alternativePlaceName = alternativePlaceName,
        userConfirmedPlaceId = userConfirmedPlaceId,
        reviewedAt = reviewedAt,
        notes = notes
    )
}

fun List<VisitReviewEntity>.toDomainModels(): List<VisitReview> {
    return map { it.toDomainModel() }
}

fun List<VisitReview>.toEntities(): List<VisitReviewEntity> {
    return map { it.toEntity() }
}
