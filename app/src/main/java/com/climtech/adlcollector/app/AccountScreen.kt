package com.climtech.adlcollector.app

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    tenantName: String,
    baseUrl: String,
    onLogout: () -> Unit,
    onSyncData: () -> Unit = {},
    onClearCache: () -> Unit = {},
    hasPendingObservations: Boolean = false,
    isSyncing: Boolean = false
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Profile Section
            item {
                ProfileCard(
                    tenantName = tenantName, baseUrl = baseUrl
                )
            }

            // App Information Section
            item {
                AppInfoCard()
            }

            // Actions Section
            item {
                ActionsCard(
                    onLogout = onLogout,
                    onSyncData = onSyncData,
                    onClearCache = onClearCache,
                    hasPendingObservations = hasPendingObservations,
                    isSyncing = isSyncing
                )
            }

            // Add some bottom padding
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProfileCard(tenantName: String, baseUrl: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Linked to",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = tenantName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider()

            // Connection details
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(
                    icon = Icons.Filled.Language, label = "Server", value = extractDomain(baseUrl)
                )
            }
        }
    }
}

@Composable
private fun AppInfoCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "App Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(
                    icon = Icons.Filled.Apps, label = "Version", value = "1.0.0"
                )
            }
        }
    }
}

@Composable
private fun ActionsCard(
    onLogout: () -> Unit,
    onSyncData: () -> Unit,
    onClearCache: () -> Unit,
    hasPendingObservations: Boolean,
    isSyncing: Boolean
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Action items
            Column {
                SyncActionItem(
                    onClick = onSyncData,
                    hasPendingObservations = hasPendingObservations,
                    isSyncing = isSyncing
                )

                ActionItem(
                    icon = Icons.Filled.CleaningServices,
                    title = "Clear Cache",
                    subtitle = "Clear cached station data",
                    onClick = { showClearCacheDialog = true },
                    showChevron = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ActionItem(
                    icon = Icons.Filled.Logout,
                    title = "Sign Out",
                    subtitle = "Sign out of this ADL instance",
                    onClick = { showLogoutDialog = true },
                    isDestructive = true,
                    showChevron = false
                )
            }
        }
    }

    // Clear cache confirmation dialog
    if (showClearCacheDialog) {
        AlertDialog(onDismissRequest = { showClearCacheDialog = false }, title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Clear Cache")
            }
        }, text = {
            Text("This will clear all cached station data. You'll need to be online to reload station information. Observations will not be affected.")
        }, confirmButton = {
            TextButton(
                onClick = {
                    showClearCacheDialog = false
                    onClearCache()
                }) {
                Text("Clear Cache")
            }
        }, dismissButton = {
            TextButton(onClick = { showClearCacheDialog = false }) {
                Text("Cancel")
            }
        })
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(onDismissRequest = { showLogoutDialog = false }, title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Sign Out")
            }
        }, text = {
            Text("Are you sure you want to sign out? Any unsynchronized observations will remain on this device.")
        }, confirmButton = {
            TextButton(
                onClick = {
                    showLogoutDialog = false
                    onLogout()
                }, colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Sign Out")
            }
        }, dismissButton = {
            TextButton(onClick = { showLogoutDialog = false }) {
                Text("Cancel")
            }
        })
    }
}

@Composable
private fun SyncActionItem(
    onClick: () -> Unit, hasPendingObservations: Boolean, isSyncing: Boolean
) {
    val canSync = hasPendingObservations && !isSyncing

    // Rotation animation for sync icon
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    Surface(
        onClick = { if (canSync) onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .alpha(if (canSync) 1f else 0.6f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Sync, contentDescription = null, tint = when {
                    isSyncing -> MaterialTheme.colorScheme.primary
                    canSync -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.outline
                }, modifier = Modifier
                    .size(24.dp)
                    .rotate(if (isSyncing) rotationAngle else 0f)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isSyncing -> "Syncing Data..."
                        hasPendingObservations -> "Sync Data"
                        else -> "Sync Data"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isSyncing -> MaterialTheme.colorScheme.primary
                        canSync -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
                Text(
                    text = when {
                        isSyncing -> "Upload in progress..."
                        hasPendingObservations -> "Force sync all observations"
                        else -> "All observations are synced"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // No chevron for sync action since it's immediate
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    showChevron: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showChevron) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = valueColor
            )
        }
    }
}

// Helper function to extract domain from URL
private fun extractDomain(url: String): String {
    return try {
        val uri = java.net.URI(url)
        uri.host ?: url
    } catch (e: Exception) {
        url
    }
}

// Sample data for preview
private fun sampleTenantData() = Pair(
    "Kenya ADL Instance", "https://ke.adl.example.org"
)

@Preview(showBackground = true, name = "Account Screen • Light")
@Composable
private fun PreviewAccountScreen() {
    com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme {
        val (tenantName, baseUrl) = sampleTenantData()
        AccountScreen(
            tenantName = tenantName,
            baseUrl = baseUrl,
            onLogout = {},
            onSyncData = {},
            onClearCache = {},
            hasPendingObservations = true,
            isSyncing = false
        )
    }
}

@Preview(
    showBackground = true, name = "Account Screen • Dark", uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewAccountScreenDark() {
    com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme(darkTheme = true) {
        val (tenantName, baseUrl) = sampleTenantData()
        AccountScreen(
            tenantName = tenantName,
            baseUrl = baseUrl,
            onLogout = {},
            onSyncData = {},
            onClearCache = {},
            hasPendingObservations = false,
            isSyncing = false
        )
    }
}

@Preview(showBackground = true, name = "Account Screen • Syncing")
@Composable
private fun PreviewAccountScreenSyncing() {
    com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme {
        val (tenantName, baseUrl) = sampleTenantData()
        AccountScreen(
            tenantName = tenantName,
            baseUrl = baseUrl,
            onLogout = {},
            onSyncData = {},
            onClearCache = {},
            hasPendingObservations = true,
            isSyncing = true
        )
    }
}