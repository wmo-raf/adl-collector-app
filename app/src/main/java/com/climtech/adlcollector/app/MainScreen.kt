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
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.climtech.adlcollector.core.model.TenantConfig
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
                    onLogout = onLogout,
                    onOpenStation = { stationId, stationName ->
                        innerNav.navigate("station/$stationId/${stationName.encodeForRoute()}")
                    })
            }

            // TAB: Observations (placeholder)
            composable(BottomDest.Observations.route) {
                ObservationsScreenPlaceholder()
            }

            // TAB: Account
            composable(BottomDest.Account.route) {
                AccountScreen(
                    tenantName = tenant.name, baseUrl = tenant.baseUrl, onLogout = onLogout
                )
            }

            // Detail: Station
            composable("station/{stationId}/{stationName}") { backStackEntry ->
                val stationId = backStackEntry.arguments?.getString("stationId")!!.toLong()
                val stationName =
                    backStackEntry.arguments?.getString("stationName")!!.decodeFromRoute()
                StationDetailScreen(
                    stationId = stationId,
                    stationName = stationName,
                    onBack = { innerNav.popBackStack() },
                    onAddObservation = {
                        innerNav.navigate("observation/new/$stationId/${stationName.encodeForRoute()}")
                    })
            }

            // Form: New Observation
            composable("observation/new/{stationId}/{stationName}") { backStackEntry ->
                val stationId = backStackEntry.arguments?.getString("stationId")!!.toLong()
                val stationName =
                    backStackEntry.arguments?.getString("stationName")!!.decodeFromRoute()
                ObservationFormScreenPlaceholder(
                    stationId = stationId,
                    stationName = stationName,
                    onCancel = { innerNav.popBackStack() },
                    onSubmit = {
                        // Later: persist & sync; for now bounce to Observations tab
                        innerNav.popBackStack(
                            route = BottomDest.Observations.route, inclusive = false
                        )
                    })
            }
        }
    }
}

private fun NavDestination.hierarchyHasRoute(route: String): Boolean =
    hierarchy.any { it.route == route }

private fun String.encodeForRoute(): String = java.net.URLEncoder.encode(this, "utf-8")
private fun String.decodeFromRoute(): String = java.net.URLDecoder.decode(this, "utf-8")
