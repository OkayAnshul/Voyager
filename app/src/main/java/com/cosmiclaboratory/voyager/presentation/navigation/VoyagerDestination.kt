package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
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
        icon = Icons.Filled.List
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
    
    companion object {
        val bottomNavItems = listOf(Dashboard, Map, Timeline, Insights, Settings)
    }
}