package com.capitalbri.android.ui.report

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capitalbri.shared.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportFormScreen(
    segmentId: String,
    segmentName: String,
    pctAlong: Double,
    onBack: () -> Unit,
    viewModel: ReportFormViewModel = viewModel()
) {
    val state by viewModel.formState.collectAsState()

    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Report Conditions")
                        Text(
                            text = segmentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isSubmitting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Validation errors
            if (state.validationErrors.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        state.validationErrors.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            state.errorMessage?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Impediment Duration
            FormSection(title = "Issue type") {
                ChipGroup(
                    options = ImpedimentDuration.all,
                    selected = setOfNotNull(state.impedimentDuration),
                    single = true,
                    displayName = ImpedimentDuration::display,
                    onToggle = { viewModel.setImpedimentDuration(if (state.impedimentDuration == it) null else it) }
                )
            }

            // Hazard Types
            FormSection(title = "Hazards on road") {
                ChipGroup(
                    options = HazardType.all,
                    selected = state.selectedHazardTypes,
                    single = false,
                    displayName = HazardType::display,
                    onToggle = { viewModel.toggleHazardType(it) }
                )
            }

            // Surface Conditions
            FormSection(title = "Surface conditions") {
                ChipGroup(
                    options = SurfaceCondition.all,
                    selected = state.selectedConditions,
                    single = false,
                    displayName = SurfaceCondition::display,
                    onToggle = { viewModel.toggleCondition(it) }
                )
            }

            // Traffic Level
            FormSection(title = "Traffic level") {
                ChipGroup(
                    options = TrafficLevel.all,
                    selected = setOfNotNull(state.trafficLevel),
                    single = true,
                    displayName = TrafficLevel::display,
                    onToggle = { viewModel.setTrafficLevel(if (state.trafficLevel == it) null else it) }
                )
            }

            // Infrastructure
            FormSection(title = "Cycling infrastructure") {
                ChipGroup(
                    options = Infrastructure.all,
                    selected = setOfNotNull(state.infrastructure),
                    single = true,
                    displayName = Infrastructure::display,
                    onToggle = { viewModel.setInfrastructure(if (state.infrastructure == it) null else it) }
                )
            }

            // Bike Lane Availability
            if (state.infrastructure != null &&
                state.infrastructure != Infrastructure.SHARED_ROAD &&
                state.infrastructure != Infrastructure.NONE) {
                FormSection(title = "Bike lane availability") {
                    ChipGroup(
                        options = BikeLaneAvailability.all,
                        selected = setOfNotNull(state.bikeLaneAvailability),
                        single = true,
                        displayName = BikeLaneAvailability::display,
                        onToggle = { viewModel.setBikeLaneAvailability(if (state.bikeLaneAvailability == it) null else it) }
                    )
                }
            }

            // Cycling Experience Rating
            FormSection(title = "Cycling experience (1–10)") {
                RatingSlider(
                    value = state.cyclingExperienceRating,
                    onValueChange = { viewModel.setCyclingExperienceRating(it) }
                )
            }

            // Pleasantness Rating
            FormSection(title = "Pleasantness (1–10)") {
                RatingSlider(
                    value = state.pleasantnessRating,
                    onValueChange = { viewModel.setPleasantnessRating(it) }
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.submit(segmentId, pctAlong) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit Report")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun ChipGroup(
    options: List<String>,
    selected: Set<String>,
    single: Boolean,
    displayName: (String) -> String,
    onToggle: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected.contains(option),
                onClick = { onToggle(option) },
                label = { Text(displayName(option)) }
            )
        }
    }
}

@Composable
private fun RatingSlider(value: Int?, onValueChange: (Int?) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value?.toString() ?: "–",
                modifier = Modifier.width(32.dp),
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = (value ?: 0).toFloat(),
                onValueChange = { v ->
                    val rounded = v.toInt()
                    onValueChange(if (rounded == 0) null else rounded)
                },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value == null) {
            Text(
                "Slide to rate (optional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
