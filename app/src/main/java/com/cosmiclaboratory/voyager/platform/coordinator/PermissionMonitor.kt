package com.cosmiclaboratory.voyager.platform.coordinator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.cosmiclaboratory.voyager.domain.model.enums.PermissionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Granular snapshot of every permission the app cares about. */
data class PermissionSnapshot(
    val hasFineLocation: Boolean = false,
    val hasCoarseLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasActivityRecognition: Boolean = false,
    val hasNotifications: Boolean = false,
    val isBatteryOptimizationExempt: Boolean = false
) {
    val hasAnyLocation get() = hasFineLocation || hasCoarseLocation

    /**
     * True when the user granted only "Approximate" location (Android 12+ coarse-only).
     * Samples land on a ~1–3 km grid, so precise place clustering produces nonsense —
     * this is the trigger for rough-timeline mode (city-level clustering + banner).
     */
    val isApproximateLocationOnly get() = hasCoarseLocation && !hasFineLocation

    val isComplete get() = hasFineLocation && hasBackgroundLocation && hasActivityRecognition && hasNotifications && isBatteryOptimizationExempt
    val missingCount get() = listOf(
        !hasFineLocation,
        !hasBackgroundLocation,
        !hasActivityRecognition,
        !hasNotifications,
        !isBatteryOptimizationExempt
    ).count { it }
}

@Singleton
class PermissionMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _permissionState = MutableStateFlow(checkCurrentState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _snapshot = MutableStateFlow(buildSnapshot())
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()

    fun refresh() {
        _permissionState.value = checkCurrentState()
        _snapshot.value = buildSnapshot()
    }

    private fun buildSnapshot(): PermissionSnapshot {
        fun has(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryExempt = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        return PermissionSnapshot(
            hasFineLocation = has(Manifest.permission.ACCESS_FINE_LOCATION),
            hasCoarseLocation = has(Manifest.permission.ACCESS_COARSE_LOCATION),
            hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                has(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true,
            hasActivityRecognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                has(Manifest.permission.ACTIVITY_RECOGNITION) else true,
            hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                has(Manifest.permission.POST_NOTIFICATIONS) else true,
            isBatteryOptimizationExempt = batteryExempt
        )
    }

    fun checkCurrentState(): PermissionState {
        val s = buildSnapshot()
        return when {
            s.hasFineLocation && s.hasBackgroundLocation && s.hasActivityRecognition -> PermissionState.FULL
            s.hasFineLocation && s.hasBackgroundLocation -> PermissionState.FINE_LOCATION
            s.hasFineLocation || s.hasCoarseLocation -> PermissionState.COARSE_LOCATION
            s.hasActivityRecognition -> PermissionState.NO_LOCATION_WITH_AR
            else -> PermissionState.NOTHING
        }
    }

    fun hasLocationPermission(): Boolean {
        val state = checkCurrentState()
        return state == PermissionState.FULL || state == PermissionState.FINE_LOCATION || state == PermissionState.COARSE_LOCATION
    }

    fun getPermissionSnapshot(): String {
        return when (checkCurrentState()) {
            PermissionState.FINE_LOCATION -> "fine"
            PermissionState.COARSE_LOCATION -> "coarse"
            else -> "none"
        }
    }
}
