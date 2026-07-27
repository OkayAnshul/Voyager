package com.cosmiclaboratory.voyager.presentation.screen.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cosmiclaboratory.voyager.domain.model.RoutePoint
import com.cosmiclaboratory.voyager.domain.model.WorkoutType
import com.cosmiclaboratory.voyager.presentation.di.MapEngineEntryPoint
import com.cosmiclaboratory.voyager.presentation.theme.VoyagerColors
import dagger.hilt.android.EntryPointAccessors

/** The route colour for a workout, drawn vividly against the near-black basemap. */
internal fun workoutRouteColorArgb(type: WorkoutType): Int = when (type) {
    WorkoutType.RUN -> VoyagerColors.AccentOrange.toArgb()
    WorkoutType.WALK -> VoyagerColors.TransportWalk.toArgb()
    WorkoutType.CYCLE -> VoyagerColors.TransportCycle.toArgb()
    WorkoutType.HIKE -> VoyagerColors.Success.toArgb()
    WorkoutType.OTHER -> VoyagerColors.Primary.toArgb()
}

/**
 * A MapLibre map that draws a workout [route] as a coloured polyline. Reuses the singleton
 * [com.cosmiclaboratory.voyager.domain.map.MapEngine] (same as the Map tab) via the same
 * lifecycle/AndroidView pattern.
 *
 * [follow] = true keeps the camera on the latest fix (live recording); false frames the whole
 * route once (a saved activity's detail view).
 */
@Composable
fun WorkoutMap(
    route: List<RoutePoint>,
    color: Int,
    modifier: Modifier = Modifier,
    follow: Boolean = true,
) {
    val context = LocalContext.current
    val mapEngine = remember {
        EntryPointAccessors.fromApplication<MapEngineEntryPoint>(
            context.applicationContext
        ).mapEngine()
    }

    AndroidView(
        factory = { ctx -> mapEngine.createMapView(ctx) },
        modifier = modifier,
    )

    // Redraw the route whenever it changes.
    LaunchedEffect(route, color) {
        mapEngine.clearAll()
        val pts = route.map { it.lat to it.lng }
        if (pts.size >= 2) {
            mapEngine.addPolyline(pts, color, 10f)
        }
        route.lastOrNull()?.let { last ->
            mapEngine.setCurrentLocationMarker(last.lat, last.lng, last.accuracyM)
            if (follow) {
                mapEngine.animateTo(last.lat, last.lng, 16.0)
            }
        }
        if (!follow && pts.size >= 2) {
            mapEngine.fitBounds(pts, paddingPx = 64, maxZoom = 16.0)
        }
    }

    // Forward the host lifecycle to the MapView (required for MapLibre to render).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapEngine.onStart()
                Lifecycle.Event.ON_RESUME -> mapEngine.onResume()
                Lifecycle.Event.ON_PAUSE -> mapEngine.onPause()
                Lifecycle.Event.ON_STOP -> mapEngine.onStop()
                Lifecycle.Event.ON_DESTROY -> mapEngine.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapEngine.onStart()
        mapEngine.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // The singleton engine cleans up its view on re-entry; just clear + pause here.
            mapEngine.clearAll()
            mapEngine.onPause()
            mapEngine.onStop()
        }
    }
}
