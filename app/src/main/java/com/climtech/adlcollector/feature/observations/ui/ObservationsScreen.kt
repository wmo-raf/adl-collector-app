package com.climtech.adlcollector.feature.observations.ui

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme
import com.climtech.adlcollector.feature.observations.presentation.ObservationSummary
import com.climtech.adlcollector.feature.observations.presentation.ObservationsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationsScreen(
    tenantId: String,
    vm: ObservationsViewModel = hiltViewModel(),
    onObservationClick: (ObservationEntity) -> Unit
) {
    LaunchedEffect(tenantId) {
        vm.start(tenantId)
    }

    val uiState by vm.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Observations") }, actions = {
                SyncButton(
                    isSyncing = uiState.isSyncing,
                    hasPendingObservations = uiState.hasPendingObservations,
                    onSyncClick = { vm.syncNow() })
            })
        }) { padding ->
        when {
            uiState.error != null -> {
                ErrorContent(
                    error = uiState.error!!,
                    onRetry = { vm.refresh() },
                    modifier = Modifier.padding(padding)
                )
            }

            else -> {
                ObservationsContent(
                    padding = padding,
                    summary = uiState.summary,
                    observations = uiState.observations,
                    isLoading = uiState.isLoading,
                    onObservationClick = {
                        onObservationClick(it)
                    })
            }
        }
    }
}

@Composable
private fun SyncButton(
    isSyncing: Boolean, hasPendingObservations: Boolean, onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    IconButton(
        onClick = onSyncClick, enabled = hasPendingObservations && !isSyncing
    ) {
        Icon(
            Icons.Filled.CloudSync, contentDescription = when {
                isSyncing -> "Syncing observations..."
                hasPendingObservations -> "Sync pending observations"
                else -> "No observations to sync"
            }, modifier = if (isSyncing) {
                Modifier.rotate(rotationAngle)
            } else {
                Modifier
            }
        )
    }
}


@Composable
private fun ObservationsContent(
    padding: PaddingValues,
    summary: ObservationSummary,
    observations: List<ObservationEntity>,
    isLoading: Boolean,
    onObservationClick: (ObservationEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
    ) {
        // Summary Card
        TodaySummaryCard(
            summary = summary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // List Header
        Text(
            "All Observations",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Scrollable content below
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            }

            if (observations.isEmpty() && !isLoading) {
                item {
                    EmptyObservationsState()
                }
            } else {
                // Table header
                item {
                    ObservationsTableHeader()
                }

                // Observations list
                items(observations) { observation ->
                    ObservationRow(
                        observation = observation, onClick = { onObservationClick(observation) })
                    HorizontalDivider()
                }
            }
        }
    }
}


@Composable
private fun TodaySummaryCard(
    summary: ObservationSummary, modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Today's Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    icon = Icons.Filled.CheckCircle,
                    count = summary.syncedToday,
                    label = "Synced",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                SummaryItem(
                    icon = Icons.Filled.CloudUpload,
                    count = summary.pendingToday,
                    label = "Pending",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )

                SummaryItem(
                    icon = Icons.Filled.Error,
                    count = summary.failedToday,
                    label = "Failed",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector, count: Int, label: String, color: Color, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.12f),
            contentColor = color
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ObservationsTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Station & Time",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f)
        )
        Text(
            "Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.25f),
            textAlign = TextAlign.Center
        )
        // Empty space for chevron column
        Spacer(modifier = Modifier.weight(0.15f))
    }
    HorizontalDivider()
}


@Composable
private fun ObservationRow(
    observation: ObservationEntity, onClick: () -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = observation.stationName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatObservationTime(observation.obsTimeUtcMs, observation.timezone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(8.dp))

        StatusPill(
            status = observation.status, isLate = observation.late
        )

        // Chevron
        Box(
            modifier = Modifier.weight(0.15f), contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "View details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(
    status: ObservationEntity.SyncStatus, isLate: Boolean = false
) {
    val (colorInfo, icon) = when (status) {
        ObservationEntity.SyncStatus.SYNCED -> {
            val color = MaterialTheme.colorScheme.primary
            val bg = color.copy(alpha = 0.12f)
            val label = if (isLate) "SYNCED (LATE)" else "SYNCED"
            Triple(bg, color, label) to Icons.Filled.CheckCircle
        }

        ObservationEntity.SyncStatus.QUEUED -> {
            val color = MaterialTheme.colorScheme.tertiary
            val bg = color.copy(alpha = 0.12f)
            Triple(bg, color, "QUEUED") to Icons.Filled.Schedule
        }

        ObservationEntity.SyncStatus.UPLOADING -> {
            val color = MaterialTheme.colorScheme.secondary
            val bg = color.copy(alpha = 0.12f)
            Triple(bg, color, "UPLOADING") to Icons.Filled.CloudUpload
        }

        ObservationEntity.SyncStatus.FAILED -> {
            val color = MaterialTheme.colorScheme.error
            val bg = color.copy(alpha = 0.12f)
            Triple(bg, color, "FAILED") to Icons.Filled.Error
        }
    }

    val (backgroundColor, textColor, label) = colorInfo
    Surface(
        shape = MaterialTheme.shapes.small, color = backgroundColor, contentColor = textColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyObservationsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "No observations yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Observations you submit will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(
    error: String, onRetry: () -> Unit, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/* ---------------------------
   Helper Functions
   --------------------------- */

private fun calculateSummary(observations: List<ObservationEntity>): ObservationSummary {
    val today = LocalDate.now()
    val todayObservations = observations.filter { obs ->
        val obsDate =
            Instant.ofEpochMilli(obs.obsTimeUtcMs).atZone(ZoneId.systemDefault()).toLocalDate()
        obsDate == today
    }

    return ObservationSummary(
        syncedToday = todayObservations.count { it.status == ObservationEntity.SyncStatus.SYNCED },
        pendingToday = todayObservations.count {
            it.status == ObservationEntity.SyncStatus.QUEUED || it.status == ObservationEntity.SyncStatus.UPLOADING
        },
        failedToday = todayObservations.count { it.status == ObservationEntity.SyncStatus.FAILED })
}

private fun formatObservationTime(utcMs: Long, timezone: String): String {
    val instant = Instant.ofEpochMilli(utcMs)
    val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
    val localDateTime = instant.atZone(zone)
    return DateTimeFormatter.ofPattern("MMM dd, HH:mm").format(localDateTime)
}

/* ---------------------------
   Sample Data
   --------------------------- */

private fun createSampleObservationsData(): List<ObservationEntity> {
    val now = System.currentTimeMillis()
    return listOf(
        ObservationEntity(
            obsKey = "ke:101:${now - 3600000}",
            tenantId = "ke",
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
            remoteId = 1001
        ), ObservationEntity(
            obsKey = "ke:102:${now - 7200000}",
            tenantId = "ke",
            stationId = 102,
            stationName = "Kisumu Lake Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 7200000, // 2 hours ago
            createdAtMs = now - 7200000,
            updatedAtMs = now - 7200000,
            late = true,
            locked = false,
            scheduleMode = "windowed_only",
            payloadJson = """{"records":[{"variable_mapping_id":2,"value":15.2}]}""",
            status = ObservationEntity.SyncStatus.SYNCED,
            remoteId = 1002
        ), ObservationEntity(
            obsKey = "ke:103:${now - 10800000}",
            tenantId = "ke",
            stationId = 103,
            stationName = "Mombasa Port Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 10800000, // 3 hours ago
            createdAtMs = now - 10800000,
            updatedAtMs = now - 10800000,
            late = false,
            locked = false,
            scheduleMode = "fixed_local",
            payloadJson = """{"records":[{"variable_mapping_id":3,"value":28.1}]}""",
            status = ObservationEntity.SyncStatus.QUEUED,
            remoteId = null
        ), ObservationEntity(
            obsKey = "ke:104:${now - 14400000}",
            tenantId = "ke",
            stationId = 104,
            stationName = "Eldoret Highland Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 14400000, // 4 hours ago
            createdAtMs = now - 14400000,
            updatedAtMs = now - 14400000,
            late = false,
            locked = false,
            scheduleMode = "fixed_local",
            payloadJson = """{"records":[{"variable_mapping_id":4,"value":19.3}]}""",
            status = ObservationEntity.SyncStatus.FAILED,
            remoteId = null,
            lastError = "Network timeout"
        ), ObservationEntity(
            obsKey = "ke:101:${now - 86400000}",
            tenantId = "ke",
            stationId = 101,
            stationName = "Nairobi Weather Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 86400000, // Yesterday
            createdAtMs = now - 86400000,
            updatedAtMs = now - 86400000,
            late = false,
            locked = true,
            scheduleMode = "fixed_local",
            payloadJson = """{"records":[{"variable_mapping_id":1,"value":22.1}]}""",
            status = ObservationEntity.SyncStatus.SYNCED,
            remoteId = 999
        )
    )
}

/* ---------------------------
   Previews
   --------------------------- */

@Preview(name = "Observations Screen • Light", showBackground = true)
@Composable
private fun PreviewObservationsScreen() {
    ADLCollectorTheme {
        ObservationsScreenContent(
            summary = ObservationSummary(
                syncedToday = 5, pendingToday = 2, failedToday = 1
            ),
            observations = createSampleObservationsData(),
            isLoading = false,
            hasPendingObservations = true, // Has pending items - Sync now should be enabled
            error = null,
            onObservationClick = {},
            onRetry = {})
    }
}

@Preview(name = "Syncing State", showBackground = true)
@Composable
private fun PreviewSyncingState() {
    ADLCollectorTheme {
        ObservationsScreenContent(
            summary = ObservationSummary(
                syncedToday = 3, pendingToday = 2, failedToday = 0
            ),
            observations = createSampleObservationsData(),
            isLoading = false,
            hasPendingObservations = true,
            isSyncing = true, // Show rotating sync icon
            error = null,
            onObservationClick = {},
            onRetry = {})
    }
}

@Preview(name = "No Pending Observations", showBackground = true)
@Composable
private fun PreviewNoPendingObservations() {
    ADLCollectorTheme {
        val syncedObservations = createSampleObservationsData().map {
            it.copy(status = ObservationEntity.SyncStatus.SYNCED)
        }

        ObservationsScreenContent(
            summary = ObservationSummary(
                syncedToday = 7, pendingToday = 0, failedToday = 0
            ),
            observations = syncedObservations,
            isLoading = false,
            hasPendingObservations = false, // No pending items - Sync now should be disabled
            error = null,
            onObservationClick = {},
            onRetry = {})
    }
}

@Preview(name = "Loading State", showBackground = true)
@Composable
private fun PreviewObservationsLoading() {
    ADLCollectorTheme {
        ObservationsScreenContent(
            summary = ObservationSummary(),
            observations = emptyList(),
            isLoading = true,
            hasPendingObservations = false,
            error = null,
            onObservationClick = {},
            onRetry = {})
    }
}

@Preview(name = "Error State", showBackground = true)
@Composable
private fun PreviewObservationsError() {
    ADLCollectorTheme {
        ObservationsScreenContent(
            summary = ObservationSummary(),
            observations = emptyList(),
            isLoading = false,
            hasPendingObservations = false,
            error = "Network connection failed",
            onObservationClick = {},
            onRetry = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObservationsScreenContent(
    summary: ObservationSummary,
    observations: List<ObservationEntity>,
    isLoading: Boolean,
    hasPendingObservations: Boolean = false,
    isSyncing: Boolean = false,
    error: String?,
    onObservationClick: (ObservationEntity) -> Unit,
    onRetry: () -> Unit,
    onSyncNow: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Observations") }, actions = {
                SyncButton(
                    isSyncing = isSyncing,
                    hasPendingObservations = hasPendingObservations,
                    onSyncClick = onSyncNow
                )
            })
        }) { padding ->
        when {
            error != null -> {
                ErrorContent(
                    error = error, onRetry = onRetry, modifier = Modifier.padding(padding)
                )
            }

            else -> {
                ObservationsContent(
                    padding = padding,
                    summary = summary,
                    observations = observations,
                    isLoading = isLoading,
                    onObservationClick = onObservationClick
                )
            }
        }
    }
}

@Preview(name = "Summary Card", showBackground = true)
@Composable
private fun PreviewSummaryCard() {
    ADLCollectorTheme {
        TodaySummaryCard(
            summary = ObservationSummary(
                syncedToday = 5, pendingToday = 2, failedToday = 1
            )
        )
    }
}

@Preview(name = "Empty State", showBackground = true)
@Composable
private fun PreviewEmptyObservations() {
    ADLCollectorTheme {
        EmptyObservationsState()
    }
}

@Preview(
    name = "Observations Screen • Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewObservationsScreenDark() {
    ADLCollectorTheme(darkTheme = true) {
        ObservationsScreenContent(
            summary = ObservationSummary(
                syncedToday = 3, pendingToday = 1, failedToday = 0
            ),
            observations = createSampleObservationsData(),
            isLoading = false,
            hasPendingObservations = true,
            error = null,
            onObservationClick = {},
            onRetry = {})
    }
}