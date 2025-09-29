package com.cosmiclaboratory.voyager.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.domain.model.enums.PlaceLifecycleStatus
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages geofences for CONFIRMED places. Geofences use Wi-Fi/cell (not GPS)
 * for triggering, enabling instant visit detection at known places with minimal
 * battery cost. Android limits 100 geofences per app.
 */
@Singleton
class PlaceGeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val placeDao: PlaceDao
) {
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)
    private val activeGeofenceIds = mutableSetOf<String>()

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun syncGeofences() {
        val confirmedPlaces = placeDao.getFrequentPlaces(limit = 80)
            .filter { it.lifecycleStatus == PlaceLifecycleStatus.CONFIRMED.name }

        val desiredIds = confirmedPlaces.map { geofenceId(it.placeId) }.toSet()
        val toRemove = activeGeofenceIds - desiredIds
        val toAdd = confirmedPlaces.filter { geofenceId(it.placeId) !in activeGeofenceIds }

        if (toRemove.isNotEmpty()) {
            geofencingClient.removeGeofences(toRemove.toList())
            activeGeofenceIds.removeAll(toRemove)
        }

        if (toAdd.isNotEmpty()) {
            val geofences = toAdd.map { place ->
                Geofence.Builder()
                    .setRequestId(geofenceId(place.placeId))
                    .setCircularRegion(
                        place.centroidLat,
                        place.centroidLng,
                        (place.radiusM + BUFFER_M).coerceAtLeast(MIN_RADIUS_M)
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .build()
            }

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()

            geofencingClient.addGeofences(request, geofencePendingIntent)
            activeGeofenceIds.addAll(toAdd.map { geofenceId(it.placeId) })
        }
    }

    fun removeAllGeofences() {
        if (activeGeofenceIds.isNotEmpty()) {
            geofencingClient.removeGeofences(geofencePendingIntent)
            activeGeofenceIds.clear()
        }
    }

    companion object {
        private const val BUFFER_M = 50f
        private const val MIN_RADIUS_M = 100f
        private const val GEOFENCE_ID_PREFIX = "place_"

        fun geofenceId(placeId: Long): String = "$GEOFENCE_ID_PREFIX$placeId"

        fun parsePlaceId(geofenceId: String): Long? =
            geofenceId.removePrefix(GEOFENCE_ID_PREFIX).toLongOrNull()
    }
}
