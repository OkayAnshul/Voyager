package com.cosmiclaboratory.voyager.domain.model

data class UserCalibrationProfile(
    val arWeight: Float = 0.6f,
    val speedHeuristicWeight: Float = 0.25f,
    val stepRateWeight: Float = 0.15f,
    val minDwellMinutes: Int = 5,
    val placeMatchRadiusBoostM: Int = 0,
    val regionOverrides: Map<String, RegionCalibration> = emptyMap()
)

data class RegionCalibration(
    val geohash: String,
    val hdbscanMinClusterSizeOverride: Int? = null,
    val hdbscanEpsilonOverride: Double? = null
)
