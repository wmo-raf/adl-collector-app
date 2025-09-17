package com.climtech.adlcollector.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.work.WorkManager
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.components.LoadingScreen
import com.climtech.adlcollector.feature.login.ui.LoginScreen
import com.climtech.adlcollector.feature.observations.sync.UploadObservationsWorker
import com.climtech.adlcollector.feature.observations.ui.ObservationFormScreen
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel
import com.climtech.adlcollector.feature.stations.ui.StationDetailScreen


@Composable
fun AppNavGraph(
    nav: androidx.navigation.NavHostController,
    startDestination: String,
    tenants: List<TenantConfig>,
    selectedTenantId: String?,
    isLoggedIn: Boolean,
    authInFlight: Boolean,
    tenantsLoading: Boolean,
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
            LoginScreen(
                tenants = tenants,
                selectedId = selectedTenantId,
                loginBusy = authInFlight,
                tenantsLoading = tenantsLoading,
                onSelectTenant = onSelectTenant,
                onLoginClick = onLoginClick,
                onRefreshTenants = onRefreshTenants
            )
        }

        // Container that shows the BottomBar + inner tabs
        composable(
            route = Route.Main.route,
            arguments = listOf(navArgument("tenantId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments!!.getString("tenantId")!!
            val tenant = tenants.firstOrNull { it.id == tenantId }
            if (tenant == null) {
                // Fallback to login if tenant list hasn't loaded yet
                LoginScreen(
                    tenants = tenants,
                    selectedId = selectedTenantId,
                    loginBusy = authInFlight,
                    tenantsLoading = tenantsLoading,
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

        // NEW: Station Detail - Outside the bottom bar navigation
        composable(
            route = Route.StationDetail.route,
            arguments = listOf(
                navArgument("tenantId") { type = NavType.StringType },
                navArgument("stationId") { type = NavType.LongType },
                navArgument("stationName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tenantId = backStackEntry.arguments!!.getString("tenantId")!!
            val stationId = backStackEntry.arguments!!.getLong("stationId")
            val stationName = backStackEntry.arguments!!.getString("stationName")!!

            val tenant = tenants.firstOrNull { it.id == tenantId }
            if (tenant != null) {
                StationDetailScreen(
                    tenant = tenant,
                    stationId = stationId,
                    stationName = stationName,
                    onBack = {
                        nav.popBackStack()
                    },
                    onAddObservation = {
                        nav.navigate(Route.ObservationForm.build(tenantId, stationId, stationName))
                    },
                    onOpenObservation = { obsId ->
                        // TODO: Navigate to observation detail when available
                    },
                    onRefresh = { /* TODO: trigger refresh if you add caching */ }
                )
            }
        }

        // NEW: Observation Form - Also outside bottom bar
        composable(
            route = Route.ObservationForm.route,
            arguments = listOf(
                navArgument("tenantId") { type = NavType.StringType },
                navArgument("stationId") { type = NavType.LongType },
                navArgument("stationName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val tenantId = backStackEntry.arguments!!.getString("tenantId")!!
            val stationId = backStackEntry.arguments!!.getLong("stationId")
            val stationName = backStackEntry.arguments!!.getString("stationName")!!

            val tenant = tenants.firstOrNull { it.id == tenantId }
            if (tenant != null) {
                // Build submit endpoint under the tenant base
                val submitUrl = tenant.api(
                    "plugins", "api", "adl-collector", "manual-obs", "submit", trailingSlash = true
                ).toString()

                ObservationFormScreen(
                    tenant = tenant,
                    stationId = stationId,
                    stationName = stationName,
                    submitEndpointUrl = submitUrl,
                    onCancel = { nav.popBackStack() },
                    onSubmitted = {
                        // enqueue upload and go back to station detail
                        val req = UploadObservationsWorker.oneShot(tenantId, submitUrl)
                        WorkManager.getInstance(context).enqueue(req)
                        nav.popBackStack()
                    }
                )
            }
        }
    }
}