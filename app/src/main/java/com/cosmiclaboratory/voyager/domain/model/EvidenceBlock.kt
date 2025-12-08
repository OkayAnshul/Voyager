package com.cosmiclaboratory.voyager.domain.model

data class EvidenceBlock(
    val sampleCount: Int,
    val avgSpeed: Float?,
    val maxSpeed: Float?,
    val stepCount: Int?,
    val headingConsistency: Float?,
    val activityVotes: Map<String, Int>,
    val providerMix: Map<String, Int>,
    val explanation: InferenceExplanation?
)

data class ConfidenceBlock(
    val overall: Float,
    val arrival: Float? = null,
    val departure: Float? = null,
    val category: Float? = null,
    val label: String? = null
)
