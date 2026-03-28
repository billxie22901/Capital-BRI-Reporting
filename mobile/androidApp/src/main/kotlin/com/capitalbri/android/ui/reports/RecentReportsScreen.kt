package com.capitalbri.android.ui.reports

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capitalbri.android.CapitalBriApp
import com.capitalbri.shared.model.HazardType
import com.capitalbri.shared.model.ReportListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecentReportsViewModel(app: Application) : AndroidViewModel(app) {
    private val reportRepository = (app as CapitalBriApp).reportRepository

    private val _reports = MutableStateFlow<List<ReportListItem>>(emptyList())
    val reports: StateFlow<List<ReportListItem>> = _reports.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            reportRepository.getRecentReports()
                .onSuccess { _reports.value = it }
            _loading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentReportsScreen(
    onBack: () -> Unit,
    viewModel: RecentReportsViewModel = viewModel()
) {
    val reports by viewModel.reports.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No reports yet.", style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reports) { report ->
                    ReportCard(report)
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: ReportListItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = report.captured_at.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                report.cycling_experience_rating?.let {
                    Text("Experience: $it/10", style = MaterialTheme.typography.bodySmall)
                }
            }
            report.hazard_types?.takeIf { it.isNotEmpty() }?.let { hazards ->
                Text(
                    text = hazards.joinToString(", ") { HazardType.display(it) },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            report.traffic_level?.let {
                Text("Traffic: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
