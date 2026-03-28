package com.capitalbri.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Geometry(
    val type: String,
    val coordinates: List<List<Double>>
)

@Serializable
data class Segment(
    val id: String,
    val name: String? = null,
    val highway: String? = null,
    val cycleway: String? = null,
    val bicycle: String? = null,
    val length_m: Double = 0.0,
    val geometry: Geometry
)

@Serializable
data class SegmentsResponse(
    val segments: List<Segment>
)

@Serializable
data class NearestCandidate(
    val id: String,
    val name: String? = null,
    val distance_m: Double,
    val pct_along: Double,
    val geometry: Geometry
)

@Serializable
data class NearestResponse(
    val candidates: List<NearestCandidate>
)
