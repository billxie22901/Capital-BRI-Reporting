package com.capitalbri.shared.api

import com.capitalbri.shared.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiClient(private val baseUrl: String = ApiConfig.BASE_URL) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
    }

    private var token: String? = null

    suspend fun login(username: String = "demo", password: String = "demo"): Result<String> {
        return try {
            val response = client.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("username" to username, "password" to password))
            }
            val body = response.body<Map<String, String>>()
            val accessToken = body["access_token"] ?: return Result.failure(Exception("No token in response"))
            token = accessToken
            Result.success(accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSegments(
        minLon: Double, minLat: Double, maxLon: Double, maxLat: Double,
        zoom: Int? = null
    ): Result<SegmentsResponse> {
        return try {
            val response = client.get("$baseUrl/segments") {
                parameter("bbox", "$minLon,$minLat,$maxLon,$maxLat")
                zoom?.let { parameter("zoom", it) }
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNearestSegments(
        lat: Double, lon: Double,
        radiusM: Int = 50,
        limit: Int = 3
    ): Result<NearestResponse> {
        return try {
            val response = client.get("$baseUrl/segments/nearest") {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("radius_m", radiusM)
                parameter("limit", limit)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitReport(report: ReportRequest): Result<ReportResponse> {
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = client.post("$baseUrl/reports") {
                contentType(ContentType.Application.Json)
                bearerAuth(authToken)
                setBody(report)
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReports(segmentId: String? = null, limit: Int = 20): Result<List<ReportListItem>> {
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = client.get("$baseUrl/reports") {
                bearerAuth(authToken)
                parameter("limit", limit)
                segmentId?.let { parameter("segment_id", it) }
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkHealth(): Boolean {
        return try {
            val response = client.get("$baseUrl/health")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    fun close() = client.close()
}
