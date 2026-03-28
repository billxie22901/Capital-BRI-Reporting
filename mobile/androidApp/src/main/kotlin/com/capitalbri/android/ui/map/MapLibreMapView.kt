package com.capitalbri.android.ui.map

import android.annotation.SuppressLint
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.capitalbri.shared.model.NearestCandidate
import com.capitalbri.shared.model.Segment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

private const val TAG = "MapLibreMapView"

private const val SEGMENTS_SOURCE  = "segments-source"
private const val SEGMENTS_LAYER   = "segments-layer"
private const val SELECTED_SOURCE  = "selected-source"
private const val SELECTED_LAYER   = "selected-layer"
private const val ENDPOINT_SOURCE  = "endpoint-source"
private const val ENDPOINT_LAYER   = "endpoint-layer"
private const val PENDING_SOURCE   = "pending-source"
private const val PENDING_LAYER    = "pending-layer"

// Basemap road layers from OpenFreeMap liberty style to query on tap
private val BASEMAP_ROAD_LAYERS = arrayOf(
    "road_minor", "road_secondary_tertiary", "road_trunk_primary",
    "road_motorway", "road_link", "road_service_track", "road_motorway_link",
    "bridge_street", "bridge_secondary_tertiary", "bridge_trunk_primary",
    "bridge_motorway", "bridge_link", "bridge_service_track", "bridge_motorway_link"
)

// US Capitol building
private val DC_CENTER = LatLng(38.8897, -77.0090)
private const val DC_ZOOM = 15.0

// OpenFreeMap — free OSM-based street tiles, no API key required
private const val DEMO_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

@SuppressLint("MissingPermission")
@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    segments: List<Segment>,
    selectedCandidate: NearestCandidate?,
    isReportingActive: Boolean,
    locationPermissionGranted: Boolean,
    onLocationUpdate: (Double, Double, Float) -> Unit,
    onViewportChanged: (minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) -> Unit,
    onSegmentTapped: (segmentId: String) -> Unit,
    onMapTapped: (lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    var mapLibreMap       by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapStyle          by remember { mutableStateOf<Style?>(null) }
    var pendingHighlight  by remember { mutableStateOf<String?>(null) }
    // Stable ref readable from click listener closure (factory runs once — params would be stale)
    val isReportingRef    = remember { androidx.compose.runtime.mutableStateOf(false) }
    val pendingRef        = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    isReportingRef.value  = isReportingActive
    // Keep pendingRef in sync so click listener can write to it
    LaunchedEffect(pendingRef.value) { pendingHighlight = pendingRef.value }

    // Track the last candidate ID we fitted the camera to — avoid re-fitting on extend/retract
    var lastFittedCandidateId by remember { mutableStateOf<String?>(null) }

    // Update all-segments overlay when viewport segments change
    LaunchedEffect(segments, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        updateSegmentOverlay(style, segments)
    }

    // Clear ALL overlays when reporting is cancelled (state → Browsing)
    LaunchedEffect(isReportingActive, mapStyle) {
        if (!isReportingActive) {
            pendingHighlight = null
            pendingRef.value = null
            lastFittedCandidateId = null
            val style = mapStyle ?: return@LaunchedEffect
            style.getSourceAs<GeoJsonSource>(PENDING_SOURCE)?.setGeoJson(emptyFeatureCollection())
            updateSelectedOverlay(style, null)
            updateEndpointMarkers(style, null)
        }
    }

    // Immediate pending highlight — shows basemap road tile while backend snaps
    LaunchedEffect(pendingHighlight, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(PENDING_SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(pendingHighlight ?: emptyFeatureCollection())
    }

    // Update confirmed highlight, endpoint markers, and camera when candidate changes
    LaunchedEffect(selectedCandidate, mapStyle) {
        val style = mapStyle ?: return@LaunchedEffect
        // Clear pending immediately so it doesn't flash alongside the confirmed highlight
        if (selectedCandidate != null) {
            pendingHighlight = null
            pendingRef.value = null
            style.getSourceAs<GeoJsonSource>(PENDING_SOURCE)?.setGeoJson(emptyFeatureCollection())
        }
        updateSelectedOverlay(style, selectedCandidate)
        updateEndpointMarkers(style, selectedCandidate)
        // Only fit camera when a new segment is selected, not on every extend/retract
        if (selectedCandidate != null && selectedCandidate.id != lastFittedCandidateId) {
            lastFittedCandidateId = selectedCandidate.id
            mapLibreMap?.let { fitCameraToCandidate(it, selectedCandidate) }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    getMapAsync { map ->
                        mapLibreMap = map

                        Log.d(TAG, "Loading style: $DEMO_STYLE_URL")
                        map.setStyle(DEMO_STYLE_URL) { style ->
                            Log.i(TAG, "Style loaded OK — ${style.layers.size} layers")
                            mapStyle = style

                            map.cameraPosition = CameraPosition.Builder()
                                .target(DC_CENTER)
                                .zoom(DC_ZOOM)
                                .build()

                            initOverlayLayers(style)

                            val bounds = map.projection.visibleRegion.latLngBounds
                            Log.d(TAG, "Initial viewport: ${bounds.longitudeWest},${bounds.latitudeSouth} → ${bounds.longitudeEast},${bounds.latitudeNorth}")
                            onViewportChanged(
                                bounds.longitudeWest, bounds.latitudeSouth,
                                bounds.longitudeEast, bounds.latitudeNorth
                            )
                        }

                        map.addOnCameraIdleListener {
                            val bounds = map.projection.visibleRegion.latLngBounds
                            Log.d(TAG, "Camera idle — reloading viewport")
                            onViewportChanged(
                                bounds.longitudeWest, bounds.latitudeSouth,
                                bounds.longitudeEast, bounds.latitudeNorth
                            )
                        }

                        // Tap: check overlay segments first, then basemap roads, then raw coords
                        map.addOnMapClickListener { point ->
                            val sp = map.projection.toScreenLocation(point)
                            val touchBox = RectF(sp.x - 24f, sp.y - 24f, sp.x + 24f, sp.y + 24f)

                            // 1. Check our loaded segment overlay (exact point)
                            val overlayFeatures = map.queryRenderedFeatures(sp, SEGMENTS_LAYER)
                            Log.d(TAG, "Map tap lat=${point.latitude} lon=${point.longitude} — ${overlayFeatures.size} overlay features")
                            if (overlayFeatures.isNotEmpty()) {
                                val segmentId = overlayFeatures.first().getStringProperty("id")
                                Log.d(TAG, "  → overlay hit: id=$segmentId")
                                if (segmentId != null) {
                                    onSegmentTapped(segmentId)
                                    return@addOnMapClickListener true
                                }
                            }

                            // 2. Query basemap road layers with 24px tolerance box for reliable line hit
                            val roadFeatures = map.queryRenderedFeatures(touchBox, *BASEMAP_ROAD_LAYERS)
                            Log.d(TAG, "  → ${roadFeatures.size} basemap road features in touch box")
                            if (roadFeatures.isNotEmpty()) {
                                val f = roadFeatures.first()
                                val roadName = f.getStringProperty("name") ?: "(unnamed road)"
                                Log.d(TAG, "  → basemap road: $roadName")
                                if (isReportingRef.value) {
                                    pendingRef.value = """{"type":"FeatureCollection","features":[${f.toJson()}]}"""
                                }
                            } else {
                                Log.d(TAG, "  → no road in touch box")
                            }

                            // 3. Forward to ViewModel for backend snap
                            onMapTapped(point.latitude, point.longitude)
                            false
                        }
                    }
                }
            },
            update = { _ ->
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

        // Zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn()) },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in")
            }
            SmallFloatingActionButton(
                onClick = { mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut()) },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom out")
            }
        }
    }
}

private fun initOverlayLayers(style: Style) {
    // All viewport segments — thin blue lines
    if (style.getSource(SEGMENTS_SOURCE) == null) {
        style.addSource(GeoJsonSource(SEGMENTS_SOURCE))
    }
    if (style.getLayer(SEGMENTS_LAYER) == null) {
        style.addLayer(
            LineLayer(SEGMENTS_LAYER, SEGMENTS_SOURCE).withProperties(
                lineColor("#2196F3"),
                lineWidth(3f),
                lineOpacity(0.6f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    // Selected segment — thick orange highlight
    if (style.getSource(SELECTED_SOURCE) == null) {
        style.addSource(GeoJsonSource(SELECTED_SOURCE))
    }
    if (style.getLayer(SELECTED_LAYER) == null) {
        style.addLayer(
            LineLayer(SELECTED_LAYER, SELECTED_SOURCE).withProperties(
                lineColor("#FF5722"),
                lineWidth(8f),
                lineOpacity(1f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    // Pending highlight — immediate glow from basemap tile feature while backend snaps
    if (style.getSource(PENDING_SOURCE) == null) {
        style.addSource(GeoJsonSource(PENDING_SOURCE))
    }
    if (style.getLayer(PENDING_LAYER) == null) {
        style.addLayer(
            LineLayer(PENDING_LAYER, PENDING_SOURCE).withProperties(
                lineColor("#FF9800"),
                lineWidth(6f),
                lineOpacity(0.75f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    // Endpoint markers — circles at segment start and end nodes
    // TODO: make these draggable to snap to adjacent OSM graph nodes
    if (style.getSource(ENDPOINT_SOURCE) == null) {
        style.addSource(GeoJsonSource(ENDPOINT_SOURCE))
    }
    if (style.getLayer(ENDPOINT_LAYER) == null) {
        style.addLayer(
            CircleLayer(ENDPOINT_LAYER, ENDPOINT_SOURCE).withProperties(
                circleRadius(8f),
                circleColor("#FF5722"),
                circleStrokeWidth(2.5f),
                circleStrokeColor("#FFFFFF")
            )
        )
    }
}

private fun updateSegmentOverlay(style: Style, segments: List<Segment>) {
    val source = style.getSourceAs<GeoJsonSource>(SEGMENTS_SOURCE) ?: return
    source.setGeoJson(buildSegmentFeatureCollection(segments))
}

private fun updateSelectedOverlay(style: Style, candidate: NearestCandidate?) {
    val source = style.getSourceAs<GeoJsonSource>(SELECTED_SOURCE) ?: return
    if (candidate == null) {
        source.setGeoJson(emptyFeatureCollection())
        return
    }
    val feature = JsonObject()
    feature.addProperty("type", "Feature")
    val props = JsonObject().also { it.addProperty("id", candidate.id) }
    feature.add("properties", props)
    val geom = JsonObject()
    geom.addProperty("type", "LineString")
    val coords = JsonArray()
    for (coord in candidate.geometry.coordinates) {
        val pair = JsonArray()
        pair.add(coord[0])
        pair.add(coord[1])
        coords.add(pair)
    }
    geom.add("coordinates", coords)
    feature.add("geometry", geom)
    val fc = JsonObject()
    fc.addProperty("type", "FeatureCollection")
    val features = JsonArray().also { it.add(feature) }
    fc.add("features", features)
    source.setGeoJson(fc.toString())
}

private fun updateEndpointMarkers(style: Style, candidate: NearestCandidate?) {
    val source = style.getSourceAs<GeoJsonSource>(ENDPOINT_SOURCE) ?: return
    if (candidate == null) {
        source.setGeoJson(emptyFeatureCollection())
        return
    }
    val coords = candidate.geometry.coordinates
    val endpoints = listOf(coords.first(), coords.last())
    val features = JsonArray()
    for (coord in endpoints) {
        val feature = JsonObject()
        feature.addProperty("type", "Feature")
        feature.add("properties", JsonObject())
        val geom = JsonObject()
        geom.addProperty("type", "Point")
        val c = JsonArray()
        c.add(coord[0])
        c.add(coord[1])
        geom.add("coordinates", c)
        feature.add("geometry", geom)
        features.add(feature)
    }
    val fc = JsonObject()
    fc.addProperty("type", "FeatureCollection")
    fc.add("features", features)
    source.setGeoJson(fc.toString())
}

private fun fitCameraToCandidate(map: MapLibreMap, candidate: NearestCandidate) {
    val coords = candidate.geometry.coordinates
    if (coords.size < 2) return
    val boundsBuilder = LatLngBounds.Builder()
    coords.forEach { boundsBuilder.include(LatLng(it[1], it[0])) }
    try {
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 200))
    } catch (e: Exception) {
        Log.w(TAG, "Camera fit failed: ${e.message}")
    }
}

private fun emptyFeatureCollection(): String {
    val fc = JsonObject()
    fc.addProperty("type", "FeatureCollection")
    fc.add("features", JsonArray())
    return fc.toString()
}

private fun buildSegmentFeatureCollection(segments: List<Segment>): String {
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
