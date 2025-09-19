package com.climtech.adlcollector.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.observations.presentation.ObservationsViewModel
import com.climtech.adlcollector.feature.observations.ui.ObservationsScreen
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel
import com.climtech.adlcollector.feature.stations.ui.StationsScreen

@Composable
fun MainScreen(
    outerNav: NavHostController,
    tenant: TenantConfig,
    stationsVm: StationsViewModel,
    onLogout: () -> Unit
) {
    val innerNav = rememberNavController()

    val currentDest = innerNav.currentBackStackEntryAsState().value?.destination

    val observationsVm: ObservationsViewModel = hiltViewModel(key = "observations-${tenant.id}")

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomDest.items.forEach { item ->
                    val selected = currentDest?.hierarchyHasRoute(item.route) == true
                    val icon = when (item) {
                        BottomDest.Stations -> Icons.Filled.LocationOn
                        BottomDest.Observations -> Icons.AutoMirrored.Filled.List
                        BottomDest.Account -> Icons.Filled.Person
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            innerNav.navigate(item.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label) })
                }
            }
        }) { padding ->
        NavHost(
            navController = innerNav,
            startDestination = BottomDest.Stations.route,
            modifier = Modifier.padding(padding)
        ) {
            // TAB: Stations (Home)
            composable(BottomDest.Stations.route) {
                StationsScreen(
                    tenant = tenant,
                    viewModel = stationsVm,
                    onOpenStation = { stationId, stationName ->
                        // Navigate to top-level station detail (outside bottom bar)
                        outerNav.navigate(
                            Route.StationDetail.build(
                                tenant.id,
                                stationId,
                                stationName
                            )
                        )
                    })
            }

            // TAB: Observations
            composable(BottomDest.Observations.route) {
                ObservationsScreen(
                    tenantId = tenant.id,
                    onObservationClick = { observation ->
                        // Navigate to detail using obsKey instead of remoteId
                        outerNav.navigate(
                            Route.ObservationDetail.build(
                                tenant.id,
                                observation.obsKey
                            )
                        )
                    })
            }

            // TAB: Account
            composable(BottomDest.Account.route) {
                // Get the observations ViewModel to access sync state
                val observationsVm: ObservationsViewModel =
                    hiltViewModel(key = "observations-${tenant.id}")
                val observationsState by observationsVm.uiState.collectAsState()

                AccountScreen(
                    tenantName = tenant.name,
                    baseUrl = tenant.baseUrl,
                    onLogout = onLogout,
                    onSyncData = {
                        observationsVm.syncNow(allowMetered = false, isUrgent = true)
                    },
                    onClearCache = {
                        stationsVm.clearCache(tenant)
                    },
                    hasPendingObservations = observationsState.hasPendingObservations,
                    isSyncing = observationsState.isSyncing
                )
            }
        }
    }
}

private fun NavDestination.hierarchyHasRoute(route: String): Boolean =
    hierarchy.any { it.route == route }