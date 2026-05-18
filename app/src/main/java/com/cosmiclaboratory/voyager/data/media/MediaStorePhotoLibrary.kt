package com.cosmiclaboratory.voyager.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.cosmiclaboratory.voyager.domain.model.DevicePhoto
import com.cosmiclaboratory.voyager.domain.photo.PhotoLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PhotoLibrary] backed by the Android `MediaStore`.
 *
 * Photos never leave the device and are never copied into Voyager's database — the
 * Photo Day Story correlates them on the fly. Only image URIs and timestamps are read;
 * EXIF GPS is a best-effort extra that needs `ACCESS_MEDIA_LOCATION`.
 */
@Singleton
class MediaStorePhotoLibrary @Inject constructor(
    @ApplicationContext private val context: Context
) : PhotoLibrary {

    override fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    override suspend fun photosBetween(startMs: Long, endMs: Long): List<DevicePhoto> {
        if (!hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) { query(startMs, endMs) }
    }

    private fun query(startMs: Long, endMs: Long): List<DevicePhoto> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN
        )
        // DATE_TAKEN is epoch millis. Photos with no DATE_TAKEN (rare for camera shots)
        // are skipped — a story is built around when a photo was taken.
        val selection =
            "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?"
        val args = arrayOf(startMs.toString(), endMs.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} ASC"

        val photos = mutableListOf<DevicePhoto>()
        try {
            context.contentResolver.query(collection, projection, selection, args, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val takenAt = cursor.getLong(takenCol)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val (lat, lng) = readLocation(uri)
                        photos += DevicePhoto(uri.toString(), takenAt, lat, lng)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query failed", e)
        }
        return photos
    }

    /**
     * Best-effort EXIF GPS read. Needs `ACCESS_MEDIA_LOCATION`; on Android 10+ the
     * unredacted original must be requested explicitly. Any failure (permission
     * denied, no GPS tags, unreadable file) degrades silently to no location —
     * timestamp correlation still works without it.
     */
    private fun readLocation(uri: Uri): Pair<Double?, Double?> {
        return try {
            val readUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setRequireOriginal(uri)
            } else {
                uri
            }
            context.contentResolver.openInputStream(readUri)?.use { stream ->
                val latLng = ExifInterface(stream).latLong
                if (latLng != null) latLng[0] to latLng[1] else null to null
            } ?: (null to null)
        } catch (e: Exception) {
            null to null
        }
    }

    private companion object {
        const val TAG = "MediaStorePhotoLibrary"
    }
}
