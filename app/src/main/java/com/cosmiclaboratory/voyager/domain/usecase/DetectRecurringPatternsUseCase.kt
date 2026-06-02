package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * A regular visit pattern detected from a place's history.
 *
 * "You visit Gym every Tuesday around 7pm." The data substrate for that
 * statement: same place, same day-of-week, same hour bucket, ≥4 visits in
 * the lookback window, tight timing variance.
 *
 * Day-of-week values match [Calendar.DAY_OF_WEEK] (SUNDAY=1 … SATURDAY=7)
 * so callers can render with `DayOfWeek.of(...)` or render locally without
 * an extra translation step.
 */
data class RecurringPattern(
    val placeId: Long,
    val dayOfWeek: Int,           // Calendar.SUNDAY..SATURDAY
    val typicalHour: Int,         // 0..23, the median arrival hour in the bucket
    val visitCount: Int,
    val arrivalHourStdDev: Float, // hours; lower = tighter timing
    val confidence: Float         // 0..1
)

class DetectRecurringPatternsUseCase @Inject constructor(
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
) {

    companion object {
        /** A pattern needs at least this many matching visits in the window to count. */
        const val MIN_VISITS_FOR_PATTERN = 4
        /** Lookback window — older history shouldn't keep a pattern alive after the user moves on. */
        const val LOOKBACK_WEEKS = 8
        /** Max allowed std dev (hours) on arrival hour within a bucket — tighter = more regular. */
        const val MAX_HOUR_STD_DEV = 1.5f
        /** Patterns within ±1 hour of each other are merged so we don't double-count "Tue 7pm" + "Tue 8pm". */
        private const val HOUR_BUCKET_TOLERANCE = 1
    }

    /**
     * Patterns across all confirmed places — handy for surfacing "your week" at a glance.
     * Heavy-ish; the per-place [forPlace] form is the cheap path when the caller
     * knows which place they're rendering.
     */
    suspend fun forAllPlaces(timeZone: TimeZone = TimeZone.getDefault()): List<RecurringPattern> {
        val places = placeDao.getAllActive()
        val nowMs = System.currentTimeMillis()
        val cutoff = nowMs - LOOKBACK_WEEKS * 7L * 86_400_000L
        return places.flatMap { place ->
            val visits = visitDao.getByPlaceId(place.placeId)
                .filter { it.arrivalAt >= cutoff }
            detect(place.placeId, visits, timeZone)
        }
    }

    suspend fun forPlace(
        placeId: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<RecurringPattern> {
        val cutoff = System.currentTimeMillis() - LOOKBACK_WEEKS * 7L * 86_400_000L
        val visits = visitDao.getByPlaceId(placeId).filter { it.arrivalAt >= cutoff }
        return detect(placeId, visits, timeZone)
    }

    /** Pure variant — exposed for tests and for callers holding the visit list. */
    fun detect(
        placeId: Long,
        visits: List<VisitEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<RecurringPattern> {
        if (visits.size < MIN_VISITS_FOR_PATTERN) return emptyList()
        val cal = Calendar.getInstance(timeZone)
        // Group every visit into a (dayOfWeek, hour) bucket.
        val groups = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for (visit in visits) {
            cal.timeInMillis = visit.arrivalAt
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val key = dow to hour
            groups.getOrPut(key) { mutableListOf() }.add(hour)
        }

        // Merge ±1h-adjacent buckets on the same day so "Tue 18:55" and "Tue 19:05"
        // don't fall on opposite sides of an hour boundary.
        val merged = mergeAdjacentHours(groups)

        return merged.mapNotNull { (key, hours) ->
            if (hours.size < MIN_VISITS_FOR_PATTERN) return@mapNotNull null
            val stdDev = stdDev(hours).toFloat()
            if (stdDev > MAX_HOUR_STD_DEV) return@mapNotNull null
            val median = hours.sorted()[hours.size / 2]
            // Confidence: bounded by sample count (more = surer) and timing tightness.
            val countFactor = (hours.size.toFloat() / 8f).coerceIn(0.5f, 1.0f)
            val timingFactor = (1f - stdDev / MAX_HOUR_STD_DEV).coerceIn(0f, 1f)
            val confidence = (countFactor * 0.6f + timingFactor * 0.4f).coerceIn(0.4f, 0.95f)
            RecurringPattern(
                placeId = placeId,
                dayOfWeek = key.first,
                typicalHour = median,
                visitCount = hours.size,
                arrivalHourStdDev = stdDev,
                confidence = confidence
            )
        }
    }

    /**
     * Collapse adjacent (dayOfWeek, hour) buckets within ±[HOUR_BUCKET_TOLERANCE].
     * Each merged bucket reports the *union* of arrival hours and is keyed on the
     * peak count's hour, so 5 visits at 19 and 2 at 18 collapse onto "Tuesday 19h"
     * with 7 entries instead of two separate weak buckets.
     */
    private fun mergeAdjacentHours(
        groups: Map<Pair<Int, Int>, List<Int>>
    ): Map<Pair<Int, Int>, List<Int>> {
        val result = HashMap<Pair<Int, Int>, MutableList<Int>>()
        // Sort by (dayOfWeek, hour) and greedily merge into running buckets.
        val sorted = groups.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
        var anchor: Pair<Int, Int>? = null
        var bucket: MutableList<Int>? = null
        var anchorCount = 0
        for ((key, hours) in sorted) {
            if (anchor == null || anchor.first != key.first ||
                key.second - anchor.second > HOUR_BUCKET_TOLERANCE) {
                // Start a fresh anchor.
                anchor = key
                bucket = hours.toMutableList()
                anchorCount = hours.size
                result[anchor] = bucket
            } else {
                // Within tolerance — fold into the running bucket. Promote the
                // anchor to whichever hour holds the most visits, preserving the
                // existing bucket reference under the new key.
                bucket!!.addAll(hours)
                if (hours.size > anchorCount) {
                    result.remove(anchor)
                    anchor = key
                    anchorCount = hours.size
                    result[anchor] = bucket
                }
            }
        }
        return result
    }

    private fun stdDev(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}
