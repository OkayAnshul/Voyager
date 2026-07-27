package com.cosmiclaboratory.voyager.platform.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.usecase.DetectRoutineBreaksUseCase
import com.cosmiclaboratory.voyager.domain.usecase.RoutineBreak
import com.cosmiclaboratory.voyager.platform.notification.VoyagerNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Surfaces a broken routine the user would otherwise not notice — "you usually go to the Gym on
 * Tuesdays, but nothing's logged today." Runs each evening (after the usual hour is well past, so
 * an unfinished day isn't judged early) and posts at most one nudge; the manager further throttles
 * it to once a day and suppresses it outside daytime hours, so it informs without nagging.
 *
 * Gated on the discovery/routine-alerts setting ([SettingsRepository]) so the user can silence it.
 */
@HiltWorker
class RoutineNudgeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val detectRoutineBreaks: DetectRoutineBreaksUseCase,
    private val settingsRepository: SettingsRepository,
    private val notificationManager: VoyagerNotificationManager,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "routine_nudge"
    }

    override suspend fun doWork(): Result {
        if (!settingsRepository.observeSettings().value.placeConfirmationPromptsEnabled) {
            return Result.success()
        }
        return try {
            val missed = detectRoutineBreaks.forToday()
                .firstOrNull { it.kind == RoutineBreak.Kind.MISSED }
            if (missed != null) {
                val name = missed.placeName ?: "a regular spot"
                notificationManager.showRoutineNudge(
                    title = "Off your routine today",
                    body = "You usually visit $name around ${formatHour(missed.expectedHour)} — nothing logged yet today.",
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun formatHour(hour24: Int): String {
        val ampm = if (hour24 < 12) "AM" else "PM"
        val hour12 = ((hour24 + 11) % 12) + 1
        return "$hour12 $ampm"
    }
}
