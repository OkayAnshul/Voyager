package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "current_runtime_state")
data class CurrentRuntimeStateEntity(
    @PrimaryKey
    val id: Int = 1, // singleton
    val activeSessionId: Long? = null,
    val currentSegmentId: Long? = null,
    val pendingVisitCandidateJson: String? = null,
    val lastConfirmedVisitId: Long? = null,
    val lastAcceptedSampleId: Long? = null,
    val lastAcceptedAt: Long? = null,
    val livePlaceStateJson: String? = null,
    val lastWorkerHeartbeatsJson: String? = null,
    val stateVersion: Long = 0,
    val lastPipelineLatencyMs: Long? = null,
    val lastMotionState: String? = null,
    val lastDepartedCentroidLat: Double? = null,
    val lastDepartedCentroidLng: Double? = null,
    val lastDepartureTime: Long? = null,
    val lastDepartedVisitId: Long? = null
)
