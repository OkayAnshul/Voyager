package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.abs

/**
 * Forward-looking companion to [DetectRoutineBreaksUseCase]: what the day usually
 * still holds. From the recurring-pattern histogram, the strong patterns for
 * today whose usual hour is still ahead (and which haven't happened yet) become
 * "coming up" predictions — "you usually visit the Gym around 7pm".
 *
 * Confidence-gated and only ever about routines the user has actually built, so
 * it anticipates without pretending to know the future.
 */
data class UpcomingVisit(
    val placeId: Long,
    val placeName: String?,
    val expectedHour: Int,   // 0..23
    val confidence: Float
)

class PredictNextPlaceUseCase @Inject constructor(
    private val detectRecurringPatterns: DetectRecurringPatternsUseCase,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
) {
    companion object {
        const val MIN_CONFIDENCE = 0.55f
        /** A visit already within this many hours of the usual time means it's done. */
        const val ALREADY_DONE_WINDOW_H = 2
        const val MAX_RESULTS = 3
    }

    suspend fun upcomingToday(
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<UpcomingVisit> {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = now }
        val todayDow = cal.get(Calendar.DAY_OF_WEEK)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val todayYear = cal.get(Calendar.YEAR)
        val todayDoy = cal.get(Calendar.DAY_OF_YEAR)

        val patterns = detectRecurringPatterns.forAllPlaces(timeZone)
            .filter { it.confidence >= MIN_CONFIDENCE && it.dayOfWeek == todayDow }
        if (patterns.isEmpty()) return emptyList()

        val placeNames = mutableMapOf<Long, String?>()
        val visitHoursToday = mutableMapOf<Long, List<Int>>()
        for (placeId in patterns.map { it.placeId }.toSet()) {
            placeNames[placeId] = placeDao.getById(placeId)?.let { effectiveName(it.userDisplayName, it.bestProviderName) }
            visitHoursToday[placeId] = visitDao.getByPlaceId(placeId).mapNotNull { v ->
                val vc = Calendar.getInstance(timeZone).apply { timeInMillis = v.arrivalAt }
                if (vc.get(Calendar.YEAR) == todayYear && vc.get(Calendar.DAY_OF_YEAR) == todayDoy) {
                    vc.get(Calendar.HOUR_OF_DAY)
                } else null
            }
        }

        return detect(patterns, placeNames, visitHoursToday, currentHour)
    }

    /** Pure — exposed for tests and callers holding today's visit hours. */
    fun detect(
        todaysPatterns: List<RecurringPattern>,
        placeNames: Map<Long, String?>,
        visitHoursTodayByPlace: Map<Long, List<Int>>,
        currentHour: Int
    ): List<UpcomingVisit> = todaysPatterns
        .filter { it.confidence >= MIN_CONFIDENCE }
        .filter { it.typicalHour >= currentHour }
        .filter { p ->
            visitHoursTodayByPlace[p.placeId].orEmpty().none { abs(it - p.typicalHour) <= ALREADY_DONE_WINDOW_H }
        }
        .sortedBy { it.typicalHour }
        .distinctBy { it.placeId to it.typicalHour }
        .take(MAX_RESULTS)
        .map { UpcomingVisit(it.placeId, placeNames[it.placeId], it.typicalHour, it.confidence) }

    private fun effectiveName(userName: String?, providerName: String?): String? =
        userName?.takeIf { it.isNotBlank() } ?: providerName?.takeIf { it.isNotBlank() }
}
