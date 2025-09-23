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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.time.format.FormatStyle

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
            TopAppBar(
                title = { Text("Observations") },
                actions = {
                    SyncButton(
                        isSyncing = uiState.isSyncing,
                        hasPendingObservations = uiState.hasPendingObservations,
                        onSyncClick = { vm.syncNow() }
                    )
                }
            )
        }
    ) { padding ->
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
                    selectedDate = uiState.selectedDate,
                    availableDates = uiState.availableDates,
                    isLoading = uiState.isLoading,
                    onObservationClick = onObservationClick,
                    onDateSelected = { date -> vm.selectDate(date) }
                )
            }
        }
    }
}

@Composable
private fun SyncButton(
    isSyncing: Boolean,
    hasPendingObservations: Boolean,
    onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    IconButton(
        onClick = onSyncClick,
        enabled = hasPendingObservations && !isSyncing
    ) {
        Icon(
            Icons.Filled.CloudSync,
            contentDescription = when {
                isSyncing -> "Syncing observations..."
                hasPendingObservations -> "Sync pending observations"
                else -> "No observations to sync"
            },
            modifier = if (isSyncing) {
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
    selectedDate: LocalDate,
    availableDates: List<LocalDate>,
    isLoading: Boolean,
    onObservationClick: (ObservationEntity) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
    ) {
        // Summary Card (shows today's summary regardless of selected date)
        TodaySummaryCard(
            summary = summary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // Compact Dropdown Date Selector
        CompactDateDropdown(
            selectedDate = selectedDate,
            availableDates = availableDates,
            onDateSelected = onDateSelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // List Header with selected date
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val isToday = selectedDate == LocalDate.now()
        val headerText = if (isToday) {
            "Today's Observations"
        } else {
            "Observations for ${selectedDate.format(dateFormatter)}"
        }

        Text(
            headerText,
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
                        CircularProgressIndicator()
                    }
                }
            }

            if (observations.isEmpty() && !isLoading) {
                item {
                    EmptyObservationsState(selectedDate = selectedDate)
                }
            } else {
                // Table header
                item {
                    ObservationsTableHeader()
                }

                // Observations list
                items(observations) { observation ->
                    ObservationRow(
                        observation = observation,
                        onClick = { onObservationClick(observation) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// Compact Dropdown Selector
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDateDropdown(
    selectedDate: LocalDate,
    availableDates: List<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    // Combine today with available dates and sort by most recent first
    val allDates = (availableDates + today).distinct().sortedDescending()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = formatDateForDropdown(selectedDate),
            onValueChange = { },
            readOnly = true,
            label = { Text("Select Date") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            leadingIcon = {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            allDates.forEach { date ->
                val isSelected = selectedDate == date
                val hasObservations = availableDates.contains(date)

                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formatDateForDropdown(date),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (hasObservations && date != today) {
                                    Text(
                                        text = "Has observations",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (date == today) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onDateSelected(date)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(
    summary: ObservationSummary,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Today's Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
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
    icon: ImageVector,
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
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
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
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
    observation: ObservationEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            status = observation.status,
            isLate = observation.late
        )

        // Chevron
        Box(
            modifier = Modifier.weight(0.15f),
            contentAlignment = Alignment.CenterEnd
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
    status: ObservationEntity.SyncStatus,
    isLate: Boolean = false
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
        shape = MaterialTheme.shapes.small,
        color = backgroundColor,
        contentColor = textColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
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
private fun EmptyObservationsState(selectedDate: LocalDate) {
    val today = LocalDate.now()
    val isToday = selectedDate == today
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

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
            text = if (isToday) "No observations today" else "No observations",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = if (isToday) {
                "Observations you submit today will appear here"
            } else {
                "No observations found for ${selectedDate.format(dateFormatter)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
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
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/* ---------------------------
   Helper Functions
   --------------------------- */

private fun formatDateForDropdown(date: LocalDate): String {
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("MMM d"))
        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
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
            obsTimeUtcMs = now - 3600000,
            createdAtMs = now - 3600000,
            updatedAtMs = now - 3600000,
            late = false,
            locked = false,
            scheduleMode = "fixed_local",
            payloadJson = """{"records":[{"variable_mapping_id":1,"value":23.5}]}""",
            status = ObservationEntity.SyncStatus.SYNCED,
            remoteId = 1001
        ),
        ObservationEntity(
            obsKey = "ke:102:${now - 7200000}",
            tenantId = "ke",
            stationId = 102,
            stationName = "Kisumu Lake Station",
            timezone = "Africa/Nairobi",
            obsTimeUtcMs = now - 7200000,
            createdAtMs = now - 7200000,
            updatedAtMs = now - 7200000,
            late = true,
            locked = false,
            scheduleMode = "windowed_only",
            payloadJson = """{"records":[{"variable_mapping_id":2,"value":15.2}]}""",
            status = ObservationEntity.SyncStatus.SYNCED,
            remoteId = 1002
        )
    )
}

private fun createSampleAvailableDates(): List<LocalDate> {
    val today = LocalDate.now()
    return listOf(
        today,
        today.minusDays(1),
        today.minusDays(2),
        today.minusDays(5),
        today.minusDays(7)
    )
}

/* ---------------------------
   Previews
   --------------------------- */

@Preview(name = "Dropdown Observations Screen • Light", showBackground = true)
@Composable
private fun PreviewDropdownObservationsScreen() {
    ADLCollectorTheme {
        ObservationsContent(
            padding = PaddingValues(0.dp),
            summary = ObservationSummary(
                syncedToday = 5,
                pendingToday = 2,
                failedToday = 1
            ),
            observations = createSampleObservationsData(),
            selectedDate = LocalDate.now(),
            availableDates = createSampleAvailableDates(),
            isLoading = false,
            onObservationClick = {},
            onDateSelected = {}
        )
    }
}

@Preview(
    name = "Dropdown Observations Screen • Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewDropdownObservationsScreenDark() {
    ADLCollectorTheme(darkTheme = true) {
        ObservationsContent(
            padding = PaddingValues(0.dp),
            summary = ObservationSummary(
                syncedToday = 3,
                pendingToday = 1,
                failedToday = 0
            ),
            observations = createSampleObservationsData(),
            selectedDate = LocalDate.now().minusDays(1),
            availableDates = createSampleAvailableDates(),
            isLoading = false,
            onObservationClick = {},
            onDateSelected = {}
        )
    }
}