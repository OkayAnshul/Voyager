package com.cosmiclaboratory.voyager.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

sealed class DateRangePeriod {
    data object Today : DateRangePeriod()
    data object ThisWeek : DateRangePeriod()
    data object ThisMonth : DateRangePeriod()
    data object Last30Days : DateRangePeriod()
    data class Custom(val start: LocalDate, val end: LocalDate) : DateRangePeriod()

    fun toDateTimeRange(): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        return when (this) {
            is Today -> LocalDate.now().atStartOfDay() to now
            is ThisWeek -> {
                val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                weekStart.atStartOfDay() to now
            }
            is ThisMonth -> {
                val monthStart = LocalDate.now().withDayOfMonth(1)
                monthStart.atStartOfDay() to now
            }
            is Last30Days -> now.minusDays(30) to now
            is Custom -> start.atStartOfDay() to end.atTime(LocalTime.MAX)
        }
    }

    fun displayLabel(): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d")
        return when (this) {
            is Today -> "Today"
            is ThisWeek -> "This Week"
            is ThisMonth -> "This Month"
            is Last30Days -> "Last 30 Days"
            is Custom -> "${start.format(formatter)} – ${end.format(formatter)}"
        }
    }
}
