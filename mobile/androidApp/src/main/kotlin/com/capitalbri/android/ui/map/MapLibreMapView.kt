package com.capitalbri.android.ui.map

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.capitalbri.shared.model.Segment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.Property

private const val SEGMENTS_SOURCE = "segments-source"
private const val SEGMENTS_LAYER = "segments-layer"
private const val SELECTED_SOURCE = "selected-source"
private const val SELECTED_LAYER = "selected-layer"

// DC center coordinates
private val DC_CENTER = LatLng(38.9072, -77.0369)
private const val DC_ZOOM = 13.0

// MapLibre demo tile style — replace with Protomaps/Maptiler URL for production
private const val DEMO_STYLE_URL = "https://demotiles.maplibre.org/style.json"

@SuppressLint("MissingPermission")
@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    segments: List<Segment>,
    selectedSegmentId: String?,
    locationPermissionGranted: Boolean,
    onLocationUpdate: (Double, Double, Float) -> Unit,
    onViewportChanged: (minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) -> Unit,
    onSegmentTapped: (segmentId: String) -> Unit
) {
    val context = LocalContext.current
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle by remember { mutableStateOf<Style?>(null) }

    // Initialize MapLibre
    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    // Update segment overlay when segments change
    LaunchedEffect(segments, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        updateSegmentOverlay(style, segments)
    }

    // Update selected segment highlight
    LaunchedEffect(selectedSegmentId, segments, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val selected = segments.filter { it.id == selectedSegmentId }
        updateSelectedOverlay(style, selected)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                getMapAsync { map ->
                    mapLibreMap = map

                    map.setStyle(DEMO_STYLE_URL) { style ->
                        mapStyle = style

                        // Set initial camera to DC
                        map.cameraPosition = CameraPosition.Builder()
                            .target(DC_CENTER)
                            .zoom(DC_ZOOM)
                            .build()

                        // Initialize sources and layers
                        initOverlayLayers(style)

                        // Load initial viewport
                        val bounds = map.projection.visibleRegion.latLngBounds
                        onViewportChanged(
                            bounds.lonWest, bounds.latSouth,
                            bounds.lonEast, bounds.latNorth
                        )
                    }

                    // Listen for camera moves to reload segments
                    map.addOnCameraIdleListener {
                        val bounds = map.projection.visibleRegion.latLngBounds
                        onViewportChanged(
                            bounds.lonWest, bounds.latSouth,
                            bounds.lonEast, bounds.latNorth
                        )
                    }

                    // Tap handler for segment selection
                    map.addOnMapClickListener { point ->
                        val screenPoint = map.projection.toScreenLocation(point)
                        val features = map.queryRenderedFeatures(screenPoint, SEGMENTS_LAYER)
                        if (features.isNotEmpty()) {
                            val segmentId = features.first().getStringProperty("id")
                            if (segmentId != null) {
                                onSegmentTapped(segmentId)
                                return@addOnMapClickListener true
                            }
                        }
                        false
                    }
                }
            }
        },
        update = { mapView ->
            // Enable/disable location component based on permission
            mapLibreMap?.getStyle { style ->
                if (locationPermissionGranted) {
                    try {
                        val locationComponent = mapLibreMap!!.locationComponent
                        if (!locationComponent.isLocationComponentActivated) {
                            locationComponent.activateLocationComponent(
                                LocationComponentActivationOptions.builder(context, style).build()
                            )
                        }
                        locationComponent.isLocationComponentEnabled = true
                        locationComponent.cameraMode = CameraMode.NONE
                        locationComponent.renderMode = RenderMode.COMPASS
                        locationComponent.addOnLocationClickListener {}
                    } catch (e: Exception) {
                        // Location component setup failed — ignore for lab
                    }
                }
            }
        }
    )
}

private fun initOverlayLayers(style: Style) {
    // Segments source + layer (idle state — thin blue lines)
    if (style.getSource(SEGMENTS_SOURCE) == null) {
        style.addSource(GeoJsonSource(SEGMENTS_SOURCE))
    }
    if (style.getLayer(SEGMENTS_LAYER) == null) {
        style.addLayer(
            LineLayer(SEGMENTS_LAYER, SEGMENTS_SOURCE)
                .withProperties(
                    lineColor("#2196F3"),
                    lineWidth(3f),
                    lineOpacity(0.6f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND)
                )
        )
    }

    // Selected segment source + layer (thick highlight)
    if (style.getSource(SELECTED_SOURCE) == null) {
        style.addSource(GeoJsonSource(SELECTED_SOURCE))
    }
    if (style.getLayer(SELECTED_LAYER) == null) {
        style.addLayer(
            LineLayer(SELECTED_LAYER, SELECTED_SOURCE)
                .withProperties(
                    lineColor("#FF5722"),
                    lineWidth(7f),
                    lineOpacity(1f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND)
                )
        )
    }
}

private fun updateSegmentOverlay(style: Style, segments: List<Segment>) {
    val source = style.getSourceAs<GeoJsonSource>(SEGMENTS_SOURCE) ?: return
    val featureCollection = buildFeatureCollection(segments)
    source.setGeoJson(featureCollection)
}

private fun updateSelectedOverlay(style: Style, segments: List<Segment>) {
    val source = style.getSourceAs<GeoJsonSource>(SELECTED_SOURCE) ?: return
    val featureCollection = buildFeatureCollection(segments)
    source.setGeoJson(featureCollection)
}

private fun buildFeatureCollection(segments: List<Segment>): String {
    val features = JsonArray()
    for (seg in segments) {
        val feature = JsonObject()
        feature.addProperty("type", "Feature")

        val props = JsonObject()
        props.addProperty("id", seg.id)
        props.addProperty("name", seg.name ?: "")
        feature.add("properties", props)

        val geom = JsonObject()
        geom.addProperty("type", "LineString")
        val coords = JsonArray()
        for (coord in seg.geometry.coordinates) {
            val pair = JsonArray()
            pair.add(coord[0])
            pair.add(coord[1])
            coords.add(pair)
        }
        geom.add("coordinates", coords)
        feature.add("geometry", geom)

        features.add(feature)
    }

    val fc = JsonObject()
    fc.addProperty("type", "FeatureCollection")
    fc.add("features", features)
    return fc.toString()
}
