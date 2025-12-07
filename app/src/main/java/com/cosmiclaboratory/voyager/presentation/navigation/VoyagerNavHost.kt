package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cosmiclaboratory.voyager.presentation.screen.analytics.StatisticsScreen
import com.cosmiclaboratory.voyager.presentation.screen.dashboard.DashboardScreen
import com.cosmiclaboratory.voyager.presentation.screen.debug.DebugDataInsertionScreen
import com.cosmiclaboratory.voyager.presentation.screen.insights.InsightsScreen
import com.cosmiclaboratory.voyager.presentation.screen.insights.PlacePatternsScreen
import com.cosmiclaboratory.voyager.presentation.screen.map.MapScreen
import com.cosmiclaboratory.voyager.presentation.screen.review.PlaceReviewScreen
import com.cosmiclaboratory.voyager.presentation.screen.settings.AdvancedSettingsScreen
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
            DashboardScreen(
                permissionStatus = permissionStatus,
                onNavigateToReview = {
                    navController.navigate(VoyagerDestination.PlaceReview.route)
                }
            )
        }
        
        composable(VoyagerDestination.Map.route) {
            MapScreen(permissionStatus = permissionStatus)
        }
        
        composable(VoyagerDestination.Timeline.route) {
            TimelineScreen(permissionStatus = permissionStatus)
        }
        
        composable(VoyagerDestination.Insights.route) {
            InsightsScreen(
                permissionStatus = permissionStatus,
                onNavigateToWeeklyComparison = {
                    navController.navigate(VoyagerDestination.WeeklyComparison.route)
                },
                onNavigateToPlacePatterns = {
                    navController.navigate(VoyagerDestination.PlacePatterns.route)
                },
                onNavigateToMovementAnalytics = {
                    navController.navigate(VoyagerDestination.MovementAnalytics.route)
                },
                onNavigateToSocialHealthAnalytics = {
                    navController.navigate(VoyagerDestination.SocialHealthAnalytics.route)
                }
            )
        }

        composable(VoyagerDestination.WeeklyComparison.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VoyagerDestination.PlacePatterns.route) {
            PlacePatternsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VoyagerDestination.Settings.route) {
            SettingsScreen(
                permissionStatus = permissionStatus,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onNavigateToDebugDataInsertion = {
                    navController.navigate(VoyagerDestination.DebugDataInsertion.route)
                },
                onNavigateToAdvancedSettings = {
                    navController.navigate(VoyagerDestination.EnhancedSettings.route)
                }
            )
        }

        composable(VoyagerDestination.EnhancedSettings.route) {
            AdvancedSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VoyagerDestination.ExpertSettings.route) {
            AdvancedSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VoyagerDestination.DebugDataInsertion.route) {
            DebugDataInsertionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VoyagerDestination.MovementAnalytics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VoyagerDestination.SocialHealthAnalytics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VoyagerDestination.PlaceReview.route) {
            PlaceReviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}