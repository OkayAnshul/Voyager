package com.cosmiclaboratory.voyager.presentation.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "Voyager"
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(true)
                
                // Set initial position
                val mapController: IMapController = controller
                center?.let { (lat, lng) ->
                    mapController.setCenter(GeoPoint(lat, lng))
                }
                mapController.setZoom(zoomLevel.toDouble())
                
                onMapReady(this)
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
    
    val polyline = Polyline().apply {
        color = android.graphics.Color.BLUE
        width = 5f
    }
    
    val geoPoints = locations.map { location ->
        GeoPoint(location.latitude, location.longitude)
    }
    
    polyline.setPoints(geoPoints)
    mapView.overlays.add(polyline)
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
    val userMarker = Marker(mapView).apply {
        position = GeoPoint(location.latitude, location.longitude)
        title = "Your Location"
        snippet = "Current position"
        
        // Set blue dot for user location
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    }
    
    mapView.overlays.add(userMarker)
}