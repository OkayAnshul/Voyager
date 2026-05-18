package com.cosmiclaboratory.voyager.domain.photo

import com.cosmiclaboratory.voyager.domain.model.DevicePhoto

/**
 * Reads photos from the device's media library.
 *
 * Implemented by `MediaStorePhotoLibrary`; abstracted here so
 * [com.cosmiclaboratory.voyager.domain.usecase.BuildDayStoryUseCase] can be unit-tested
 * with a fake and without Android.
 */
interface PhotoLibrary {

    /**
     * Photos taken in `[startMs, endMs)`, ordered by capture time ascending.
     *
     * Returns an empty list when the media permission is not granted — callers must
     * consult [hasPermission] to tell "no photos" apart from "no access".
     */
    suspend fun photosBetween(startMs: Long, endMs: Long): List<DevicePhoto>

    /** Whether `READ_MEDIA_IMAGES` (or the legacy storage permission) is currently granted. */
    fun hasPermission(): Boolean
}
