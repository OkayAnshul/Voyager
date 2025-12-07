package com.cosmiclaboratory.voyager.domain.repository

import com.cosmiclaboratory.voyager.domain.model.UserCorrection
import com.cosmiclaboratory.voyager.domain.model.CorrectionType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

interface UserCorrectionRepository {

    fun getAllCorrections(): Flow<List<UserCorrection>>

    fun getCorrectionsForPlace(placeId: Long): Flow<List<UserCorrection>>

    fun getCorrectionsByType(correctionType: CorrectionType): Flow<List<UserCorrection>>

    suspend fun getUnappliedCorrections(): List<UserCorrection>

    suspend fun getCorrectionById(id: Long): UserCorrection?

    suspend fun getCorrectionCountByType(correctionType: CorrectionType): Int

    suspend fun getRecentCorrectionCount(since: LocalDateTime): Int

    suspend fun insertCorrection(correction: UserCorrection): Long

    suspend fun updateCorrection(correction: UserCorrection)

    suspend fun deleteCorrection(correction: UserCorrection)

    suspend fun deleteCorrectionById(id: Long)

    suspend fun markCorrectionAsApplied(id: Long)

    suspend fun deleteOldCorrections(before: LocalDateTime)
}
