package com.cosmiclaboratory.voyager.platform.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cosmiclaboratory.voyager.platform.coordinator.TrackingRuntimeCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var coordinator: TrackingRuntimeCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coordinator.restoreFromCrash()
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Failed to restore tracking", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
