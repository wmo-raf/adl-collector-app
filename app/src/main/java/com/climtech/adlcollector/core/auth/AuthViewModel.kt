package com.climtech.adlcollector.core.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.app.MainActivity
import com.climtech.adlcollector.core.model.TenantConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import javax.inject.Inject

data class AuthState(
    val isLoggedIn: Boolean = false,
    val selectedTenant: TenantConfig? = null,
    val authInProgress: Boolean = false,
    val error: String? = null
)

sealed class AuthIntent {
    data object StartLogin : AuthIntent()
    data object Logout : AuthIntent()
    data object ClearError : AuthIntent()
    data class HandleAuthCallback(val intent: Intent) : AuthIntent()
    data class HandleAuthCanceled(val intent: Intent) : AuthIntent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val tenantLocalStore: TenantLocalStore
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var authService: AuthorizationService? = null
    private var currentAuthRequest: AuthorizationRequest? = null

    companion object {
        const val ACTION_AUTH_COMPLETED = "com.climtech.adlcollector.AUTH_COMPLETED"
        const val ACTION_AUTH_CANCELLED = "com.climtech.adlcollector.AUTH_CANCELLED"
    }

    init {
        // Initialize auth service
        authService = AuthorizationService(context)

        // Observe tenant selection and auth state
        viewModelScope.launch {
            combine(
                tenantLocalStore.selectedTenantId,
                tenantLocalStore.selectedTenantId // We'll get the full tenant config
            ) { selectedTenantId, _ ->
                selectedTenantId?.let {
                    tenantLocalStore.getTenantById(it)
                }
            }.collect { tenant ->
                _authState.value = _authState.value.copy(
                    selectedTenant = tenant,
                    isLoggedIn = tenant != null && hasValidTokens(tenant)
                )
            }
        }
    }

    fun handle(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.StartLogin -> startLogin()
            is AuthIntent.Logout -> logout()
            is AuthIntent.ClearError -> clearError()
            is AuthIntent.HandleAuthCallback -> handleAuthCallback(intent.intent)
            is AuthIntent.HandleAuthCanceled -> handleAuthCanceled(intent.intent)
        }
    }

    private fun startLogin() {
        val tenant = _authState.value.selectedTenant
        if (tenant == null) {
            _authState.value = _authState.value.copy(error = "Please select an ADL Instance.")
            return
        }
        if (!tenant.enabled) {
            _authState.value = _authState.value.copy(error = "${tenant.name} is disabled.")
            return
        }

        try {
            _authState.value = _authState.value.copy(
                authInProgress = true,
                error = null
            )

            val serviceConfig = AuthorizationServiceConfiguration(
                tenant.authorizeEndpoint,
                tenant.tokenEndpoint
            )

            val authRequest = AuthorizationRequest.Builder(
                serviceConfig,
                tenant.clientId,
                ResponseTypeValues.CODE,
                tenant.redirectUri.toUri()
            ).setScopes(*tenant.scopes.toTypedArray()).build()

            currentAuthRequest = authRequest

            val completeIntent = createPendingIntent(ACTION_AUTH_COMPLETED, 1001)
            val cancelIntent = createPendingIntent(ACTION_AUTH_CANCELLED, 1002)

            authService?.performAuthorizationRequest(authRequest, completeIntent, cancelIntent)
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Login error", e)
            _authState.value = _authState.value.copy(
                authInProgress = false,
                error = "Login error: ${e.message}"
            )
        }
    }

    private fun handleAuthCallback(intent: Intent) {
        _authState.value = _authState.value.copy(authInProgress = true)

        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            _authState.value = _authState.value.copy(
                authInProgress = false,
                error = "Login failed: ${exception.errorDescription ?: exception.error}"
            )
            return
        }

        if (response == null) {
            _authState.value = _authState.value.copy(
                authInProgress = false,
                error = "Login failed: empty response"
            )
            return
        }

        val tokenRequest = response.createTokenExchangeRequest()
        authService?.performTokenRequest(tokenRequest) { tokenResponse, tokenException ->
            viewModelScope.launch {
                if (tokenException != null || tokenResponse == null) {
                    _authState.value = _authState.value.copy(
                        authInProgress = false,
                        error = "Token exchange failed: ${tokenException?.errorDescription ?: "Unknown error"}"
                    )
                    return@launch
                }

                val tenant = _authState.value.selectedTenant
                if (tenant == null) {
                    _authState.value = _authState.value.copy(
                        authInProgress = false,
                        error = "Missing tenant during token save."
                    )
                    return@launch
                }

                // Persist tokens and tenant config
                authManager.persistFromTokenResponse(tenant, tokenResponse)
                tenantLocalStore.saveTenantConfig(tenant)

                _authState.value = _authState.value.copy(
                    authInProgress = false,
                    isLoggedIn = true,
                    error = null
                )
            }
        }
    }

    private fun handleAuthCanceled(intent: Intent) {
        _authState.value = _authState.value.copy(
            authInProgress = false,
            error = "Login cancelled."
        )
    }

    private fun logout() {
        viewModelScope.launch {
            val tenant = _authState.value.selectedTenant
            if (tenant != null) {
                tenantLocalStore.clearTokensForTenant(tenant.id)
            }
            _authState.value = _authState.value.copy(
                isLoggedIn = false,
                error = null
            )
        }
    }

    private fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }

    private suspend fun hasValidTokens(tenant: TenantConfig): Boolean {
        val accessToken = tenantLocalStore.getAccessToken(tenant.id)
        val expiry = tenantLocalStore.getAccessExpiry(tenant.id)
        val now = System.currentTimeMillis()

        return !accessToken.isNullOrBlank() &&
                expiry != null &&
                expiry > now + 60_000 // 1 minute buffer
    }

    private fun createPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            setAction(action)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun getAuthService(): AuthorizationService? = authService

    override fun onCleared() {
        super.onCleared()
        authService?.dispose()
    }
}