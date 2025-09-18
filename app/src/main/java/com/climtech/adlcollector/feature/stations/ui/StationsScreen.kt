@file:OptIn(ExperimentalMaterial3Api::class)

package com.climtech.adlcollector.feature.stations.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme
import com.climtech.adlcollector.feature.stations.data.net.Station
import com.climtech.adlcollector.feature.stations.presentation.StationsUiState
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel
import java.net.URI

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
        tenant = tenant,
        state = state,
        onRefresh = { viewModel.refresh(tenant, showSpinner = true) },
        onRetryRefresh = { viewModel.retryRefresh(tenant) },
        onClearError = { viewModel.clearError() },
        onOpenStation = onOpenStation
    )
}

@Composable
fun StationsContent(
    tenant: TenantConfig,
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
            onClearError()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Stations") }, actions = {
            var menuOpen by remember { mutableStateOf(false) }

            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Refresh List") }, onClick = {
                    menuOpen = false
                    onRefresh()
                })
            }
        })
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // ---- Tenant card ----
            TenantInfoCard(
                tenant = tenant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
            )

            //  Offline tip under the card
            AnimatedVisibility(
                visible = state.isOffline && state.stations.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OfflineIndicatorCard(
                    onRetry = onRetryRefresh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ---- Main content area ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                when {
                    // If loading and we have no cached stations yet, show a centered spinner
                    state.loading && state.stations.isEmpty() -> {
                        LoadingContent()
                    }

                    // If there's an error and no cached stations, show the error
                    state.error != null && state.stations.isEmpty() -> {
                        ErrorContent(error = state.error, onRetry = onRefresh)
                    }

                    // Empty state
                    state.stations.isEmpty() && !state.loading -> {
                        EmptyStationsState(onRefresh = onRefresh)
                    }

                    else -> {
                        // Stations List
                        StationsList(
                            stations = state.stations, onOpenStation = onOpenStation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationsList(
    stations: List<Station>, onOpenStation: (Long, String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        item {
            Text(
                text = "Your Assigned Stations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        // Stations
        items(items = stations, key = { it.id }) { station ->
            StationCard(
                station = station,
                onClick = { onOpenStation(station.id, station.name) },
                modifier = Modifier.animateItem()
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}


@Composable
private fun TenantInfoCard(
    tenant: TenantConfig, modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Domain,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tenant.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Server information
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = extractDomain(tenant.baseUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StationCard(
    station: Station, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Station icon with gradient background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Station details
            Column(
                modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Station ID: ${station.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status indicator
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Chevron icon
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open station",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(30.dp)
            )
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
            text = "Loading stations...", style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorContent(
    error: String, onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

// Helper function to extract domain from URL
private fun extractDomain(url: String): String {
    return try {
        val uri = URI(url)
        uri.host ?: url
    } catch (e: Exception) {
        url
    }
}

// Sample data for previews
private fun sampleTenant(): TenantConfig = TenantConfig(
    id = "ke",
    name = "Kenya ADL",
    baseUrl = "https://ke.adl.example.org",
    clientId = "mobile-app",
    enabled = true,
    visible = true
)

private fun sampleStations(): List<Station> = listOf(
    Station(id = 101, name = "Nairobi Central Weather Station"),
    Station(id = 202, name = "Kisumu Bay Monitoring Station"),
    Station(id = 303, name = "Mombasa Port Climate Station"),
    Station(id = 404, name = "Eldoret Highland Station")
)

@Preview(showBackground = true, name = "Enhanced Stations • Light")
@Composable
private fun PreviewEnhancedStationsLoaded() {
    ADLCollectorTheme {
        StationsContent(
            tenant = sampleTenant(), state = StationsUiState(
            loading = false, stations = sampleStations(), error = null
        ), onRefresh = {}, onRetryRefresh = {}, onClearError = {}, onOpenStation = { _, _ -> })
    }
}

@Preview(
    showBackground = true, name = "Stations • Dark", uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewEnhancedStationsDark() {
    ADLCollectorTheme(darkTheme = true) {
        StationsContent(
            tenant = sampleTenant(), state = StationsUiState(
            loading = false, stations = sampleStations(), error = null
        ), onRefresh = {}, onRetryRefresh = {}, onClearError = {}, onOpenStation = { _, _ -> })
    }
}

@Preview(showBackground = true, name = "Tenant Info Card")
@Composable
private fun PreviewTenantInfoCard() {
    ADLCollectorTheme {
        TenantInfoCard(
            tenant = sampleTenant(), modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "Station Card")
@Composable
private fun PreviewEnhancedStationCard() {
    ADLCollectorTheme {
        StationCard(
            station = Station(id = 101, name = "Nairobi Central Weather Station"),
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}