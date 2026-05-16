package com.cosmiclaboratory.voyager

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cosmiclaboratory.voyager.pipeline.PipelineConsumer
import com.cosmiclaboratory.voyager.platform.crash.LocalCrashHandler
import com.cosmiclaboratory.voyager.platform.worker.WorkerScheduler
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
import com.cosmiclaboratory.voyager.storage.database.dao.HealthLogDao
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // Install crash handler before anything else so we capture init-time crashes too.
        LocalCrashHandler.install(this)

        if (BuildConfig.DEBUG) {
            installStrictMode()
        }

        super.onCreate()

        // Flush any pending crash files to HealthLog now that DI is ready.
        LocalCrashHandler.flushPending(this, healthLogDao, applicationScope)

        applicationScope.launch {
            try {
                timelineStateStore.initialize()
            } catch (e: Exception) {
                android.util.Log.e("VoyagerApp", "Failed to initialize state store", e)
            }
        }

        // Start pipeline consumer — processes location samples through segmentation pipeline
        pipelineConsumer.start(applicationScope)

        // Schedule all periodic workers (place discovery, geocode, rollups, etc.)
        WorkerScheduler.scheduleAll(androidx.work.WorkManager.getInstance(this))
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
