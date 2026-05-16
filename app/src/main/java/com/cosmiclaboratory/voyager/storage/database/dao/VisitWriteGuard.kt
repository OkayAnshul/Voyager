package com.cosmiclaboratory.voyager.storage.database.dao

import com.cosmiclaboratory.voyager.storage.database.entity.VisitEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mutex-guarded wrapper around [VisitDao.insertIfNotOverlapping] (H4 fix).
 *
 * The DAO method is `@Transaction` but the SELECT-then-INSERT invariant is enforced in
 * application code, not the schema. Two coroutines hitting `insertIfNotOverlapping`
 * simultaneously can each see "no overlap" and both insert overlapping rows.
 *
 * This wrapper serializes those calls process-wide. The schema also gains a partial
 * unique index in the v3 migration as a belt-and-braces guard against future bypasses.
 */
@Singleton
class VisitWriteGuard @Inject constructor(
    private val visitDao: VisitDao,
) {
    private val mutex = Mutex()

    /** Returns the new visit's id, or -1 if an overlapping visit already exists. */
    suspend fun insertIfNotOverlapping(visit: VisitEntity): Long = mutex.withLock {
        visitDao.insertIfNotOverlapping(visit)
    }
}
