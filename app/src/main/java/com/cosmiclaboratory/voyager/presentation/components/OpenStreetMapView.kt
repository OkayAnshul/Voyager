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
            
            // Add location path
            if (locations.isNotEmpty()) {
                addLocationPath(mapView, locations)
            }
            
            // Add place markers
            places.forEach { place ->
                addPlaceMarker(mapView, place, onPlaceClick, place == currentPlace)
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

private fun addPlaceMarker(mapView: MapView, place: Place, onPlaceClick: (Place) -> Unit, isCurrent: Boolean = false) {
    val marker = Marker(mapView).apply {
        position = GeoPoint(place.latitude, place.longitude)
        title = place.name
        snippet = "${place.category.name} • ${place.visitCount} visits"
        
        setOnMarkerClickListener { _, _ ->
            onPlaceClick(place)
            true
        }
        
        // Set marker anchor for proper positioning
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        
        // Set different icons based on place category and current status
        try {
            val iconRes = when {
                isCurrent -> android.R.drawable.star_big_on // Current place highlighted
                place.category == com.cosmiclaboratory.voyager.domain.model.PlaceCategory.HOME -> 
                    android.R.drawable.ic_menu_mylocation
                place.category == com.cosmiclaboratory.voyager.domain.model.PlaceCategory.WORK -> 
                    android.R.drawable.ic_menu_agenda
                else -> android.R.drawable.ic_menu_mapmode
            }
            
            icon = mapView.context.getDrawable(iconRes)
            
            // Scale icon appropriately
            val density = mapView.context.resources.displayMetrics.density
            val size = (32 * density).toInt()
            icon?.setBounds(0, 0, size, size)
            
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