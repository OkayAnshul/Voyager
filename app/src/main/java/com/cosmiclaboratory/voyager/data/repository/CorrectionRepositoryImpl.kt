package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.repository.CorrectionRepository
import com.cosmiclaboratory.voyager.storage.database.dao.CorrectionFeedbackDao
import com.cosmiclaboratory.voyager.storage.database.entity.CorrectionFeedbackEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorrectionRepositoryImpl @Inject constructor(
    private val correctionFeedbackDao: CorrectionFeedbackDao
) : CorrectionRepository {

    override suspend fun applyCorrection(
        correctionType: CorrectionType,
        entityType: String,
        entityId: Long,
        beforeValue: String?,
        afterValue: String?
    ): Result<Unit> = runCatching {
        correctionFeedbackDao.insert(
            CorrectionFeedbackEntity(
                correctionType = correctionType.name,
                entityType = entityType,
                entityId = entityId,
                beforeValueJson = beforeValue,
                afterValueJson = afterValue,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
