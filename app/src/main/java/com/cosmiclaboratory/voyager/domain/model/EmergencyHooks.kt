package com.cosmiclaboratory.voyager.domain.model

import kotlinx.coroutines.flow.Flow

// Deferred v2+ — defined but unused in v1
interface EmergencySensor {
    fun observeEmergencyTriggers(): Flow<EmergencyTrigger>
    fun getLastKnownSafeState(): SafeState
}

data class EmergencyTrigger(
    val triggerType: EmergencyTriggerType,
    val detectedAt: Long,
    val lastKnownLat: Double?,
    val lastKnownLng: Double?,
    val confidence: Float,
    val supportingData: Map<String, Any>
)

enum class EmergencyTriggerType {
    CRASH_DETECTED, FALL_DETECTED, DEAD_MAN_SWITCH, SOS_MANUAL, ANOMALY_SEVERE
}

data class SafeState(
    val lastKnownPlaceId: Long?,
    val lastSeenAt: Long?,
    val isAtKnownPlace: Boolean
)
