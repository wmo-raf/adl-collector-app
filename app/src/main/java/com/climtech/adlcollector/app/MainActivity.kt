package com.climtech.adlcollector.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.climtech.adlcollector.core.auth.OAuthManager
import com.climtech.adlcollector.core.auth.OAuthResult
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.auth.TenantManager
import com.climtech.adlcollector.core.ui.components.ErrorScreen
import com.climtech.adlcollector.core.ui.components.LoadingScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var oauthManager: OAuthManager

    @Inject
    lateinit var tenantManager: TenantManager

    @Inject
    lateinit var tenantLocalStore: TenantLocalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("OAUTH_APP", "========== APP STARTED ==========")

        // Firebase init
        FirebaseApp.initializeApp(this)

        // Optional connectivity check
        FirebaseFirestore.getInstance().collection("tenants").get().addOnSuccessListener { docs ->
            Log.i("FIREBASE", "Loaded tenants snapshot: ${docs.size()}")
        }.addOnFailureListener { e ->
            Log.e("FIREBASE", "Error fetching tenants", e)
        }

        // Initialize OAuth service
        oauthManager.initialize(this)

        // Setup lifecycle observer for cleanup
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                oauthManager.dispose()
            }
        })

        // Initialize tenant manager
        lifecycleScope.launch {
            tenantManager.initialize()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        when (intent?.action) {
            OAuthManager.ACTION_AUTH_CANCELLED -> {
                oauthManager.handleAuthCancelled()
            }

            OAuthManager.ACTION_AUTH_COMPLETED -> {
                lifecycleScope.launch {
                    when (val result = oauthManager.handleAuthCompleted(intent)) {
                        is OAuthResult.Success -> {
                            Log.d("MainActivity", "OAuth completed successfully")
                        }

                        is OAuthResult.Error -> {
                            Log.e("MainActivity", "OAuth error: ${result.message}")
                        }

                        OAuthResult.Cancelled -> {
                            Log.d("MainActivity", "OAuth cancelled by user")
                        }
                    }
                }
            }
        }
    }

    private fun startLogin() {
        val tenant = tenantManager.state.value.selectedTenant
        if (tenant == null) {
            // This should not happen given UI constraints, but handle gracefully
            Log.w("MainActivity", "No tenant selected for login")
            return
        }

        oauthManager.startLogin(this, tenant)
    }

    private fun logout() {
        oauthManager.logout()
    }

    private fun onSelectTenant(tenantId: String) {
        lifecycleScope.launch {
            tenantManager.selectTenant(tenantId)
        }
    }

    private fun onRefreshTenants() {
        lifecycleScope.launch {
            tenantManager.loadTenants(preserveSelection = true)
        }
    }

    // In MainActivity.onCreate()
    private fun updateUI() {
        setContent {
            val tenantState by tenantManager.state.collectAsState()
            val oauthState by oauthManager.state.collectAsState()

            // Show error only when not in any active state
            val showError =
                !tenantState.isLoading && !oauthState.isInProgress && !oauthState.isLoggedIn && (tenantState.error != null || oauthState.error != null)

            val errorMessage = if (showError) {
                tenantState.error ?: oauthState.error
            } else null

            when {
                errorMessage != null -> {
                    ErrorScreen(error = errorMessage) {
                        oauthManager.clearError()
                        tenantManager.clearError()
                        onRefreshTenants()
                    }
                }

                oauthState.isInProgress -> {
                    LoadingScreen()
                }

                else -> {
                    val nav = rememberNavController()
                    AppNavGraph(
                        nav = nav,
                        startDestination = Route.Splash.route,
                        tenants = tenantState.tenants,
                        selectedTenantId = tenantState.selectedTenantId,
                        // ADD THIS CHECK: if we have stored tokens, consider it logged in
                        isLoggedIn = oauthState.isLoggedIn || hasStoredAuth(tenantState.selectedTenantId),
                        authInFlight = oauthState.isInProgress,
                        tenantsLoading = tenantState.isLoading,
                        onSelectTenant = ::onSelectTenant,
                        onRefreshTenants = ::onRefreshTenants,
                        onLoginClick = ::startLogin,
                        onLogout = {
                            logout()
                            nav.navigate(Route.Login.route) {
                                popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                                restoreState = false
                            }
                        })

                    // Handle navigation after successful login OR when we detect stored auth
                    LaunchedEffect(
                        oauthState.isLoggedIn,
                        tenantState.selectedTenantId,
                        hasStoredAuth(tenantState.selectedTenantId)
                    ) {
                        val tenantId = tenantState.selectedTenantId
                        val shouldNavigateToMain =
                            (oauthState.isLoggedIn || hasStoredAuth(tenantId)) && !tenantId.isNullOrBlank()

                        if (shouldNavigateToMain) {
                            nav.navigate(Route.Main.build(tenantId)) {
                                popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasStoredAuth(tenantId: String?): Boolean {
        return if (tenantId != null) {
            runBlocking {
                val accessToken = tenantLocalStore.getAccessToken(tenantId)
                val refreshToken = tenantLocalStore.getRefreshToken(tenantId)
                !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()
            }
        } else false
    }
}