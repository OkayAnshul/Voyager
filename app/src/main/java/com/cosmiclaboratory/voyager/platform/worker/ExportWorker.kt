package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cosmiclaboratory.voyager.domain.model.enums.ExportFormat
import com.cosmiclaboratory.voyager.domain.repository.ExportRepository
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * On-demand export worker. Takes format and dayKey/range from inputData.
 *
 * Input data keys:
 * - KEY_FORMAT: ExportFormat name (GPX, GEOJSON, VOYAGER_JSON, CSV)
 * - KEY_DAY_KEY: single day key (YYYY-MM-DD) for single-day export
 * - KEY_START_DAY / KEY_END_DAY: range export (overrides KEY_DAY_KEY)
 *
 * Output data:
 * - KEY_OUTPUT_URI: content URI string of the exported file
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val exportRepository: ExportRepository,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "export"
        const val KEY_FORMAT = "format"
        const val KEY_DAY_KEY = "dayKey"
        const val KEY_START_DAY = "startDay"
        const val KEY_END_DAY = "endDay"
        const val KEY_OUTPUT_URI = "outputUri"
    }

    override suspend fun doWork(): Result {
        return try {
            val formatName = inputData.getString(KEY_FORMAT)
                ?: return failWithMessage("Missing format in input data")
            val format = try {
                ExportFormat.valueOf(formatName)
            } catch (e: IllegalArgumentException) {
                return failWithMessage("Invalid format: $formatName")
            }

            val dayKey = inputData.getString(KEY_DAY_KEY)
            val startDay = inputData.getString(KEY_START_DAY)
            val endDay = inputData.getString(KEY_END_DAY)

            val exportResult = when {
                startDay != null && endDay != null -> {
                    // Range export
                    exportRepository.exportRange(
                        range = com.cosmiclaboratory.voyager.domain.model.DateRange(startDay, endDay),
                        format = format,
                    )
                }
                dayKey != null -> {
                    exportRepository.exportDay(dayKey, format)
                }
                else -> {
                    return failWithMessage("Missing dayKey or startDay/endDay in input data")
                }
            }

            exportResult.fold(
                onSuccess = { uri ->
                    logCompletion(formatName, dayKey ?: "$startDay..$endDay")
                    Result.success(workDataOf(KEY_OUTPUT_URI to uri.toString()))
                },
                onFailure = { e ->
                    logFailure(e as? Exception ?: Exception(e.message))
                    Result.retry()
                },
            )
        } catch (e: Exception) {
            logFailure(e)
            Result.retry()
        }
    }

    private suspend fun failWithMessage(message: String): Result {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HealthEventTypeWorkerFailure,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","error":"$message"}""",
            )
        )
        return Result.failure(workDataOf("error" to message))
    }

    private suspend fun logCompletion(format: String, scope: String) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HEALTH_EVENT_WORKER_COMPLETE,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","format":"$format","scope":"$scope"}""",
            )
        )
    }

    private suspend fun logFailure(e: Exception) {
        healthLogDao.insert(
            HealthLogEntity(
                eventType = HealthEventTypeWorkerFailure,
                eventAt = System.currentTimeMillis(),
                detailsJson = """{"worker":"$WORK_NAME","error":"${e.message?.take(200)}"}""",
            )
        )
    }
}
