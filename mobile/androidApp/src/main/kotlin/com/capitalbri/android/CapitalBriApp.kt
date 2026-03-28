package com.capitalbri.android

import android.app.Application
import com.capitalbri.shared.api.ApiClient
import com.capitalbri.shared.repository.RemoteReportRepository
import com.capitalbri.shared.repository.RemoteSegmentRepository
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class CapitalBriApp : Application() {

    val apiClient by lazy { ApiClient() }
    val segmentRepository by lazy { RemoteSegmentRepository(apiClient) }
    val reportRepository by lazy { RemoteReportRepository(apiClient) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this, "", WellKnownTileServer.MapLibre)
    }
}
