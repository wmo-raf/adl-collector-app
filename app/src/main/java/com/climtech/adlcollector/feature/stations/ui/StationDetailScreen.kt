package com.climtech.adlcollector.feature.stations.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.stations.presentation.StationDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    tenant: TenantConfig,
    stationId: Long,
    stationName: String,
    onBack: () -> Unit,
    onAddObservation: () -> Unit,
    onOpenObservation: (obsId: Long) -> Unit,
    onRefresh: () -> Unit,
    vm: StationDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(stationId, tenant.id) { vm.start(tenant, stationId) }
    val ui by vm.ui.collectAsState()
    val recent by vm.recent.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stationName) }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }, actions = {
                IconButton(onClick = { vm.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            })
        }) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                ui.error != null -> ErrorBox(
                    message = ui.error!!,
                    onRetry = { vm.reload() },
                    modifier = Modifier.padding(padding)
                )

                ui.detail != null -> DetailBody(
                    detail = ui.detail!!,
                    padding = padding,
                    onAddObservation = onAddObservation,
                    recent = recent,
                    onOpenObservationRemote = onOpenObservation
                )

                else -> Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No detail to show.")
                }
            }

            // Overlay a translucent loading panel while reloading (content stays visible)
            if (ui.loading) {
                LoadingBox(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    onAddObservation: () -> Unit,
    recent: List<ObservationEntity>,
    onOpenObservationRemote: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Basic Info Card ---
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = detail.name, style = MaterialTheme.typography.titleLarge
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Station ID", detail.id.toString())
                        InfoRow("Timezone", detail.timezone)
                    }
                }
            }
        }

        // --- Schedule Card ---
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Schedule", style = MaterialTheme.typography.titleMedium)

                    when (detail.schedule.mode) {
                        "fixed_local" -> FixedScheduleCompact(detail.schedule.config)
                        "windowed_only" -> WindowedScheduleCompact(detail.schedule.config)
                        else -> InfoRow("Mode", "Unknown (${detail.schedule.mode})")
                    }
                }
            }
        }

        // --- Space to visually separate cards from recent list ---
        item { Spacer(Modifier.height(24.dp)) }

        // --- Recent Observations (table-like list) ---
        item {
            Text("Recent observations", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        if (recent.isEmpty()) {
            item {
                EmptyState(
                    icon = { Icon(Icons.Filled.Inbox, contentDescription = null) },
                    text = "No recent observations"
                )
            }
        } else {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }

            items(recent) { obs ->
                ObservationsRow(
                    obs = obs,
                    onClick = obs.remoteId?.let { id -> { onOpenObservationRemote(id) } })
                HorizontalDivider()
            }
        }

        // --- Quick Action ---
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
private fun FixedScheduleCompact(cfg: Map<String, Any?>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoRow("Mode", "Fixed local slots")
        val slots =
            (cfg["slots"] as? List<*>)?.joinToString(separator = "  •  ") { it.toString() } ?: "—"
        InfoRow("Slots", slots)
    }
}

@Composable
private fun WindowedScheduleCompact(cfg: Map<String, Any?>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoRow("Mode", "Windowed only")
        InfoRow(
            "Window", "${cfg["window_start"] ?: "—"} – ${cfg["window_end"] ?: "—"}"
        )
    }
}

/* ---------------------------
   Recent Observations: Rows
   --------------------------- */

@Composable
private fun ObservationsRow(
    obs: ObservationEntity, onClick: (() -> Unit)?
) {
    val enabled = onClick != null
    Row(modifier = Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else 0.75f)
        .let { base ->
            if (enabled) base.clickable(onClick = onClick!!) else base
        }
        .padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        // Observation time (station timezone)
        Text(
            text = formatObsTime(obs.obsTimeUtcMs, obs.timezone),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.7f)
        )

        // Status (right aligned)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f), contentAlignment = Alignment.CenterEnd
        ) {
            StatusPill(obs.status)
        }
    }
}


@Composable
private fun StatusPill(status: ObservationEntity.SyncStatus) {
    val (bg, fg, label) = when (status) {
        ObservationEntity.SyncStatus.SYNCED -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "SYNCED"
        )

        ObservationEntity.SyncStatus.QUEUED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "QUEUED"
        )

        ObservationEntity.SyncStatus.UPLOADING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "UPLOADING"
        )

        ObservationEntity.SyncStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "FAILED"
        )
    }
    Box(
        modifier = Modifier
            .background(bg, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyState(
    icon: @Composable () -> Unit, text: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.height(64.dp), contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Text(
            text, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ---------------------------
   Helpers
   --------------------------- */

private fun formatObsTime(utcMs: Long, tz: String): String {
    val instant = Instant.ofEpochMilli(utcMs)
    val zone = runCatching { ZoneId.of(tz) }.getOrElse { ZoneId.of("UTC") }
    val local = instant.atZone(zone)
    return DateTimeFormatter.ofPattern("EEE, dd MMM yyyy • HH:mm").format(local)
}


/* ---------------------------
   Previews (optional)
   --------------------------- */

@Preview(name = "Empty state", showBackground = true)
@Composable
private fun Preview_EmptyState() {
    EmptyState(
        icon = { Icon(Icons.Filled.Inbox, contentDescription = null) },
        text = "No recent observations"
    )
}

@Preview(
    name = "Status pills", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun Preview_StatusPills_Dark() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusPill(ObservationEntity.SyncStatus.SYNCED)
        StatusPill(ObservationEntity.SyncStatus.QUEUED)
        StatusPill(ObservationEntity.SyncStatus.UPLOADING)
        StatusPill(ObservationEntity.SyncStatus.FAILED)
    }
}
