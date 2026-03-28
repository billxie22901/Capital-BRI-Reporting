package com.capitalbri.shared.domain

enum class GpsAccuracy {
    HIGH,    // < 15 m — GPS-nearest auto-selects
    MEDIUM,  // 15–50 m — suggested, user must confirm
    LOW,     // > 50 m — GPS-nearest disabled, force tap
    NONE     // No signal
}

object GpsAccuracyClassifier {
    fun classify(accuracyMeters: Float?): GpsAccuracy {
        if (accuracyMeters == null) return GpsAccuracy.NONE
        return when {
            accuracyMeters < 15f -> GpsAccuracy.HIGH
            accuracyMeters < 50f -> GpsAccuracy.MEDIUM
            else -> GpsAccuracy.LOW
        }
    }
}
