package com.capitalbri.android.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capitalbri.android.CapitalBriApp
import com.capitalbri.shared.domain.GpsAccuracy
import com.capitalbri.shared.domain.GpsAccuracyClassifier
import com.capitalbri.shared.model.NearestCandidate
import com.capitalbri.shared.model.Segment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MapUiState {
    object Browsing : MapUiState()
    object SnappingToSegment : MapUiState()
    data class SegmentSuggested(
        val candidates: List<NearestCandidate>,
        val currentIndex: Int = 0
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

    init {
        login()
    }

    private fun login() {
        viewModelScope.launch {
            app.apiClient.login().onSuccess {
                _isLoggedIn.value = true
            }
        }
    }

    fun onGpsUpdate(lat: Double, lon: Double, accuracyMeters: Float) {
        _gpsAccuracy.value = GpsAccuracyClassifier.classify(accuracyMeters)
    }

    fun loadSegmentsForViewport(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) {
        viewModelScope.launch {
            app.segmentRepository.getSegmentsInViewport(minLon, minLat, maxLon, maxLat)
                .onSuccess { _segments.value = it }
        }
    }

    fun onReportFabTapped(lat: Double?, lon: Double?, accuracyMeters: Float?) {
        val accuracy = GpsAccuracyClassifier.classify(accuracyMeters)
        if (lat == null || lon == null || accuracy == GpsAccuracy.LOW || accuracy == GpsAccuracy.NONE) {
            // Fallback to tap mode — stay in Browsing but signal user to tap road
            _uiState.value = MapUiState.NoSegmentFound
            return
        }
        _uiState.value = MapUiState.SnappingToSegment
        viewModelScope.launch {
            app.segmentRepository.getNearestCandidates(lat, lon)
                .onSuccess { candidates ->
                    if (candidates.isEmpty()) {
                        _uiState.value = MapUiState.NoSegmentFound
                    } else {
                        _uiState.value = MapUiState.SegmentSuggested(candidates)
                    }
                }
                .onFailure {
                    // Fall back to tap mode silently
                    _uiState.value = MapUiState.Browsing
                }
        }
    }

    fun confirmSegment() {
        val state = _uiState.value
        if (state is MapUiState.SegmentSuggested) {
            state.current?.let { _uiState.value = MapUiState.SegmentConfirmed(it) }
        }
    }

    fun nextCandidate() {
        val state = _uiState.value
        if (state is MapUiState.SegmentSuggested) {
            val next = state.currentIndex + 1
            if (next < state.candidates.size) {
                _uiState.value = state.copy(currentIndex = next)
            } else {
                _uiState.value = MapUiState.NoSegmentFound
            }
        }
    }

    fun cancelReporting() {
        _uiState.value = MapUiState.Browsing
    }

    fun onSegmentTapped(segmentId: String) {
        val segment = _segments.value.find { it.id == segmentId } ?: return
        // Create a synthetic candidate from a tapped segment
        val candidate = NearestCandidate(
            id = segment.id,
            name = segment.name,
            distance_m = 0.0,
            pct_along = 0.5,
            geometry = segment.geometry
        )
        _uiState.value = MapUiState.SegmentSuggested(listOf(candidate))
    }
}
