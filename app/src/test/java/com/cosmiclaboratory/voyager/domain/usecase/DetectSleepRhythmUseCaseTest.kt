package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DetectSleepRhythmUseCaseTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val useCase = DetectSleepRhythmUseCase(
        placeDao = mockk<PlaceDao>(relaxed = true),
        visitDao = mockk<VisitDao>(relaxed = true),
    )

    /** A home stay from [arrHour]:00 on the given date to [depHour]:00 [depDayOffset] days later. */
    private fun night(
        month: Int, day: Int,
        arrHour: Int, arrMin: Int = 0,
        depHour: Int, depMin: Int = 0,
        depDayOffset: Int = 1
    ): VisitEntity {
        val arrival = LocalDate.of(2026, month, day).atTime(arrHour, arrMin)
            .atZone(zone).toInstant().toEpochMilli()
        val departure = LocalDate.of(2026, month, day).plusDays(depDayOffset.toLong())
            .atTime(depHour, depMin).atZone(zone).toInstant().toEpochMilli()
        return VisitEntity(
            placeId = 1L,
            arrivalAt = arrival,
            departureAt = departure,
            dwellMs = departure - arrival,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = "2026-%02d-%02d".format(month, day),
            centroidLat = 0.0,
            centroidLng = 0.0
        )
    }

    @Test
    fun `fewer than five nights yields no rhythm`() {
        val visits = (6..9).map { night(1, it, 23, depHour = 7) } // only 4 nights
        assertNull(useCase.detect(visits, zone))
    }

    @Test
    fun `consistent 11pm to 7am nights produce a tight rhythm`() {
        val visits = (6..12).map { night(1, it, 23, depHour = 7) } // 7 nights
        val rhythm = useCase.detect(visits, zone)!!
        assertEquals(7, rhythm.nightsAnalyzed)
        assertEquals(23 * 60, rhythm.settleMinuteOfDay)              // 23:00
        assertEquals(7 * 60, rhythm.wakeMinuteOfDay)                 // 07:00
        assertEquals(8L * 3_600_000L, rhythm.medianOvernightMs)     // 8h
        assertEquals(SleepRhythm.Consistency.CONSISTENT, rhythm.consistency)
    }

    @Test
    fun `daytime-only stays are ignored (do not span the night)`() {
        val daytime = (6..12).map { night(2, it, 10, depHour = 14, depDayOffset = 0) } // 10:00–14:00
        assertNull(useCase.detect(daytime, zone))
    }

    @Test
    fun `scattered wake times read as variable`() {
        val wakeHours = listOf(5, 9, 6, 10, 5, 11) // wide spread
        val visits = wakeHours.mapIndexed { i, wake -> night(3, 6 + i, 23, depHour = wake) }
        val rhythm = useCase.detect(visits, zone)!!
        assertEquals(SleepRhythm.Consistency.VARIABLE, rhythm.consistency)
    }
}
