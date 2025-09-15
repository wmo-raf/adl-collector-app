package com.climtech.adlcollector.feature.login.ui

import android.content.res.Configuration
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantSelector(
    tenants: List<TenantConfig>,
    selectedId: String?,
    onSelectTenant: (String) -> Unit,
    onRefreshTenants: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val ordered = remember(tenants) { tenants.sortedByDescending { it.enabled } }
    val selectedTenant = ordered.firstOrNull { it.id == selectedId }
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
                            if (t.enabled) onSelectTenant(t.id)
                            expanded = false
                        },
                        enabled = t.enabled,
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRefreshTenants) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh tenants")
        }
    }
}


@Composable
fun LoginScreen(
    tenants: List<TenantConfig>,
    selectedId: String?,
    onSelectTenant: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRefreshTenants: () -> Unit
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

            var expanded by remember { mutableStateOf(false) }
            val selectedTenant = tenants.firstOrNull { it.id == selectedId }
            val currentName = selectedTenant?.name
                ?: if (tenants.isNotEmpty()) "Select Country" else "No Instances"

            if (tenants.isNotEmpty()) {
                TenantSelector(
                    tenants = tenants,
                    selectedId = selectedId,
                    onSelectTenant = onSelectTenant,
                    onRefreshTenants = onRefreshTenants
                )

            } else {
                Button(onClick = onRefreshTenants, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh Tenants")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedTenant != null && selectedTenant.enabled
            ) {
                Text("Login")
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

@Preview(showBackground = true, name = "Login • Light")
@Composable
private fun PreviewLoginLight() {
    ADLCollectorTheme {
        LoginScreen(
            tenants = sampleTenants(),
            selectedId = "ke",
            onSelectTenant = {},
            onLoginClick = {},
            onRefreshTenants = {})
    }
}

@Preview(
    showBackground = true, name = "Login • Dark", uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PreviewLoginDark() {
    ADLCollectorTheme(darkTheme = true) {
        LoginScreen(
            tenants = sampleTenants(),
            selectedId = "ke",
            onSelectTenant = {},
            onLoginClick = {},
            onRefreshTenants = {})
    }
}

@Preview(showBackground = true, name = "Login • Empty list")
@Composable
private fun PreviewLoginEmpty() {
    ADLCollectorTheme {
        LoginScreen(
            tenants = emptyList(),
            selectedId = null,
            onSelectTenant = {},
            onLoginClick = {},
            onRefreshTenants = {})
    }
}