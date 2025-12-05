package com.cosmiclaboratory.voyager.domain.model

import com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity

data class ComparisonResult(
    val periodA: DateRange,
    val periodB: DateRange,
    val metricDeltas: Map<String, MetricDelta>,
    val highlights: List<String>,
    val confidence: Float
)

data class MetricDelta(
    val valueA: Double,
    val valueB: Double,
    val absoluteDelta: Double,
    val percentDelta: Double,
    val trend: Trend
)

data class DateRange(
    val startDay: String, // YYYY-MM-DD
    val endDay: String    // YYYY-MM-DD
)

data class Anomaly(
    val metricKey: String,
    val observedValue: Double,
    val baselineMean: Double,
    val baselineStdDev: Double,
    val deviationSigma: Float,
    val severity: AnomalySeverity,
    val baselinePeriod: DateRange,
    val impactedDay: String,
    val humanExplanation: String
)
