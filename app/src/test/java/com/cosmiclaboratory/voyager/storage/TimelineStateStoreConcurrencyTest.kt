package com.cosmiclaboratory.voyager.storage

import com.cosmiclaboratory.voyager.domain.time.Clock
import com.cosmiclaboratory.voyager.storage.database.dao.CurrentRuntimeStateDao
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.CurrentRuntimeStateEntity
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the H1 fix: concurrent read-modify-write updates to the single runtime-state
 * row do not lose writes.
 *
 * [RacyFakeDao] deliberately yields between the read and the write inside atomicUpdate's
 * implementation path, so without the Mutex in [TimelineStateStore.update] the test
 * would observe lost updates.
 */
class TimelineStateStoreConcurrencyTest {

    /**
     * In-memory DAO whose get()/upsert() straddle a coroutine yield point. atomicUpdate
     * is the DAO's own default method: get() -> transform -> upsert(). The yield inside
     * get() opens the interleaving window the H1 race needs.
     */
    private class RacyFakeDao : CurrentRuntimeStateDao {
        @Volatile private var row: CurrentRuntimeStateEntity = CurrentRuntimeStateEntity()

        override suspend fun upsert(state: CurrentRuntimeStateEntity) {
            yield()
            row = state
        }

        override suspend fun get(): CurrentRuntimeStateEntity {
            yield()
            return row
        }

        override fun observe(): Flow<CurrentRuntimeStateEntity?> = flowOf(row)
    }

    @Test
    fun `concurrent updates do not lose writes`() = runTest {
        val dao = RacyFakeDao()
        val store = TimelineStateStore(
            currentRuntimeStateDao = dao,
            healthLogDao = mockk<HealthLogDao>(relaxed = true),
            clock = mockk<Clock>(relaxed = true),
        )

        val updateCount = 200
        withContext(Dispatchers.Default) {
            (1..updateCount).map {
                async {
                    // Each update bumps lastAcceptedSampleId by 1 from whatever it reads.
                    store.update { state ->
                        state.copy(
                            lastAcceptedSampleId = (state.lastAcceptedSampleId ?: 0L) + 1L
                        )
                    }
                }
            }.awaitAll()
        }

        // With the Mutex every increment is applied; final value equals the update count.
        assertEquals(updateCount.toLong(), store.getState().lastAcceptedSampleId)
    }
}
