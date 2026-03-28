package com.capitalbri.android.ui.map

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capitalbri.android.CapitalBriApp
import com.capitalbri.shared.domain.GpsAccuracy
import com.capitalbri.shared.domain.GpsAccuracyClassifier
import com.capitalbri.shared.model.Geometry
import com.capitalbri.shared.model.NearestCandidate
import com.capitalbri.shared.model.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MapViewModel"

sealed class MapUiState {
    object Browsing : MapUiState()
    object SnappingToSegment : MapUiState()
    data class SegmentSuggested(
        val candidates: List<NearestCandidate>,
        val currentIndex: Int = 0,
        val backExtensions: Int = 0,
        val forwardExtensions: Int = 0
    ) : MapUiState() {
        val current: NearestCandidate? get() = candidates.getOrNull(currentIndex)
    }
    data class SegmentConfirmed(val candidate: NearestCandidate) : MapUiState()
    object NoSegmentFound : MapUiState()
    data class Error(val message: String) : MapUiState()
}

class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val app = app as CapitalBriApp

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Browsing)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _gpsAccuracy = MutableStateFlow(GpsAccuracy.NONE)
    val gpsAccuracy: StateFlow<GpsAccuracy> = _gpsAccuracy.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // The original backend candidate before any user-driven extensions
    private var originalCandidate: NearestCandidate? = null

    // Track how many coords were added by each extend operation so retract can undo exactly one step
    private val forwardExtensionSizes = ArrayDeque<Int>()
    private val backExtensionSizes = ArrayDeque<Int>()

    init {
        login()
    }

    private fun login() {
        viewModelScope.launch {
            Log.d(TAG, "Attempting login...")
            app.apiClient.login()
                .onSuccess {
                    _isLoggedIn.value = true
                    Log.i(TAG, "Login OK — token received")
                }
                .onFailure {
                    Log.e(TAG, "Login FAILED: ${it::class.simpleName}: ${it.message}")
                }
        }
    }

    fun onGpsUpdate(lat: Double, lon: Double, accuracyMeters: Float) {
        val classified = GpsAccuracyClassifier.classify(accuracyMeters)
        Log.d(TAG, "GPS update: lat=$lat lon=$lon accuracy=${accuracyMeters}m → $classified")
        _gpsAccuracy.value = classified
    }

    fun loadSegmentsForViewport(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) {
        viewModelScope.launch {
            Log.d(TAG, "Loading segments for bbox: $minLon,$minLat → $maxLon,$maxLat")
            app.segmentRepository.getSegmentsInViewport(minLon, minLat, maxLon, maxLat)
                .onSuccess {
                    Log.i(TAG, "Loaded ${it.size} segments from backend")
                    _segments.value = it
                }
                .onFailure {
                    Log.e(TAG, "Failed to load segments: ${it::class.simpleName}: ${it.message}")
                }
        }
    }

    fun onReportFabTapped(lat: Double?, lon: Double?, accuracyMeters: Float?) {
        val accuracy = GpsAccuracyClassifier.classify(accuracyMeters)
        Log.d(TAG, "Report FAB tapped — lat=$lat lon=$lon accuracyMeters=$accuracyMeters → classified=$accuracy")
        if (lat == null || lon == null || accuracy == GpsAccuracy.LOW || accuracy == GpsAccuracy.NONE) {
            Log.w(TAG, "GPS unavailable or inaccurate — switching to manual tap mode")
            _uiState.value = MapUiState.NoSegmentFound
            return
        }
        _uiState.value = MapUiState.SnappingToSegment
        fetchNearestCandidates(lat, lon, fallbackToTapMode = true)
    }

    fun onMapTapped(lat: Double, lon: Double) {
        val state = _uiState.value
        if (state !is MapUiState.NoSegmentFound && state !is MapUiState.SnappingToSegment) {
            Log.d(TAG, "Raw map tap ignored — not in tap-select state (state=${state::class.simpleName})")
            return
        }
        if (state is MapUiState.SnappingToSegment) {
            Log.d(TAG, "Already snapping, ignoring duplicate tap")
            return
        }
        Log.d(TAG, "Raw map tap in NoSegmentFound — querying nearest segment at lat=$lat lon=$lon")
        _uiState.value = MapUiState.SnappingToSegment
        fetchNearestCandidates(lat, lon, fallbackToTapMode = false)
    }

    private fun fetchNearestCandidates(lat: Double, lon: Double, fallbackToTapMode: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "Fetching nearest candidates for lat=$lat lon=$lon")
            app.segmentRepository.getNearestCandidates(lat, lon)
                .onSuccess { candidates ->
                    Log.i(TAG, "Got ${candidates.size} nearest candidates")
                    if (candidates.isEmpty()) {
                        Log.w(TAG, "No candidates returned — backend has no segments near this location")
                        _uiState.value = MapUiState.NoSegmentFound
                    } else {
                        originalCandidate = candidates.first()
                        forwardExtensionSizes.clear()
                        backExtensionSizes.clear()
                        _uiState.value = MapUiState.SegmentSuggested(candidates)
                    }
                }
                .onFailure {
                    Log.e(TAG, "getNearestCandidates FAILED: ${it::class.simpleName}: ${it.message}")
                    _uiState.value = if (fallbackToTapMode) MapUiState.NoSegmentFound else MapUiState.Browsing
                }
        }
    }

    fun confirmSegment() {
        val state = _uiState.value
        if (state is MapUiState.SegmentSuggested) {
            state.current?.let {
                Log.i(TAG, "Segment confirmed: id=${it.id} name=${it.name}")
                _uiState.value = MapUiState.SegmentConfirmed(it)
            }
        }
    }

    fun nextCandidate() {
        val state = _uiState.value
        if (state is MapUiState.SegmentSuggested) {
            val next = state.currentIndex + 1
            Log.d(TAG, "Next candidate: $next / ${state.candidates.size}")
            if (next < state.candidates.size) {
                _uiState.value = state.copy(currentIndex = next)
            } else {
                Log.w(TAG, "No more candidates — returning to NoSegmentFound")
                _uiState.value = MapUiState.NoSegmentFound
            }
        }
    }

    fun cancelReporting() {
        Log.d(TAG, "Reporting cancelled")
        originalCandidate = null
        forwardExtensionSizes.clear()
        backExtensionSizes.clear()
        _uiState.value = MapUiState.Browsing
    }

    fun onSegmentTapped(segmentId: String) {
        Log.d(TAG, "Overlay segment tapped: $segmentId (${_segments.value.size} segments loaded)")
        val segment = _segments.value.find { it.id == segmentId }
        if (segment == null) {
            Log.w(TAG, "Tapped segment $segmentId not found in local segments list")
            return
        }
        val candidate = NearestCandidate(
            id = segment.id,
            name = segment.name,
            distance_m = 0.0,
            pct_along = 0.5,
            geometry = segment.geometry
        )
        originalCandidate = candidate
        forwardExtensionSizes.clear()
        backExtensionSizes.clear()
        _uiState.value = MapUiState.SegmentSuggested(listOf(candidate))
    }

    /** Extend the selected segment by merging the adjacent loaded segment at the forward (end) node. */
    fun extendSegmentForward() {
        val candidate = currentCandidate() ?: return
        val endCoord = candidate.geometry.coordinates.last()
        val adjacent = findAdjacentAt(endCoord, candidate) ?: run {
            Log.w(TAG, "No adjacent segment found at end node")
            return
        }
        val adjCoords = if (coordsNear(adjacent.geometry.coordinates.first(), endCoord))
            adjacent.geometry.coordinates
        else
            adjacent.geometry.coordinates.reversed()
        val added = adjCoords.drop(1)
        forwardExtensionSizes.addLast(added.size)
        val merged = candidate.geometry.coordinates + added
        Log.i(TAG, "Extended forward: +${added.size} coords (history size=${forwardExtensionSizes.size})")
        replaceCandidate(candidate.copy(geometry = Geometry("LineString", merged)))
    }

    /** Retract the last forward extension from the end of the segment. */
    fun retractSegmentForward() {
        val candidate = currentCandidate() ?: return
        val size = forwardExtensionSizes.removeLastOrNull() ?: run {
            Log.w(TAG, "Nothing to retract at forward end")
            return
        }
        val trimmed = candidate.geometry.coordinates.dropLast(size)
        Log.i(TAG, "Retracted forward: -$size coords")
        replaceCandidate(candidate.copy(geometry = Geometry("LineString", trimmed)))
    }

    /** Extend the selected segment by merging the adjacent loaded segment at the back (start) node. */
    fun extendSegmentBack() {
        val candidate = currentCandidate() ?: return
        val startCoord = candidate.geometry.coordinates.first()
        val adjacent = findAdjacentAt(startCoord, candidate) ?: run {
            Log.w(TAG, "No adjacent segment found at start node")
            return
        }
        val adjCoords = if (coordsNear(adjacent.geometry.coordinates.last(), startCoord))
            adjacent.geometry.coordinates
        else
            adjacent.geometry.coordinates.reversed()
        val added = adjCoords.dropLast(1)
        backExtensionSizes.addLast(added.size)
        val merged = added + candidate.geometry.coordinates
        Log.i(TAG, "Extended back: +${added.size} coords (history size=${backExtensionSizes.size})")
        replaceCandidate(candidate.copy(geometry = Geometry("LineString", merged)))
    }

    /** Retract the last back extension from the start of the segment. */
    fun retractSegmentBack() {
        val candidate = currentCandidate() ?: return
        val size = backExtensionSizes.removeLastOrNull() ?: run {
            Log.w(TAG, "Nothing to retract at back end")
            return
        }
        val trimmed = candidate.geometry.coordinates.drop(size)
        Log.i(TAG, "Retracted back: -$size coords")
        replaceCandidate(candidate.copy(geometry = Geometry("LineString", trimmed)))
    }

    /** Reset any extensions back to the original single segment from the backend. */
    fun resetSegment() {
        val orig = originalCandidate ?: return
        forwardExtensionSizes.clear()
        backExtensionSizes.clear()
        Log.d(TAG, "Resetting segment to original: id=${orig.id}")
        replaceCandidate(orig)
    }

    private fun currentCandidate(): NearestCandidate? {
        return (_uiState.value as? MapUiState.SegmentSuggested)?.current
    }

    private fun replaceCandidate(updated: NearestCandidate) {
        val state = _uiState.value as? MapUiState.SegmentSuggested ?: return
        _uiState.value = state.copy(
            candidates = listOf(updated),
            currentIndex = 0,
            backExtensions = backExtensionSizes.size,
            forwardExtensions = forwardExtensionSizes.size
        )
    }

    private fun findAdjacentAt(coord: List<Double>, current: NearestCandidate): Segment? {
        return _segments.value.firstOrNull { seg ->
            // Skip any segment whose coordinates are all already inside the current candidate
            val alreadyContained = seg.geometry.coordinates.all { segCoord ->
                current.geometry.coordinates.any { candCoord -> coordsNear(candCoord, segCoord) }
            }
            if (alreadyContained) return@firstOrNull false
            coordsNear(seg.geometry.coordinates.first(), coord) ||
                coordsNear(seg.geometry.coordinates.last(), coord)
        }
    }

    private fun coordsNear(a: List<Double>, b: List<Double>): Boolean =
        Math.abs(a[0] - b[0]) < 0.00002 && Math.abs(a[1] - b[1]) < 0.00002
}
