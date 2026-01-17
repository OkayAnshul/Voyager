package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Voyager Navigation Destinations
 *
 * Defines all navigation routes and bottom nav items.
 *
 * **Matrix UI Update**: Categories added to bottom nav, Settings moved to top-right menu
 */
sealed class VoyagerDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    // ========================================================================
    // BOTTOM NAVIGATION SCREENS (5 items)
    // ========================================================================

    object Dashboard : VoyagerDestination(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Filled.Home
    )

    object Map : VoyagerDestination(
        route = "map",
        title = "Map",
        icon = Icons.Filled.Place
    )

    object Timeline : VoyagerDestination(
        route = "timeline",
        title = "Timeline",
        icon = Icons.AutoMirrored.Filled.List
    )

    /**
     * Categories - NEW for Matrix UI
     *
     * Per-category visibility management.
     * Allows users to control which categories show on Map/Timeline.
     */
    object Categories : VoyagerDestination(
        route = "categories",
        title = "Categories",
        icon = Icons.Filled.Category // Material Icons Extended
    )

    object Insights : VoyagerDestination(
        route = "insights",
        title = "Insights",
        icon = Icons.Filled.Info
    )

    // ========================================================================
    // MENU SCREENS (Top-right menu, not in bottom nav)
    // ========================================================================

    /**
     * Settings - Moved from bottom nav to top-right menu
     */
    object Settings : VoyagerDestination(
        route = "settings",
        title = "Settings",
        icon = Icons.Filled.Settings
    )

    object EnhancedSettings : VoyagerDestination(
        route = "enhanced_settings",
        title = "Power User Settings",
        icon = Icons.Filled.Settings
    )

    object ExpertSettings : VoyagerDestination(
        route = "expert_settings",
        title = "Expert Settings",
        icon = Icons.Filled.Build
    )

    // ========================================================================
    // NESTED SCREENS (Accessible from other screens)
    // ========================================================================

    object PermissionGateway : VoyagerDestination(
        route = "permission_gateway",
        title = "Permissions",
        icon = Icons.Filled.Lock
    )

    object WeeklyComparison : VoyagerDestination(
        route = "weekly_comparison",
        title = "Weekly Comparison",
        icon = Icons.Filled.DateRange
    )

    object PlacePatterns : VoyagerDestination(
        route = "place_patterns",
        title = "Patterns & Insights",
        icon = Icons.Filled.Star
    )

    object MovementAnalytics : VoyagerDestination(
        route = "movement_analytics",
        title = "Movement & Time Patterns",
        icon = Icons.Filled.Star
    )

    object SocialHealthAnalytics : VoyagerDestination(
        route = "social_health_analytics",
        title = "Social & Health Insights",
        icon = Icons.Filled.Favorite
    )

    object PlaceReview : VoyagerDestination(
        route = "place_review",
        title = "Place Reviews",
        icon = Icons.Filled.Check
    )

    object DeveloperProfile : VoyagerDestination(
        route = "developer_profile",
        title = "About Developer",
        icon = Icons.Filled.Person
    )

    // ========================================================================
    // DEBUG SCREENS (Developer mode only)
    // ========================================================================

    object DebugDataInsertion : VoyagerDestination(
        route = "debug_data_insertion",
        title = "Debug: Insert Test Data",
        icon = Icons.Filled.Build
    )

    // ========================================================================
    // NAVIGATION CONFIGURATION
    // ========================================================================

    companion object {
        /**
         * Bottom Navigation Items
         *
         * Matrix UI: 5 items (Dashboard, Map, Timeline, Insights, Settings)
         * Categories moved to Dashboard or top menu
         */
        val bottomNavItems = listOf(
            Dashboard,
            Map,
            Timeline,
            Insights,    // Analytics and insights
            Settings     // Easily accessible settings
        )

        /**
         * Top-Right Menu Items
         *
         * Accessible via menu button in top app bar
         */
        val menuItems = listOf(
            Categories,  // Category management moved from bottom nav
            Settings,
            EnhancedSettings,
            ExpertSettings
        )
    }
}