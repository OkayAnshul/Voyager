package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class VoyagerDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
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
    
    object Insights : VoyagerDestination(
        route = "insights",
        title = "Insights",
        icon = Icons.Filled.Info
    )
    
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

    object DebugDataInsertion : VoyagerDestination(
        route = "debug_data_insertion",
        title = "Debug: Insert Test Data",
        icon = Icons.Filled.Build
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

    companion object {
        val bottomNavItems = listOf(Dashboard, Map, Timeline, Insights, Settings)
    }
}