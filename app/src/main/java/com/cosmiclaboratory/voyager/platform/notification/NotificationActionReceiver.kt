package com.cosmiclaboratory.voyager.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.domain.model.enums.CorrectionType
import com.cosmiclaboratory.voyager.domain.model.enums.PauseReason
import com.cosmiclaboratory.voyager.domain.model.enums.StopReason
import com.cosmiclaboratory.voyager.domain.repository.CorrectionRepository
import com.cosmiclaboratory.voyager.platform.coordinator.TrackingRuntimeCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action button taps:
 *  - Confirm / Rename / Dismiss a detected visit
 *  - Pause or Stop tracking from the ongoing notification
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONFIRM_VISIT = "com.cosmiclaboratory.voyager.ACTION_CONFIRM_VISIT"
        const val ACTION_RENAME_PLACE = "com.cosmiclaboratory.voyager.ACTION_RENAME_PLACE"
        const val ACTION_DISMISS_VISIT = "com.cosmiclaboratory.voyager.ACTION_DISMISS_VISIT"
        const val ACTION_PAUSE_TRACKING = "com.cosmiclaboratory.voyager.ACTION_PAUSE_TRACKING"
        const val ACTION_STOP_TRACKING = "com.cosmiclaboratory.voyager.ACTION_STOP_TRACKING"

        const val EXTRA_VISIT_ID = "extra_visit_id"
    }

    @Inject lateinit var correctionRepository: CorrectionRepository
    @Inject lateinit var trackingCoordinator: TrackingRuntimeCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CONFIRM_VISIT -> {
                        val visitId = intent.getLongExtra(EXTRA_VISIT_ID, -1L)
                        if (visitId != -1L) {
                            correctionRepository.applyCorrection(
                                correctionType = CorrectionType.CONFIRM_VISIT,
                                entityType = "visit",
                                entityId = visitId,
                                beforeValue = null,
                                afterValue = "confirmed"
                            )
                        }
                    }

                    ACTION_RENAME_PLACE -> {
                        // Opens the PlaceDetail screen — we launch the main activity
                        // with a deep-link. The actual rename happens in the UI.
                        val visitId = intent.getLongExtra(EXTRA_VISIT_ID, -1L)
                        if (visitId != -1L) {
                            val launchIntent = context.packageManager
                                .getLaunchIntentForPackage(context.packageName)
                                ?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    putExtra("navigate_to", "place_detail")
                                    putExtra("visit_id", visitId)
                                }
                            launchIntent?.let { context.startActivity(it) }
                        }
                    }

                    ACTION_DISMISS_VISIT -> {
                        val visitId = intent.getLongExtra(EXTRA_VISIT_ID, -1L)
                        if (visitId != -1L) {
                            correctionRepository.applyCorrection(
                                correctionType = CorrectionType.DISMISS_VISIT,
                                entityType = "visit",
                                entityId = visitId,
                                beforeValue = null,
                                afterValue = "dismissed"
                            )
                        }
                    }

                    ACTION_PAUSE_TRACKING -> {
                        trackingCoordinator.pause(PauseReason.USER)
                    }

                    ACTION_STOP_TRACKING -> {
                        trackingCoordinator.stop(StopReason.USER)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
