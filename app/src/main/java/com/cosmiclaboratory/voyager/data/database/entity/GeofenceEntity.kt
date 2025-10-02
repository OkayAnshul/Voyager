package com.cosmiclaboratory.voyager.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "geofences",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["placeId"]),
        Index(value = ["isActive"]),
        Index(value = ["latitude", "longitude"])
    ]
)
data class GeofenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
    val isActive: Boolean = true,
    val enterAlert: Boolean = false,
    val exitAlert: Boolean = false,
    val placeId: Long? = null
)