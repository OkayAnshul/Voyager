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
    
    object PermissionGateway : VoyagerDestination(
        route = "permission_gateway",
        title = "Permissions",
        icon = Icons.Filled.Lock
    )
    
    companion object {
        val bottomNavItems = listOf(Dashboard, Map, Timeline, Insights, Settings)
    }
}