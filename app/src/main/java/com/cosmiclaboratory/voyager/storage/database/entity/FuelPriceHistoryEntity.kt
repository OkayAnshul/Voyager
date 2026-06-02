package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Time-versioned fuel-price history per vehicle.
 *
 * A retrospective query for the price on a given timestamp scans this table
 * back to the most-recent row with `effectiveFrom <= timestamp`. Lets the
 * monthly mileage summary recompute accurately when fuel prices change.
 */
@Entity(
    tableName = "fuel_price_history",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["vehicleId"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId", "effectiveFrom"])]
)
data class FuelPriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val priceId: Long = 0,
    val vehicleId: Long,
    val pricePerUnit: Double,
    val currencyCode: String,
    val effectiveFrom: Long,
    /** MANUAL / IMPORTED / SCRAPED */
    val source: String = "MANUAL",
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null,
    val userId: String = ""
)
