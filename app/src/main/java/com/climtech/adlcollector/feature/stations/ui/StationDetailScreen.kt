package com.climtech.adlcollector.feature.stations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.stations.presentation.StationDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    tenant: TenantConfig,                // <— now we require tenant
    stationId: Long,
    stationName: String,
    onBack: () -> Unit,
    onAddObservation: () -> Unit,
    onOpenObservation: (obsId: Long) -> Unit, // reserved for future
    onRefresh: () -> Unit,                    // reserved for future
    vm: StationDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(stationId, tenant.id) { vm.start(tenant, stationId) }
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stationName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            ui.loading -> LoadingBox(Modifier.padding(padding))
            ui.error != null -> ErrorBox(
                message = ui.error!!,
                onRetry = { vm.reload() },
                modifier = Modifier.padding(padding)
            )

            ui.detail != null -> DetailBody(
                detail = ui.detail!!,
                padding = padding,
                onAddObservation = onAddObservation
            )

            else -> Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text("No detail to show.")
            }
        }
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Failed to load station",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(message)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun DetailBody(
    detail: com.climtech.adlcollector.feature.stations.data.net.StationDetail,
    padding: PaddingValues,
    onAddObservation: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Station Info", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            InfoRow("Name", detail.name)
            InfoRow("Timezone", detail.timezone)
            InfoRow("ID", detail.id.toString())
        }

        item {
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            Text("Schedule", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            when (detail.schedule.mode) {
                "fixed_local" -> FixedScheduleSummary(detail.schedule.config)
                "windowed_only" -> WindowedScheduleSummary(detail.schedule.config)
                else -> Text("Unknown mode: ${detail.schedule.mode}")
            }
        }

        item {
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            Text("Variables", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }

        items(detail.variable_mappings) { m ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m.adl_parameter_name, fontWeight = FontWeight.SemiBold)
                    InfoRow("Unit", m.obs_parameter_unit)
                    if (m.is_rainfall) AssistChip(onClick = {}, label = { Text("Rainfall") })
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAddObservation, modifier = Modifier.fillMaxWidth()) {
                Text("Add Observation")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FixedScheduleSummary(cfg: Map<String, Any?>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoRow("Mode", "Fixed local slots")
        InfoRow("Slots", (cfg["slots"] as? List<*>)?.joinToString() ?: "—")
        InfoRow("Window ± (min)", "${cfg["window_before_mins"]} / ${cfg["window_after_mins"]}")
        InfoRow("Grace late (min)", "${cfg["grace_late_mins"]}")
        InfoRow("Rounding (min)", "${cfg["rounding_increment_mins"]}")
        InfoRow("Backfill (days)", "${cfg["backfill_days"]}")
        InfoRow("Future allow (min)", "${cfg["allow_future_mins"]}")
        InfoRow("Lock after (min)", "${cfg["lock_after_mins"]}")
    }
}

@Composable
private fun WindowedScheduleSummary(cfg: Map<String, Any?>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoRow("Mode", "Windowed only")
        InfoRow("Window", "${cfg["window_start"]} – ${cfg["window_end"]}")
        InfoRow("Grace late (min)", "${cfg["grace_late_mins"]}")
        InfoRow("Rounding (min)", "${cfg["rounding_increment_mins"]}")
        InfoRow("Backfill (days)", "${cfg["backfill_days"]}")
        InfoRow("Future allow (min)", "${cfg["allow_future_mins"]}")
        InfoRow("Lock after (min)", "${cfg["lock_after_mins"]}")
    }
}
