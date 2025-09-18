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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme
import com.climtech.adlcollector.feature.stations.data.net.StationDetail
import com.climtech.adlcollector.feature.stations.presentation.StationDetailViewModel

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoRowStacked(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            value,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 24.dp) // Align with label text
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDetailScreen(
    tenant: TenantConfig,
    stationId: Long,
    stationName: String,
    onBack: () -> Unit,
    onAddObservation: () -> Unit,
    onOpenObservationDetail: (obsKey: String) -> Unit,  // Add this new callback
    onRefresh: () -> Unit,
    vm: StationDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(stationId, tenant.id) { vm.start(tenant, stationId) }
    val ui by vm.ui.collectAsState()
    val recent by vm.recent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar for errors only when no cached data
    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stationName) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }, actions = {
            IconButton(onClick = { vm.reload() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        })
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column {
                // Show offline indicator when using cached data
                if (ui.isOffline && ui.detail != null) {
                    OfflineIndicatorCard(
                        onRetry = { vm.retryRefresh() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Show background refresh indicator
                if (ui.refreshing && ui.detail != null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Main content
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        // Show error only when no cached detail available
                        ui.error != null && ui.detail == null -> {
                            ErrorBox(
                                message = ui.error!!,
                                onRetry = { vm.reload() },
                                modifier = Modifier.padding(padding)
                            )
                        }

                        // Show cached or fresh detail
                        ui.detail != null -> {
                            DetailBody(
                                detail = ui.detail!!,
                                padding = padding,
                                onAddObservation = onAddObservation,
                                recent = recent,
                                onOpenObservationDetail = onOpenObservationDetail
                            )
                        }

                        // Show loading only when no cached data
                        ui.loading -> {
                            Column(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text("Loading station details...")
                            }
                        }

                        // Fallback state
                        else -> {
                            Box(
                                Modifier
                                    .padding(padding)
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No detail to show.")
                            }
                        }
                    }
                }
            }

            // Overlay a translucent loading panel while background refreshing (content stays visible)
            if (ui.refreshing && ui.detail != null) {
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
private fun OfflineIndicatorCard(
    onRetry: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = "Station details may be outdated. Connect to refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onRetry) {
                Text("Retry")
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
    detail: StationDetail,
    padding: PaddingValues,
    onAddObservation: () -> Unit,
    recent: List<ObservationEntity>,
    onOpenObservationDetail: (obsKey: String) -> Unit,
) {
    val windowStatus = getWindowStatus(
        detail.timezone, detail.schedule.config, detail.schedule.mode == "fixed_local"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 80.dp), // Space for floating button
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

            // --- Enhanced Schedule Card ---
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ScheduleCardHeader()

                        // Show current status with color coding
                        ScheduleStatusIndicator(
                            stationTimezone = detail.timezone,
                            scheduleConfig = detail.schedule.config,
                            isFixedSlot = detail.schedule.mode == "fixed_local"
                        )

                        when (detail.schedule.mode) {
                            "fixed_local" -> FixedScheduleCompact(
                                detail.schedule.config, detail.timezone
                            )

                            "windowed_only" -> WindowedScheduleCompact(
                                detail.schedule.config, detail.timezone
                            )

                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Schedule information unavailable",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Time",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.5f)
                        )
                        Text(
                            "Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.35f),
                            textAlign = TextAlign.Center
                        )
                        // Empty space for chevron column
                        Spacer(modifier = Modifier.weight(0.15f))
                    }
                    HorizontalDivider()
                }

                items(recent) { obs ->
                    ObservationsRow(
                        obs = obs, onClick = { onOpenObservationDetail(obs.obsKey) })
                    HorizontalDivider()
                }
            }
        }

        // Floating Add Observation Button
        Button(
            onClick = onAddObservation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            enabled = windowStatus.canSubmit,
            elevation = androidx.compose.material3.ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 8.dp
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (windowStatus.canSubmit) Icons.Filled.AccessTime else Icons.Filled.Schedule,
                    contentDescription = null
                )
                Text(
                    if (windowStatus.canSubmit) "Add Observation" else windowStatus.reasonClosed
                        ?: "Window Closed"
                )
            }
        }
    }
}

@Composable
private fun ScheduleCardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Observation Schedule", style = MaterialTheme.typography.titleMedium)
        }

        // Simple help icon - could show a dialog or navigate to help screen
        IconButton(
            onClick = {
                // TODO: Show help dialog or navigate to help screen
                // For now, just the icon serves as visual indicator
            }, modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Filled.Help,
                contentDescription = "Schedule help - This shows when you can submit observations for this station",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ScheduleStatusIndicator(
    stationTimezone: String, scheduleConfig: Map<String, Any?>, isFixedSlot: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val (statusText, statusColor, statusIcon) = remember(
        stationTimezone, scheduleConfig, colorScheme
    ) {
        val now = run {
            val instant = Instant.now()
            val zone =
                runCatching { ZoneId.of(stationTimezone) }.getOrElse { ZoneId.systemDefault() }
            instant.atZone(zone).toLocalDateTime()
        }

        if (isFixedSlot) {
            // For fixed slots, show next observation time and window status
            val slots = (scheduleConfig["slots"] as? List<*>)?.mapNotNull {
                runCatching { LocalTime.parse(it.toString()) }.getOrNull()
            } ?: emptyList()

            val windowBefore = (scheduleConfig["window_before_mins"] as? Number)?.toLong() ?: 0
            val windowAfter = (scheduleConfig["window_after_mins"] as? Number)?.toLong() ?: 0

            if (slots.isEmpty()) {
                Triple("No observation times configured", colorScheme.error, Icons.Filled.Warning)
            } else {
                val currentSlotInfo = slots.map { slot ->
                    val slotToday = LocalDateTime.of(now.toLocalDate(), slot)
                    val windowStart = slotToday.minusMinutes(windowBefore)
                    val windowEnd = slotToday.plusMinutes(windowAfter)

                    when {
                        now.isBefore(windowStart) -> {
                            val minutesUntil = Duration.between(now, windowStart).toMinutes()
                            "upcoming" to minutesUntil
                        }

                        now.isAfter(windowStart) && now.isBefore(windowEnd) -> {
                            "open" to 0L
                        }

                        else -> "closed" to Long.MAX_VALUE
                    }
                }

                val openWindow = currentSlotInfo.find { it.first == "open" }
                val nextWindow =
                    currentSlotInfo.filter { it.first == "upcoming" }.minByOrNull { it.second }

                when {
                    openWindow != null -> Triple(
                        "Observation window open now", colorScheme.primary, Icons.Filled.AccessTime
                    )

                    nextWindow != null -> {
                        val slot = slots[currentSlotInfo.indexOf(nextWindow)]
                        val minutesUntil = nextWindow.second
                        val timeText = if (minutesUntil < 60) {
                            "in ${minutesUntil}min"
                        } else {
                            "at ${slot.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                        }
                        Triple(
                            "Next window opens $timeText",
                            colorScheme.tertiary,
                            Icons.Filled.Schedule
                        )
                    }

                    else -> Triple(
                        "All windows closed for today", colorScheme.outline, Icons.Filled.Schedule
                    )
                }
            }
        } else {
            // For windowed, show if currently in window
            val startTime = scheduleConfig["window_start"]?.toString()?.let {
                runCatching { LocalTime.parse(it) }.getOrNull()
            }
            val endTime = scheduleConfig["window_end"]?.toString()?.let {
                runCatching { LocalTime.parse(it) }.getOrNull()
            }
            val graceMins = (scheduleConfig["grace_late_mins"] as? Number)?.toLong() ?: 0

            if (startTime != null && endTime != null) {
                val currentTime = now.toLocalTime()
                val graceEndTime = endTime.plusMinutes(graceMins)

                when {
                    currentTime.isBefore(startTime) -> {
                        val minutesUntil = Duration.between(currentTime, startTime).toMinutes()
                        val timeText = if (minutesUntil < 60) "in ${minutesUntil}min" else "at ${
                            startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                        }"
                        Triple(
                            "Window opens $timeText", colorScheme.tertiary, Icons.Filled.Schedule
                        )
                    }

                    currentTime.isAfter(startTime) && currentTime.isBefore(endTime) -> Triple(
                        "Observation window open", colorScheme.primary, Icons.Filled.AccessTime
                    )

                    graceMins > 0 && currentTime.isAfter(endTime) && currentTime.isBefore(
                        graceEndTime
                    ) -> Triple(
                        "Grace period (late submissions)",
                        colorScheme.secondary,
                        Icons.Filled.Warning
                    )

                    else -> Triple(
                        "Observation window closed", colorScheme.outline, Icons.Filled.Schedule
                    )
                }
            } else {
                Triple("Window times not configured", colorScheme.error, Icons.Filled.Warning)
            }
        }
    }

    Surface(
        color = statusColor.copy(alpha = 0.12f),
        contentColor = statusColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                statusIcon, contentDescription = null, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FixedScheduleCompact(cfg: Map<String, Any?>, timezone: String) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales.get(0) ?: Locale.getDefault()

    val slots = (cfg["slots"] as? List<*>)?.mapNotNull {
        runCatching { LocalTime.parse(it.toString()) }.getOrNull()
    } ?: emptyList()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Main description
        Text(
            "Observations are collected at specific times each day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Show observation times with localized formatting
        if (slots.isNotEmpty()) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm", locale)
            InfoRowStacked(
                icon = Icons.Filled.Schedule,
                label = "Observation times:",
                value = slots.joinToString(" • ") { it.format(formatter) })
        }
    }
}

@Composable
private fun WindowedScheduleCompact(cfg: Map<String, Any?>, timezone: String) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales.get(0) ?: Locale.getDefault()

    val windowStart = cfg["window_start"]?.toString()?.let {
        runCatching { LocalTime.parse(it) }.getOrNull()
    }
    val windowEnd = cfg["window_end"]?.toString()?.let {
        runCatching { LocalTime.parse(it) }.getOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Main description
        Text(
            "Observations can be submitted anytime within the daily window",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Show submission window with localized formatting
        if (windowStart != null && windowEnd != null) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm", locale)
            InfoRowStacked(
                icon = Icons.Filled.AccessTime,
                label = "Daily window:",
                value = "${windowStart.format(formatter)} - ${windowEnd.format(formatter)}"
            )
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
private fun InfoRowWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            value,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.padding(start = 8.dp)
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
        .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        // Observation time (station timezone)
        Text(
            text = formatObsTime(obs.obsTimeUtcMs, obs.timezone),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.5f)
        )

        // Status (center)
        Box(
            modifier = Modifier.weight(0.35f), contentAlignment = Alignment.Center
        ) {
            StatusPill(obs.status)
        }

        // Chevron (right) - only show if clickable
        Box(
            modifier = Modifier.weight(0.15f), contentAlignment = Alignment.CenterEnd
        ) {
            if (enabled) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "View details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
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

data class WindowStatus(
    val canSubmit: Boolean, val reasonClosed: String? = null
)

fun getWindowStatus(
    stationTimezone: String, scheduleConfig: Map<String, Any?>, isFixedSlot: Boolean
): WindowStatus {
    val now = run {
        val instant = Instant.now()
        val zone = runCatching { ZoneId.of(stationTimezone) }.getOrElse { ZoneId.systemDefault() }
        instant.atZone(zone).toLocalDateTime()
    }

    return if (isFixedSlot) {
        // For fixed slots, check if any observation window is currently open
        val slots = (scheduleConfig["slots"] as? List<*>)?.mapNotNull {
            runCatching { LocalTime.parse(it.toString()) }.getOrNull()
        } ?: emptyList()

        val windowBefore = (scheduleConfig["window_before_mins"] as? Number)?.toLong() ?: 0
        val windowAfter = (scheduleConfig["window_after_mins"] as? Number)?.toLong() ?: 0
        val graceMins = (scheduleConfig["grace_late_mins"] as? Number)?.toLong() ?: 0

        if (slots.isEmpty()) {
            WindowStatus(false, "No observation times")
        } else {
            val inWindow = slots.any { slot ->
                val slotToday = LocalDateTime.of(now.toLocalDate(), slot)
                val windowStart = slotToday.minusMinutes(windowBefore)
                val windowEnd = slotToday.plusMinutes(windowAfter + graceMins)

                now.isAfter(windowStart) && now.isBefore(windowEnd)
            }

            if (inWindow) {
                WindowStatus(true)
            } else {
                val nextSlot = slots.minByOrNull { slot ->
                    val slotToday = LocalDateTime.of(now.toLocalDate(), slot)
                    val slotTomorrow = slotToday.plusDays(1)

                    when {
                        slotToday.isAfter(now) -> Duration.between(now, slotToday).toMinutes()
                        else -> Duration.between(now, slotTomorrow).toMinutes()
                    }
                }

                val nextTime = nextSlot?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Unknown"
                WindowStatus(false, "Next at $nextTime")
            }
        }
    } else {
        // For windowed, check if currently in daily window
        val startTime = scheduleConfig["window_start"]?.toString()?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }
        val endTime = scheduleConfig["window_end"]?.toString()?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }
        val graceMins = (scheduleConfig["grace_late_mins"] as? Number)?.toLong() ?: 0

        if (startTime != null && endTime != null) {
            val currentTime = now.toLocalTime()
            val graceEndTime = endTime.plusMinutes(graceMins)

            val inWindow = currentTime.isAfter(startTime) && currentTime.isBefore(graceEndTime)

            if (inWindow) {
                WindowStatus(true)
            } else if (currentTime.isBefore(startTime)) {
                WindowStatus(
                    false, "Opens at ${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                )
            } else {
                WindowStatus(false, "Window closed")
            }
        } else {
            WindowStatus(false, "No window configured")
        }
    }
}

/* ---------------------------
   Sample Data for Previews
   --------------------------- */

private fun createSampleFixedStationDetail(): StationDetail {
    return StationDetail(
        id = 101,
        name = "Nairobi Weather Station",
        timezone = "Africa/Nairobi",
        variable_mappings = listOf(
            StationDetail.VariableMapping(
                id = 1,
                adl_parameter_name = "Temperature",
                obs_parameter_unit = "°C",
                is_rainfall = false
            ), StationDetail.VariableMapping(
                id = 2,
                adl_parameter_name = "Rainfall",
                obs_parameter_unit = "mm",
                is_rainfall = true
            )
        ),
        schedule = StationDetail.Schedule(
            mode = "fixed_local", config = mapOf(
                "slots" to listOf("06:00", "12:00", "18:00"),
                "window_before_mins" to 15,
                "window_after_mins" to 45,
                "grace_late_mins" to 30,
                "lock_after_mins" to 120,
                "rounding_increment_mins" to 5,
                "backfill_days" to 2,
                "allow_future_mins" to 10
            )
        )
    )
}

private fun createSampleWindowedStationDetail(): StationDetail {
    return StationDetail(
        id = 202,
        name = "Kisumu Lake Station",
        timezone = "Africa/Nairobi",
        variable_mappings = listOf(
            StationDetail.VariableMapping(
                id = 3,
                adl_parameter_name = "Water Level",
                obs_parameter_unit = "m",
                is_rainfall = false
            )
        ),
        schedule = StationDetail.Schedule(
            mode = "windowed_only", config = mapOf(
                "window_start" to "08:00",
                "window_end" to "17:00",
                "grace_late_mins" to 60,
                "lock_after_mins" to 180,
                "rounding_increment_mins" to 15,
                "backfill_days" to 1,
                "allow_future_mins" to 30
            )
        )
    )
}

private fun createSampleObservations(): List<ObservationEntity> {
    val now = System.currentTimeMillis()
    return listOf(
        ObservationEntity(
            obsKey = "tenant:101:${now - 3600000}",
            tenantId = "sample",
            stationId = 101,
            stationName = "Nairobi Weather Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 3600000, // 1 hour ago
            createdAtMs = now - 3600000,
            updatedAtMs = now - 3600000,
            late = false,
            locked = false,
            scheduleMode = "fixed_local",
            payloadJson = """{"records":[{"variable_mapping_id":1,"value":23.5}]}""",
            status = ObservationEntity.SyncStatus.SYNCED,
            remoteId = 12345
        ), ObservationEntity(
            obsKey = "tenant:101:${now - 7200000}",
            tenantId = "sample",
            stationId = 101,
            stationName = "Nairobi Weather Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 7200000, // 2 hours ago
            createdAtMs = now - 7200000,
            updatedAtMs = now - 7200000,
            late = true,
            locked = false,
            scheduleMode = "fixed_local",
            payloadJson = """{"records":[{"variable_mapping_id":1,"value":24.1}]}""",
            status = ObservationEntity.SyncStatus.QUEUED,
            remoteId = null
        ), ObservationEntity(
            obsKey = "tenant:101:${now - 10800000}",
            tenantId = "sample",
            stationId = 101,
            stationName = "Nairobi Weather Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 10800000, // 3 hours ago
            createdAtMs = now - 10800000,
            updatedAtMs = now - 10800000,
            late = false,
            locked = true,
            scheduleMode = "fixed_local",
            payloadJson = """{"records":[{"variable_mapping_id":1,"value":22.8}]}""",
            status = ObservationEntity.SyncStatus.FAILED,
            remoteId = null,
            lastError = "Network timeout"
        )
    )
}

/* ---------------------------
   Previews
   --------------------------- */

@Preview(name = "Fixed Schedule • Light", showBackground = true)
@Composable
private fun PreviewFixedScheduleDetail() {
    ADLCollectorTheme {
        DetailBody(
            detail = createSampleFixedStationDetail(),
            padding = PaddingValues(0.dp),
            onAddObservation = {},
            recent = createSampleObservations(),
            onOpenObservationDetail = {})
    }
}

@Preview(
    name = "Windowed Schedule • Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewWindowedScheduleDetail() {
    ADLCollectorTheme(darkTheme = true) {
        DetailBody(
            detail = createSampleWindowedStationDetail(),
            padding = PaddingValues(0.dp),
            onAddObservation = {},
            recent = emptyList(),
            onOpenObservationDetail = {})
    }
}