package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

sealed class VoyagerDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    // Bottom Navigation
    object Home : VoyagerDestination("home", "Home", Icons.Filled.Home)
    object Map : VoyagerDestination("map", "Map", Icons.Filled.Place)
    object Timeline : VoyagerDestination("timeline", "Timeline", Icons.AutoMirrored.Filled.List)
    object Insights : VoyagerDestination("insights", "Insights", Icons.Filled.Insights)

    // Top-level
    object Settings : VoyagerDestination("settings", "Settings", Icons.Filled.Settings)
    object Search : VoyagerDestination("search", "Search", Icons.Filled.Search)
    object Export : VoyagerDestination("export", "Export", Icons.Filled.Share)

    // Detail screens
    object PlaceDetail : VoyagerDestination("place_detail/{placeId}", "Place", Icons.Filled.Place) {
        fun createRoute(placeId: Long) = "place_detail/$placeId"
    }

    object SegmentDetail : VoyagerDestination("segment_detail/{segmentId}", "Segment", Icons.Filled.Timeline) {
        fun createRoute(segmentId: Long) = "segment_detail/$segmentId"
    }

    object VisitDetail : VoyagerDestination("visit_detail/{visitId}", "Visit", Icons.Filled.Place) {
        fun createRoute(visitId: Long) = "visit_detail/$visitId"
    }

    // Debug
    object DebugDataInsertion : VoyagerDestination("debug_data_insertion", "Debug", Icons.Filled.Build)
    object PipelineDebug : VoyagerDestination("pipeline_debug", "Pipeline Debug", Icons.Filled.BugReport)

    // Push navigation screens
    object PlaceReview : VoyagerDestination("place_review", "Review", Icons.Filled.RateReview)
    object Categories : VoyagerDestination("categories", "Categories", Icons.Filled.Category)
    object DeveloperProfile : VoyagerDestination("developer_profile", "About Developer", Icons.Filled.Person)
    object OpenSourceLicenses : VoyagerDestination("open_source_licenses", "Open-source licenses", Icons.Filled.Code)
    object Feedback : VoyagerDestination("feedback", "Send feedback", Icons.Filled.Send)
    object Reliability : VoyagerDestination("reliability", "Reliability", Icons.Filled.HealthAndSafety)
    object Mileage : VoyagerDestination("mileage", "Mileage log", Icons.Filled.DirectionsCar)
    object Paywall : VoyagerDestination("paywall", "Voyager Pro", Icons.Filled.WorkspacePremium)

    object Trips : VoyagerDestination("trips", "Trips", Icons.Filled.Luggage)

    object TripDetail : VoyagerDestination("trip_detail/{tripId}", "Trip", Icons.Filled.Luggage) {
        fun createRoute(tripId: Long) = "trip_detail/$tripId"
    }

    /** Photo Day Story — optional `dayKey` arg deep-links from the Timeline day header. */
    object DayStory : VoyagerDestination(
        "day_story?dayKey={dayKey}", "Photo Day Story", Icons.Filled.PhotoLibrary
    ) {
        fun createRoute(dayKey: String? = null): String =
            if (dayKey.isNullOrBlank()) "day_story" else "day_story?dayKey=$dayKey"
    }

    companion object {
        /** Bottom nav: 4 tabs only. Settings is push-nav from top bar gear. */
        val bottomNavItems get() = listOf(Home, Map, Timeline, Insights)

        fun NavController.navigateToTab(route: String) {
            navigate(route) {
                popUpTo(graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}
