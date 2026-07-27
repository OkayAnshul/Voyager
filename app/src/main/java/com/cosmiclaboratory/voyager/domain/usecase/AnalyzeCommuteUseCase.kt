package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.storage.database.dao.PlaceDao
import com.cosmiclaboratory.voyager.storage.database.dao.VisitDao
import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Commute analytics between the HOME place and any WORK place, derived from the
 * gap between a departure from one and the next arrival at the other.
 *
 * A commute leg = time from leaving Home to arriving at Work (or the reverse),
 * capped so a "went home, ran an errand, then to work three hours later" gap
 * isn't mistaken for a two-hour commute. Duration includes any brief stop the
 * user made en route (we don't try to subtract a coffee run), which the cap
 * keeps honest.
 */
data class CommuteStats(
    /** Home → Work. */
    val toWork: CommuteLeg?,
    /** Work → Home. */
    val toHome: CommuteLeg?,
)

data class CommuteLeg(
    val samples: Int,
    val medianDurationMs: Long,
    val fastestMs: Long,
    val slowestMs: Long,
    /** Median clock time the user set off, minute-of-day 0..1439, or null. */
    val typicalDepartureMinuteOfDay: Int?
)

class AnalyzeCommuteUseCase @Inject constructor(
    private val placeDao: PlaceDao,
    private val visitDao: VisitDao,
) {
    companion object {
        const val LOOKBACK_DAYS = 30
        /** A leg needs at least this many observations before we report it. */
        const val MIN_COMMUTES = 3
        /** Gaps longer than this aren't a commute — they're a commute plus a life. */
        const val MAX_COMMUTE_MS = 3L * 3_600_000L
    }

    suspend fun analyze(zone: ZoneId = ZoneId.systemDefault()): CommuteStats? {
        val home = placeDao.getHomePlace() ?: return null
        val workIds = placeDao.getByCategory("WORK").map { it.placeId }.toSet()
        if (workIds.isEmpty()) return null

        val cutoff = System.currentTimeMillis() - LOOKBACK_DAYS.toLong() * 86_400_000L
        val homeVisits = visitDao.getByPlaceId(home.placeId)
            .filter { it.departureAt != null && it.arrivalAt >= cutoff }
        val workVisits = workIds.flatMap { visitDao.getByPlaceId(it) }
            .filter { it.departureAt != null && it.arrivalAt >= cutoff }

        return detect(home.placeId, workIds, homeVisits + workVisits, zone)
    }

    /** Pure — exposed for tests and callers holding the home+work visit list. */
    fun detect(
        homeId: Long,
        workIds: Set<Long>,
        visits: List<VisitEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): CommuteStats? {
        val labeled = visits.filter { it.departureAt != null }.sortedBy { it.arrivalAt }

        val toWork = mutableListOf<Long>()
        val toWorkDep = mutableListOf<Int>()
        val toHome = mutableListOf<Long>()
        val toHomeDep = mutableListOf<Int>()

        for (i in 0 until labeled.size - 1) {
            val a = labeled[i]
            val b = labeled[i + 1]
            val gap = b.arrivalAt - a.departureAt!!
            if (gap <= 0L || gap > MAX_COMMUTE_MS) continue
            val aHome = a.placeId == homeId
            val aWork = a.placeId in workIds
            val bHome = b.placeId == homeId
            val bWork = b.placeId in workIds
            when {
                aHome && bWork -> { toWork += gap; toWorkDep += departureMinute(a.departureAt!!, zone) }
                aWork && bHome -> { toHome += gap; toHomeDep += departureMinute(a.departureAt!!, zone) }
            }
        }

        val legToWork = leg(toWork, toWorkDep)
        val legToHome = leg(toHome, toHomeDep)
        if (legToWork == null && legToHome == null) return null
        return CommuteStats(legToWork, legToHome)
    }

    private fun leg(durations: List<Long>, departures: List<Int>): CommuteLeg? {
        if (durations.size < MIN_COMMUTES) return null
        val sorted = durations.sorted()
        val depSorted = departures.sorted()
        return CommuteLeg(
            samples = durations.size,
            medianDurationMs = sorted[sorted.size / 2],
            fastestMs = sorted.first(),
            slowestMs = sorted.last(),
            typicalDepartureMinuteOfDay = if (depSorted.isEmpty()) null else depSorted[depSorted.size / 2]
        )
    }

    private fun departureMinute(ms: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().toSecondOfDay() / 60
}
