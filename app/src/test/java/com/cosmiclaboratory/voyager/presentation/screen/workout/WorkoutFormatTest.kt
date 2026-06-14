package com.cosmiclaboratory.voyager.presentation.screen.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutFormatTest {

    @Test
    fun `distance is kilometres to two decimals`() {
        assertEquals("5.42", WorkoutFormat.distanceKm(5_420.0))
        assertEquals("0.00", WorkoutFormat.distanceKm(0.0))
    }

    @Test
    fun `duration is m colon ss under an hour`() {
        assertEquals("0:00", WorkoutFormat.duration(0L))
        assertEquals("5:09", WorkoutFormat.duration(309_000L))
        assertEquals("0:00", WorkoutFormat.duration(-1L)) // clamped, never negative
    }

    @Test
    fun `duration is h colon mm colon ss past an hour`() {
        assertEquals("1:02:03", WorkoutFormat.duration((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun `pace is minutes per km, em-dash when not moving`() {
        assertEquals("5:00", WorkoutFormat.pace(300.0))   // 5:00 /km
        assertEquals("4:30", WorkoutFormat.pace(270.0))
        assertEquals("—", WorkoutFormat.pace(null))
        assertEquals("—", WorkoutFormat.pace(0.0))
        assertEquals("—", WorkoutFormat.pace(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `speed is km per h to one decimal`() {
        assertEquals("36.0", WorkoutFormat.speedKmh(10f)) // 10 m/s = 36 km/h
        assertEquals("0.0", WorkoutFormat.speedKmh(0f))
    }
}
