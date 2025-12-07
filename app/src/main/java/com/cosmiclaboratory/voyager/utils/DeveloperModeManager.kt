package com.cosmiclaboratory.voyager.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for developer mode functionality
 *
 * Features:
 * - Tap version 7 times in settings to enable developer mode
 * - Provides access to debug screens and advanced features
 * - Persists developer mode state
 *
 * Usage:
 * - Call registerTap() when user taps version number
 * - Observe isDeveloperModeEnabled flow for current state
 * - Call setDeveloperMode() to manually enable/disable
 */
@Singleton
class DeveloperModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "developer_mode_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val DEVELOPER_MODE_KEY = "developer_mode_enabled"
        private const val TAPS_REQUIRED = 7
        private const val TAP_TIMEOUT_MS = 3000L // 3 seconds
    }

    private var tapCount = 0
    private var lastTapTime = 0L

    private val _isDeveloperModeEnabled = MutableStateFlow(
        prefs.getBoolean(DEVELOPER_MODE_KEY, false)
    )

    /**
     * Flow that emits the current developer mode state
     */
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    /**
     * Register a tap on the version number
     * Returns the number of remaining taps needed, or null if developer mode was enabled
     */
    fun registerTap(): Int? {
        val currentTime = System.currentTimeMillis()

        // Reset tap count if timeout exceeded
        if (currentTime - lastTapTime > TAP_TIMEOUT_MS) {
            tapCount = 0
        }

        lastTapTime = currentTime
        tapCount++

        return when {
            tapCount >= TAPS_REQUIRED -> {
                setDeveloperMode(true)
                null // Developer mode enabled
            }
            else -> {
                TAPS_REQUIRED - tapCount // Remaining taps
            }
        }
    }

    /**
     * Manually set developer mode state
     */
    fun setDeveloperMode(enabled: Boolean) {
        prefs.edit().putBoolean(DEVELOPER_MODE_KEY, enabled).apply()
        _isDeveloperModeEnabled.value = enabled

        if (!enabled) {
            // Reset tap count when disabling
            tapCount = 0
            lastTapTime = 0L
        }
    }

    /**
     * Reset tap counter (useful when navigating away from settings)
     */
    fun resetTapCounter() {
        tapCount = 0
        lastTapTime = 0L
    }
}
