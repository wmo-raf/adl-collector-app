package com.climtech.adlcollector.feature.stations.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme
import com.climtech.adlcollector.feature.stations.data.net.Station
import com.climtech.adlcollector.feature.stations.presentation.StationsUiState
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    tenant: TenantConfig,
    viewModel: StationsViewModel,
    onLogout: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(tenant.id) {
        viewModel.start(tenant) // start stream + background refresh
    }

    StationsContent(
        tenantName = tenant.name,
        state = state,
        onRefresh = { viewModel.refresh(tenant, showSpinner = false) },
        onRetry = { viewModel.start(tenant) },
        onLogout = onLogout
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsContent(
    tenantName: String,
    state: StationsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit
) {


    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stations • $tenantName") },
                actions = {
                    TextButton(onClick = { onRefresh() }) { Text("Refresh") }
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when {
                state.loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading stations…")
                    }
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }

                else -> {
                    StationsList(state.stations)
                }
            }
        }
    }
}

@Composable
private fun StationsList(itemsList: List<Station>) {
    if (itemsList.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No stations available.")
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(itemsList) { s ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        s.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("ID: ${s.id}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}


private fun sampleStations(): List<Station> = listOf(
    Station(id = 101, name = "Nairobi West Substation"),
    Station(id = 202, name = "Kisumu Bay Station"),
    Station(id = 303, name = "Mombasa Port Station")
)

@Preview(showBackground = true, name = "Stations • Loaded")
@Composable
private fun PreviewStationsLoaded() {
    ADLCollectorTheme {
        StationsContent(
            tenantName = "Kenya",
            state = StationsUiState(
                loading = false,
                stations = sampleStations(),
                error = null
            ),
            onRefresh = {},
            onRetry = {},
            onLogout = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Stations • Loading",
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Composable
private fun PreviewStationsLoading() {
    ADLCollectorTheme {
        StationsContent(
            tenantName = "Kenya",
            state = StationsUiState(
                loading = true,
                stations = emptyList(),
                error = null
            ),
            onRefresh = {},
            onRetry = {},
            onLogout = {}
        )
    }
}

@Preview(
    showBackground = true,
    name = "Stations • Error",
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewStationsError() {
    ADLCollectorTheme(darkTheme = true) {
        StationsContent(
            tenantName = "Kenya",
            state = StationsUiState(
                loading = false,
                stations = emptyList(),
                error = "Failed to fetch stations: HTTP 401"
            ),
            onRefresh = {},
            onRetry = {},
            onLogout = {}
        )
    }
}