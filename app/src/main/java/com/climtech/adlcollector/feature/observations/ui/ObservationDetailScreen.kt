package com.climtech.adlcollector.feature.observations.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.feature.observations.presentation.ObservationDetailViewModel
import com.climtech.adlcollector.feature.observations.presentation.ObservationVariable

data class StatusInfo(
    val backgroundColor: Color, val textColor: Color, val icon: ImageVector, val statusText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationDetailScreen(
    tenantId: String,
    obsKey: String,
    onBack: () -> Unit,
    vm: ObservationDetailViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(obsKey) {
        vm.load(obsKey)
    }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Observation Details") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when {
            ui.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Loading observation...")
                }
            }

            ui.observation != null -> {
                ObservationDetailContent(
                    ui = ui,
                    tenantId = tenantId,
                    onRetry = { vm.retryUpload(tenantId) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
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
                        "Observation not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ObservationDetailContent(
    ui: com.climtech.adlcollector.feature.observations.presentation.ObservationDetailUi,
    tenantId: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val obs = ui.observation!!
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        StatusCard(
            status = obs.status,
            isLate = obs.late,
            isLocked = obs.locked,
            lastError = obs.lastError,
            onRetry = if (obs.status == ObservationEntity.SyncStatus.FAILED) onRetry else null,
            isRetrying = ui.retrying
        )

        // Basic Info Card
        BasicInfoCard(
            stationName = obs.stationName,
            obsTime = ui.formattedObsTime,
            timezone = obs.timezone,
            scheduleMode = obs.scheduleMode
        )

        // Variables Card
        if (ui.variables.isNotEmpty()) {
            VariablesCard(variables = ui.variables)
        }

        // Technical Details Card
        TechnicalDetailsCard(
            obsKey = obs.obsKey,
            remoteId = obs.remoteId,
            createdTime = ui.formattedCreatedTime,
            updatedTime = ui.formattedUpdatedTime,
            payloadJson = obs.payloadJson
        )
    }
}

@Composable
private fun StatusCard(
    status: ObservationEntity.SyncStatus,
    isLate: Boolean,
    isLocked: Boolean,
    lastError: String?,
    onRetry: (() -> Unit)?,
    isRetrying: Boolean
) {
    val statusInfo = when (status) {
        ObservationEntity.SyncStatus.SYNCED -> {
            val text = if (isLate) "Synced (Late Submission)" else "Successfully Synced"
            StatusInfo(
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = Icons.Filled.CheckCircle,
                statusText = text
            )
        }

        ObservationEntity.SyncStatus.QUEUED -> StatusInfo(
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            textColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Filled.Schedule,
            statusText = "Queued for Upload"
        )

        ObservationEntity.SyncStatus.UPLOADING -> StatusInfo(
            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Filled.CloudUpload,
            statusText = "Uploading to Server"
        )

        ObservationEntity.SyncStatus.FAILED -> StatusInfo(
            backgroundColor = MaterialTheme.colorScheme.errorContainer,
            textColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Filled.Error,
            statusText = "Upload Failed"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = statusInfo.backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = statusInfo.icon,
                    contentDescription = null,
                    tint = statusInfo.textColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = statusInfo.statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusInfo.textColor
                )
            }

            // Show flags
            if (isLate || isLocked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isLate) {
                        StatusFlag("Late Submission", Icons.Filled.Schedule)
                    }
                    if (isLocked) {
                        StatusFlag("Locked", Icons.Filled.Warning)
                    }
                }
            }

            // Show error details
            if (lastError != null) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Error Details:",
                        style = MaterialTheme.typography.labelMedium,
                        color = statusInfo.textColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = lastError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusInfo.textColor
                    )
                }
            }

            // Retry button
            if (onRetry != null) {
                Button(
                    onClick = onRetry, enabled = !isRetrying, modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Retrying...")
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry Upload")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFlag(text: String, icon: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                text = text, style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun BasicInfoCard(
    stationName: String, obsTime: String, timezone: String, scheduleMode: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Observation Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            InfoRow("Station", stationName)
            InfoRow("Time", obsTime)
            InfoRow("Timezone", timezone)
            InfoRow(
                "Schedule Mode", scheduleMode.replace("_", " ").replaceFirstChar { it.uppercase() })
        }
    }
}

@Composable
private fun VariablesCard(
    variables: List<ObservationVariable>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Recorded Values",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            variables.forEach { variable ->
                VariableRow(variable = variable)
            }
        }
    }
}

@Composable
private fun VariableRow(
    variable: ObservationVariable
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Parameter name and unit on left
        Column(
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = variable.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = variable.unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Value on right
        Text(
            text = variable.value.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun TechnicalDetailsCard(
    obsKey: String, remoteId: Long?, createdTime: String, updatedTime: String, payloadJson: String
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Technical Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoRow("Observation Key", obsKey)
                    InfoRow("Remote ID", remoteId?.toString() ?: "Not synced")
                    InfoRow("Created", createdTime)
                    InfoRow("Last Updated", updatedTime)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Payload JSON:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = Color(0xFF1E1E1E),
                            contentColor = Color(0xFFE0E0E0)
                        ) {
                            Text(
                                text = payloadJson, // Just show raw JSON
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ), color = Color(0xFFE0E0E0), modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

