package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.abs

/**
 * A departure from an established routine, today. Builds on the recurring-pattern
 * engine: if a strong pattern says "you're usually at the Gym on Tuesdays ~7pm"
 * and today is Tuesday, we check whether it actually happened — and, if not (or
 * far off the usual time), surface it.
 *
 * Deliberately conservative: only strong patterns count, "missed" is only claimed
 * once the usual hour is well past (so an unfinished day isn't judged early), and
 * the copy states the expectation plainly rather than nagging.
 */
data class RoutineBreak(
    val placeId: Long,
    val placeName: String?,
    val expectedHour: Int,     // 0..23, the usual arrival hour
    val kind: Kind,
    val dayKey: String
) {
    enum class Kind { MISSED, LATE, EARLY }
}

class DetectRoutineBreaksUseCase @Inject constructor(
    private val detectRecurringPatterns: DetectRecurringPatternsUseCase,
    private val visitDao: VisitDao,
    private val placeDao: PlaceDao,
) {
    companion object {
        /** Only patterns at least this confident can raise a break. */
        const val MIN_CONFIDENCE = 0.6f
        /** Wait this many hours past the usual time before calling a routine "missed". */
        const val GRACE_HOURS_AFTER = 3
        /** A visit more than this many hours from the usual hour reads as late/early. */
        const val LATE_EARLY_HOURS = 3
    }

    suspend fun forToday(
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<RoutineBreak> {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = now }
        val todayDow = cal.get(Calendar.DAY_OF_WEEK)
        val todayHour = cal.get(Calendar.HOUR_OF_DAY)
        val dayKey = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val todayYear = cal.get(Calendar.YEAR)
        val todayDoy = cal.get(Calendar.DAY_OF_YEAR)

        val patterns = detectRecurringPatterns.forAllPlaces(timeZone)
            .filter { it.confidence >= MIN_CONFIDENCE && it.dayOfWeek == todayDow }
        if (patterns.isEmpty()) return emptyList()

        val placeNames = mutableMapOf<Long, String?>()
        val visitHoursToday = mutableMapOf<Long, List<Int>>()
        for (placeId in patterns.map { it.placeId }.toSet()) {
            placeNames[placeId] = placeDao.getById(placeId)?.let { effectiveName(it.userDisplayName, it.bestProviderName) }
            val hours = visitDao.getByPlaceId(placeId).mapNotNull { v ->
                val vc = Calendar.getInstance(timeZone).apply { timeInMillis = v.arrivalAt }
                if (vc.get(Calendar.YEAR) == todayYear && vc.get(Calendar.DAY_OF_YEAR) == todayDoy) {
                    vc.get(Calendar.HOUR_OF_DAY)
                } else null
            }
            visitHoursToday[placeId] = hours
        }

        return detect(patterns, placeNames, visitHoursToday, todayHour, dayKey)
    }

    /** Pure — exposed for tests and callers holding today's visit hours. */
    fun detect(
        todaysPatterns: List<RecurringPattern>,
        placeNames: Map<Long, String?>,
        visitHoursTodayByPlace: Map<Long, List<Int>>,
        todayHour: Int,
        dayKey: String
    ): List<RoutineBreak> {
        val breaks = mutableListOf<RoutineBreak>()
        for (pattern in todaysPatterns) {
            if (pattern.confidence < MIN_CONFIDENCE) continue
            val hoursToday = visitHoursTodayByPlace[pattern.placeId].orEmpty()
            if (hoursToday.isEmpty()) {
                // Only call it missed once the usual hour is comfortably past.
                if (todayHour >= pattern.typicalHour + GRACE_HOURS_AFTER) {
                    breaks += RoutineBreak(pattern.placeId, placeNames[pattern.placeId], pattern.typicalHour, RoutineBreak.Kind.MISSED, dayKey)
                }
                continue
            }
            // Visited — but far from the usual hour?
            val closest = hoursToday.minByOrNull { abs(it - pattern.typicalHour) }!!
            val delta = closest - pattern.typicalHour
            if (abs(delta) > LATE_EARLY_HOURS) {
                val kind = if (delta > 0) RoutineBreak.Kind.LATE else RoutineBreak.Kind.EARLY
                breaks += RoutineBreak(pattern.placeId, placeNames[pattern.placeId], pattern.typicalHour, kind, dayKey)
            }
        }
        return breaks
    }

    private fun effectiveName(userName: String?, providerName: String?): String? =
        userName?.takeIf { it.isNotBlank() } ?: providerName?.takeIf { it.isNotBlank() }
}
