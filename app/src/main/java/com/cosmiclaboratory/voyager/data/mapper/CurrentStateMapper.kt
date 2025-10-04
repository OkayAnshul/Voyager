package com.cosmiclaboratory.voyager.data.mapper

import com.cosmiclaboratory.voyager.data.database.entity.CurrentStateEntity
import com.cosmiclaboratory.voyager.domain.model.CurrentState
import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.model.Visit

/**
 * Mapper functions for CurrentState domain model
 */

/**
 * Convert CurrentStateEntity to CurrentState domain model
 */
fun CurrentStateEntity.toDomainModel(
    currentPlace: Place? = null,
    currentVisit: Visit? = null
): CurrentState {
    return CurrentState(
        id = id,
        currentPlace = currentPlace,
        currentVisit = currentVisit,
        lastLocationUpdate = lastLocationUpdate,
        isLocationTrackingActive = isLocationTrackingActive,
        trackingStartTime = trackingStartTime,
        currentSessionStartTime = currentSessionStartTime,
        currentPlaceEntryTime = currentPlaceEntryTime,
        totalLocationsToday = totalLocationsToday,
        totalPlacesVisitedToday = totalPlacesVisitedToday,
        totalTimeTrackedToday = totalTimeTrackedToday,
        lastUpdated = lastUpdated
    )
}

/**
 * Convert CurrentState domain model to CurrentStateEntity
 */
fun CurrentState.toEntity(): CurrentStateEntity {
    return CurrentStateEntity(
        id = id,
        currentPlaceId = currentPlace?.id,
        currentVisitId = currentVisit?.id,
        lastLocationUpdate = lastLocationUpdate,
        isLocationTrackingActive = isLocationTrackingActive,
        trackingStartTime = trackingStartTime,
        currentSessionStartTime = currentSessionStartTime,
        currentPlaceEntryTime = currentPlaceEntryTime,
        totalLocationsToday = totalLocationsToday,
        totalPlacesVisitedToday = totalPlacesVisitedToday,
        totalTimeTrackedToday = totalTimeTrackedToday,
        lastUpdated = lastUpdated
    )
}