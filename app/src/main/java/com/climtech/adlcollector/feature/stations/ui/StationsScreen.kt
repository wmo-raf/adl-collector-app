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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    onLogout: () -> Unit,
    onOpenStation: (stationId: Long, stationName: String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(tenant.id) {
        viewModel.start(tenant) // start stream + background refresh
    }

    StationsContent(
        tenantName = tenant.name,
        state = state,
        onRefresh = { viewModel.refresh(tenant, showSpinner = true) },
        onRetry = { viewModel.start(tenant) },
        onLogout = onLogout,
        onOpenStation = onOpenStation
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsContent(
    tenantName: String,
    state: StationsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onOpenStation: (Long, String) -> Unit
) {


    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stations") },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }

                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh List") },
                            onClick = {
                                menuOpen = false
                                onRefresh()
                            }
                        )
                    }
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
            if (state.loading && state.stations.isNotEmpty()) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                )
            }

            when {
                state.loading && state.stations.isEmpty() -> {
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

                state.stations.isEmpty() -> {
                    EmptyStationsState(onRefresh = onRefresh)
                }

                else -> {
                    StationsList(state.stations, onOpenStation)
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
            tenantName = "Kenya", state = StationsUiState(
                loading = false, stations = sampleStations(), error = null
            ), onRefresh = {}, onRetry = {}, onLogout = {}, onOpenStation = { _, _ -> })
    }
}

@Preview(
    showBackground = true, name = "Stations • Loading", uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Composable
private fun PreviewStationsLoading() {
    ADLCollectorTheme {
        StationsContent(
            tenantName = "Kenya", state = StationsUiState(
                loading = true, stations = emptyList(), error = null
            ), onRefresh = {}, onRetry = {}, onLogout = {}, onOpenStation = { _, _ -> })
    }
}

@Preview(
    showBackground = true, name = "Stations • Error", uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewStationsError() {
    ADLCollectorTheme(darkTheme = true) {
        StationsContent(
            tenantName = "Kenya", state = StationsUiState(
                loading = false,
                stations = emptyList(),
                error = "Failed to fetch stations: HTTP 401"
            ), onRefresh = {}, onRetry = {}, onLogout = {}, onOpenStation = { _, _ -> })
    }
}