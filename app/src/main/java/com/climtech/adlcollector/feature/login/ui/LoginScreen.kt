package com.climtech.adlcollector.feature.login.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.theme.ADLCollectorTheme
import com.climtech.adlcollector.feature.login.presentation.TenantIntent
import com.climtech.adlcollector.feature.login.presentation.TenantState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantSelector(
    tenantState: TenantState,
    onTenantIntent: (TenantIntent) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val ordered = remember(tenantState.tenants) {
        tenantState.tenants.sortedByDescending { it.enabled }
    }
    val selectedTenant = ordered.firstOrNull { it.id == tenantState.selectedTenantId }
    val currentName = selectedTenant?.name
        ?: if (ordered.isNotEmpty()) "Select Country" else "No Instances"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (ordered.isNotEmpty()) expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentName,
                onValueChange = { /* readOnly */ },
                readOnly = true,
                label = { Text("ADL Instance") },
                supportingText = {
                    if (selectedTenant != null) Text(selectedTenant.baseUrl)
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                enabled = ordered.isNotEmpty(),
                singleLine = true
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                ordered.forEach { t ->
                    val label = if (t.enabled) t.name else "${t.name} (disabled)"
                    DropdownMenuItem(
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            if (t.enabled) onTenantIntent(TenantIntent.SelectTenant(t.id))
                            expanded = false
                        },
                        enabled = t.enabled,
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onTenantIntent(TenantIntent.RefreshTenants) }) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh tenants")
        }
    }
}

@Composable
fun LoginScreen(
    tenantState: TenantState,
    authInProgress: Boolean,
    authError: String?,
    onTenantIntent: (TenantIntent) -> Unit,
    onLoginClick: () -> Unit,
    onClearAuthError: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ADL Collector",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            // Show loading state consistently at the top
            if (tenantState.loading) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Maintain the same height when not loading to prevent layout shift
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(16.dp))

            if (tenantState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Error loading instances:",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(tenantState.error)
                        Button(
                            onClick = { onTenantIntent(TenantIntent.ClearError) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (authError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Login error:",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(authError)
                        Button(
                            onClick = onClearAuthError,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Only show tenant selection UI when not loading
            AnimatedVisibility(
                visible = !tenantState.loading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when {
                        tenantState.tenants.isNotEmpty() -> {
                            TenantSelector(
                                tenantState = tenantState,
                                onTenantIntent = onTenantIntent
                            )

                            val selectedTenant = tenantState.tenants.firstOrNull {
                                it.id == tenantState.selectedTenantId
                            }

                            Button(
                                onClick = onLoginClick,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !authInProgress &&
                                        selectedTenant != null &&
                                        selectedTenant.enabled
                            ) {
                                Text(if (authInProgress) "Logging in..." else "Login")
                            }
                        }

                        else -> {
                            // No tenants - show retry option
                            Button(
                                onClick = { onTenantIntent(TenantIntent.RefreshTenants) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Load Instances")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sampleTenants(): List<TenantConfig> = listOf(
    TenantConfig(
        id = "ke",
        name = "Kenya",
        baseUrl = "https://ke.adl.example.org",
        clientId = "mobile-app",
        enabled = true,
        visible = true
    ), TenantConfig(
        id = "tz",
        name = "Tanzania",
        baseUrl = "https://tz.adl.example.org",
        clientId = "mobile-app",
        enabled = false,        // show disabled state in menu
        visible = true
    ), TenantConfig(
        id = "ug",
        name = "Uganda",
        baseUrl = "https://ug.adl.example.org",
        clientId = "mobile-app",
        enabled = true,
        visible = true
    )
)

private fun sampleTenantState() = TenantState(
    tenants = sampleTenants(),
    selectedTenantId = "ke",
    loading = false,
    error = null
)

@Preview(showBackground = true, name = "Login • Light")
@Composable
private fun PreviewLoginLight() {
    ADLCollectorTheme {
        LoginScreen(
            tenantState = sampleTenantState(),
            authInProgress = false,
            authError = null,
            onTenantIntent = {},
            onLoginClick = {},
            onClearAuthError = {}
        )
    }
}

@Preview(
    showBackground = true, name = "Login • Dark", uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewLoginDark() {
    ADLCollectorTheme(darkTheme = true) {
        LoginScreen(
            tenantState = sampleTenantState(),
            authInProgress = false,
            authError = null,
            onTenantIntent = {},
            onLoginClick = {},
            onClearAuthError = {}
        )
    }
}

@Preview(showBackground = true, name = "Login • Empty list")
@Composable
private fun PreviewLoginEmpty() {
    ADLCollectorTheme {
        LoginScreen(
            tenantState = TenantState(loading = false, error = "No instances available"),
            authInProgress = false,
            authError = null,
            onTenantIntent = {},
            onLoginClick = {},
            onClearAuthError = {}
        )
    }
}

@Preview(showBackground = true, name = "Login • With error")
@Composable
private fun PreviewLoginWithError() {
    ADLCollectorTheme {
        LoginScreen(
            tenantState = sampleTenantState(),
            authInProgress = false,
            authError = "Session expired. Please try again.",
            onTenantIntent = {},
            onLoginClick = {},
            onClearAuthError = {}
        )
    }
}