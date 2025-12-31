package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.EvidenceBlock
import com.cosmiclaboratory.voyager.domain.model.InferenceExplanation
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.PlaceEvidenceDao
import kotlinx.serialization.json.Json
import javax.inject.Inject

class BuildEvidenceSummaryUseCase @Inject constructor(
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val visitEvidenceDao: VisitEvidenceDao,
    private val placeEvidenceDao: PlaceEvidenceDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun buildForSegment(segmentId: Long): EvidenceBlock? {
        val evidence = segmentEvidenceDao.getBySegmentId(segmentId) ?: return null

        val activityVotes: Map<String, Int> = evidence.activityVotesJson?.let {
            try { json.decodeFromString(it) } catch (_: Exception) { emptyMap() }
        } ?: emptyMap()

        val providerMix: Map<String, Int> = evidence.providerMixJson?.let {
            try { json.decodeFromString(it) } catch (_: Exception) { emptyMap() }
        } ?: emptyMap()

        val explanation = evidence.explanationJson?.let { expJson ->
            try {
                InferenceExplanation(
                    label = activityVotes.maxByOrNull { it.value }?.key ?: "UNKNOWN",
                    confidence = 0.7f,
                    supportingMetrics = mapOf(
                        "avgSpeedMps" to (evidence.avgSpeedMps ?: 0f),
                        "sampleCount" to evidence.sampleCount
                    ),
                    counterEvidence = evidence.counterEvidenceJson?.let {
                        try { json.decodeFromString<List<String>>(it) } catch (_: Exception) { emptyList() }
                    } ?: emptyList(),
                    ruleVersion = evidence.decisionRuleVersion ?: "v1",
                    sourceSet = providerMix.keys,
                    humanExplanation = expJson
                )
            } catch (_: Exception) { null }
        }

        return EvidenceBlock(
            sampleCount = evidence.sampleCount,
            avgSpeed = evidence.avgSpeedMps,
            maxSpeed = evidence.maxSpeedMps,
            stepCount = evidence.stepCount,
            headingConsistency = evidence.headingConsistency,
            activityVotes = activityVotes,
            providerMix = providerMix,
            explanation = explanation
        )
    }
}
