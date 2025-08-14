package com.cosmiclaboratory.voyager.platform.map

import android.content.Context
import android.graphics.*
import android.graphics.Typeface
import android.view.View
import com.cosmiclaboratory.voyager.domain.map.MapEngine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * MapLibre implementation of [MapEngine].
 *
 * Uses GeoJSON sources + layers for polylines and markers.
 * Each polyline/marker gets its own source+layer pair for independent control.
 */
class MapLibreMapEngine @Inject constructor() : MapEngine {

    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private val initialized = AtomicBoolean(false)

    private val polylineSources = mutableMapOf<String, String>() // id -> sourceId
    private val markerSources = mutableMapOf<String, MarkerInfo>()

    private var onMapClick: ((Double, Double) -> Unit)? = null
    private var onMarkerClick: ((String) -> Unit)? = null

    private val pendingOps = mutableListOf<() -> Unit>()

    private data class MarkerInfo(
        val sourceId: String,
        val layerId: String,
        val textLayerId: String,
        val lat: Double,
        val lng: Double
    )

    companion object {
        // OpenFreeMap — full OSM detail, no API key, no rate limits
        private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        private const val MARKER_TAP_RADIUS_PX = 30.0
        private const val ARROW_ICON_ID = "route-arrow-icon"
        // Current location indicator layer IDs
        private const val LOC_SOURCE = "current-location-source"
        private const val LOC_RING_LAYER = "current-location-ring"
        private const val LOC_DOT_LAYER = "current-location-dot"
    }

    override fun createMapView(context: Context): View {
        MapLibre.getInstance(context)

        // Clean up previous MapView if singleton is being re-entered after navigation
        mapView?.onDestroy()
        mapView = null
        map = null
        style = null
        initialized.set(false)
        pendingOps.clear()
        polylineSources.clear()
        markerSources.clear()

        val mv = MapView(context)
        mapView = mv

        mv.addOnDidFailLoadingMapListener {
            android.util.Log.e("MapLibreMapEngine", "Failed to load map style from $STYLE_URL")
        }

        mv.getMapAsync { mapLibreMap ->
            map = mapLibreMap

            mapLibreMap.setStyle(Style.Builder().fromUri(STYLE_URL)) { loadedStyle ->
                if (mapView == null) return@setStyle // Guard: destroy() already ran
                style = loadedStyle
                initialized.set(true)

                // Tap detection: find nearest marker within radius
                mapLibreMap.addOnMapClickListener { point ->
                    val screenPoint = mapLibreMap.projection.toScreenLocation(point)
                    var tappedMarkerId: String? = null
                    var minDist = MARKER_TAP_RADIUS_PX

                    for ((id, info) in markerSources) {
                        val markerScreen = mapLibreMap.projection.toScreenLocation(
                            LatLng(info.lat, info.lng)
                        )
                        val dx = (screenPoint.x - markerScreen.x).toDouble()
                        val dy = (screenPoint.y - markerScreen.y).toDouble()
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist < minDist) {
                            minDist = dist
                            tappedMarkerId = id
                        }
                    }

                    if (tappedMarkerId != null) {
                        onMarkerClick?.invoke(tappedMarkerId)
                    } else {
                        onMapClick?.invoke(point.latitude, point.longitude)
                    }
                    true
                }

                pendingOps.forEach { it() }
                pendingOps.clear()
            }
        }

        return mv
    }

    override fun setCenter(lat: Double, lng: Double, zoom: Double) {
        runWhenReady {
            map?.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom)
            )
        }
    }

    override fun addPolyline(
        points: List<Pair<Double, Double>>,
        color: Int,
        width: Float
    ): String {
        val id = UUID.randomUUID().toString()
        val sourceId = "line-source-$id"
        val layerId = "line-layer-$id"

        runWhenReady {
            val s = style ?: return@runWhenReady

            val coordinates = points.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
            val lineString = LineString.fromLngLats(coordinates)
            val feature = Feature.fromGeometry(lineString)

            s.addSource(GeoJsonSource(sourceId, feature))
            s.addLayer(
                LineLayer(layerId, sourceId)
                    .withProperties(
                        PropertyFactory.lineColor(colorToString(color)),
                        PropertyFactory.lineWidth(width),
                        PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND)
                    )
            )

            polylineSources[id] = sourceId
        }
        return id
    }

    override fun removePolyline(id: String) {
        runWhenReady {
            val s = style ?: return@runWhenReady
            val sourceId = polylineSources.remove(id) ?: return@runWhenReady
            val layerId = "line-layer-$id"
            s.removeLayer("arrow-layer-$id")
            s.removeLayer(layerId)
            s.removeSource(sourceId)
        }
    }

    override fun addDirectionalPolyline(
        points: List<Pair<Double, Double>>,
        color: Int,
        width: Float
    ): String {
        val id = UUID.randomUUID().toString()
        val sourceId = "line-source-$id"
        val layerId = "line-layer-$id"
        val arrowLayerId = "arrow-layer-$id"

        runWhenReady {
            try {
                val s = style ?: run {
                    android.util.Log.w("MapLibreEngine", "addDirectionalPolyline SKIP: style is null")
                    return@runWhenReady
                }
                android.util.Log.d("MapLibreEngine", "addDirectionalPolyline EXEC: id=$id points=${points.size}")

                ensureArrowIcon(s)

                val coordinates = points.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
                val lineString = LineString.fromLngLats(coordinates)
                val feature = Feature.fromGeometry(lineString)

                s.addSource(GeoJsonSource(sourceId, feature))
                s.addLayer(
                    LineLayer(layerId, sourceId)
                        .withProperties(
                            PropertyFactory.lineColor(colorToString(color)),
                            PropertyFactory.lineWidth(width),
                            PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND)
                        )
                )

                // Arrow symbols along the line
                s.addLayer(
                    SymbolLayer(arrowLayerId, sourceId)
                        .withProperties(
                            PropertyFactory.symbolPlacement(org.maplibre.android.style.layers.Property.SYMBOL_PLACEMENT_LINE),
                            PropertyFactory.symbolSpacing(80f),
                            PropertyFactory.iconImage(ARROW_ICON_ID),
                            PropertyFactory.iconSize(0.5f),
                            PropertyFactory.iconRotationAlignment(org.maplibre.android.style.layers.Property.ICON_ROTATION_ALIGNMENT_MAP),
                            PropertyFactory.iconAllowOverlap(true)
                        )
                )

                polylineSources[id] = sourceId
            } catch (e: Exception) {
                android.util.Log.e("MapLibreEngine", "addDirectionalPolyline FAILED: $id", e)
            }
        }
        return id
    }

    private var arrowIconRegistered = false

    private fun ensureArrowIcon(style: Style) {
        if (arrowIconRegistered) return
        // Programmatic arrow bitmap: right-pointing triangle
        val size = 24
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            this.style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(4f, 4f)
            lineTo(20f, 12f)
            lineTo(4f, 20f)
            close()
        }
        canvas.drawPath(path, paint)
        style.addImage(ARROW_ICON_ID, bitmap)
        arrowIconRegistered = true
    }

    /** Programmatic red pin bitmap with white number inside. */
    private fun createNumberedPinBitmap(number: Int?): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val circleY = size * 0.38f
        val radius = size * 0.33f

        // White border
        canvas.drawCircle(cx, circleY, radius + 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.FILL
        })
        // Red fill
        canvas.drawCircle(cx, circleY, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935"); style = Paint.Style.FILL
        })
        // Pointer triangle
        val ptr = Path().apply {
            moveTo(cx - 10f, circleY + radius - 4f)
            lineTo(cx, circleY + radius + 20f)
            lineTo(cx + 10f, circleY + radius - 4f)
            close()
        }
        canvas.drawPath(ptr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935"); style = Paint.Style.FILL
        })
        // Number
        if (number != null) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = if (number >= 10) 30f else 36f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(number.toString(), cx, circleY + tp.textSize * 0.35f, tp)
        }
        return bitmap
    }

    override fun addMarker(
        lat: Double,
        lng: Double,
        title: String?,
        iconRes: Int?,
        number: Int?
    ): String {
        val id = UUID.randomUUID().toString()
        val sourceId = "marker-source-$id"
        val circleLayerId = "marker-circle-$id"
        val circleBorderLayerId = "marker-border-$id"
        val textLayerId = "marker-text-$id"

        runWhenReady {
            try {
                val s = style ?: run {
                    android.util.Log.w("MapLibreEngine", "addMarker SKIP: style is null, id=$id")
                    return@runWhenReady
                }
                android.util.Log.d("MapLibreEngine", "addMarker EXEC: id=$id number=$number title=${title?.take(30)}")

                // Register numbered pin icon (reuse across markers with same number)
                val iconId = "marker-pin-${number ?: "default"}"
                if (s.getImage(iconId) == null) {
                    s.addImage(iconId, createNumberedPinBitmap(number))
                    android.util.Log.d("MapLibreEngine", "addMarker: registered icon $iconId")
                }

                val point = Point.fromLngLat(lng, lat)
                val feature = Feature.fromGeometry(point).apply {
                    addStringProperty("title", title ?: "")
                }

                // Use MapLibre marker annotation API for reliable rendering
                val markerOptions = org.maplibre.android.annotations.MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title(title ?: "")
                    .snippet("")
                val iconBitmap = createNumberedPinBitmap(number)
                val iconFactory = org.maplibre.android.annotations.IconFactory.getInstance(mapView!!.context)
                markerOptions.icon(iconFactory.fromBitmap(iconBitmap))
                map?.addMarker(markerOptions)
                android.util.Log.d("MapLibreEngine", "addMarker: annotation added at $lat,$lng")

                markerSources[id] = MarkerInfo(sourceId, circleLayerId, textLayerId, lat, lng)
            } catch (e: Exception) {
                android.util.Log.e("MapLibreEngine", "addMarker FAILED: $id", e)
            }
        }
        return id
    }

    override fun removeMarker(id: String) {
        runWhenReady {
            markerSources.remove(id)
            // Annotation markers are removed via clearAll; individual removal not needed
        }
    }

    override fun setCurrentLocationMarker(lat: Double, lng: Double, accuracyM: Float?) {
        runWhenReady {
            val s = style ?: return@runWhenReady
            val point = Point.fromLngLat(lng, lat)
            val feature = Feature.fromGeometry(point)

            val existingSource = s.getSource(LOC_SOURCE) as? GeoJsonSource
            if (existingSource != null) {
                // Update position without removing/re-adding layers
                existingSource.setGeoJson(feature)
            } else {
                // First time — create source + layers
                s.addSource(GeoJsonSource(LOC_SOURCE, feature))

                // Outer accuracy ring (translucent blue)
                val ringRadius = accuracyM?.coerceIn(12f, 80f) ?: 24f
                s.addLayer(
                    CircleLayer(LOC_RING_LAYER, LOC_SOURCE)
                        .withProperties(
                            PropertyFactory.circleRadius(ringRadius / 2f),
                            PropertyFactory.circleColor("rgba(66,133,244,0.15)"),
                            PropertyFactory.circleStrokeWidth(1.5f),
                            PropertyFactory.circleStrokeColor("rgba(66,133,244,0.3)")
                        )
                )

                // Inner solid blue dot
                s.addLayer(
                    CircleLayer(LOC_DOT_LAYER, LOC_SOURCE)
                        .withProperties(
                            PropertyFactory.circleRadius(7f),
                            PropertyFactory.circleColor("#4285F4"),
                            PropertyFactory.circleStrokeWidth(2.5f),
                            PropertyFactory.circleStrokeColor("#FFFFFF")
                        )
                )
            }
        }
    }

    override fun removeCurrentLocationMarker() {
        runWhenReady {
            val s = style ?: return@runWhenReady
            s.removeLayer(LOC_DOT_LAYER)
            s.removeLayer(LOC_RING_LAYER)
            s.removeSource(LOC_SOURCE)
        }
    }

    override fun clearAll() {
        runWhenReady {
            val s = style ?: return@runWhenReady
            android.util.Log.d("MapLibreEngine", "clearAll: polylines=${polylineSources.size} markers=${markerSources.size}")

            for ((id, sourceId) in polylineSources) {
                s.removeLayer("arrow-layer-$id")
                s.removeLayer("line-layer-$id")
                s.removeSource(sourceId)
            }
            polylineSources.clear()

            // Remove annotation markers
            map?.markers?.forEach { map?.removeMarker(it) }
            markerSources.clear()

            // Also remove current location indicator
            s.removeLayer(LOC_DOT_LAYER)
            s.removeLayer(LOC_RING_LAYER)
            s.removeSource(LOC_SOURCE)
        }
    }

    override fun animateTo(lat: Double, lng: Double, zoom: Double, durationMs: Long) {
        runWhenReady {
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom),
                durationMs.toInt()
            )
        }
    }

    override fun fitBounds(points: List<Pair<Double, Double>>, paddingPx: Int, maxZoom: Double?) {
        if (points.size < 2) return
        runWhenReady {
            val builder = LatLngBounds.Builder()
            points.forEach { (lat, lng) -> builder.include(LatLng(lat, lng)) }
            val bounds = builder.build()

            // Check if span is too wide — center on most recent point instead
            val latSpan = bounds.latitudeSpan
            val lngSpan = bounds.longitudeSpan
            if (maxZoom != null && (latSpan > 0.1 || lngSpan > 0.1)) {
                // Wide area: center on last point at clamped zoom
                val last = points.last()
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(last.first, last.second), maxZoom),
                    500
                )
                return@runWhenReady
            }

            map?.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, paddingPx),
                500
            )
            // Clamp zoom if it ended up too low (zoomed out too far)
            if (maxZoom != null) {
                map?.cameraPosition?.let { pos ->
                    if (pos.zoom < maxZoom) {
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(bounds.center, maxZoom),
                            300
                        )
                    }
                }
            }
        }
    }

    override fun setOnMapClickListener(listener: (lat: Double, lng: Double) -> Unit) {
        onMapClick = listener
    }

    override fun setOnMarkerClickListener(listener: (markerId: String) -> Unit) {
        onMarkerClick = listener
    }

    override fun onStart() { mapView?.onStart() }
    override fun onResume() { mapView?.onResume() }
    override fun onPause() { mapView?.onPause() }
    override fun onStop() { mapView?.onStop() }

    override fun destroy() {
        initialized.set(false)
        pendingOps.clear()
        // Direct cleanup — don't go through runWhenReady since initialized is false
        style?.let { s ->
            for ((id, sourceId) in polylineSources) {
                s.removeLayer("arrow-layer-$id")
                s.removeLayer("line-layer-$id")
                s.removeSource(sourceId)
            }
            for ((id, info) in markerSources) {
                s.removeLayer(info.textLayerId)
                s.removeLayer(info.layerId)
                s.removeSource(info.sourceId)
            }
            s.removeLayer(LOC_DOT_LAYER)
            s.removeLayer(LOC_RING_LAYER)
            s.removeSource(LOC_SOURCE)
        }
        polylineSources.clear()
        markerSources.clear()
        arrowIconRegistered = false
        mapView?.onDestroy()
        map = null
        style = null
        mapView = null
    }

    private fun colorToString(color: Int): String {
        return String.format(
            "rgba(%d,%d,%d,%.2f)",
            Color.red(color),
            Color.green(color),
            Color.blue(color),
            Color.alpha(color) / 255.0
        )
    }

    private fun runWhenReady(block: () -> Unit) {
        if (initialized.get()) {
            block()
        } else if (mapView != null) {
            android.util.Log.d("MapLibreEngine", "runWhenReady: QUEUED (not initialized yet), pending=${pendingOps.size + 1}")
            pendingOps.add(block)
        } else {
            android.util.Log.w("MapLibreEngine", "runWhenReady: DROPPED (mapView is null)")
        }
    }
}
