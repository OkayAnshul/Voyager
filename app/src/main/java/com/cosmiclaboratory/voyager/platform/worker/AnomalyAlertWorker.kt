package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.billing.ProEntitlementManager
import com.cosmiclaboratory.voyager.domain.model.DateRange
import com.cosmiclaboratory.voyager.domain.model.enums.AnomalySeverity
import com.cosmiclaboratory.voyager.domain.repository.AnalyticsRepository
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.platform.notification.VoyagerNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Proactively surfaces unusual patterns. Fires each morning, checks the last week
 * for statistically significant anomalies, and posts one notification when any are
 * found — so the user learns about an off-routine week without opening the app.
 *
 * A Pro feature: gated on the `anomalyAlertsEnabled` setting *and* Pro entitlement.
 * Scheduled via WorkerScheduler.
 */
@HiltWorker
class AnomalyAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analyticsRepository: AnalyticsRepository,
    private val settingsRepository: SettingsRepository,
    private val proEntitlementManager: ProEntitlementManager,
    private val notificationManager: VoyagerNotificationManager,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "anomaly_alert"
        private const val LOOKBACK_DAYS = 7L
    }

    override suspend fun doWork(): Result {
        if (!settingsRepository.observeSettings().value.anomalyAlertsEnabled) {
            return Result.success()
        }
        // Advanced insights are a Pro feature — stay silent for free users.
        if (!proEntitlementManager.isPro.value) {
            return Result.success()
        }
        return try {
            val today = LocalDate.now()
            val range = DateRange(
                startDay = today.minusDays(LOOKBACK_DAYS).toString(),
                endDay = today.toString()
            )
            val significant = analyticsRepository.observeAnomalies(range).first()
                .filter { it.severity == AnomalySeverity.SIGNIFICANT }

            if (significant.isEmpty()) return Result.success()

            val title = if (significant.size == 1) {
                "Unusual pattern this week"
            } else {
                "${significant.size} unusual patterns this week"
            }
            notificationManager.showInsight(title, significant.first().humanExplanation)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
