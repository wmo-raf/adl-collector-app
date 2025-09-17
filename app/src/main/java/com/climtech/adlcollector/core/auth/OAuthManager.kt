package com.climtech.adlcollector.core.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.climtech.adlcollector.core.model.TenantConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import javax.inject.Inject
import javax.inject.Singleton

data class OAuthState(
    val isInProgress: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val userInfo: String = ""
)

sealed class OAuthResult {
    object Success : OAuthResult()
    data class Error(val message: String) : OAuthResult()
    object Cancelled : OAuthResult()
}

@Singleton
class OAuthManager @Inject constructor(
    private val authManager: AuthManager, private val tenantLocalStore: TenantLocalStore
) {
    companion object {
        const val ACTION_AUTH_COMPLETED = "com.climtech.adlcollector.AUTH_COMPLETED"
        const val ACTION_AUTH_CANCELLED = "com.climtech.adlcollector.AUTH_CANCELLED"
    }

    private val _state = MutableStateFlow(OAuthState())
    val state: StateFlow<OAuthState> = _state.asStateFlow()

    private var authService: AuthorizationService? = null
    private var currentTenant: TenantConfig? = null
    private var currentAuthRequest: AuthorizationRequest? = null

    fun initialize(context: Context) {
        authService?.dispose()
        authService = AuthorizationService(context)
        Log.i("OAuthManager", "Initialized OAuth service")
    }

    fun dispose() {
        authService?.dispose()
        authService = null
    }

    fun startLogin(context: Context, tenant: TenantConfig): Boolean {
        if (tenant.name.isBlank() || !tenant.enabled) {
            _state.value = _state.value.copy(error = "Please select a valid ADL Instance.")
            return false
        }

        return try {
            // Clear any previous errors and start login
            _state.value = _state.value.copy(
                isInProgress = true, error = null, isLoggedIn = false  // Reset login state
            )
            currentTenant = tenant

            val serviceConfig = AuthorizationServiceConfiguration(
                tenant.authorizeEndpoint, tenant.tokenEndpoint
            )

            val authRequest = AuthorizationRequest.Builder(
                serviceConfig, tenant.clientId, ResponseTypeValues.CODE, tenant.redirectUri.toUri()
            ).setScopes(*tenant.scopes.toTypedArray()).build()

            currentAuthRequest = authRequest

            val completeIntent = createPendingIntent(context, ACTION_AUTH_COMPLETED, 1001)
            val cancelIntent = createPendingIntent(context, ACTION_AUTH_CANCELLED, 1002)

            authService?.performAuthorizationRequest(authRequest, completeIntent, cancelIntent)
            true
        } catch (e: Exception) {
            Log.e("OAuthManager", "Login error", e)
            _state.value =
                _state.value.copy(isInProgress = false, error = "Login error: ${e.message}")
            false
        }
    }

    suspend fun handleAuthCompleted(intent: Intent): OAuthResult {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            val errorMsg = "Login failed: ${exception.errorDescription ?: exception.error}"
            _state.value = _state.value.copy(isInProgress = false, error = errorMsg)
            return OAuthResult.Error(errorMsg)
        }

        if (response == null) {
            val errorMsg = "Login failed: empty response"
            _state.value = _state.value.copy(isInProgress = false, error = errorMsg)
            return OAuthResult.Error(errorMsg)
        }

        return try {
            val tokenRequest = response.createTokenExchangeRequest()
            val tokenResult = performTokenExchange(tokenRequest)

            when (tokenResult) {
                is TokenResult.Success -> {
                    val tenant = currentTenant
                        ?: return OAuthResult.Error("Missing tenant during token save")

                    authManager.persistFromTokenResponse(tenant, tokenResult.response)
                    tenantLocalStore.saveTenantConfig(tenant)

                    _state.value = _state.value.copy(
                        isInProgress = false,
                        isLoggedIn = true,
                        userInfo = "Logged in at ${System.currentTimeMillis()}",
                        error = null
                    )
                    OAuthResult.Success
                }

                is TokenResult.Error -> {
                    _state.value =
                        _state.value.copy(isInProgress = false, error = tokenResult.message)
                    OAuthResult.Error(tokenResult.message)
                }
            }
        } catch (e: Exception) {
            val errorMsg = "Token exchange failed: ${e.message}"
            _state.value = _state.value.copy(isInProgress = false, error = errorMsg)
            OAuthResult.Error(errorMsg)
        }
    }

    fun handleAuthCancelled(): OAuthResult {
        _state.value = _state.value.copy(isInProgress = false, error = "Login cancelled")
        return OAuthResult.Cancelled
    }

    fun logout() {
        _state.value = _state.value.copy(
            isLoggedIn = false, userInfo = "", error = null, isInProgress = false
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun performTokenExchange(tokenRequest: TokenRequest): TokenResult {
        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                authService?.performTokenRequest(tokenRequest) { tokenResponse, tokenEx ->
                    when {
                        tokenEx != null -> {
                            val errorMsg =
                                "Token exchange failed: ${tokenEx.errorDescription ?: tokenEx.error ?: "Unknown error"}"
                            continuation.resumeWith(Result.success(TokenResult.Error(errorMsg)))
                        }

                        tokenResponse != null -> {
                            continuation.resumeWith(Result.success(TokenResult.Success(tokenResponse)))
                        }

                        else -> {
                            continuation.resumeWith(Result.success(TokenResult.Error("Unknown token exchange error")))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            TokenResult.Error("Token exchange exception: ${e.message}")
        }
    }

    private fun createPendingIntent(
        context: Context, action: String, requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, context.javaClass).apply {
            this.action = action
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private sealed class TokenResult {
        data class Success(val response: net.openid.appauth.TokenResponse) : TokenResult()
        data class Error(val message: String) : TokenResult()
    }
}