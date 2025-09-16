package com.climtech.adlcollector.feature.observations.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.observations.presentation.ObservationFormViewModel
import com.climtech.adlcollector.feature.observations.presentation.UiEvent
import kotlinx.coroutines.flow.collectLatest

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

    // Listen for one-shot events from the VM
    LaunchedEffect(vm) {
        vm.events.collectLatest { evt ->
            when (evt) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, evt.message, Toast.LENGTH_SHORT).show()
                    onSubmitted() // navigate only after success toast
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("New Observation") }, navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back"
                    )
                }
            })
        }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (ui.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text("Station: ${ui.stationName}")
            Text("Timezone: ${ui.timezone}")
            Text("Local time: ${ui.displayLocal}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.onTimeChange(ui.displayLocal.minusMinutes(5)) }) { Text("-5 min") }
                Button(onClick = { vm.onTimeChange(ui.displayLocal.plusMinutes(5)) }) { Text("+5 min") }
            }

            if (ui.reason != null && !ui.valid) {
                AssistChip(onClick = {}, label = { Text(ui.reason ?: "") })
            }
            if (ui.late) AssistChip(onClick = {}, label = { Text("Late") })
            if (ui.locked) AssistChip(onClick = {}, label = { Text("Locked") })

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            ui.variables.forEach { v ->
                var txt by remember(v.id) { mutableStateOf(v.valueText) }
                OutlinedTextField(
                    value = txt,
                    onValueChange = {
                        txt = it
                        vm.onVariableChanged(v.id, it)
                    },
                    label = { Text("${v.name} (${v.unit})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (ui.error != null) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    vm.submit()
                    // onSubmitted() moved to the toast event to ensure success
                }, enabled = ui.valid && !ui.submitting, modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (ui.submitting) "Submitting…" else "Submit")
            }
        }
    }
}