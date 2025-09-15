package com.climtech.adlcollector.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.components.LoadingScreen
import com.climtech.adlcollector.feature.login.ui.LoginScreen
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel
import com.climtech.adlcollector.feature.stations.ui.StationsScreen

@Composable
fun AppNavGraph(
    nav: NavHostController,
    startDestination: String,
    tenants: List<TenantConfig>,
    selectedTenantId: String?,
    isLoggedIn: Boolean,
    authInFlight: Boolean,
    onSelectTenant: (String) -> Unit,
    onRefreshTenants: () -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(navController = nav, startDestination = startDestination, modifier = modifier) {

        composable(Route.Splash.route) {
            // Decide where to go, without rendering Login UI
            LaunchedEffect(isLoggedIn, selectedTenantId) {
                val dest = if (isLoggedIn && !selectedTenantId.isNullOrBlank()) {
                    Route.Stations.build(selectedTenantId)
                } else {
                    Route.Login.route
                }
                nav.navigate(dest) {
                    popUpTo(Route.Splash.route) { inclusive = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            // Optional: show a tiny progress so we never flash Login
            LoadingScreen()
        }


        composable(Route.Login.route) {
            if (isLoggedIn && !selectedTenantId.isNullOrBlank()) {
                // Don’t render the tenant selector; jump immediately
                LaunchedEffect(Unit) {
                    nav.navigate(Route.Stations.build(selectedTenantId)) {
                        popUpTo(Route.Login.route) { inclusive = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                LoadingScreen()
            } else {
                LoginScreen(
                    tenants = tenants,
                    selectedId = selectedTenantId,
                    loginBusy = authInFlight,
                    onSelectTenant = onSelectTenant,
                    onLoginClick = onLoginClick,
                    onRefreshTenants = onRefreshTenants
                )
            }
        }

        composable(
            route = Route.Stations.route,
            arguments = listOf(navArgument("tenantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments!!.getString("tenantId")!!
            val tenant = tenants.firstOrNull { it.id == tenantId }

            if (tenant == null) {
                // fallback UI if tenant list hasn't loaded yet
                LoginScreen(
                    tenants = tenants,
                    selectedId = selectedTenantId,
                    loginBusy = authInFlight,
                    onSelectTenant = onSelectTenant,
                    onLoginClick = onLoginClick,
                    onRefreshTenants = onRefreshTenants
                )
            } else {
                val vm: StationsViewModel = hiltViewModel(key = "stations-$tenantId")
                StationsScreen(
                    tenant = tenant, viewModel = vm, onLogout = onLogout
                )
            }
        }
    }
}