package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

/**
 * Pure pattern-based place-category inference from a place's visit history.
 *
 * Sits behind the OSM-POI category mapper ([com.cosmiclaboratory.voyager.data.geocoding.PoiCategoryMapper])
 * for places where no POI tag is available — typically homes and offices.
 *
 * The thresholds are deliberately conservative: a category is only proposed
 * when the user has enough visits at a place that the pattern is real, not a
 * coincidence. Below those thresholds the result is `null` and the place's
 * category stays UNKNOWN. Confidence is a 0–1 score the caller can use to
 * decide whether to write the inferred category — the worker writes only
 * when the place is currently UNKNOWN and no userCategory override exists.
 */
class InferPlaceCategoryUseCase @Inject constructor() {

    data class CategoryProposal(val category: PlaceCategory, val confidence: Float)

    /**
     * @param visits all confirmed visits to a single place (closed; departureAt + dwellMs set).
     * @param timeZone timezone used for hour-of-day / day-of-week — pass the user's home tz.
     */
    fun infer(
        visits: List<VisitEntity>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): CategoryProposal? {
        if (visits.size < MIN_VISITS_FOR_ANY_INFERENCE) return null
        val closed = visits.filter { it.departureAt != null && (it.dwellMs ?: 0) > 0 }
        if (closed.size < MIN_VISITS_FOR_ANY_INFERENCE) return null

        val features = computeFeatures(closed, timeZone)
        return inferHome(features)
            ?: inferWork(features)
            ?: inferEducation(features)
            ?: inferRestaurant(features) // before gym: 1h dinner-time visits are restaurants, not gyms
            ?: inferGym(features)
            ?: inferTransitHub(features)
    }

    private fun computeFeatures(closed: List<VisitEntity>, tz: TimeZone): Features {
        val cal = Calendar.getInstance(tz)
        var nightArrivals = 0     // arrival 22:00..02:00
        var weekdayDaytimeArrivals = 0 // arrival 07:00..10:00 on Mon-Fri
        var mealTimeArrivals = 0  // arrival 12:00..14:00 or 18:00..21:00
        var weekdayCount = 0
        val dwells = mutableListOf<Long>()

        for (visit in closed) {
            cal.timeInMillis = visit.arrivalAt
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekday = dow in Calendar.MONDAY..Calendar.FRIDAY

            if (hour >= 22 || hour < 2) nightArrivals++
            if (isWeekday) {
                weekdayCount++
                if (hour in 7..9) weekdayDaytimeArrivals++
            }
            if (hour in 12..13 || hour in 18..20) mealTimeArrivals++
            visit.dwellMs?.let { dwells += it }
        }
        return Features(
            total = closed.size,
            nightArrivals = nightArrivals,
            weekdayDaytimeArrivals = weekdayDaytimeArrivals,
            mealTimeArrivals = mealTimeArrivals,
            weekdayShare = if (closed.isEmpty()) 0f else weekdayCount.toFloat() / closed.size,
            medianDwellMs = median(dwells)
        )
    }

    private fun inferHome(f: Features): CategoryProposal? {
        if (f.nightArrivals >= 10 && f.medianDwellMs >= 6 * H_MS) {
            val confidence = (f.nightArrivals.toFloat() / f.total).coerceIn(0.6f, 0.95f)
            return CategoryProposal(PlaceCategory.HOME, confidence)
        }
        return null
    }

    private fun inferWork(f: Features): CategoryProposal? {
        if (f.weekdayDaytimeArrivals >= 10 && f.weekdayShare >= 0.7f &&
            f.medianDwellMs in 6 * H_MS..10 * H_MS) {
            val confidence = (f.weekdayDaytimeArrivals.toFloat() / f.total).coerceIn(0.6f, 0.95f)
            return CategoryProposal(PlaceCategory.WORK, confidence)
        }
        return null
    }

    private fun inferEducation(f: Features): CategoryProposal? {
        // School / university — weekday daytime, longer dwell than work, term-time visits.
        if (f.weekdayDaytimeArrivals >= 10 && f.weekdayShare >= 0.8f &&
            f.medianDwellMs > 10 * H_MS) {
            return CategoryProposal(PlaceCategory.EDUCATION, 0.75f)
        }
        return null
    }

    private fun inferGym(f: Features): CategoryProposal? {
        // 30–90 min visits, recurrent (≥8 of last visits in that band).
        if (f.total < 8) return null
        if (f.medianDwellMs in 30 * MIN_MS..90 * MIN_MS) {
            return CategoryProposal(PlaceCategory.GYM, 0.65f)
        }
        return null
    }

    private fun inferRestaurant(f: Features): CategoryProposal? {
        if (f.total < 5) return null
        if (f.mealTimeArrivals.toFloat() / f.total >= 0.7f &&
            f.medianDwellMs in 30 * MIN_MS..2 * H_MS) {
            return CategoryProposal(PlaceCategory.RESTAURANT, 0.7f)
        }
        return null
    }

    private fun inferTransitHub(f: Features): CategoryProposal? {
        // Frequent very-short stops — bus stop, train station entrance, taxi rank.
        if (f.total >= 20 && f.medianDwellMs < 15 * MIN_MS) {
            return CategoryProposal(PlaceCategory.TRANSIT_HUB, 0.65f)
        }
        return null
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private data class Features(
        val total: Int,
        val nightArrivals: Int,
        val weekdayDaytimeArrivals: Int,
        val mealTimeArrivals: Int,
        val weekdayShare: Float,
        val medianDwellMs: Long
    )

    companion object {
        private const val MIN_VISITS_FOR_ANY_INFERENCE = 5
        private const val MIN_MS = 60_000L
        private const val H_MS = 3_600_000L
    }
}
