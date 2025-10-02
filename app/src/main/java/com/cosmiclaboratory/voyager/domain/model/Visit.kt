package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

data class Visit(
    val id: Long = 0L,
    val placeId: Long,
    val entryTime: LocalDateTime,
    val exitTime: LocalDateTime? = null,
    val duration: Long = 0L, // in milliseconds
    val confidence: Float = 1.0f
)

data class VisitSummary(
    val place: Place,
    val totalDuration: Long,
    val visitCount: Int,
    val averageDuration: Long,
    val lastVisit: LocalDateTime?
)