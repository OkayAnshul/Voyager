package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.VehicleAutoAssignmentEngine
import com.cosmiclaboratory.voyager.domain.usecase.VehicleAutoAssignmentEngine.Predicate
import com.cosmiclaboratory.voyager.domain.usecase.VehicleAutoAssignmentEngine.Rule
import com.cosmiclaboratory.voyager.domain.usecase.VehicleAutoAssignmentEngine.SegmentFeatures
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class VehicleAutoAssignmentEngineTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun mondayAt(hour: Int): Long = Calendar.getInstance(utc).apply {
        clear(); set(2026, Calendar.JANUARY, 5, hour, 0, 0) // Mon Jan 5 2026
    }.timeInMillis

    private fun saturdayAt(hour: Int): Long = Calendar.getInstance(utc).apply {
        clear(); set(2026, Calendar.JANUARY, 10, hour, 0, 0) // Sat Jan 10 2026
    }.timeInMillis

    @Test
    fun `no rules returns null`() {
        val pick = VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = 20f, startAtMs = mondayAt(9)),
            rules = emptyList(),
            timeZone = utc
        )
        assertNull(pick)
    }

    @Test
    fun `speed-band rule picks heavier vehicle on a fast segment`() {
        val rules = listOf(
            Rule(vehicleId = 1L, priority = 5, predicates = listOf(Predicate.SpeedMin(25f))), // car
            Rule(vehicleId = 2L, priority = 5, predicates = listOf(Predicate.SpeedMax(10f))), // bike
        )
        val carPick = VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = 30f, startAtMs = mondayAt(9)), rules, utc
        )
        assertEquals(1L, carPick)
        val bikePick = VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = 8f, startAtMs = mondayAt(9)), rules, utc
        )
        assertEquals(2L, bikePick)
    }

    @Test
    fun `time-window rule matches weekday morning commute`() {
        val rules = listOf(
            Rule(vehicleId = 7L, priority = 10, predicates = listOf(
                Predicate.TimeWindow(7, 10),
                Predicate.DayOfWeek(setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
            ))
        )
        val weekdayMorning = VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = null, startAtMs = mondayAt(8)), rules, utc
        )
        assertEquals(7L, weekdayMorning)
        val weekendMorning = VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = null, startAtMs = saturdayAt(8)), rules, utc
        )
        assertNull(weekendMorning)
    }

    @Test
    fun `priority breaks ties between matching rules`() {
        val rules = listOf(
            Rule(vehicleId = 1L, priority = 1, predicates = listOf(Predicate.SpeedMin(5f))),
            Rule(vehicleId = 2L, priority = 9, predicates = listOf(Predicate.SpeedMin(5f))),
        )
        val pick = VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = 20f, startAtMs = mondayAt(9)), rules, utc
        )
        assertEquals(2L, pick)
    }

    @Test
    fun `wrap-around time window catches overnight hours`() {
        val rules = listOf(
            Rule(vehicleId = 3L, priority = 1, predicates = listOf(Predicate.TimeWindow(22, 6)))
        )
        assertEquals(3L, VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = null, startAtMs = mondayAt(23)), rules, utc
        ))
        assertEquals(3L, VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = null, startAtMs = mondayAt(2)), rules, utc
        ))
        assertNull(VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = null, startAtMs = mondayAt(10)), rules, utc
        ))
    }

    @Test
    fun `missing maxSpeed cannot match a speed predicate`() {
        val rules = listOf(
            Rule(vehicleId = 1L, priority = 1, predicates = listOf(Predicate.SpeedMin(0f))),
        )
        // SpeedMin(0) would always match if null were treated as 0; we want it to fail.
        assertNull(VehicleAutoAssignmentEngine.pickVehicle(
            SegmentFeatures(maxSpeedMps = null, startAtMs = mondayAt(9)), rules, utc
        ))
    }
}
