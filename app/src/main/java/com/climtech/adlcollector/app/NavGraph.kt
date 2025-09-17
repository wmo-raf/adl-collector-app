package com.climtech.adlcollector.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.climtech.adlcollector.core.auth.AuthIntent
import com.climtech.adlcollector.core.auth.AuthState
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.components.LoadingScreen
import com.climtech.adlcollector.feature.login.presentation.TenantIntent
import com.climtech.adlcollector.feature.login.presentation.TenantState
import com.climtech.adlcollector.feature.login.ui.LoginScreen
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel

@Composable
fun AppNavGraph(
    nav: androidx.navigation.NavHostController,
    startDestination: String,
    authState: AuthState,
    tenantState: TenantState,
    onAuthIntent: (AuthIntent) -> Unit,
    onTenantIntent: (TenantIntent) -> Unit,
    selectedTenant: TenantConfig?,
    modifier: Modifier = Modifier
) {
    NavHost(navController = nav, startDestination = startDestination, modifier = modifier) {

        composable(Route.Splash.route) {
            LaunchedEffect(authState.isLoggedIn, selectedTenant?.id) {
                val dest = if (authState.isLoggedIn && selectedTenant != null) {
                    Route.Main.build(selectedTenant.id)
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
            if (authState.isLoggedIn && selectedTenant != null) {
                LaunchedEffect(Unit) {
                    nav.navigate(Route.Main.build(selectedTenant.id)) {
                        popUpTo(Route.Login.route) { inclusive = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                LoadingScreen()
            } else {
                LoginScreen(
                    tenantState = tenantState,
                    authInProgress = authState.authInProgress,
                    authError = authState.error,
                    onTenantIntent = onTenantIntent,
                    onLoginClick = { onAuthIntent(AuthIntent.StartLogin) },
                    onClearAuthError = { onAuthIntent(AuthIntent.ClearError) }
                )
            }
        }

        // Container that shows the BottomBar + inner tabs
        composable(
            route = Route.Main.route,
            arguments = listOf(navArgument("tenantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments!!.getString("tenantId")!!
            val tenant = tenantState.tenants.firstOrNull { it.id == tenantId }

            if (tenant == null) {
                // Fallback to login if tenant not found
                LoginScreen(
                    tenantState = tenantState,
                    authInProgress = authState.authInProgress,
                    authError = authState.error,
                    onTenantIntent = onTenantIntent,
                    onLoginClick = { onAuthIntent(AuthIntent.StartLogin) },
                    onClearAuthError = { onAuthIntent(AuthIntent.ClearError) }
                )
            } else {
                val stationsVm: StationsViewModel = hiltViewModel(key = "stations-$tenantId")
                MainScreen(
                    outerNav = nav,
                    tenant = tenant,
                    stationsVm = stationsVm,
                    onLogout = { onAuthIntent(AuthIntent.Logout) }
                )
            }
        }
    }
}