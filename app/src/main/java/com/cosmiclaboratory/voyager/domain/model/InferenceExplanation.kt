package com.cosmiclaboratory.voyager.domain.model

data class InferenceExplanation(
    val label: String,
    val confidence: Float,
    val supportingMetrics: Map<String, Any>,
    val counterEvidence: List<String>,
    val ruleVersion: String,
    val sourceSet: Set<String>,
    val humanExplanation: String
)
