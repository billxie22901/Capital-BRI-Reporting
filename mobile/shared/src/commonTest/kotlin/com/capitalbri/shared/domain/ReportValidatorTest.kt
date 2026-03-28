package com.capitalbri.shared.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportValidatorTest {

    @Test
    fun validReportWithHazardType() {
        val result = ReportValidator.validate(
            segmentId = "some-uuid",
            capturedAt = "2026-03-28T14:30:00Z",
            hazardTypes = listOf("pothole"),
            conditions = emptyList(),
            cyclingExperienceRating = null,
            pleasantnessRating = null
        )
        assertTrue(result.isValid)
    }

    @Test
    fun validReportWithRatingOnly() {
        val result = ReportValidator.validate(
            segmentId = "some-uuid",
            capturedAt = "2026-03-28T14:30:00Z",
            hazardTypes = emptyList(),
            conditions = emptyList(),
            cyclingExperienceRating = 7,
            pleasantnessRating = null
        )
        assertTrue(result.isValid)
    }

    @Test
    fun invalidReportMissingSegmentId() {
        val result = ReportValidator.validate(
            segmentId = null,
            capturedAt = "2026-03-28T14:30:00Z",
            hazardTypes = listOf("pothole"),
            conditions = emptyList(),
            cyclingExperienceRating = null,
            pleasantnessRating = null
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Segment ID") })
    }

    @Test
    fun invalidReportNoQualifyingField() {
        val result = ReportValidator.validate(
            segmentId = "some-uuid",
            capturedAt = "2026-03-28T14:30:00Z",
            hazardTypes = emptyList(),
            conditions = emptyList(),
            cyclingExperienceRating = null,
            pleasantnessRating = null
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("At least one") })
    }

    @Test
    fun invalidRatingOutOfRange() {
        val result = ReportValidator.validate(
            segmentId = "some-uuid",
            capturedAt = "2026-03-28T14:30:00Z",
            hazardTypes = emptyList(),
            conditions = emptyList(),
            cyclingExperienceRating = 11,
            pleasantnessRating = null
        )
        assertFalse(result.isValid)
    }

    @Test
    fun gpsAccuracyClassification() {
        assert(GpsAccuracyClassifier.classify(10f) == GpsAccuracy.HIGH)
        assert(GpsAccuracyClassifier.classify(30f) == GpsAccuracy.MEDIUM)
        assert(GpsAccuracyClassifier.classify(100f) == GpsAccuracy.LOW)
        assert(GpsAccuracyClassifier.classify(null) == GpsAccuracy.NONE)
    }
}
