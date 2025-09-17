package com.climtech.adlcollector.feature.observations.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.observations.presentation.ObservationFormViewModel
import com.climtech.adlcollector.feature.observations.presentation.UiEvent
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationFormScreen(
    tenant: TenantConfig,
    stationId: Long,
    stationName: String,
    submitEndpointUrl: String,
    onCancel: () -> Unit,
    onSubmitted: () -> Unit,
    vm: ObservationFormViewModel = hiltViewModel()
) {
    LaunchedEffect(stationId, tenant.id) {
        vm.start(tenant, stationId, stationName, submitEndpointUrl)
    }

    val ui by vm.ui.collectAsState()
    val scroll = rememberScrollState()
    val context = LocalContext.current

    var showValidationDialog by rememberSaveable { mutableStateOf(false) }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    // Listen for one-shot events from the VM
    LaunchedEffect(vm) {
        vm.events.collectLatest { evt ->
            when (evt) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, evt.message, Toast.LENGTH_SHORT).show()
                    onSubmitted()
                }
            }
        }
    }

    // Validation Dialog
    if (showValidationDialog) {
        ValidationDialog(
            reason = ui.reason ?: "Unable to submit at this time",
            onDismiss = { showValidationDialog = false }
        )
    }

    // Cancel Confirmation Dialog
    if (showCancelDialog) {
        CancelConfirmationDialog(
            onConfirm = {
                showCancelDialog = false
                onCancel()
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Observation") },
                navigationIcon = {
                    IconButton(onClick = { showCancelDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loading indicator
            AnimatedVisibility(
                visible = ui.loading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Station Info Card
            StationInfoCard(
                stationName = ui.stationName,
                timezone = ui.timezone,
                displayLocal = ui.displayLocal,
                onTimeAdjust = { minutes ->
                    vm.onTimeChange(ui.displayLocal.plusMinutes(minutes.toLong()))
                }
            )

            // Status Indicators
            StatusSection(
                late = ui.late,
                locked = ui.locked,
                valid = ui.valid,
                reason = ui.reason,
                onShowValidation = { showValidationDialog = true }
            )

            // Variables Input Section
            VariablesSection(
                variables = ui.variables,
                onVariableChanged = { id, value -> vm.onVariableChanged(id, value) }
            )

            // Error Display
            AnimatedVisibility(visible = ui.error != null) {
                ErrorCard(error = ui.error ?: "")
            }

            // Action Buttons
            ActionButtons(
                canSubmit = ui.valid && !ui.submitting && !ui.locked,
                isSubmitting = ui.submitting,
                variables = ui.variables,
                onSubmit = { vm.submit() },
                onCancel = { showCancelDialog = true }
            )

            // Bottom padding for better scrolling
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StationInfoCard(
    stationName: String,
    timezone: String,
    displayLocal: java.time.LocalDateTime,
    onTimeAdjust: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stationName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Timezone: $timezone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Time section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Observation Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = displayLocal.format(
                        DateTimeFormatter.ofPattern(
                            "EEEE, MMMM d, yyyy 'at' HH:mm",
                            Locale.getDefault()
                        )
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                // Time adjustment buttons
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimeAdjustButton(label = "-15m", minutes = -15, onClick = onTimeAdjust)
                    TimeAdjustButton(label = "-5m", minutes = -5, onClick = onTimeAdjust)
                    TimeAdjustButton(label = "+5m", minutes = 5, onClick = onTimeAdjust)
                    TimeAdjustButton(label = "+15m", minutes = 15, onClick = onTimeAdjust)
                }
            }
        }
    }
}

@Composable
private fun TimeAdjustButton(
    label: String,
    minutes: Int,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit
) {
    OutlinedButton(
        onClick = { onClick(minutes) },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusSection(
    late: Boolean,
    locked: Boolean,
    valid: Boolean,
    reason: String?,
    onShowValidation: () -> Unit
) {
    if (late || locked || !valid) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!valid && reason != null) {
                StatusChip(
                    text = reason,
                    icon = Icons.Filled.Warning,
                    isError = true,
                    onClick = onShowValidation
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (late) {
                    StatusChip(
                        text = "Late Submission",
                        icon = Icons.Filled.Schedule,
                        isError = false
                    )
                }

                if (locked) {
                    StatusChip(
                        text = "Editing Locked",
                        icon = Icons.Filled.Warning,
                        isError = true
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isError: Boolean,
    onClick: (() -> Unit)? = null
) {
    AssistChip(
        onClick = onClick ?: {},
        label = { Text(text) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            labelColor = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        )
    )
}

@Composable
private fun VariablesSection(
    variables: List<com.climtech.adlcollector.feature.observations.presentation.ObservationVariableUi>,
    onVariableChanged: (Long, String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Measurements",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        variables.forEach { variable ->
            VariableInputField(
                variable = variable,
                onValueChange = { newValue ->
                    onVariableChanged(variable.id, newValue)
                }
            )
        }
    }
}

@Composable
private fun VariableInputField(
    variable: com.climtech.adlcollector.feature.observations.presentation.ObservationVariableUi,
    onValueChange: (String) -> Unit
) {
    var value by remember(variable.id) { mutableStateOf(variable.valueText) }
    var isError by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            value = newValue
            onValueChange(newValue)

            // Basic validation - check if it's a valid number
            isError = newValue.isNotBlank() && newValue.toDoubleOrNull() == null
        },
        label = {
            Text("${variable.name} (${variable.unit})")
        },
        supportingText = if (variable.isRainfall) {
            { Text("Rainfall measurement") }
        } else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal
        ),
        isError = isError,
        trailingIcon = if (isError) {
            {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "Invalid number",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (value.isNotBlank() && !isError) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Valid",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else null
    )
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ActionButtons(
    canSubmit: Boolean,
    isSubmitting: Boolean,
    variables: List<com.climtech.adlcollector.feature.observations.presentation.ObservationVariableUi>,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    // Check if all variables have valid values
    val allFieldsFilled = variables.all { variable ->
        variable.valueText.isNotBlank() && variable.valueText.toDoubleOrNull() != null
    }

    val submitEnabled = canSubmit && allFieldsFilled && !isSubmitting

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSubmit,
            enabled = submitEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Submitting...")
            } else {
                Text(
                    if (!allFieldsFilled) "Fill in all measurements"
                    else "Submit Observation"
                )
            }
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ValidationDialog(
    reason: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Submission Not Available")
            }
        },
        text = {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun CancelConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cancel Observation")
        },
        text = {
            Text("Are you sure you want to cancel? Any entered data will be lost.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Cancel Observation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue Editing")
            }
        }
    )
}