package com.climtech.adlcollector.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.components.LoadingScreen
import com.climtech.adlcollector.feature.login.ui.LoginScreen
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel

@Composable
fun AppNavGraph(
    nav: androidx.navigation.NavHostController,
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
            LaunchedEffect(isLoggedIn, selectedTenantId) {
                val dest = if (isLoggedIn && !selectedTenantId.isNullOrBlank()) {
                    Route.Main.build(selectedTenantId)
                } else {
                    Route.Login.route
                }
                nav.navigate(dest) {
                    popUpTo(Route.Splash.route) { inclusive = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            LoadingScreen()
        }

        composable(Route.Login.route) {
            if (isLoggedIn && !selectedTenantId.isNullOrBlank()) {
                LaunchedEffect(Unit) {
                    nav.navigate(Route.Main.build(selectedTenantId)) {
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

        // Container that shows the BottomBar + inner tabs
        composable(
            route = Route.Main.route,
            arguments = listOf(navArgument("tenantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments!!.getString("tenantId")!!
            val tenant = tenants.firstOrNull { it.id == tenantId }
            if (tenant == null) {
                // Fallback to login if tenant list hasn’t loaded yet
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
                MainScreen(
                    outerNav = nav,
                    tenant = tenant,
                    stationsVm = vm,
                    onLogout = onLogout
                )
            }
        }
    }
}
