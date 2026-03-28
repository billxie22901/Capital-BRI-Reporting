package com.capitalbri.shared.repository

import com.capitalbri.shared.api.ApiClient
import com.capitalbri.shared.model.ReportListItem
import com.capitalbri.shared.model.ReportRequest
import com.capitalbri.shared.model.ReportResponse

interface ReportRepository {
    suspend fun submitReport(report: ReportRequest): Result<ReportResponse>
    suspend fun getRecentReports(segmentId: String? = null): Result<List<ReportListItem>>
}

class RemoteReportRepository(private val apiClient: ApiClient) : ReportRepository {

    override suspend fun submitReport(report: ReportRequest): Result<ReportResponse> {
        return apiClient.submitReport(report)
    }

    override suspend fun getRecentReports(segmentId: String?): Result<List<ReportListItem>> {
        return apiClient.getReports(segmentId = segmentId)
    }
}
