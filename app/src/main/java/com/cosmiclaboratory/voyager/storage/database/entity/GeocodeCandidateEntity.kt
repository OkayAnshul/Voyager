package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "geocode_candidates",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["placeId"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["placeId", "rank"])
    ]
)
data class GeocodeCandidateEntity(
    @PrimaryKey(autoGenerate = true)
    val geocodeId: Long = 0,
    val placeId: Long,
    val provider: String, // ANDROID_GEOCODER/PHOTON/NOMINATIM/GOOGLE/CUSTOM
    val rank: Int,
    val language: String? = null,
    val displayName: String,
    val structuredPartsJson: String? = null,
    val confidence: Float = 0.5f,
    val licenseClass: String = "FREE", // FREE/ATTRIBUTION/PAID
    val cachedUntil: Long? = null,
    val fetchedAt: Long
)
