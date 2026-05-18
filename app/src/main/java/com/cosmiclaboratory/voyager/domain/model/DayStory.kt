package com.cosmiclaboratory.voyager.domain.model

/**
 * A single photo from the device's media library.
 *
 * The [uri] is kept as a String so the domain layer stays free of `android.net.Uri`
 * and [com.cosmiclaboratory.voyager.domain.usecase.BuildDayStoryUseCase] is unit-testable
 * without Robolectric. The presentation layer parses it back into a `Uri`.
 *
 * [lat]/[lng] come from EXIF GPS metadata and are present only when the photo was
 * geotagged AND the `ACCESS_MEDIA_LOCATION` permission was granted.
 */
data class DevicePhoto(
    val uri: String,
    val takenAt: Long,
    val lat: Double? = null,
    val lng: Double? = null
) {
    val hasLocation: Boolean get() = lat != null && lng != null
}

/**
 * One day's photos correlated to the places visited that day — the Photo Day Story.
 *
 * [places] is chronological by arrival; [unplacedPhotos] holds photos taken on the day
 * but inside no visit window (in transit, or a place never detected).
 */
data class DayStory(
    val dayKey: String,
    val places: List<DayStoryPlace>,
    val unplacedPhotos: List<DevicePhoto>,
    val totalPhotoCount: Int
) {
    val isEmpty: Boolean get() = totalPhotoCount == 0

    companion object {
        fun empty(dayKey: String) = DayStory(dayKey, emptyList(), emptyList(), 0)
    }
}

/** A place visited on the day, with the photos taken while the user was there. */
data class DayStoryPlace(
    val placeId: Long,
    val displayName: String,
    val emoji: String?,
    val arrivalAt: Long,
    val departureAt: Long?,
    val photos: List<DevicePhoto>
)
