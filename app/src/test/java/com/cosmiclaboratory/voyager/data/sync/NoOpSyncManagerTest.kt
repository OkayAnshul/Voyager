package com.cosmiclaboratory.voyager.data.sync

import com.cosmiclaboratory.voyager.domain.sync.ChangeBatch
import com.cosmiclaboratory.voyager.domain.sync.Resolution
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the default sync manager is inert and applies last-write-wins. */
class NoOpSyncManagerTest {

    private val manager = NoOpSyncManager()

    @Test
    fun `pendingChanges emits nothing`() = runTest {
        assertTrue(manager.pendingChanges(sinceVersion = 0).toList().isEmpty())
    }

    @Test
    fun `apply is a no-op success`() = runTest {
        val result = manager.apply(ChangeBatch(sinceVersion = 0, changes = emptyList()))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `conflict keeps the higher revision, ties keep local`() {
        assertEquals(Resolution.KeepRemote, manager.resolveConflict(localRevision = 1, remoteRevision = 2))
        assertEquals(Resolution.KeepLocal, manager.resolveConflict(localRevision = 3, remoteRevision = 2))
        assertEquals(Resolution.KeepLocal, manager.resolveConflict(localRevision = 5, remoteRevision = 5))
    }
}
