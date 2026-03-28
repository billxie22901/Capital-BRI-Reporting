package com.capitalbri.android.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capitalbri.shared.domain.GpsAccuracy

@Composable
fun MapScreen(
    onNavigateToReportForm: (segmentId: String, segmentName: String, pctAlong: Double) -> Unit,
    onNavigateToRecentReports: () -> Unit,
    viewModel: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val gpsAccuracy by viewModel.gpsAccuracy.collectAsState()
    val segments by viewModel.segments.collectAsState()

    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
        if (!granted) showPermissionRationale = true
    }

    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLon by remember { mutableStateOf<Double?>(null) }
    var currentAccuracy by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // MapLibre Map
        MapLibreMapView(
            modifier = Modifier.fillMaxSize(),
            segments = segments,
            selectedCandidate = when (val s = uiState) {
                is MapUiState.SegmentSuggested -> s.current
                is MapUiState.SegmentConfirmed -> s.candidate
                else -> null
            },
            isReportingActive = uiState !is MapUiState.Browsing,
            locationPermissionGranted = locationPermissionGranted,
            onLocationUpdate = { lat, lon, accuracy ->
                currentLat = lat
                currentLon = lon
                currentAccuracy = accuracy
                viewModel.onGpsUpdate(lat, lon, accuracy)
            },
            onViewportChanged = { minLon, minLat, maxLon, maxLat ->
                viewModel.loadSegmentsForViewport(minLon, minLat, maxLon, maxLat)
            },
            onSegmentTapped = { segmentId ->
                viewModel.onSegmentTapped(segmentId)
            },
            onMapTapped = { lat, lon ->
                viewModel.onMapTapped(lat, lon)
            }
        )

        // GPS accuracy warning
        if (gpsAccuracy == GpsAccuracy.LOW || gpsAccuracy == GpsAccuracy.NONE) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (gpsAccuracy == GpsAccuracy.NONE)
                        "No GPS signal — tap your road on the map"
                    else
                        "Location unclear — tap your road on the map",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = onNavigateToRecentReports,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.List, contentDescription = "Recent reports")
            }
            FloatingActionButton(
                onClick = {
                    viewModel.onReportFabTapped(currentLat, currentLon, currentAccuracy)
                }
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Report")
            }
        }

        // Segment suggestion / confirmation sheet
        when (val state = uiState) {
            is MapUiState.SegmentSuggested -> {
                SegmentConfirmSheet(
                    segmentName = state.current?.name ?: "Unknown road",
                    distanceMeters = state.current?.distance_m,
                    canRetractBack = state.backExtensions > 0,
                    canRetractForward = state.forwardExtensions > 0,
                    onConfirm = {
                        viewModel.confirmSegment()
                        val candidate = state.current ?: return@SegmentConfirmSheet
                        onNavigateToReportForm(
                            candidate.id,
                            candidate.name ?: "Unknown road",
                            candidate.pct_along
                        )
                    },
                    onNotThisRoad = { viewModel.nextCandidate() },
                    onDismiss = { viewModel.cancelReporting() },
                    onExtendBack = { viewModel.extendSegmentBack() },
                    onRetractBack = { viewModel.retractSegmentBack() },
                    onExtendForward = { viewModel.extendSegmentForward() },
                    onRetractForward = { viewModel.retractSegmentForward() },
                    onReset = { viewModel.resetSegment() }
                )
            }
            is MapUiState.NoSegmentFound -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tap a road on the map to select it.")
                        TextButton(onClick = { viewModel.cancelReporting() }) {
                            Text("Cancel")
                        }
                    }
                }
            }
            is MapUiState.SnappingToSegment -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
            else -> {}
        }

        // Location permission rationale
        if (showPermissionRationale) {
            AlertDialog(
                onDismissRequest = { showPermissionRationale = false },
                title = { Text("Location needed") },
                text = { Text("Capital BRI uses your location to identify the road you're on and anchor reports to the right street segment.") },
                confirmButton = {
                    TextButton(onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) {
                        Text("Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionRationale = false }) { Text("Not now") }
                }
            )
        }
    }
}

@Composable
fun SegmentConfirmSheet(
    segmentName: String,
    distanceMeters: Double?,
    canRetractBack: Boolean,
    canRetractForward: Boolean,
    onConfirm: () -> Unit,
    onNotThisRoad: () -> Unit,
    onDismiss: () -> Unit,
    onExtendBack: () -> Unit,
    onRetractBack: () -> Unit,
    onExtendForward: () -> Unit,
    onRetractForward: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Report on this road?",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = segmentName,
                style = MaterialTheme.typography.bodyLarge
            )
            distanceMeters?.let {
                Text(
                    text = "${it.toInt()} m from your location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Segment length controls — left column controls start of segment, right column controls end
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back-end (start) controls
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onExtendBack,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) { Text("← Extend", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onRetractBack,
                        enabled = canRetractBack,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) { Text("← Retract", style = MaterialTheme.typography.labelSmall) }
                }
                // Reset in the middle
                TextButton(onClick = onReset, enabled = canRetractBack || canRetractForward) {
                    Text("Reset", style = MaterialTheme.typography.bodySmall)
                }
                // Forward-end (end) controls
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onExtendForward,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) { Text("Extend →", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = onRetractForward,
                        enabled = canRetractForward,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) { Text("Retract →", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Action row: Cancel exits reporting. "Not this road" tries the next nearest candidate.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                OutlinedButton(
                    onClick = onNotThisRoad,
                    modifier = Modifier.weight(1f)
                ) { Text("Not this road") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) { Text("Confirm") }
            }
        }
    }
}
