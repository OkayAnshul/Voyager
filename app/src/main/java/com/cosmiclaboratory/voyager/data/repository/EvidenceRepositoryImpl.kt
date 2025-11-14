package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.ConfidenceBlock
import com.cosmiclaboratory.voyager.domain.model.EvidenceBlock
import com.cosmiclaboratory.voyager.domain.model.InferenceExplanation
import com.cosmiclaboratory.voyager.domain.repository.EvidenceRepository
import com.cosmiclaboratory.voyager.domain.usecase.BuildEvidenceSummaryUseCase
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvidenceRepositoryImpl @Inject constructor(
    private val buildEvidenceSummaryUseCase: BuildEvidenceSummaryUseCase,
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val visitEvidenceDao: VisitEvidenceDao,
    private val placeEvidenceDao: PlaceEvidenceDao
) : EvidenceRepository {

    override suspend fun getSegmentEvidence(segmentId: Long): EvidenceBlock? {
        return buildEvidenceSummaryUseCase.buildForSegment(segmentId)
    }

    override suspend fun getVisitEvidence(visitId: Long): ConfidenceBlock? {
        val evidence = visitEvidenceDao.getByVisitId(visitId) ?: return null
        return ConfidenceBlock(
            overall = (evidence.arrivalConfidence + evidence.departureConfidence) / 2f,
            arrival = evidence.arrivalConfidence,
            departure = evidence.departureConfidence
        )
    }

    override suspend fun getPlaceEvidence(placeId: Long): ConfidenceBlock? {
        val evidence = placeEvidenceDao.getByPlaceId(placeId) ?: return null
        return ConfidenceBlock(
            overall = evidence.repeatabilityScore,
            label = "Visits: ${evidence.totalVisitCount}"
        )
    }

    override suspend fun getInferenceExplanation(segmentId: Long): InferenceExplanation? {
        val evidence = segmentEvidenceDao.getBySegmentId(segmentId) ?: return null
        return InferenceExplanation(
            label = "Segment $segmentId",
            confidence = 0.7f,
            supportingMetrics = buildMap {
                evidence.avgSpeedMps?.let { put("avgSpeedMps", it) }
                evidence.maxSpeedMps?.let { put("maxSpeedMps", it) }
                put("sampleCount", evidence.sampleCount)
            },
            counterEvidence = emptyList(),
            ruleVersion = evidence.decisionRuleVersion ?: "v1",
            sourceSet = emptySet(),
            humanExplanation = evidence.explanationJson ?: "No explanation available"
        )
    }
}
