package com.cosmiclaboratory.voyager

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cosmiclaboratory.voyager.domain.repository.SettingsRepository
import com.cosmiclaboratory.voyager.domain.usecase.IntegrityRepairUseCase
import com.cosmiclaboratory.voyager.pipeline.PipelineConsumer
import com.cosmiclaboratory.voyager.platform.crash.LocalCrashHandler
import com.cosmiclaboratory.voyager.platform.notification.VoyagerNotificationManager
import com.cosmiclaboratory.voyager.platform.worker.WorkerScheduler
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VoyagerApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var timelineStateStore: TimelineStateStore
    @Inject lateinit var pipelineConsumer: PipelineConsumer
    @Inject lateinit var healthLogDao: HealthLogDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var integrityRepairUseCase: IntegrityRepairUseCase
    @Inject lateinit var rawLocationSampleDao: RawLocationSampleDao
    @Inject lateinit var notificationManager: VoyagerNotificationManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** Cutoff window for closing visits left open by the previous process.
         *  Visits whose arrival precedes (lastKnownAliveTimestamp - 30 min) and
         *  still have departureAt == null are considered stranded and closed.
         *  30 min covers typical Doze cycles without false-closing genuine
         *  long stays still in progress. */
        private const val BOOTSTRAP_STALE_GAP_MS = 30L * 60_000L
    }

    override fun onCreate() {
        // Install crash handler before anything else so we capture init-time crashes too.
        LocalCrashHandler.install(this)

        if (BuildConfig.DEBUG) {
            installStrictMode()
        }

        super.onCreate()

        // Flush any pending crash files to HealthLog now that DI is ready.
        LocalCrashHandler.flushPending(this, healthLogDao, applicationScope)

        // Create notification channels up front so worker-posted notifications are never
        // silently dropped when they fire before the tracking service has ever run.
        notificationManager.createChannels()

        applicationScope.launch {
            try {
                timelineStateStore.initialize()
                // Close any visits left open by the previous process before the
                // pipeline resumes. Otherwise lastConfirmedVisitId in the state
                // store points at a stranded visit and the timeline shows
                // "still at Place" until a new departure event happens.
                repairStrandedVisits()
            } catch (e: Exception) {
                android.util.Log.e("VoyagerApp", "Failed to initialize state store", e)
            }
        }

        // Start pipeline consumer — processes location samples through segmentation pipeline
        pipelineConsumer.start(applicationScope)

        // Schedule all periodic workers (place discovery, geocode, rollups, etc.).
        // WorkManager enqueue is disk I/O — keep it off the cold-start critical path.
        applicationScope.launch {
            WorkerScheduler.scheduleAll(
                androidx.work.WorkManager.getInstance(this@VoyagerApplication),
                settingsRepository.observeSettings().value
            )
        }
    }

    private suspend fun repairStrandedVisits() {
        val latest = rawLocationSampleDao.getLatest()
        val lastKnownAlive = latest?.capturedAt ?: System.currentTimeMillis()
        val cutoffMs = lastKnownAlive - BOOTSTRAP_STALE_GAP_MS
        // Select visits stranded for >30 min, but close them at the last sample we actually
        // recorded — not the selection cutoff, which would truncate the dwell by 30 min (T3).
        val closed = integrityRepairUseCase.closeStaleVisits(
            staleBeforeMs = cutoffMs,
            closeAtMs = lastKnownAlive,
        )
        if (closed > 0) {
            timelineStateStore.setLastConfirmedVisitId(null)
        }
    }

    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
