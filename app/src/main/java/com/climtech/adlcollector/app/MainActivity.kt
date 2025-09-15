package com.climtech.adlcollector.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.climtech.adlcollector.core.auth.AuthManager
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.data.db.AppDatabase
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.login.data.TenantRepository
import com.climtech.adlcollector.feature.login.ui.LoginScreen
import com.climtech.adlcollector.feature.stations.data.StationsRepository
import com.climtech.adlcollector.feature.stations.presentation.StationsViewModel
import com.climtech.adlcollector.feature.stations.ui.StationsScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest

class MainActivity : ComponentActivity() {

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

        // Handle possible redirect
        intent?.let { handleIntent(it) }
        updateUI()
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

                // ✅ use visibleList for checks and assignment
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
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            Log.i("OAUTH_APP", "OAuth redirect detected: ${intent.data}")
            handleAuthorizationRedirectUri(intent.data!!)
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
                tenant.authorizeEndpoint, // Uri from TenantConfig
                tenant.tokenEndpoint      // Uri from TenantConfig
            )

            val authRequest = AuthorizationRequest.Builder(
                serviceConfig, tenant.clientId, ResponseTypeValues.CODE, tenant.redirectUri.toUri()
            ).setScopes(*tenant.scopes.toTypedArray()).build()

            currentAuthRequest = authRequest
            val intent = authService.getAuthorizationRequestIntent(authRequest)
            startActivity(intent)
        } catch (e: Exception) {
            isLoading.value = false
            errorMessage.value = "Login error: ${e.message}"
            updateUI()
        }
    }

    private fun handleAuthorizationRedirectUri(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val launchedRequest = currentAuthRequest
        val tenant = currentTenant

        if (code.isNullOrBlank() || launchedRequest == null || tenant == null) {
            errorMessage.value = "Auth flow incomplete. Please try again."
            updateUI(); return
        }
        if (state != launchedRequest.state) {
            errorMessage.value = "Security error: state mismatch"
            updateUI(); return
        }

        val tokenRequest = TokenRequest.Builder(
            launchedRequest.configuration, tenant.clientId
        ).setGrantType(GrantTypeValues.AUTHORIZATION_CODE).setAuthorizationCode(code)
            .setRedirectUri(tenant.redirectUri.toUri())
            .setCodeVerifier(launchedRequest.codeVerifier).build()

        isLoading.value = true
        updateUI()

        authService.performTokenRequest(tokenRequest) { tokenResponse, ex ->
            lifecycleScope.launch {
                isLoading.value = false

                if (tokenResponse != null) {
                    val accessToken = tokenResponse.accessToken
                    val refreshToken = tokenResponse.refreshToken

                    // Wait for DataStore commit BEFORE navigating to the stations screen
                    tenantLocal.saveTokens(accessToken, refreshToken)

                    isLoggedIn.value = true
                    userInfo.value = "Logged in at ${System.currentTimeMillis()}"
                    errorMessage.value = ""
                    updateUI()
                } else {
                    errorMessage.value = "Token exchange failed: ${ex?.message ?: "Unknown error"}"
                    updateUI()
                }
            }
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
                errorMessage.value.isNotEmpty() -> ErrorScreen(errorMessage.value) {
                    errorMessage.value = ""
                    loadTenants()
                }

                isLoading.value -> LoadingScreen()

                !isLoggedIn.value -> {
                    LoginScreen(
                        tenants = tenants.value,
                        selectedId = selectedTenantId.value,
                        onSelectTenant = { id ->
                            selectedTenantId.value = id
                            currentTenant = tenants.value.firstOrNull { it.id == id }
                            lifecycleScope.launch { tenantLocal.saveSelectedTenantId(id) }
                        },
                        onLoginClick = { startLoginDynamic() },
                        onRefreshTenants = { loadTenants(preserveSelection = true) })
                }

                else -> {
                    val t = currentTenant
                    if (t == null) {
                        ErrorScreen("Missing tenant selection") { isLoggedIn.value = false }
                    } else {
                        val db = remember { AppDatabase.get(this) }
                        val auth = remember { AuthManager(this, tenantLocal) }
                        val repo = remember(t.id) { StationsRepository(auth, db) }
                        val vm = remember(t.id) { StationsViewModel(repo) }
                        StationsScreen(tenant = t, viewModel = vm, onLogout = { logout() })
                    }
                }
            }
        }
    }
}

/* ---------- Composables ---------- */


@Composable
fun LoadingScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Working…")
        }
    }
}

@Composable
fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Error",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text(text = error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
