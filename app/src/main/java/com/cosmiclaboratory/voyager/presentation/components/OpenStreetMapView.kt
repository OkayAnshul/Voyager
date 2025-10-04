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
    onPlaceClick: (Place) -> Unit = {},
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
                    
                    // Add my location overlay for user position
                    val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    myLocationOverlay.enableMyLocation()
                    myLocationOverlay.enableFollowLocation()
                    overlays.add(myLocationOverlay)
                    
                    onMapReady(this)
                    
                } catch (e: Exception) {
                    // Handle map initialization errors gracefully
                    android.util.Log.e("OpenStreetMapView", "Map initialization failed", e)
                }
            }
        },
        update = { mapView ->
            // Clear existing overlays
            mapView.overlays.clear()
            
            // Add location path
            if (locations.isNotEmpty()) {
                addLocationPath(mapView, locations)
            }
            
            // Add place markers
            places.forEach { place ->
                addPlaceMarker(mapView, place, onPlaceClick)
            }
            
            // Add user location marker
            userLocation?.let { location ->
                addUserLocationMarker(mapView, location)
            }
            
            // Update center if provided
            center?.let { (lat, lng) ->
                mapView.controller.setCenter(GeoPoint(lat, lng))
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

private fun addPlaceMarker(mapView: MapView, place: Place, onPlaceClick: (Place) -> Unit) {
    val marker = Marker(mapView).apply {
        position = GeoPoint(place.latitude, place.longitude)
        title = place.name
        snippet = "${place.category.name} • ${place.visitCount} visits"
        
        setOnMarkerClickListener { _, _ ->
            onPlaceClick(place)
            true
        }
        
        // Set different icons based on place category
        when (place.category) {
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.HOME -> {
                // Use default home icon
            }
            com.cosmiclaboratory.voyager.domain.model.PlaceCategory.WORK -> {
                // Use work icon
            }
            else -> {
                // Use generic place icon
            }
        }
    }
    
    mapView.overlays.add(marker)
}

private fun addUserLocationMarker(mapView: MapView, location: Location) {
    try {
        val userMarker = Marker(mapView).apply {
            position = GeoPoint(location.latitude, location.longitude)
            title = "Your Current Location"
            snippet = "Accuracy: ${location.accuracy}m\nTime: ${location.timestamp}"
            
            // Set marker anchor for proper positioning
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            
            // Use default user location icon
            icon = mapView.context.getDrawable(android.R.drawable.ic_menu_mylocation)
            
            // Make marker draggable for manual correction if needed
            isDraggable = false
        }
        
        mapView.overlays.add(userMarker)
        
        // Center map on user location
        mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
        
    } catch (e: Exception) {
        android.util.Log.e("OpenStreetMapView", "Failed to add user location marker", e)
    }
}