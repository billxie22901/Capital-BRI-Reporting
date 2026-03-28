package com.capitalbri.android

import android.app.Application
import com.capitalbri.shared.api.ApiClient
import com.capitalbri.shared.repository.RemoteReportRepository
import com.capitalbri.shared.repository.RemoteSegmentRepository

class CapitalBriApp : Application() {

    val apiClient by lazy { ApiClient() }
    val segmentRepository by lazy { RemoteSegmentRepository(apiClient) }
    val reportRepository by lazy { RemoteReportRepository(apiClient) }

    override fun onCreate() {
        super.onCreate()
        // MapLibre initializes automatically on first MapView creation
    }
}
