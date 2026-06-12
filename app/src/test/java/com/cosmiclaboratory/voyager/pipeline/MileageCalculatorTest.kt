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

    @Test
    fun `MPG_UK converts on the imperial-gallon factor`() {
        // 30 MPG UK ≈ 10.62 km/L → ~0.0942 L/km → 50 km uses ~4.71 L (more than US, bigger gallon).
        val fuel = MileageCalculator.fuelUsed(
            distanceKm = 50.0,
            efficiencyValue = 30.0,
            efficiencyUnit = MileageCalculator.EfficiencyUnit.MPG_UK
        )
        assertEquals(4.71, fuel, 0.02)
    }

    @Test
    fun `equivalent efficiencies in different units agree on units-per-km`() {
        // 20 km/L is exactly 5 L/100km — both must yield 0.05 L/km.
        val a = MileageCalculator.toUnitsPerKm(20.0, MileageCalculator.EfficiencyUnit.KM_PER_L)
        val b = MileageCalculator.toUnitsPerKm(5.0, MileageCalculator.EfficiencyUnit.L_PER_100KM)
        assertEquals(0.05, a, 1e-9)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun `EV energy use comes from the kWh-per-100km unit`() {
        // 18 kWh/100km over 50 km = 9 kWh.
        val kwh = MileageCalculator.fuelUsed(
            distanceKm = 50.0,
            efficiencyValue = 18.0,
            efficiencyUnit = MileageCalculator.EfficiencyUnit.KWH_PER_100KM
        )
        assertEquals(9.0, kwh, 0.001)
    }

    @Test
    fun `CO2 factors for EV hybrid and CNG match the table`() {
        assertEquals(7.1, MileageCalculator.co2Kg(10.0, MileageCalculator.FuelType.EV), 0.001)
        assertEquals(18.5, MileageCalculator.co2Kg(10.0, MileageCalculator.FuelType.HYBRID), 0.001)
        assertEquals(18.1, MileageCalculator.co2Kg(10.0, MileageCalculator.FuelType.CNG), 0.001)
    }

    @Test
    fun `forSegment computes an EV trip end-to-end on the kWh path`() {
        // 100 km at 18 kWh/100km = 18 kWh; ₹8/kWh = ₹144 = 14400 paise; 18 × 0.71 = 12.78 kg CO2.
        val trip = MileageCalculator.forSegment(
            distanceKm = 100.0,
            efficiencyValue = 18.0,
            efficiencyUnit = MileageCalculator.EfficiencyUnit.KWH_PER_100KM,
            pricePerUnit = 8.0,
            fuelType = MileageCalculator.FuelType.EV
        )
        assertEquals(18.0, trip.fuelUsed, 0.001)
        assertEquals(14400L, trip.costMinor)
        assertEquals(12.78, trip.co2Kg, 0.001)
    }

    @Test
    fun `cost rejects a negative price`() {
        try {
            MileageCalculator.costMinor(fuelUsed = 3.0, pricePerUnit = -1.0)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
