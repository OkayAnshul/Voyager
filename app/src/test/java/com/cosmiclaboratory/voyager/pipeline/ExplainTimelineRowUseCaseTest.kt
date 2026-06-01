package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.domain.usecase.ExplainTimelineRowUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.entity.MovementSegmentEntity
import com.cosmiclaboratory.voyager.storage.database.entity.SegmentEvidenceEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEvidenceEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExplainTimelineRowUseCaseTest {

    private val movementSegmentDao = mockk<MovementSegmentDao>(relaxed = true)
    private val segmentEvidenceDao = mockk<SegmentEvidenceDao>(relaxed = true)
    private val visitDao = mockk<VisitDao>(relaxed = true)
    private val visitEvidenceDao = mockk<VisitEvidenceDao>(relaxed = true)

    private val useCase = ExplainTimelineRowUseCase(
        movementSegmentDao, segmentEvidenceDao, visitDao, visitEvidenceDao
    )

    @Test
    fun `unknown segment id returns null`() = runTest {
        coEvery { movementSegmentDao.getById(999L) } returns null
        assertNull(useCase.explainSegment(999L))
    }

    @Test
    fun `segment headline contains type duration and distance`() = runTest {
        val start = 1_700_000_000_000L
        val seg = MovementSegmentEntity(
            segmentId = 1L,
            segmentType = "WALK",
            startAt = start,
            endAt = start + 18 * 60_000L, // 18 min
            startSampleId = 1L,
            endSampleId = 50L,
            distanceM = 1_200.0,
            confidence = 0.8f,
            dayKey = "2026-01-01"
        )
        coEvery { movementSegmentDao.getById(1L) } returns seg
        coEvery { segmentEvidenceDao.getBySegmentId(1L) } returns null

        val explanation = useCase.explainSegment(1L)!!
        assertEquals("SEGMENT", explanation.rowType)
        assertTrue(explanation.headline.contains("WALK"))
        assertTrue(explanation.headline.contains("18 min"))
        assertTrue(explanation.headline.contains("1.2 km"))
    }

    @Test
    fun `segment signals include speed band activity votes`() = runTest {
        val seg = MovementSegmentEntity(
            segmentId = 2L,
            segmentType = "WALK",
            startAt = 0L,
            endAt = 60_000L,
            startSampleId = 1L,
            endSampleId = 5L,
            distanceM = 100.0,
            confidence = 0.7f,
            dayKey = "2026-01-01"
        )
        val evidence = SegmentEvidenceEntity(
            segmentId = 2L,
            avgSpeedMps = 1.4f,
            maxSpeedMps = 2.0f,
            sampleCount = 30,
            activityVotesJson = """{"WALKING":25,"STILL":5}"""
        )
        coEvery { movementSegmentDao.getById(2L) } returns seg
        coEvery { segmentEvidenceDao.getBySegmentId(2L) } returns evidence

        val explanation = useCase.explainSegment(2L)!!
        val labels = explanation.signals.map { it.label }
        assertTrue("avg speed signal present", labels.any { it.contains("avg speed") })
        assertTrue("samples signal present", labels.any { it.contains("samples 30") })
        assertTrue("WALKING vote shown", labels.any { it.contains("WALKING") })
    }

    @Test
    fun `visit headline shows dwell hours and minutes`() = runTest {
        val arrival = 1_700_000_000_000L
        val visit = VisitEntity(
            visitId = 5L,
            placeId = 99L,
            arrivalAt = arrival,
            departureAt = arrival + (2 * 3_600_000L + 15 * 60_000L), // 2h 15m
            dwellMs = 2 * 3_600_000L + 15 * 60_000L,
            source = "LIVE_DETECTION",
            confidence = 0.85f,
            dayKey = "2026-01-01",
            centroidLat = 0.0,
            centroidLng = 0.0
        )
        coEvery { visitDao.getById(5L) } returns visit
        coEvery { visitEvidenceDao.getByVisitId(5L) } returns null

        val explanation = useCase.explainVisit(5L)!!
        assertEquals("VISIT", explanation.rowType)
        assertTrue("hours rendered", explanation.headline.contains("2h"))
        assertTrue("minutes rendered", explanation.headline.contains("15m"))
    }

    @Test
    fun `visit explanation flags missing place link`() = runTest {
        val visit = VisitEntity(
            visitId = 6L,
            placeId = 0L,
            arrivalAt = 0L,
            departureAt = 60_000L,
            dwellMs = 60_000L,
            source = "LIVE_DETECTION",
            confidence = 0.7f,
            dayKey = "2026-01-01",
            centroidLat = 0.0,
            centroidLng = 0.0
        )
        coEvery { visitDao.getById(6L) } returns visit
        coEvery { visitEvidenceDao.getByVisitId(6L) } returns null

        val explanation = useCase.explainVisit(6L)!!
        assertTrue(explanation.signals.any { it.label.contains("not linked") })
    }
}
