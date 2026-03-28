package com.capitalbri.shared.repository

import com.capitalbri.shared.api.ApiClient
import com.capitalbri.shared.model.NearestCandidate
import com.capitalbri.shared.model.Segment

interface SegmentRepository {
    suspend fun getSegmentsInViewport(
        minLon: Double, minLat: Double, maxLon: Double, maxLat: Double
    ): Result<List<Segment>>

    suspend fun getNearestCandidates(lat: Double, lon: Double): Result<List<NearestCandidate>>
}

class RemoteSegmentRepository(private val apiClient: ApiClient) : SegmentRepository {

    override suspend fun getSegmentsInViewport(
        minLon: Double, minLat: Double, maxLon: Double, maxLat: Double
    ): Result<List<Segment>> {
        return apiClient.getSegments(minLon, minLat, maxLon, maxLat)
            .map { it.segments }
    }

    override suspend fun getNearestCandidates(lat: Double, lon: Double): Result<List<NearestCandidate>> {
        return apiClient.getNearestSegments(lat, lon)
            .map { it.candidates }
    }
}
