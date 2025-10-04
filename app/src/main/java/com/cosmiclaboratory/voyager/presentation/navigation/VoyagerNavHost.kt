package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cosmiclaboratory.voyager.presentation.screen.dashboard.DashboardScreen
import com.cosmiclaboratory.voyager.presentation.screen.insights.InsightsScreen
import com.cosmiclaboratory.voyager.presentation.screen.map.MapScreen
import com.cosmiclaboratory.voyager.presentation.screen.settings.SettingsScreen
import com.cosmiclaboratory.voyager.presentation.screen.timeline.TimelineScreen
import com.cosmiclaboratory.voyager.utils.PermissionStatus

@Composable
fun VoyagerNavHost(
    navController: NavHostController,
    permissionStatus: PermissionStatus,
    onRequestNotificationPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = VoyagerDestination.Dashboard.route,
        modifier = modifier
    ) {
        composable(VoyagerDestination.Dashboard.route) {
            DashboardScreen(permissionStatus = permissionStatus)
        }
        
        composable(VoyagerDestination.Map.route) {
            MapScreen(permissionStatus = permissionStatus)
        }
        
        composable(VoyagerDestination.Timeline.route) {
            TimelineScreen(permissionStatus = permissionStatus)
        }
        
        composable(VoyagerDestination.Insights.route) {
            InsightsScreen(permissionStatus = permissionStatus)
        }
        
        composable(VoyagerDestination.Settings.route) {
            SettingsScreen(
                permissionStatus = permissionStatus,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
        }
    }
}