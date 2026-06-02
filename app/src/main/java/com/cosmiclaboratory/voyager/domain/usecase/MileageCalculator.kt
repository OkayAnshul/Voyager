package com.cosmiclaboratory.voyager.domain.usecase

/**
 * Pure mileage / fuel / CO2 math. Lives in `domain/` because every input is
 * a typed primitive — no DAOs, no Android types. Easy to test in isolation
 * and easy to call from a worker, the dashboard, or an exporter.
 *
 * Efficiency units are normalised to litres-per-kilometre internally so the
 * downstream summing math doesn't have to branch on the user's display
 * preference. CO2 emission factors are conservative averages — accurate
 * enough for a personal-comparison view, not a regulatory submission.
 */
object MileageCalculator {

    enum class FuelType { PETROL, DIESEL, EV, HYBRID, CNG }
    enum class EfficiencyUnit { KM_PER_L, L_PER_100KM, MPG_US, MPG_UK, KWH_PER_100KM }

    /** kg CO2 per litre (or per kWh for EV) — single source of truth. */
    private val emissionFactor = mapOf(
        FuelType.PETROL to 2.31,
        FuelType.DIESEL to 2.68,
        FuelType.EV to 0.71,     // kg CO2 / kWh — depends on grid mix; user-overridable later
        FuelType.HYBRID to 1.85, // weighted blend; overridable later
        FuelType.CNG to 1.81,
    )

    /** Convert any supported efficiency unit to L (or kWh for EV) per km. */
    fun toUnitsPerKm(value: Double, unit: EfficiencyUnit): Double = when (unit) {
        EfficiencyUnit.KM_PER_L -> 1.0 / value
        EfficiencyUnit.L_PER_100KM -> value / 100.0
        EfficiencyUnit.MPG_US -> 1.0 / (value * 0.4251) // miles/gallon × 0.4251 = km/L
        EfficiencyUnit.MPG_UK -> 1.0 / (value * 0.3540)
        EfficiencyUnit.KWH_PER_100KM -> value / 100.0
    }

    /**
     * Fuel (or kWh) consumed for [distanceKm] at the given vehicle efficiency.
     * Pure arithmetic — no rounding.
     */
    fun fuelUsed(distanceKm: Double, efficiencyValue: Double, efficiencyUnit: EfficiencyUnit): Double {
        require(efficiencyValue > 0.0) { "efficiency must be positive" }
        return distanceKm * toUnitsPerKm(efficiencyValue, efficiencyUnit)
    }

    /** Monetary cost in minor units (cents/paise) — kept integer for safe summing. */
    fun costMinor(fuelUsed: Double, pricePerUnit: Double): Long {
        require(pricePerUnit >= 0.0) { "price must be non-negative" }
        return Math.round(fuelUsed * pricePerUnit * 100.0)
    }

    fun co2Kg(fuelUsed: Double, fuelType: FuelType): Double =
        fuelUsed * (emissionFactor[fuelType] ?: 0.0)

    /**
     * Convenience: compute fuel + cost + CO2 from raw inputs in one call.
     * Useful in the summary worker where every DRIVE segment runs through here.
     */
    data class Trip(
        val distanceKm: Double,
        val fuelUsed: Double,
        val costMinor: Long,
        val co2Kg: Double,
    )

    fun forSegment(
        distanceKm: Double,
        efficiencyValue: Double,
        efficiencyUnit: EfficiencyUnit,
        pricePerUnit: Double,
        fuelType: FuelType,
    ): Trip {
        val fuel = fuelUsed(distanceKm, efficiencyValue, efficiencyUnit)
        return Trip(
            distanceKm = distanceKm,
            fuelUsed = fuel,
            costMinor = costMinor(fuel, pricePerUnit),
            co2Kg = co2Kg(fuel, fuelType)
        )
    }
}
