package com.climtech.adlcollector.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.ui.components.ErrorScreen
import com.climtech.adlcollector.core.ui.components.LoadingScreen
import com.climtech.adlcollector.feature.login.data.TenantRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authManager: com.climtech.adlcollector.core.auth.AuthManager
    private lateinit var authService: AuthorizationService
    private val tenantRepo = TenantRepository()
    private lateinit var tenantLocal: TenantLocalStore

    private var currentTenant: TenantConfig? = null
    private var currentAuthRequest: AuthorizationRequest? = null

    // UI state
    private var isLoggedIn = mutableStateOf(false)
    private var userInfo = mutableStateOf("")
    private var isLoading = mutableStateOf(false)
    private var errorMessage = mutableStateOf("")
    private var tenants = mutableStateOf<List<TenantConfig>>(emptyList())
    private var selectedTenantId = mutableStateOf<String?>(null)

    private var authInFlight = false

    private companion object {
        private const val ACTION_AUTH_COMPLETED = "com.climtech.adlcollector.AUTH_COMPLETED"
        private const val ACTION_AUTH_CANCELLED = "com.climtech.adlcollector.AUTH_CANCELLED"
    }

    private fun pendingIntent(action: String, requestCode: Int) = PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("OAUTH_APP", "========== APP STARTED ==========")

        // Firebase init
        FirebaseApp.initializeApp(this)

        // (Optional) quick connectivity check
        FirebaseFirestore.getInstance().collection("tenants").get().addOnSuccessListener { docs ->
            Log.i(
                "FIREBASE", "Loaded tenants snapshot: ${docs.size()}"
            )
        }.addOnFailureListener { e -> Log.e("FIREBASE", "Error fetching tenants", e) }

        tenantLocal = TenantLocalStore(this)
        authService = AuthorizationService(this)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (::authService.isInitialized) authService.dispose()
            }
        })

        // Observe saved tenant id; when list is present, restore selection
        lifecycleScope.launch {
            tenantLocal.selectedTenantId.collect { savedId ->
                if (savedId != null && tenants.value.isNotEmpty()) {
                    selectedTenantId.value = savedId
                    currentTenant = tenants.value.firstOrNull { it.id == savedId }
                    updateUI()
                }
            }
        }

        // Load tenants from Firestore
        loadTenants()

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        if (authInFlight && isLoading.value) {
            // if we’re back in the app and no callback has arrived within ~1s, treat as cancel
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                if (authInFlight && isLoading.value) {
                    authInFlight = false
                    isLoading.value = false
                    errorMessage.value = "Login cancelled."
                    updateUI()
                }
            }
        }
    }

    private fun loadTenants(preserveSelection: Boolean = true) {
        isLoading.value = true
        updateUI()

        val previouslySelectedId = if (preserveSelection) selectedTenantId.value else null

        lifecycleScope.launch {
            try {
                val list = tenantRepo.listTenants()
                val visibleList = list.filter { it.visible } // hide soft-removed tenants
                tenants.value = visibleList

                // use visibleList for checks and assignment
                selectedTenantId.value = when {
                    previouslySelectedId != null && visibleList.any { it.id == previouslySelectedId } -> previouslySelectedId
                    else -> visibleList.firstOrNull()?.id
                }

                currentTenant = visibleList.firstOrNull { it.id == selectedTenantId.value }

                isLoading.value = false
                updateUI()
            } catch (e: Exception) {
                isLoading.value = false
                errorMessage.value = "Failed to load ADL Instances: ${e.message}"
                updateUI()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        when (intent?.action) {
            ACTION_AUTH_CANCELLED -> {
                authInFlight = false
                isLoading.value = false
                errorMessage.value = "Login cancelled."
                updateUI()
            }

            ACTION_AUTH_COMPLETED -> {
                authInFlight = false

                val resp = AuthorizationResponse.fromIntent(intent)
                val ex = AuthorizationException.fromIntent(intent)

                if (ex != null) {
                    isLoading.value = false
                    errorMessage.value = "Login failed: ${ex.errorDescription ?: ex.error}"
                    updateUI()
                    return
                }
                if (resp == null) {
                    isLoading.value = false
                    errorMessage.value = "Login failed: empty response"
                    updateUI()
                    return
                }

                // We are still busy until we finish the code exchange + state updates
                isLoading.value = true
                updateUI()

                val tokenRequest: TokenRequest = resp.createTokenExchangeRequest()
                authService.performTokenRequest(tokenRequest) { tokenResponse, tokenEx ->
                    lifecycleScope.launch {
                        if (tokenEx != null || tokenResponse == null) {
                            isLoading.value = false
                            errorMessage.value =
                                "Token exchange failed: ${tokenEx?.errorDescription ?: tokenEx?.error ?: "Unknown error"}"
                            updateUI()
                            return@launch
                        }

                        val tenant = currentTenant ?: run {
                            isLoading.value = false
                            errorMessage.value = "Missing tenant during token save."
                            updateUI()
                            return@launch
                        }

                        // Persist, then mark logged in, THEN clear loading
                        authManager.persistFromTokenResponse(tenant, tokenResponse)

                        isLoggedIn.value = true
                        userInfo.value = "Logged in at ${System.currentTimeMillis()}"
                        errorMessage.value = ""

                        // Only now let the UI proceed (so it won't show Login)
                        isLoading.value = false
                        updateUI()
                    }
                }
            }
        }
    }


    private fun startLoginDynamic() {
        val tenant = currentTenant
        if (tenant == null) {
            errorMessage.value = "Please select an ADL Instance."
            updateUI(); return
        }
        if (!tenant.enabled) {
            errorMessage.value = "${tenant.name} is disabled."
            updateUI(); return
        }

        try {
            isLoading.value = true
            errorMessage.value = ""
            updateUI()

            val serviceConfig = AuthorizationServiceConfiguration(
                tenant.authorizeEndpoint, tenant.tokenEndpoint
            )

            val authRequest = AuthorizationRequest.Builder(
                serviceConfig, tenant.clientId, ResponseTypeValues.CODE, tenant.redirectUri.toUri()
            ).setScopes(*tenant.scopes.toTypedArray()).build()

            currentAuthRequest = authRequest
            authInFlight = true

            // Build success + cancel intents
            val complete = pendingIntent(ACTION_AUTH_COMPLETED, 1001)
            val cancelled = pendingIntent(ACTION_AUTH_CANCELLED, 1002)

            // Launch the browser-based flow
            authService.performAuthorizationRequest(authRequest, complete, cancelled)
        } catch (e: Exception) {
            authInFlight = false
            isLoading.value = false
            errorMessage.value = "Login error: ${e.message}"
            updateUI()
        }
    }


    private fun logout() {
        isLoggedIn.value = false
        userInfo.value = ""
        errorMessage.value = ""
        updateUI()
    }

    private fun updateUI() {
        setContent {
            when {
                errorMessage.value.isNotEmpty() -> {
                    ErrorScreen(error = errorMessage.value) {
                        errorMessage.value = ""
                        loadTenants()
                    }
                }

                isLoading.value -> {
                    LoadingScreen()
                }

                else -> {
                    val nav = rememberNavController()
                    AppNavGraph(
                        nav = nav,
                        startDestination = Route.Splash.route,
                        tenants = tenants.value,
                        selectedTenantId = selectedTenantId.value,
                        isLoggedIn = isLoggedIn.value,
                        authInFlight = authInFlight,
                        onSelectTenant = { id ->
                            selectedTenantId.value = id
                            currentTenant = tenants.value.firstOrNull { it.id == id }
                            lifecycleScope.launch { tenantLocal.saveSelectedTenantId(id) }
                        },
                        onRefreshTenants = { loadTenants(preserveSelection = true) },
                        onLoginClick = { startLoginDynamic() },
                        onLogout = {
                            // Clear local state & navigate back to Login
                            logout()
                            nav.navigate(Route.Login.route) {
                                // Clear the back stack
                                popUpTo(nav.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                                restoreState = false
                            }
                        })

                    // When login completes (or tenant changes while logged in), move to Stations/{tenantId}
                    LaunchedEffect(
                        isLoggedIn.value, selectedTenantId.value
                    ) {
                        val tid = selectedTenantId.value
                        if (isLoggedIn.value && !tid.isNullOrBlank()) {
                            nav.navigate(Route.Stations.build(tid)) {
                                // Remove Login from back stack
                                popUpTo(Route.Login.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            }
        }
    }
}
