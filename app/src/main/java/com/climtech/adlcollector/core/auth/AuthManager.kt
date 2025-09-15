package com.climtech.adlcollector.core.auth

import android.content.Context
import com.climtech.adlcollector.core.model.TenantConfig
import kotlinx.coroutines.flow.first

/**
 * Central place to get/refresh tokens.
 * - Phase 1: just return stored access token (what your repo does today).
 * - Phase 2 (TODO): use AppAuth to refresh when expired/missing.
 */
class AuthManager(
    private val appContext: Context, private val localStore: TenantLocalStore
) {
    /**
     * Return a valid access token for the current session.
     * Throws if missing (caller should route to login).
     */
    suspend fun getValidAccessToken(tenant: TenantConfig): String {
        // TODO: If you store expiry time, check and refresh here via AppAuth.
        val access = localStore.accessToken.first()
        return access ?: throw IllegalStateException("Not logged in (no access token)")
    }

    /** expose refresh token */
    suspend fun getRefreshToken(): String? = localStore.refreshToken.first()
}
