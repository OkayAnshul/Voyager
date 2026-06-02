package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pre-aggregated mileage / fuel-cost / CO2 rollup per vehicle per period.
 *
 * Period rows are wholesale-rebuilt by the periodic mileage worker — so the
 * dashboard reads a single indexed row per period instead of recomputing
 * from raw segments every time. Costs in minor units (cents/paise) so the
 * monetary math stays exact across totals.
 */
@Entity(
    tableName = "mileage_summary",
    indices = [
        Index(value = ["vehicleId", "period", "periodKey"], unique = true),
        Index(value = ["period", "periodKey"])
    ]
)
data class MileageSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val summaryId: Long = 0,
    val vehicleId: Long,
    /** DAY / WEEK / MONTH / YEAR */
    val period: String,
    /** YYYY-MM-DD for DAY/WEEK (week-start), YYYY-MM for MONTH, YYYY for YEAR */
    val periodKey: String,
    val totalDistanceKm: Double,
    val totalFuelL: Double,
    val totalCostMinor: Long,
    val currencyCode: String,
    val totalCo2Kg: Double,
    val tripCount: Int,
    val businessDistanceKm: Double = 0.0,
    val personalDistanceKm: Double = 0.0,
    val computedAt: Long
)
