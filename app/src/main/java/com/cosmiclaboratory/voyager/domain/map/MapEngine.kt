package com.cosmiclaboratory.voyager.domain.map

import android.view.View

interface MapEngine {
    fun createMapView(context: android.content.Context): View
    fun setCenter(lat: Double, lng: Double, zoom: Double)
    fun addPolyline(points: List<Pair<Double, Double>>, color: Int, width: Float): String
    fun addDirectionalPolyline(points: List<Pair<Double, Double>>, color: Int, width: Float): String {
        return addPolyline(points, color, width) // default: no arrows
    }
    fun removePolyline(id: String)
    fun addMarker(lat: Double, lng: Double, title: String?, iconRes: Int? = null, number: Int? = null): String
    fun removeMarker(id: String)
    /** Add/update a distinct current-location indicator (blue dot + accuracy ring) */
    fun setCurrentLocationMarker(lat: Double, lng: Double, accuracyM: Float? = null)
    fun removeCurrentLocationMarker()
    fun clearAll()
    fun animateTo(lat: Double, lng: Double, zoom: Double, durationMs: Long = 300)
    fun fitBounds(points: List<Pair<Double, Double>>, paddingPx: Int = 50, maxZoom: Double? = null)
    fun setOnMapClickListener(listener: (lat: Double, lng: Double) -> Unit)
    fun setOnMarkerClickListener(listener: (markerId: String) -> Unit)
    fun onStart()
    fun onResume()
    fun onPause()
    fun onStop()
    fun destroy()
}
