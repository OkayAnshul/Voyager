package com.cosmiclaboratory.voyager.domain

import com.cosmiclaboratory.voyager.domain.usecase.BuildEvidenceSummaryUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.entity.SegmentEvidenceEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildEvidenceSummaryUseCaseTest {

    private val segmentEvidenceDao = mockk<SegmentEvidenceDao>()
    private val useCase = BuildEvidenceSummaryUseCase(
        segmentEvidenceDao, mockk<VisitEvidenceDao>(relaxed = true), mockk<PlaceEvidenceDao>(relaxed = true)
    )

    @Test
    fun `returns null when there is no evidence`() = runTest {
        coEvery { segmentEvidenceDao.getBySegmentId(1) } returns null
        assertNull(useCase.buildForSegment(1))
    }

    @Test
    fun `maps fields, parses votes, and labels the explanation by the top vote`() = runTest {
        coEvery { segmentEvidenceDao.getBySegmentId(1) } returns SegmentEvidenceEntity(
            segmentId = 1, avgSpeedMps = 1.4f, sampleCount = 10,
            activityVotesJson = """{"WALK":5,"RUN":2}""",
            explanationJson = "moved at walking pace"
        )
        val block = useCase.buildForSegment(1)!!
        assertEquals(10, block.sampleCount)
        assertEquals(1.4f, block.avgSpeed!!, 1e-6f)
        assertEquals(mapOf("WALK" to 5, "RUN" to 2), block.activityVotes)
        assertEquals("WALK", block.explanation?.label)              // top vote
        assertEquals("moved at walking pace", block.explanation?.humanExplanation)
    }

    @Test
    fun `malformed votes JSON degrades to empty without crashing`() = runTest {
        coEvery { segmentEvidenceDao.getBySegmentId(1) } returns SegmentEvidenceEntity(
            segmentId = 1, activityVotesJson = "not-json", explanationJson = "x"
        )
        val block = useCase.buildForSegment(1)!!
        assertTrue(block.activityVotes.isEmpty())
        assertEquals("UNKNOWN", block.explanation?.label) // no votes → UNKNOWN label
    }
}
