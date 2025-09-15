package com.climtech.adlcollector.core.auth

import android.content.Context
import com.climtech.adlcollector.core.model.TenantConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthManager(
    private val appContext: Context, private val localStore: TenantLocalStore
) {
    private val refreshMutex = Mutex()

    companion object {
        private const val SKEW_MS = 60_000L  // refresh 1 min early
        private const val DEFAULT_LIFETIME_MS = 3_600_000L // fallback 1h if server omits exp
    }

    /** Called from UI after a successful token exchange to persist tokens + expiry. */
    suspend fun persistFromTokenResponse(response: TokenResponse) {
        val access = response.accessToken
        val refresh = response.refreshToken ?: localStore.refreshToken.first()
        val exp =
            response.accessTokenExpirationTime ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
        localStore.saveTokens(access, refresh, exp)
    }

    /** Get a valid (possibly refreshed) access token for API calls. */
    suspend fun getValidAccessToken(tenant: TenantConfig): String {
        val now = System.currentTimeMillis()
        val access = localStore.accessToken.first()
        val expiry = localStore.accessTokenExpiry.first()

        // If token is missing or expiring soon, refresh under a mutex
        if (access.isNullOrBlank() || expiry == null || expiry - SKEW_MS <= now) {
            refreshMutex.withLock {
                val latestAccess = localStore.accessToken.first()
                val latestExpiry = localStore.accessTokenExpiry.first()
                val stillExpiring =
                    latestAccess.isNullOrBlank() || latestExpiry == null || latestExpiry - SKEW_MS <= System.currentTimeMillis()
                if (stillExpiring) {
                    refreshAccessToken(tenant)
                }
            }
        }
        return localStore.accessToken.first()
            ?: throw IllegalStateException("Not logged in (no access token)")
    }

    private suspend fun refreshAccessToken(tenant: TenantConfig) {
        val refresh = localStore.refreshToken.first()
            ?: throw IllegalStateException("Missing refresh token; please log in again.")

        val service = AuthorizationService(appContext)
        try {
            // Build the service config from your tenant endpoints
            val serviceConfig = AuthorizationServiceConfiguration(
                tenant.authorizeEndpoint, tenant.tokenEndpoint
            )

            // Build a refresh_token request
            val request = TokenRequest.Builder(
                serviceConfig, tenant.clientId
            ).setGrantType(GrantTypeValues.REFRESH_TOKEN).setRefreshToken(refresh)
                // .setScopes(*tenant.scopes.toTypedArray()) // optional for refresh
                .build()

            val response = performTokenRequest(service, request)

            val newAccess = response.accessToken
            val newRefresh = response.refreshToken ?: refresh
            val exp = response.accessTokenExpirationTime
                ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)

            localStore.saveTokens(newAccess, newRefresh, exp)
        } finally {
            service.dispose()
        }
    }

    /** Suspend wrapper for AppAuth token request. */
    private suspend fun performTokenRequest(
        service: AuthorizationService, request: TokenRequest
    ): TokenResponse = suspendCancellableCoroutine { cont ->
        service.performTokenRequest(request) { resp, ex ->
            if (ex != null) cont.resumeWithException(ex)
            else if (resp != null) cont.resume(resp)
            else cont.resumeWithException(IllegalStateException("Null TokenResponse"))
        }
    }
}
