package com.climtech.adlcollector.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.climtech.adlcollector.core.auth.AuthIntent
import com.climtech.adlcollector.core.auth.AuthViewModel
import com.climtech.adlcollector.core.ui.components.ErrorScreen
import com.climtech.adlcollector.core.ui.components.LoadingScreen
import com.climtech.adlcollector.feature.login.presentation.TenantIntent
import com.climtech.adlcollector.feature.login.presentation.TenantViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val tenantViewModel: TenantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("OAUTH_APP", "========== APP STARTED ==========")

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Quick Firebase connectivity test
        FirebaseFirestore.getInstance().collection("tenants").get()
            .addOnSuccessListener { docs ->
                Log.i("FIREBASE", "Loaded tenants snapshot: ${docs.size()}")
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE", "Error fetching tenants", e)
            }

        // Clean up auth service on destroy
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                authViewModel.getAuthService()?.dispose()
            }
        })

        setContent {
            val authState by authViewModel.authState.collectAsState()
            val tenantState by tenantViewModel.tenantState.collectAsState()

            when {
                // Show error screen if there's a critical error
                authState.error != null && !authState.authInProgress -> {
                    ErrorScreen(error = authState.error!!) {
                        authViewModel.handle(AuthIntent.ClearError)
                        tenantViewModel.handle(TenantIntent.LoadTenants)
                    }
                }

                tenantState.error != null && tenantState.tenants.isEmpty() -> {
                    ErrorScreen(error = tenantState.error!!) {
                        tenantViewModel.handle(TenantIntent.ClearError)
                        tenantViewModel.handle(TenantIntent.RefreshTenants)
                    }
                }

                // Show loading during auth flow
                authState.authInProgress -> {
                    LoadingScreen()
                }

                // Main navigation
                else -> {
                    val navController = rememberNavController()
                    AppNavGraph(
                        nav = navController,
                        startDestination = Route.Splash.route,
                        authState = authState,
                        tenantState = tenantState,
                        onAuthIntent = { authViewModel.handle(it) },
                        onTenantIntent = { tenantViewModel.handle(it) },
                        selectedTenant = tenantViewModel.getSelectedTenant()
                    )

                    // Handle navigation based on auth state
                    LaunchedEffect(authState.isLoggedIn, authState.selectedTenant?.id) {
                        if (authState.isLoggedIn && authState.selectedTenant != null) {
                            navController.navigate(Route.Main.build(authState.selectedTenant!!.id)) {
                                popUpTo(Route.Login.route) { inclusive = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else if (!authState.isLoggedIn && !authState.authInProgress) {
                            navController.navigate(Route.Login.route) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        when (intent?.action) {
            AuthViewModel.ACTION_AUTH_CANCELLED -> {
                authViewModel.handle(AuthIntent.HandleAuthCanceled(intent))
            }

            AuthViewModel.ACTION_AUTH_COMPLETED -> {
                authViewModel.handle(AuthIntent.HandleAuthCallback(intent))
            }
        }
    }
}