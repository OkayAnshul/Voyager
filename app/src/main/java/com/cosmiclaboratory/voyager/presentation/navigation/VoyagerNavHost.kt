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

@Composable
fun VoyagerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = VoyagerDestination.Dashboard.route,
        modifier = modifier
    ) {
        composable(VoyagerDestination.Dashboard.route) {
            DashboardScreen()
        }
        
        composable(VoyagerDestination.Map.route) {
            MapScreen()
        }
        
        composable(VoyagerDestination.Timeline.route) {
            TimelineScreen()
        }
        
        composable(VoyagerDestination.Insights.route) {
            InsightsScreen()
        }
        
        composable(VoyagerDestination.Settings.route) {
            SettingsScreen()
        }
    }
}