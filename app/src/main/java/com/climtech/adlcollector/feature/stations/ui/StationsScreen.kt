package com.climtech.adlcollector.feature.stations.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
        onRetryRefresh = { viewModel.retryRefresh(tenant) },
        onClearError = { viewModel.clearError() },
        onOpenStation = onOpenStation
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsContent(
    tenantName: String,
    state: StationsUiState,
    onRefresh: () -> Unit,
    onRetryRefresh: () -> Unit,
    onClearError: () -> Unit,
    onOpenStation: (Long, String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onClearError() // Clear error after showing snackbar
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stations") },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }

                    IconButton(onClick = { onRefresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }

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
        ) {
            Column {
                // Show offline indicator card when data is stale
                if (state.isOffline && state.stations.isNotEmpty()) {
                    OfflineIndicatorCard(
                        onRetry = onRetryRefresh,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Show background refresh indicator
                if (state.loading && state.stations.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Main content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    when {
                        // Show full-screen loading only when no cached data
                        state.loading && state.stations.isEmpty() -> {
                            LoadingContent()
                        }

                        // Show error only when no cached data available
                        state.error != null && state.stations.isEmpty() -> {
                            ErrorContent(
                                error = state.error,
                                onRetry = onRefresh
                            )
                        }

                        // Show empty state when no stations (cached or fresh)
                        state.stations.isEmpty() && !state.loading -> {
                            EmptyStationsState(onRefresh = onRefresh)
                        }

                        // Show stations list (prioritizes cached data)
                        else -> {
                            StationsList(state.stations, onOpenStation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineIndicatorCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
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
                text = "Showing cached data. Connect to internet for updates.",
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
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Loading stations...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Unable to load stations",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// Sample data and previews remain the same...
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
            onRetryRefresh = {},
            onClearError = {},
            onOpenStation = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Stations • Offline")
@Composable
private fun PreviewStationsOffline() {
    ADLCollectorTheme {
        StationsContent(
            tenantName = "Kenya",
            state = StationsUiState(
                loading = false,
                stations = sampleStations(),
                error = null,
                isOffline = true,
                lastRefreshFailed = true
            ),
            onRefresh = {},
            onRetryRefresh = {},
            onClearError = {},
            onOpenStation = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Stations • Loading First Time")
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
            onRetryRefresh = {},
            onClearError = {},
            onOpenStation = { _, _ -> }
        )
    }
}

@Preview(
    showBackground = true,
    name = "Stations • Error No Cache",
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
                error = "You're offline. Check your connection and try again."
            ),
            onRefresh = {},
            onRetryRefresh = {},
            onClearError = {},
            onOpenStation = { _, _ -> }
        )
    }
}