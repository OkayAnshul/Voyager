package com.cosmiclaboratory.voyager.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Centralized permission management for Voyager app
 */
object PermissionManager {
    
    // Permission request codes
    const val REQUEST_LOCATION_PERMISSION = 1001
    const val REQUEST_BACKGROUND_LOCATION_PERMISSION = 1002
    const val REQUEST_NOTIFICATION_PERMISSION = 1003
    
    // Required permissions
    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    val BACKGROUND_LOCATION_PERMISSION = arrayOf(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    
    val NOTIFICATION_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
    
    /**
     * Check if basic location permissions are granted
     */
    fun hasLocationPermissions(context: Context): Boolean {
        return LOCATION_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if background location permission is granted
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Background location is automatically granted on Android < 10
            hasLocationPermissions(context)
        }
    }
    
    /**
     * Check if notification permissions are granted
     */
    fun hasNotificationPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Notifications don't require permission on Android < 13
            true
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasLocationPermissions(context) && 
               hasBackgroundLocationPermission(context) && 
               hasNotificationPermissions(context)
    }
    
    /**
     * Request basic location permissions
     */
    fun requestLocationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            LOCATION_PERMISSIONS,
            REQUEST_LOCATION_PERMISSION
        )
    }
    
    /**
     * Request background location permission (must be requested separately on Android 10+)
     */
    fun requestBackgroundLocationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                activity,
                BACKGROUND_LOCATION_PERMISSION,
                REQUEST_BACKGROUND_LOCATION_PERMISSION
            )
        }
    }
    
    /**
     * Request notification permissions (Android 13+)
     */
    fun requestNotificationPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                NOTIFICATION_PERMISSIONS,
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }
    
    /**
     * Check if permission was permanently denied
     */
    fun isPermissionPermanentlyDenied(activity: Activity, permission: String): Boolean {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
               ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Open app settings for manual permission grant
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    /**
     * Get permission rationale message for user education
     */
    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> 
                "Voyager needs location access to track your movements and detect places you visit. " +
                "Your location data is stored securely on your device and never shared."
            
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> 
                "Background location access allows Voyager to track your movements even when the app " +
                "is closed. This enables automatic place detection and visit recording. " +
                "You can always disable this in Settings."
            
            Manifest.permission.POST_NOTIFICATIONS -> 
                "Notification permission allows Voyager to inform you when you arrive at or leave " +
                "places. You can customize which notifications you receive in Settings."
            
            else -> "This permission is required for the app to function properly."
        }
    }
    
    /**
     * Get user-friendly permission status
     * Updated to reflect that background location is REQUIRED for core functionality
     */
    fun getPermissionStatus(context: Context): PermissionStatus {
        return when {
            // No location access - app cannot function
            !hasLocationPermissions(context) -> PermissionStatus.LOCATION_DENIED
            
            // Complete functionality: background location + notifications
            hasLocationPermissions(context) && 
            hasBackgroundLocationPermission(context) && 
            hasNotificationPermissions(context) -> PermissionStatus.ALL_GRANTED
            
            // Core functionality available: background location enabled
            hasLocationPermissions(context) && 
            hasBackgroundLocationPermission(context) -> PermissionStatus.LOCATION_FULL_ACCESS
            
            // Limited functionality: foreground location only - background location REQUIRED
            hasLocationPermissions(context) && !hasBackgroundLocationPermission(context) -> 
                PermissionStatus.LOCATION_BASIC_ONLY
            
            // Fallback for edge cases
            else -> PermissionStatus.PARTIAL_GRANTED
        }
    }
    
    /**
     * Get next permission to request based on current status
     * Note: Background location is REQUIRED for core app functionality, not optional
     */
    fun getNextPermissionToRequest(context: Context): String? {
        return when {
            !hasLocationPermissions(context) -> Manifest.permission.ACCESS_FINE_LOCATION
            !hasBackgroundLocationPermission(context) -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
            !hasNotificationPermissions(context) -> Manifest.permission.POST_NOTIFICATIONS
            else -> null
        }
    }
    
    /**
     * Get next optional permission that could enhance the user experience
     */
    fun getNextOptionalPermission(context: Context): String? {
        return when {
            hasLocationPermissions(context) && !hasBackgroundLocationPermission(context) -> 
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            hasLocationPermissions(context) && !hasNotificationPermissions(context) -> 
                Manifest.permission.POST_NOTIFICATIONS
            else -> null
        }
    }
}

/**
 * Permission status enum for UI state management
 * Reflects the reality that background location is REQUIRED for core functionality
 */
enum class PermissionStatus {
    LOCATION_DENIED,                    // No location access - app cannot function
    LOCATION_BASIC_ONLY,               // Foreground only - LIMITED functionality
    LOCATION_GRANTED_BACKGROUND_NEEDED, // Backward compatibility alias
    LOCATION_FULL_ACCESS,              // Background enabled - CORE functionality works
    ALL_GRANTED,                       // Full access including notifications
    PARTIAL_GRANTED                    // Legacy state for compatibility
}

/**
 * Data class for permission request results
 */
data class PermissionResult(
    val permission: String,
    val isGranted: Boolean,
    val isPermanentlyDenied: Boolean,
    val shouldShowRationale: Boolean
)