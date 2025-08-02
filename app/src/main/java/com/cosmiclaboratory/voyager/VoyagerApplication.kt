package com.cosmiclaboratory.voyager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cosmiclaboratory.voyager.pipeline.PipelineConsumer
import com.cosmiclaboratory.voyager.platform.worker.WorkerScheduler
import com.cosmiclaboratory.voyager.storage.TimelineStateStore
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
