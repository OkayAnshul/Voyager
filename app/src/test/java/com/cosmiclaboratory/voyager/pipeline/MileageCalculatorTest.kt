package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.MileageCalculator
import org.junit.Assert.*
import org.junit.Test

class MileageCalculatorTest {

    @Test
    fun `KM_PER_L 15 over 50km uses 3_33 litres`() {
        val fuel = MileageCalculator.fuelUsed(
            distanceKm = 50.0,
            efficiencyValue = 15.0,
            efficiencyUnit = MileageCalculator.EfficiencyUnit.KM_PER_L
        )
        assertEquals(3.333, fuel, 0.01)
    }

    @Test
    fun `L_PER_100KM 6_5 over 50km uses 3_25 litres`() {
        val fuel = MileageCalculator.fuelUsed(
            distanceKm = 50.0,
            efficiencyValue = 6.5,
            efficiencyUnit = MileageCalculator.EfficiencyUnit.L_PER_100KM
        )
        assertEquals(3.25, fuel, 0.001)
    }

    @Test
    fun `MPG_US round-trip matches expected L per km`() {
        // 30 MPG US ≈ 12.75 km/L → ~0.0784 L/km → 50km uses ~3.92 L
        val fuel = MileageCalculator.fuelUsed(
            distanceKm = 50.0,
            efficiencyValue = 30.0,
            efficiencyUnit = MileageCalculator.EfficiencyUnit.MPG_US
        )
        assertTrue("expected ~3.9L, got $fuel", fuel in 3.8..4.0)
    }

    @Test
    fun `cost in minor units rounds to nearest cent`() {
        // 3.33 L × ₹100 = ₹333 = 33300 paise
        val cost = MileageCalculator.costMinor(fuelUsed = 3.333, pricePerUnit = 100.0)
        assertEquals(33330L, cost)
    }

    @Test
    fun `petrol CO2 is 2_31 kg per litre`() {
        val co2 = MileageCalculator.co2Kg(fuelUsed = 10.0, fuelType = MileageCalculator.FuelType.PETROL)
        assertEquals(23.1, co2, 0.001)
    }

    @Test
    fun `diesel CO2 is 2_68 kg per litre`() {
        val co2 = MileageCalculator.co2Kg(fuelUsed = 10.0, fuelType = MileageCalculator.FuelType.DIESEL)
        assertEquals(26.8, co2, 0.001)
    }

    @Test
    fun `forSegment composes fuel cost and CO2 in one call`() {
        val trip = MileageCalculator.forSegment(
            distanceKm = 50.0,
            efficiencyValue = 15.0,
            efficiencyUnit = MileageCalculator.EfficiencyUnit.KM_PER_L,
            pricePerUnit = 100.0,
            fuelType = MileageCalculator.FuelType.PETROL
        )
        assertEquals(50.0, trip.distanceKm, 0.001)
        assertTrue("fuel positive", trip.fuelUsed > 0)
        assertTrue("cost positive", trip.costMinor > 0)
        assertTrue("CO2 positive", trip.co2Kg > 0)
    }

    @Test
    fun `efficiency must be positive`() {
        try {
            MileageCalculator.fuelUsed(50.0, 0.0, MileageCalculator.EfficiencyUnit.KM_PER_L)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
