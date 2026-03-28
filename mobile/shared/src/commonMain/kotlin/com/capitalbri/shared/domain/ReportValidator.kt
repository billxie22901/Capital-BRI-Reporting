package com.capitalbri.shared.domain

import com.capitalbri.shared.model.ReportRequest

data class ValidationResult(val isValid: Boolean, val errors: List<String>)

object ReportValidator {

    fun validate(
        segmentId: String?,
        capturedAt: String?,
        hazardTypes: List<String>,
        conditions: List<String>,
        cyclingExperienceRating: Int?,
        pleasantnessRating: Int?
    ): ValidationResult {
        val errors = mutableListOf<String>()

        if (segmentId.isNullOrBlank()) errors.add("Segment ID is required")
        if (capturedAt.isNullOrBlank()) errors.add("Captured at timestamp is required")

        val hasQualifier = hazardTypes.isNotEmpty() ||
                conditions.isNotEmpty() ||
                cyclingExperienceRating != null ||
                pleasantnessRating != null

        if (!hasQualifier) {
            errors.add("At least one of: hazard types, surface conditions, experience rating, or pleasantness rating is required")
        }

        cyclingExperienceRating?.let {
            if (it < 1 || it > 10) errors.add("Cycling experience rating must be 1–10")
        }
        pleasantnessRating?.let {
            if (it < 1 || it > 10) errors.add("Pleasantness rating must be 1–10")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}
