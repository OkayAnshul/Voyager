package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.MovementSegmentDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitEvidenceDao
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * A single explanation reason — one signal that contributed to the
 * classification or confidence shown on a timeline row.
 */
data class TimelineSignal(
    val label: String,
    val detail: String?
)

/**
 * Structured "why does the timeline show this?" data for a row.
 *
 * Built from data the pipeline already records (activity votes, sample
 * counts, dwell distributions, confidence floats) — surfacing it gives
 * users an honest view of how a segment or visit was classified, which
 * is more durable trust than a polished but opaque label.
 *
 * UI rendering deferred to the Figma pass — this layer is pure data.
 */
data class TimelineExplanation(
    val rowType: String,        // "SEGMENT" or "VISIT"
    val rowId: Long,
    val headline: String,       // short, e.g. "WALK · 18 min · 1.2 km"
    val signals: List<TimelineSignal>,
    val confidence: Float
)

class ExplainTimelineRowUseCase @Inject constructor(
    private val movementSegmentDao: MovementSegmentDao,
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val visitDao: VisitDao,
    private val visitEvidenceDao: VisitEvidenceDao,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun explainSegment(segmentId: Long): TimelineExplanation? {
        val seg = movementSegmentDao.getById(segmentId) ?: return null
        val evidence = segmentEvidenceDao.getBySegmentId(segmentId)

        val durationMs = (seg.endAt - seg.startAt).coerceAtLeast(0)
        val durationMin = durationMs / 60_000L
        val distanceKm = seg.distanceM / 1000.0
        val headline = buildString {
            append(seg.segmentType)
            append(" · ").append(durationMin).append(" min")
            if (distanceKm >= 0.1) append(" · ").append(String.format("%.1f", distanceKm)).append(" km")
        }

        val signals = mutableListOf<TimelineSignal>()
        if (evidence != null) {
            evidence.avgSpeedMps?.let {
                signals += TimelineSignal(
                    label = "avg speed ${"%.1f".format(it)} m/s",
                    detail = speedBandLabel(it)
                )
            }
            evidence.maxSpeedMps?.let {
                signals += TimelineSignal("peak speed ${"%.1f".format(it)} m/s", null)
            }
            signals += TimelineSignal("samples ${evidence.sampleCount}", null)
            evidence.activityVotesJson?.let { votesJson ->
                runCatching {
                    val map = json.decodeFromString(
                        MapSerializer(String.serializer(), Int.serializer()), votesJson
                    )
                    val total = map.values.sum().coerceAtLeast(1)
                    map.entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .forEach { (activity, count) ->
                            val pct = (count * 100 / total)
                            signals += TimelineSignal("$activity vote", "$count samples ($pct%)")
                        }
                }
            }
        }

        return TimelineExplanation(
            rowType = "SEGMENT",
            rowId = segmentId,
            headline = headline,
            signals = signals,
            confidence = seg.confidence
        )
    }

    suspend fun explainVisit(visitId: Long): TimelineExplanation? {
        val visit = visitDao.getById(visitId) ?: return null
        val evidence = visitEvidenceDao.getByVisitId(visitId)

        val dwellMs = visit.dwellMs ?: ((visit.departureAt ?: visit.arrivalAt) - visit.arrivalAt)
        val dwellMin = dwellMs / 60_000L
        val headline = buildString {
            append("VISIT · ")
            if (dwellMin >= 60) {
                append(dwellMin / 60).append("h ").append(dwellMin % 60).append("m")
            } else {
                append(dwellMin).append(" min")
            }
        }

        val signals = mutableListOf<TimelineSignal>()
        signals += TimelineSignal("arrival confidence", confidencePercent(visit.confidence))
        if (evidence != null) {
            signals += TimelineSignal("inside samples", evidence.insideCount.toString())
            if (evidence.outsideCount > 0) {
                signals += TimelineSignal("outside samples (pre-depart)", evidence.outsideCount.toString())
            }
            signals += TimelineSignal("arrival confidence", confidencePercent(evidence.arrivalConfidence))
            evidence.confirmationRuleUsed?.let {
                signals += TimelineSignal("confirmation rule", it)
            }
        }
        if (visit.placeId == 0L) {
            signals += TimelineSignal("not linked to a place", "still resolving")
        }

        return TimelineExplanation(
            rowType = "VISIT",
            rowId = visitId,
            headline = headline,
            signals = signals,
            confidence = visit.confidence
        )
    }

    private fun speedBandLabel(mps: Float): String = when {
        mps < 0.3f -> "stationary"
        mps < 1.3f -> "walking pace"
        mps < 3.0f -> "running pace"
        mps < 6.5f -> "cycling pace"
        mps < 25f -> "vehicle pace"
        else -> "high speed"
    }

    private fun confidencePercent(f: Float): String = "${(f * 100).toInt()}%"
}
