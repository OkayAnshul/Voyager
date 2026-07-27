package com.cosmiclaboratory.voyager.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

sealed class VoyagerDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    // Bottom Navigation (6 tabs — Memory: Today+Timeline · Spatial: Map · Habits: Insights · Proof · Athlete: Activities)
    object Home : VoyagerDestination("home", "Today", Icons.Filled.Today)
    object Timeline : VoyagerDestination("timeline", "Timeline", Icons.AutoMirrored.Filled.List)
    object Map : VoyagerDestination("map", "Map", Icons.Filled.Map)
    object Insights : VoyagerDestination("insights", "Insights", Icons.Filled.Insights)
    object Proof : VoyagerDestination("proof", "Proof", Icons.Filled.Verified)

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

    // Debug
    object DebugDataInsertion : VoyagerDestination("debug_data_insertion", "Debug", Icons.Filled.Build)
    object PipelineDebug : VoyagerDestination("pipeline_debug", "Pipeline Debug", Icons.Filled.BugReport)

    // Push navigation screens
    object PlaceReview : VoyagerDestination("place_review", "Review", Icons.Filled.RateReview)
    object DeveloperProfile : VoyagerDestination("developer_profile", "About Developer", Icons.Filled.Person)
    object OpenSourceLicenses : VoyagerDestination("open_source_licenses", "Open-source licenses", Icons.Filled.Code)
    /** Send feedback — optional `category` arg (BUG/FEATURE/GENERAL) preselects the composer tab. */
    object Feedback : VoyagerDestination("feedback?category={category}", "Send feedback", Icons.Filled.Send) {
        fun createRoute(category: String? = null): String =
            if (category.isNullOrBlank()) "feedback" else "feedback?category=$category"
    }
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

    // Athlete persona — workout recording (Record launched from the Activities tab / Map)
    object Record : VoyagerDestination("workout_record", "Record", Icons.Filled.FiberManualRecord)
    object Activities : VoyagerDestination("activities", "Activities", Icons.AutoMirrored.Filled.DirectionsRun)

    object ActivityDetail : VoyagerDestination("activity_detail/{activityId}", "Activity", Icons.Filled.FitnessCenter) {
        fun createRoute(activityId: Long) = "activity_detail/$activityId"
    }

    object Segments : VoyagerDestination("workout_segments", "Segments", Icons.Filled.Route)

    companion object {
        /** Bottom nav: 6 tabs. Settings is push-nav from top bar gear. */
        val bottomNavItems get() = listOf(Home, Timeline, Map, Insights, Proof, Activities)

        fun NavController.navigateToTab(route: String) {
            navigate(route) {
                popUpTo(graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
}
