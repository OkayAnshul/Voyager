package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-owned vehicle. Drives mileage / fuel-cost / CO2 calculations off
 * detected DRIVE segments (Wave 10 M1).
 *
 * Efficiency is stored in a canonical pair: a numeric value + a unit enum
 * string so the UI can render the user's preference (km/L vs MPG vs L/100km)
 * without re-deriving it. The calculator converts to L/km internally so the
 * downstream summary code does not have to branch on unit.
 *
 * Cloud-ready audit columns mirror the v3 syncable tables (inert today).
 */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val vehicleId: Long = 0,
    val name: String,
    /** PETROL / DIESEL / EV / HYBRID / CNG */
    val fuelType: String,
    val efficiencyValue: Double,
    /** KM_PER_L / L_PER_100KM / MPG_US / MPG_UK / KWH_PER_100KM */
    val efficiencyUnit: String,
    val tankCapacityL: Double? = null,
    /** Current price in the user's chosen unit. History is in `fuel_price_history`. */
    val currentFuelPricePerUnit: Double,
    val currencyCode: String,
    val createdAt: Long,
    val isDefault: Boolean = false,
    /** Optional hex tint (e.g. "#FF8800") for the UI. Null = use auto-derived color. */
    val color: String? = null,
    // Audit (cloud-ready)
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null,
    val userId: String = ""
)
