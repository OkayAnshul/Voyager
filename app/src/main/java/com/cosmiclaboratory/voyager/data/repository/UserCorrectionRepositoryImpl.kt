package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.data.database.dao.UserCorrectionDao
import com.cosmiclaboratory.voyager.data.mapper.toDomainModel
import com.cosmiclaboratory.voyager.data.mapper.toDomainModels
import com.cosmiclaboratory.voyager.data.mapper.toEntity
import com.cosmiclaboratory.voyager.domain.model.UserCorrection
import com.cosmiclaboratory.voyager.domain.model.CorrectionType
import com.cosmiclaboratory.voyager.domain.repository.UserCorrectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserCorrectionRepositoryImpl @Inject constructor(
    private val userCorrectionDao: UserCorrectionDao
) : UserCorrectionRepository {

    override fun getAllCorrections(): Flow<List<UserCorrection>> {
        return userCorrectionDao.getAllCorrections().map { it.toDomainModels() }
    }

    override fun getCorrectionsForPlace(placeId: Long): Flow<List<UserCorrection>> {
        return userCorrectionDao.getCorrectionsForPlace(placeId).map { it.toDomainModels() }
    }

    override fun getCorrectionsByType(correctionType: CorrectionType): Flow<List<UserCorrection>> {
        return userCorrectionDao.getCorrectionsByType(correctionType).map { it.toDomainModels() }
    }

    override suspend fun getUnappliedCorrections(): List<UserCorrection> {
        return userCorrectionDao.getUnappliedCorrections().toDomainModels()
    }

    override suspend fun getCorrectionById(id: Long): UserCorrection? {
        return userCorrectionDao.getCorrectionById(id)?.toDomainModel()
    }

    override suspend fun getCorrectionCountByType(correctionType: CorrectionType): Int {
        return userCorrectionDao.getCorrectionCountByType(correctionType)
    }

    override suspend fun getRecentCorrectionCount(since: LocalDateTime): Int {
        return userCorrectionDao.getRecentCorrectionCount(since)
    }

    override suspend fun insertCorrection(correction: UserCorrection): Long {
        return userCorrectionDao.insertCorrection(correction.toEntity())
    }

    override suspend fun updateCorrection(correction: UserCorrection) {
        userCorrectionDao.updateCorrection(correction.toEntity())
    }

    override suspend fun deleteCorrection(correction: UserCorrection) {
        userCorrectionDao.deleteCorrection(correction.toEntity())
    }

    override suspend fun deleteCorrectionById(id: Long) {
        userCorrectionDao.deleteCorrectionById(id)
    }

    override suspend fun markCorrectionAsApplied(id: Long) {
        userCorrectionDao.markCorrectionAsApplied(id)
    }

    override suspend fun deleteOldCorrections(before: LocalDateTime) {
        userCorrectionDao.deleteOldCorrections(before)
    }
}
