package com.cosmiclaboratory.voyager.domain.model

import java.time.LocalDateTime

data class Location(
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val timestamp: LocalDateTime,
    val accuracy: Float,
    val speed: Float? = null,
    val altitude: Double? = null,
    val bearing: Float? = null
)