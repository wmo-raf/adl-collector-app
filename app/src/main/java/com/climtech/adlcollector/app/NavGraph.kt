package com.climtech.adlcollector.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.login.ui.LoginScreen
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel
import com.climtech.adlcollector.feature.stations.ui.StationsScreen

@Composable
fun AppNavGraph(
    nav: NavHostController,
    startDestination: String,
    tenants: List<TenantConfig>,
    selectedTenantId: String?,
    onSelectTenant: (String) -> Unit,
    onRefreshTenants: () -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(navController = nav, startDestination = startDestination, modifier = modifier) {

        composable(Route.Login.route) {
            LoginScreen(
                tenants = tenants,
                selectedId = selectedTenantId,
                onSelectTenant = onSelectTenant,
                onLoginClick = onLoginClick,
                onRefreshTenants = onRefreshTenants
            )
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