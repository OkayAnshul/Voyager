package com.cosmiclaboratory.voyager.domain.sync

import kotlinx.coroutines.flow.Flow

/**
 * The frozen cloud-sync contract.
 *
 * Voyager is on-device-only by default and ships a [NoOpSyncManager]. Freezing this
 * interface now — while the schema already carries the syncable columns
 * (`lastModifiedAt`, `revision`, `deletedAt`) — means optional sync (local-network
 * or, if ethics ever permit, E2EE cloud) can later arrive as a drop-in
 * implementation rather than a schema/architecture refactor. See the hardening
 * audit §8.
 *
 * No production code depends on a real implementation yet; this is the seam.
 */
interface SyncManager {
    /** A batch of local changes not yet replicated, since the given revision watermark. */
    fun pendingChanges(sinceVersion: Long): Flow<ChangeBatch>

    /** Applies a remote batch to the local store. */
    suspend fun apply(batch: ChangeBatch): Result<Unit>

    /** Per-field conflict resolution — Voyager's policy is last-write-wins by revision. */
    fun resolveConflict(localRevision: Long, remoteRevision: Long): Resolution
}

/** A replication unit — opaque per-entity changes carried as serialized payloads. */
data class ChangeBatch(
    val sinceVersion: Long,
    val changes: List<EntityChange>
)

data class EntityChange(
    val entityType: String,
    /** Stable cross-device id (the syncable `clientId`/UUID), not the local row id. */
    val clientId: String,
    val revision: Long,
    /** Non-null marks a tombstone (soft delete) to replicate. */
    val deletedAt: Long?,
    val payloadJson: String
)

/** The outcome of reconciling a local and a remote version of the same row. */
sealed interface Resolution {
    data object KeepLocal : Resolution
    data object KeepRemote : Resolution
}
