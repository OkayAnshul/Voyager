package com.cosmiclaboratory.voyager.storage.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "visits",
    indices = [
        // Unique: belt-and-braces against exact-duplicate visit inserts (H4).
        // The VisitWriteGuard mutex is the primary defence; this catches anything
        // that ever bypasses it. Two visits to one place cannot share a ms-precise arrivalAt.
        Index(value = ["placeId", "arrivalAt"], unique = true),
        Index(value = ["dayKey", "arrivalAt"])
    ]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    val visitId: Long = 0,
    val placeId: Long,
    val arrivalAt: Long,
    val departureAt: Long? = null,
    val dwellMs: Long? = null,
    val source: String, // LIVE_DETECTION/BATCH_DISCOVERY/USER_CREATED
    val confidence: Float = 0.5f,
    val supersedesVisitId: Long? = null,
    val isUserCorrected: Boolean = false,
    val dayKey: String,
    val centroidLat: Double? = null,
    val centroidLng: Double? = null,
    // Multi-user scoping (v8) — inert until sync/multi-user ships; default = install id.
    val userId: String = "",
    // Cloud-ready audit columns (v3). Inert until sync ships — see MIGRATION_2_3.
    val lastModifiedAt: Long = 0L,
    val revision: Long = 1L,
    val deletedAt: Long? = null
)
