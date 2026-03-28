package com.capitalbri.android.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.capitalbri.android.CapitalBriApp
import com.capitalbri.shared.domain.ReportValidator
import com.capitalbri.shared.model.ReportRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReportFormState(
    val impedimentDuration: String? = null,
    val selectedHazardTypes: Set<String> = emptySet(),
    val selectedConditions: Set<String> = emptySet(),
    val trafficLevel: String? = null,
    val infrastructure: String? = null,
    val bikeLaneAvailability: String? = null,
    val cyclingExperienceRating: Int? = null,
    val pleasantnessRating: Int? = null,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null,
    val validationErrors: List<String> = emptyList()
)

class ReportFormViewModel(app: Application) : AndroidViewModel(app) {

    private val reportRepository = (app as CapitalBriApp).reportRepository

    private val _formState = MutableStateFlow(ReportFormState())
    val formState: StateFlow<ReportFormState> = _formState.asStateFlow()

    fun toggleHazardType(value: String) {
        _formState.value = _formState.value.let { s ->
            val updated = if (s.selectedHazardTypes.contains(value))
                s.selectedHazardTypes - value else s.selectedHazardTypes + value
            s.copy(selectedHazardTypes = updated, errorMessage = null)
        }
    }

    fun toggleCondition(value: String) {
        _formState.value = _formState.value.let { s ->
            val updated = if (s.selectedConditions.contains(value))
                s.selectedConditions - value else s.selectedConditions + value
            s.copy(selectedConditions = updated, errorMessage = null)
        }
    }

    fun setImpedimentDuration(value: String?) {
        _formState.value = _formState.value.copy(impedimentDuration = value, errorMessage = null)
    }

    fun setTrafficLevel(value: String?) {
        _formState.value = _formState.value.copy(trafficLevel = value, errorMessage = null)
    }

    fun setInfrastructure(value: String?) {
        _formState.value = _formState.value.copy(infrastructure = value, errorMessage = null)
    }

    fun setBikeLaneAvailability(value: String?) {
        _formState.value = _formState.value.copy(bikeLaneAvailability = value, errorMessage = null)
    }

    fun setCyclingExperienceRating(value: Int?) {
        _formState.value = _formState.value.copy(cyclingExperienceRating = value, errorMessage = null)
    }

    fun setPleasantnessRating(value: Int?) {
        _formState.value = _formState.value.copy(pleasantnessRating = value, errorMessage = null)
    }

    fun submit(segmentId: String, pctAlong: Double) {
        val state = _formState.value
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val capturedAt = sdf.format(Date())

        val validation = ReportValidator.validate(
            segmentId = segmentId,
            capturedAt = capturedAt,
            hazardTypes = state.selectedHazardTypes.toList(),
            conditions = state.selectedConditions.toList(),
            cyclingExperienceRating = state.cyclingExperienceRating,
            pleasantnessRating = state.pleasantnessRating
        )

        if (!validation.isValid) {
            _formState.value = state.copy(validationErrors = validation.errors)
            return
        }

        _formState.value = state.copy(isSubmitting = true, validationErrors = emptyList())

        viewModelScope.launch {
            val request = ReportRequest(
                segment_id = segmentId,
                captured_at = capturedAt,
                pct_along_segment = pctAlong.takeIf { it > 0 },
                impediment_duration = state.impedimentDuration,
                hazard_types = state.selectedHazardTypes.toList().takeIf { it.isNotEmpty() },
                conditions = state.selectedConditions.toList().takeIf { it.isNotEmpty() },
                traffic_level = state.trafficLevel,
                infrastructure = state.infrastructure,
                bike_lane_availability = state.bikeLaneAvailability,
                cycling_experience_rating = state.cyclingExperienceRating,
                pleasantness_rating = state.pleasantnessRating
            )

            reportRepository.submitReport(request)
                .onSuccess {
                    _formState.value = _formState.value.copy(isSubmitting = false, submitSuccess = true)
                }
                .onFailure { err ->
                    _formState.value = _formState.value.copy(
                        isSubmitting = false,
                        errorMessage = err.message ?: "Submission failed — check your connection"
                    )
                }
        }
    }
}
