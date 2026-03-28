package com.capitalbri.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.capitalbri.android.ui.map.MapScreen
import com.capitalbri.android.ui.report.ReportFormScreen
import com.capitalbri.android.ui.reports.RecentReportsScreen

object Routes {
    const val MAP = "map"
    const val REPORT_FORM = "report_form/{segmentId}/{segmentName}/{pctAlong}"
    const val RECENT_REPORTS = "recent_reports"

    fun reportForm(segmentId: String, segmentName: String, pctAlong: Double) =
        "report_form/$segmentId/${segmentName.ifBlank { "Unknown road" }}/$pctAlong"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(
                onNavigateToReportForm = { segmentId, segmentName, pctAlong ->
                    navController.navigate(Routes.reportForm(segmentId, segmentName, pctAlong))
                },
                onNavigateToRecentReports = {
                    navController.navigate(Routes.RECENT_REPORTS)
                }
            )
        }
        composable(Routes.REPORT_FORM) { backStackEntry ->
            val segmentId = backStackEntry.arguments?.getString("segmentId") ?: ""
            val segmentName = backStackEntry.arguments?.getString("segmentName") ?: ""
            val pctAlong = backStackEntry.arguments?.getString("pctAlong")?.toDoubleOrNull() ?: 0.0
            ReportFormScreen(
                segmentId = segmentId,
                segmentName = segmentName,
                pctAlong = pctAlong,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RECENT_REPORTS) {
            RecentReportsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
