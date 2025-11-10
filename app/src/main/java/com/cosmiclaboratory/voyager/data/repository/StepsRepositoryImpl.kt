package com.cosmiclaboratory.voyager.data.repository

import com.cosmiclaboratory.voyager.domain.model.HourlySteps
import com.cosmiclaboratory.voyager.domain.model.StepsSummary
import com.cosmiclaboratory.voyager.domain.model.StrideCalibration
import com.cosmiclaboratory.voyager.domain.repository.StepsRepository
import com.cosmiclaboratory.voyager.domain.util.DayBoundaryResolver
import com.cosmiclaboratory.voyager.storage.database.dao.RawStepSampleDao
import com.cosmiclaboratory.voyager.storage.database.dao.SegmentEvidenceDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepsRepositoryImpl @Inject constructor(
    private val rawStepSampleDao: RawStepSampleDao,
    private val segmentEvidenceDao: SegmentEvidenceDao,
    private val dayBoundaryResolver: DayBoundaryResolver
) : StepsRepository {

    override fun observeDailySteps(dayKey: String): Flow<StepsSummary> {
        val tz = java.util.TimeZone.getDefault().id
        val startMs = dayBoundaryResolver.getDayStartEpochMs(dayKey, tz)
        val endMs = dayBoundaryResolver.getDayEndEpochMs(dayKey, tz)

        return rawStepSampleDao.observeSumStepsByTimeRange(startMs, endMs).map { total ->
            StepsSummary(
                totalSteps = total ?: 0,
                hourlyBreakdown = emptyList(),
                goalProgress = null
            )
        }
    }

    override fun observeHourlySteps(dayKey: String): Flow<List<HourlySteps>> {
        val tz = java.util.TimeZone.getDefault().id
        val startMs = dayBoundaryResolver.getDayStartEpochMs(dayKey, tz)
        val endMs = dayBoundaryResolver.getDayEndEpochMs(dayKey, tz)

        return rawStepSampleDao.observeByTimeRange(startMs, endMs).map { samples ->
            (0..23).map { hour ->
                val hourStart = startMs + hour * 3600_000L
                val hourEnd = hourStart + 3600_000L
                val steps = samples
                    .filter { it.periodStart >= hourStart && it.periodEnd <= hourEnd }
                    .sumOf { it.stepCount }
                HourlySteps(hour = hour, steps = steps)
            }
        }
    }

    override suspend fun getStepsForSegment(segmentId: Long): Int? {
        return segmentEvidenceDao.getBySegmentId(segmentId)?.stepCount
    }

    override suspend fun getUserStrideCalibration(): StrideCalibration {
        return StrideCalibration(strideLengthM = 0.75f, sampleCount = 0, confidence = 0.5f)
    }
}
