package com.cosmiclaboratory.voyager.presentation.components

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.cosmiclaboratory.voyager.domain.model.Location
import com.cosmiclaboratory.voyager.domain.model.Place
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OpenStreetMapView(
    modifier: Modifier = Modifier,
    center: Pair<Double, Double>? = null,
    zoomLevel: Float = 15f,
    locations: List<Location> = emptyList(),
    places: List<Place> = emptyList(),
    userLocation: Location? = null,
    currentPlace: Place? = null,
    isTracking: Boolean = false,
    showRoute: Boolean = true, // NEW: Show route between places
    showTimelineNumbers: Boolean = true, // NEW: Show timeline numbers on markers
    showVisitCounts: Boolean = true, // NEW: Show visit counts on markers
    onPlaceClick: (Place) -> Unit = {},
    onMapClick: (Double, Double) -> Unit = { _, _ -> },
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Initialize OSMDroid configuration
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Initialize OSMDroid configuration
                val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                Configuration.getInstance().load(context, prefs)
                
                // Set user agent
                Configuration.getInstance().userAgentValue = "Voyager/1.0"
                
                // Configure cache and storage
                val osmConfig = Configuration.getInstance()
                
                // Set cache path for offline tiles
                val tileCache = File(context.cacheDir, "osmdroid")
                if (!tileCache.exists()) {
                    tileCache.mkdirs()
                }
                osmConfig.osmdroidTileCache = tileCache
                
                // Configure network settings (userAgentValue already set above)
                
                // Set reasonable tile download policy
                osmConfig.tileDownloadThreads = 2
                osmConfig.tileFileSystemThreads = 4
                
                // Enable debug mode for troubleshooting (disable in production)
                osmConfig.isDebugMode = false
                
            } catch (exception: Exception) {
                // Fallback configuration if initialization fails
                Configuration.getInstance().userAgentValue = "Voyager"
                android.util.Log.w("OpenStreetMapView", "OSMDroid configuration failed, using defaults", exception)
            }
        }
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                try {
                    // Set tile source with fallback handling
                    setTileSource(TileSourceFactory.MAPNIK)
                    
                    // Enable multi-touch controls
                    setMultiTouchControls(true)
                    
                    // Note: setBuiltInZoomControls is deprecated but still functional
                    @Suppress("DEPRECATION")
                    setBuiltInZoomControls(false)
                    
                    // Enable hardware acceleration (always available on modern Android)
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    // Configure zoom limits
                    minZoomLevel = 3.0
                    maxZoomLevel = 20.0
                    
                    // Set initial position
                    val mapController: IMapController = controller
                    center?.let { (lat, lng) ->
                        mapController.setCenter(GeoPoint(lat, lng))
                    }
                    mapController.setZoom(zoomLevel.toDouble())
                    
                    // Add my location overlay only if tracking is enabled
                    if (isTracking) {
                        val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                        myLocationOverlay.enableMyLocation()
                        myLocationOverlay.enableFollowLocation()
                        overlays.add(myLocationOverlay)
                    }
                    
                    // Add map click listener
                    setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            val projection = projection
                            val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt())
                            onMapClick(geoPoint.latitude, geoPoint.longitude)
                        }
                        false
                    }
                    
                    onMapReady(this)
                    
                } catch (e: Exception) {
                    // Handle map initialization errors gracefully
                    android.util.Log.e("OpenStreetMapView", "Map initialization failed", e)
                }
            }
        },
        update = { mapView ->
            // Clear existing overlays (except my location overlay)
            val myLocationOverlay = mapView.overlays.find { it is MyLocationNewOverlay }
            mapView.overlays.clear()
            
            // Re-add my location overlay if tracking is enabled
            if (isTracking && myLocationOverlay != null) {
                mapView.overlays.add(myLocationOverlay)
            }
            
            // Add location path (raw GPS trail)
            if (locations.isNotEmpty() && !showRoute) {
                addLocationPath(mapView, locations)
            }

            // Add route between places (timeline order)
            if (showRoute && places.size >= 2) {
                addPlaceRoute(mapView, places)
            }

            // Add place markers with timeline numbers
            places.forEachIndexed { index, place ->
                val timelineNumber = if (showTimelineNumbers) index + 1 else null
                addPlaceMarker(mapView, place, onPlaceClick, place == currentPlace, timelineNumber)
            }
            
            // Add user location marker (blue dot)
            userLocation?.let { location ->
                addUserLocationMarker(mapView, location, isTracking)
            }
            
            // Update center if provided
            center?.let { (lat, lng) ->
                mapView.controller.animateTo(GeoPoint(lat, lng))
            }
            
            // Update zoom level
            mapView.controller.setZoom(zoomLevel.toDouble())
            
            mapView.invalidate()
        }
    )
}

private fun addLocationPath(mapView: MapView, locations: List<Location>) {
    if (locations.size < 2) return
    
    try {
        val polyline = Polyline(mapView).apply {
            // Fix deprecated color and width properties
            outlinePaint.color = Color.BLUE
            outlinePaint.strokeWidth = 8f
            outlinePaint.isAntiAlias = true
            
            // Add some transparency for better visibility
            outlinePaint.alpha = 200
            
            // Set polyline info
            title = "Your Path"
            snippet = "${locations.size} location points"
        }
        
        val geoPoints = locations.sortedBy { it.timestamp }.map { location ->
            GeoPoint(location.latitude, location.longitude)
        }
        
        polyline.setPoints(geoPoints)
        mapView.overlays.add(polyline)
        
    } catch (e: Exception) {
        android.util.Log.e("OpenStreetMapView", "Failed to add location path", e)
    }
}

/**
 * Add route visualization between places in timeline order
 * Shows how the user traveled through the day
 */
private fun addPlaceRoute(mapView: MapView, places: List<Place>) {
    if (places.size < 2) return

    try {
        // Sort places by last visit time to show chronological route
        val sortedPlaces = places.sortedBy { it.lastVisit }

        // Create polyline for the route
        val routePolyline = Polyline(mapView).apply {
            // Styled route line
            outlinePaint.color = Color.parseColor("#FF6B35") // Orange color for route
            outlinePaint.strokeWidth = 10f
            outlinePaint.isAntiAlias = true
            outlinePaint.alpha = 220

            // Add dashed effect for better visibility
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)

            title = "Your Route"
            snippet = "${sortedPlaces.size} places visited"
        }

        // Convert places to GeoPoints
        val routePoints = sortedPlaces.map { place ->
            GeoPoint(place.latitude, place.longitude)
        }

        routePolyline.setPoints(routePoints)
        mapView.overlays.add(0, routePolyline) // Add at bottom so markers are on top

    } catch (e: Exception) {
        android.util.Log.e("OpenStreetMapView", "Failed to add place route", e)
    }
}

private fun addPlaceMarker(
    mapView: MapView,
    place: Place,
    onPlaceClick: (Place) -> Unit,
    isCurrent: Boolean = false,
    timelineNumber: Int? = null
) {
    val marker = Marker(mapView).apply {
        position = GeoPoint(place.latitude, place.longitude)

        // Enhanced title with timeline number
        title = if (timelineNumber != null) {
            "#$timelineNumber - ${place.name}"
        } else {
            place.name
        }

        snippet = if (place.isUserRenamed) {
            "Custom: ${place.name} • ${place.visitCount} visits"
        } else {
            "${place.address?.split(",")?.firstOrNull() ?: "Place"} • ${place.visitCount} visits"
        }

        setOnMarkerClickListener { _, _ ->
            onPlaceClick(place)
            true
        }

        // Set marker anchor for proper positioning
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        // Create custom marker with timeline number
        try {
            if (timelineNumber != null) {
                // Create numbered marker
                icon = createNumberedMarker(mapView.context, timelineNumber, isCurrent, place.isUserRenamed)
            } else {
                // Use default category-based icons
                val iconRes = when {
                    isCurrent -> android.R.drawable.star_big_on
                    place.category == com.cosmiclaboratory.voyager.domain.model.PlaceCategory.HOME ->
                        android.R.drawable.ic_menu_mylocation
                    place.category == com.cosmiclaboratory.voyager.domain.model.PlaceCategory.WORK ->
                        android.R.drawable.ic_menu_agenda
                    else -> android.R.drawable.ic_menu_mapmode
                }

                icon = mapView.context.getDrawable(iconRes)
            }

            // Scale icon appropriately if using default icons
            if (timelineNumber == null) {
                val density = mapView.context.resources.displayMetrics.density
                val size = (32 * density).toInt()
                icon?.setBounds(0, 0, size, size)
            }

        } catch (e: Exception) {
            android.util.Log.w("OpenStreetMapView", "Failed to set place marker icon", e)
        }
    }
    
    mapView.overlays.add(marker)
}

private fun addUserLocationMarker(mapView: MapView, location: Location, isTracking: Boolean) {
    try {
        val userMarker = Marker(mapView).apply {
            position = GeoPoint(location.latitude, location.longitude)
            title = "Your Current Location"
            snippet = "Accuracy: ${location.accuracy}m${if (location.speed?.let { it > 0 } == true) "\nSpeed: ${String.format("%.1f", (location.speed ?: 0f) * 3.6)} km/h" else ""}"
            
            // Set marker anchor for proper positioning
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            
            // Use blue dot style for current location
            try {
                val drawable = mapView.context.getDrawable(android.R.drawable.presence_online)
                icon = drawable
                
                // Scale for better visibility
                val density = mapView.context.resources.displayMetrics.density
                val size = if (isTracking) (24 * density).toInt() else (20 * density).toInt()
                icon?.setBounds(0, 0, size, size)
                
                // Set blue tint programmatically
                icon?.setTint(Color.rgb(33, 150, 243)) // Material Blue
                
            } catch (e: Exception) {
                // Fallback to default icon
                icon = mapView.context.getDrawable(android.R.drawable.ic_menu_mylocation)
                android.util.Log.w("OpenStreetMapView", "Failed to set user location icon, using fallback", e)
            }
            
            // Make marker non-draggable
            isDraggable = false
        }
        
        mapView.overlays.add(userMarker)
        
        // Add accuracy circle if tracking is active
        if (isTracking && location.accuracy > 0) {
            addAccuracyCircle(mapView, location)
        }
        
    } catch (e: Exception) {
        android.util.Log.e("OpenStreetMapView", "Failed to add user location marker", e)
    }
}

private fun addAccuracyCircle(mapView: MapView, location: Location) {
    try {
        val circle = org.osmdroid.views.overlay.Polygon(mapView).apply {
            // Create circle points
            val center = GeoPoint(location.latitude, location.longitude)
            val points = mutableListOf<GeoPoint>()
            
            // Create a circle with 32 points
            for (i in 0..32) {
                val angle = (i * 360.0 / 32.0) * Math.PI / 180.0
                val radius = location.accuracy / 111000.0 // Convert meters to degrees approximately
                val lat = location.latitude + radius * Math.cos(angle)
                val lng = location.longitude + radius * Math.sin(angle) / Math.cos(location.latitude * Math.PI / 180.0)
                points.add(GeoPoint(lat, lng))
            }
            
            setPoints(points)
            
            // Style the accuracy circle
            fillPaint.color = Color.argb(50, 33, 150, 243) // Semi-transparent blue
            outlinePaint.color = Color.argb(100, 33, 150, 243) // Blue border
            outlinePaint.strokeWidth = 2f
            
            title = "Location Accuracy"
            snippet = "±${location.accuracy.toInt()}m"
        }
        
        mapView.overlays.add(circle)
        
    } catch (e: Exception) {
        android.util.Log.w("OpenStreetMapView", "Failed to add accuracy circle", e)
    }
}

/**
 * Create a numbered marker icon for timeline visualization
 */
private fun createNumberedMarker(
    context: Context,
    number: Int,
    isCurrent: Boolean,
    isUserRenamed: Boolean
): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val size = (32 * density).toInt()  // ISSUE #4: Reduced from 48dp to 32dp

    // Create bitmap for custom marker
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Determine marker color based on state
    val markerColor = when {
        isCurrent -> Color.parseColor("#4CAF50") // Green for current
        isUserRenamed -> Color.parseColor("#9C27B0") // Purple for user-renamed
        else -> Color.parseColor("#2196F3") // Blue for regular
    }

    // Draw circle background
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
        color = markerColor
    }

    val centerX = size / 2f
    val centerY = size / 2f
    val radius = size / 2.8f  // ISSUE #4: Adjusted for smaller size

    canvas.drawCircle(centerX, centerY, radius, paint)

    // Draw white border
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 3f * density
    paint.color = Color.WHITE
    canvas.drawCircle(centerX, centerY, radius, paint)

    // Draw number text
    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 20f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val numberText = number.toString()
    val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2

    canvas.drawText(numberText, centerX, textY, textPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}