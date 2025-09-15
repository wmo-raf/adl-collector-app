package com.climtech.adlcollector.core.auth

import android.content.Context
import com.climtech.adlcollector.core.model.TenantConfig
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
    private val appContext: Context,
    private val localStore: TenantLocalStore
) {
    private val refreshMutex = Mutex()

    companion object {
        private const val SKEW_MS = 60_000L
        private const val DEFAULT_LIFETIME_MS = 3_600_000L
    }

    /** Persist tokens (and expiry) for a specific tenant after code exchange. */
    suspend fun persistFromTokenResponse(tenant: TenantConfig, response: TokenResponse) {
        val access = response.accessToken
        val existingRefresh = localStore.getRefreshToken(tenant.id)
        val refresh = response.refreshToken ?: existingRefresh
        val exp =
            response.accessTokenExpirationTime ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
        localStore.saveTokens(tenant.id, access, refresh, exp)
    }

    /** Returns a valid (possibly refreshed) access token for the tenant. */
    suspend fun getValidAccessToken(tenant: TenantConfig): String {
        val now = System.currentTimeMillis()
        val access = localStore.getAccessToken(tenant.id)
        val expiry = localStore.getAccessExpiry(tenant.id)

        if (access.isNullOrBlank() || expiry == null || expiry - SKEW_MS <= now) {
            refreshMutex.withLock {
                val latestAccess = localStore.getAccessToken(tenant.id)
                val latestExpiry = localStore.getAccessExpiry(tenant.id)
                val stillExpiring = latestAccess.isNullOrBlank() || latestExpiry == null ||
                        latestExpiry - SKEW_MS <= System.currentTimeMillis()
                if (stillExpiring) refreshAccessToken(tenant)
            }
        }
        return localStore.getAccessToken(tenant.id)
            ?: throw IllegalStateException("Not logged in (no access token for ${tenant.id})")
    }

    private suspend fun refreshAccessToken(tenant: TenantConfig) {
        val refresh = localStore.getRefreshToken(tenant.id)
            ?: throw IllegalStateException("Missing refresh token for ${tenant.id}; please log in again.")

        val service = AuthorizationService(appContext)
        try {
            val serviceConfig = AuthorizationServiceConfiguration(
                tenant.authorizeEndpoint, tenant.tokenEndpoint
            )
            val request = TokenRequest.Builder(serviceConfig, tenant.clientId)
                .setGrantType(GrantTypeValues.REFRESH_TOKEN)
                .setRefreshToken(refresh)
                .build()

            val response = performTokenRequest(service, request)
            val newAccess = response.accessToken
            val newRefresh = response.refreshToken ?: refresh
            val exp = response.accessTokenExpirationTime
                ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
            localStore.saveTokens(tenant.id, newAccess, newRefresh, exp)
        } finally {
            service.dispose()
        }
    }

    private suspend fun performTokenRequest(
        service: AuthorizationService,
        request: TokenRequest
    ): TokenResponse = suspendCancellableCoroutine { cont ->
        service.performTokenRequest(request) { resp, ex ->
            if (ex != null) cont.resumeWithException(ex)
            else if (resp != null) cont.resume(resp)
            else cont.resumeWithException(IllegalStateException("Null TokenResponse"))
        }
    }
}
