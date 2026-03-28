package com.capitalbri.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ReportRequest(
    val segment_id: String,
    val captured_at: String,
    val pct_along_segment: Double? = null,
    val impediment_duration: String? = null,
    val hazard_types: List<String>? = null,
    val conditions: List<String>? = null,
    val traffic_level: String? = null,
    val cycling_experience_rating: Int? = null,
    val pleasantness_rating: Int? = null,
    val infrastructure: String? = null,
    val bike_lane_availability: String? = null,
    val client_lat: Double? = null,
    val client_lon: Double? = null
)

@Serializable
data class ReportResponse(
    val id: String,
    val created_at: String
)

@Serializable
data class ReportListItem(
    val id: String,
    val segment_id: String,
    val created_at: String,
    val captured_at: String,
    val impediment_duration: String? = null,
    val traffic_level: String? = null,
    val hazard_types: List<String>? = null,
    val conditions: List<String>? = null,
    val infrastructure: String? = null,
    val bike_lane_availability: String? = null,
    val cycling_experience_rating: Int? = null,
    val pleasantness_rating: Int? = null
)

// Enum constants
object ImpedimentDuration {
    const val SHORT_TERM = "short_term"
    const val LONG_TERM = "long_term"
    val all = listOf(SHORT_TERM, LONG_TERM)
    fun display(v: String) = when(v) {
        SHORT_TERM -> "Short-term"
        LONG_TERM -> "Long-term / structural"
        else -> v
    }
}

object TrafficLevel {
    const val LIGHT = "light"
    const val MODERATE = "moderate"
    const val HEAVY = "heavy"
    const val GRIDLOCK = "gridlock"
    val all = listOf(LIGHT, MODERATE, HEAVY, GRIDLOCK)
    fun display(v: String) = when(v) {
        LIGHT -> "Light"
        MODERATE -> "Moderate"
        HEAVY -> "Heavy"
        GRIDLOCK -> "Gridlock"
        else -> v
    }
}

object HazardType {
    const val POTHOLE = "pothole"
    const val CRACKED_SURFACE = "cracked_surface"
    const val LOOSE_GRAVEL = "loose_gravel"
    const val SAND = "sand"
    const val GLASS = "glass"
    const val DEBRIS = "debris"
    const val FLOODING = "flooding"
    const val CONSTRUCTION = "construction"
    const val VEHICLE_BLOCKING = "vehicle_blocking"
    const val OTHER = "other"
    val all = listOf(POTHOLE, CRACKED_SURFACE, LOOSE_GRAVEL, SAND, GLASS, DEBRIS, FLOODING, CONSTRUCTION, VEHICLE_BLOCKING, OTHER)
    fun display(v: String) = when(v) {
        POTHOLE -> "Pothole"
        CRACKED_SURFACE -> "Cracked surface"
        LOOSE_GRAVEL -> "Loose gravel"
        SAND -> "Sand"
        GLASS -> "Glass"
        DEBRIS -> "Debris"
        FLOODING -> "Flooding"
        CONSTRUCTION -> "Construction"
        VEHICLE_BLOCKING -> "Vehicle blocking lane"
        OTHER -> "Other"
        else -> v
    }
}

object SurfaceCondition {
    const val DRY = "dry"
    const val WET = "wet"
    const val ICY = "icy"
    const val SNOWY = "snowy"
    const val MUDDY = "muddy"
    val all = listOf(DRY, WET, ICY, SNOWY, MUDDY)
    fun display(v: String) = when(v) {
        DRY -> "Dry"
        WET -> "Wet"
        ICY -> "Icy"
        SNOWY -> "Snowy"
        MUDDY -> "Muddy"
        else -> v
    }
}

object Infrastructure {
    const val PAINTED_LANE = "painted_lane"
    const val PROTECTED_LANE = "protected_lane"
    const val SHARED_ROAD = "shared_road"
    const val SHARED_PATH = "shared_path"
    const val CONTRAFLOW_LANE = "contraflow_lane"
    const val DOOR_ZONE_LANE = "door_zone_lane"
    const val NONE = "none"
    const val OTHER = "other"
    val all = listOf(PAINTED_LANE, PROTECTED_LANE, SHARED_ROAD, SHARED_PATH, CONTRAFLOW_LANE, DOOR_ZONE_LANE, NONE, OTHER)
    fun display(v: String) = when(v) {
        PAINTED_LANE -> "Painted bike lane"
        PROTECTED_LANE -> "Protected lane"
        SHARED_ROAD -> "Shared road"
        SHARED_PATH -> "Shared path"
        CONTRAFLOW_LANE -> "Contraflow lane"
        DOOR_ZONE_LANE -> "Door zone lane"
        NONE -> "No infrastructure"
        OTHER -> "Other"
        else -> v
    }
}

object BikeLaneAvailability {
    const val CLEAR = "clear"
    const val PARTIAL = "partial"
    const val BLOCKED = "blocked"
    const val ABSENT = "absent"
    val all = listOf(CLEAR, PARTIAL, BLOCKED, ABSENT)
    fun display(v: String) = when(v) {
        CLEAR -> "Clear"
        PARTIAL -> "Partially blocked"
        BLOCKED -> "Fully blocked"
        ABSENT -> "Lane markings absent"
        else -> v
    }
}
