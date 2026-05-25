package com.cosmiclaboratory.voyager.data.sync

import com.cosmiclaboratory.voyager.domain.sync.ChangeBatch
import com.cosmiclaboratory.voyager.domain.sync.Resolution
import com.cosmiclaboratory.voyager.domain.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [SyncManager] — Voyager is on-device-only, so nothing replicates.
 *
 * It still implements the real last-write-wins conflict policy so that a future
 * sync implementation (and its tests) can share the same resolution rule.
 */
@Singleton
class NoOpSyncManager @Inject constructor() : SyncManager {

    override fun pendingChanges(sinceVersion: Long): Flow<ChangeBatch> = emptyFlow()

    override suspend fun apply(batch: ChangeBatch): Result<Unit> = Result.success(Unit)

    /** Last-write-wins by revision; ties keep local (the device's own edit). */
    override fun resolveConflict(localRevision: Long, remoteRevision: Long): Resolution =
        if (remoteRevision > localRevision) Resolution.KeepRemote else Resolution.KeepLocal
}
