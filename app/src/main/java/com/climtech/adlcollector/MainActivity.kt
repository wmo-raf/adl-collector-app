package com.climtech.adlcollector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
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

        FirebaseApp.initializeApp(this)

        val db = FirebaseFirestore.getInstance()
        db.collection("tenants").get().addOnSuccessListener { docs ->
            Log.i("FIREBASE", "Loaded tenants: ${docs.size()}")
        }.addOnFailureListener { e ->
            Log.e("FIREBASE", "Error fetching tenants", e)
        }

        tenantLocal = TenantLocalStore(this)
        authService = AuthorizationService(this)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (::authService.isInitialized) authService.dispose()
            }
        })

        // Observe saved tenant id
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

        // capture current selection
        val previouslySelectedId = if (preserveSelection) selectedTenantId.value else null

        lifecycleScope.launch {
            try {
                val list = tenantRepo.listTenants()
                val visibleList = list.filter { it.visible } // hide soft-removed tenants
                tenants.value = visibleList

                // Choose selected id: keep previous if still present, otherwise use first (or null)
                selectedTenantId.value = when {
                    previouslySelectedId != null && list.any { it.id == previouslySelectedId } -> previouslySelectedId
                    else -> list.firstOrNull()?.id
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
                tenant.authorizeEndpoint, tenant.tokenEndpoint
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
            runOnUiThread {
                isLoading.value = false
                if (tokenResponse != null) {
                    val accessToken = tokenResponse.accessToken
                    val refreshToken = tokenResponse.refreshToken

                    lifecycleScope.launch {
                        tenantLocal.saveTokens(accessToken, refreshToken)
                    }

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
                    errorMessage.value = ""; loadTenants()
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

                else -> HelloWorldScreen {
                    lifecycleScope.launch { tenantLocal.clearAll() }
                    logout()
                }
            }
        }
    }
}

/* ---------- Composables ---------- */
@Composable
fun LoginScreen(
    tenants: List<TenantConfig>,
    selectedId: String?,
    onSelectTenant: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRefreshTenants: () -> Unit       // 👈 new
) {
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
                "ADL Collector",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (tenants.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val currentName =
                    tenants.firstOrNull { it.id == selectedId }?.name ?: "Select Country"

                // Row with the dropdown "button" and a refresh icon button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(currentName)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            tenants.forEach { t ->
                                DropdownMenuItem(text = { Text(t.name) }, onClick = {
                                    onSelectTenant(t.id)
                                    expanded = false
                                })
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Refresh icon
                    IconButton(onClick = onRefreshTenants) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Refresh,
                            contentDescription = "Refresh tenants"
                        )
                    }
                }

            } else {
                // When there are no tenants yet, still show a refresh option
                Button(onClick = onRefreshTenants, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh Tenants")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = tenants.isNotEmpty() && selectedId != null
            ) { Text("Login") }
        }
    }
}


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

@Composable
fun HelloWorldScreen(onLogout: () -> Unit) {
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
                "Hello World 👋",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text("You are logged in!")
            Spacer(Modifier.height(32.dp))
            Button(onClick = onLogout) { Text("Logout") }
        }
    }
}
