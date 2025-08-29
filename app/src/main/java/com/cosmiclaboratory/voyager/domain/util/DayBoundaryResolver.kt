package com.cosmiclaboratory.voyager.domain.util

import com.cosmiclaboratory.voyager.domain.model.enums.DayBoundaryMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayBoundaryResolver @Inject constructor() {

    private val dayKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun resolveDayKey(
        instantEpochMs: Long,
        mode: DayBoundaryMode,
        homeTimeZone: String,
        sampleTimeZone: String? = null
    ): String {
        val instant = Instant.ofEpochMilli(instantEpochMs)
        val zoneId = when (mode) {
            DayBoundaryMode.HOME_TIMEZONE -> ZoneId.of(homeTimeZone)
            DayBoundaryMode.TRAVEL_AWARE -> ZoneId.of(sampleTimeZone ?: homeTimeZone)
        }
        val localDate = instant.atZone(zoneId).toLocalDate()
        return localDate.format(dayKeyFormatter)
    }

    fun getDayStartEpochMs(dayKey: String, timeZone: String): Long {
        val date = LocalDate.parse(dayKey, dayKeyFormatter)
        val zoneId = ZoneId.of(timeZone)
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun getDayEndEpochMs(dayKey: String, timeZone: String): Long {
        val date = LocalDate.parse(dayKey, dayKeyFormatter)
        val zoneId = ZoneId.of(timeZone)
        return date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun today(timeZone: String): String {
        return LocalDate.now(ZoneId.of(timeZone)).format(dayKeyFormatter)
    }
}
