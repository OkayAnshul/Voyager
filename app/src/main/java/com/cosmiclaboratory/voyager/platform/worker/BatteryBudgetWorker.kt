package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.platform.battery.BatteryBudgetController
import com.cosmiclaboratory.voyager.platform.battery.BatteryUsageReporter
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.entity.HealthLogEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Measures real device discharge against the user's battery budget and steps the
 * tracking tier down when over budget. Runs every 6 hours.
 *
 * The applied change is always a *downgrade* — a budget that auto-upgrades would
 * fight the user's explicit tier choice. Re-raising the tier remains a user action.
 * Each downgrade writes a HealthLog entry so the change is auditable.
 */
@HiltWorker
class BatteryBudgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val batteryUsageReporter: BatteryUsageReporter,
    private val batteryBudgetController: BatteryBudgetController,
    private val settingsRepository: SettingsRepository,
    private val healthLogDao: HealthLogDao,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "battery_budget_check"
    }

    override suspend fun doWork(): Result {
        return try {
            val settings = settingsRepository.observeSettings().value
            if (settings.batteryBudgetPctPerDay <= 0) {
                return Result.success()
            }

            val estimate = batteryUsageReporter.estimate()
            val downgradeTo = batteryBudgetController.recommendDowngrade(
                measuredPctPerDay = estimate.percentPerDay,
                budgetPctPerDay = settings.batteryBudgetPctPerDay,
                currentTier = settings.trackingTier
            ) ?: return Result.success()

            val fromTier = settings.trackingTier.name
            settingsRepository.updateSetting("tracking_tier", downgradeTo.name)

            healthLogDao.insert(
                HealthLogEntity(
                    eventType = HEALTH_EVENT_WORKER_COMPLETE,
                    eventAt = System.currentTimeMillis(),
                    detailsJson = """{"worker":"$WORK_NAME","from":"$fromTier","to":"${downgradeTo.name}","measuredPct":${estimate.percentPerDay},"budget":${settings.batteryBudgetPctPerDay}}""",
                )
            )
            Result.success()
        } catch (e: Exception) {
            healthLogDao.insert(
                HealthLogEntity(
                    eventType = HealthEventTypeWorkerFailure,
                    eventAt = System.currentTimeMillis(),
                    detailsJson = """{"worker":"$WORK_NAME","error":"${e.message?.take(200)}"}""",
                )
            )
            Result.retry()
        }
    }
}
