package com.climtech.adlcollector.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.climtech.adlcollector.tenantDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TenantLocalStore(private val context: Context) {

    companion object {
        private val TENANT_ID_KEY = stringPreferencesKey("selected_tenant_id")
    }

    // --- dynamic per-tenant keys ---
    private fun accessKey(tenantId: String) = stringPreferencesKey("access_token_$tenantId")
    private fun refreshKey(tenantId: String) = stringPreferencesKey("refresh_token_$tenantId")
    private fun expiryKey(tenantId: String) = longPreferencesKey("access_token_expiry_$tenantId")

    // --- selected tenant ---
    suspend fun saveSelectedTenantId(id: String) {
        context.tenantDataStore.edit { it[TENANT_ID_KEY] = id }
    }

    val selectedTenantId: Flow<String?> = context.tenantDataStore.data.map { it[TENANT_ID_KEY] }

    // --- write/clear per-tenant tokens ---
    suspend fun saveTokens(
        tenantId: String,
        access: String?,
        refresh: String?,
        expiresAtMs: Long?
    ) {
        context.tenantDataStore.edit { prefs ->
            if (access != null) prefs[accessKey(tenantId)] = access else prefs.remove(
                accessKey(
                    tenantId
                )
            )
            if (refresh != null) prefs[refreshKey(tenantId)] = refresh else prefs.remove(
                refreshKey(
                    tenantId
                )
            )
            if (expiresAtMs != null) prefs[expiryKey(tenantId)] = expiresAtMs else prefs.remove(
                expiryKey(tenantId)
            )
        }
    }

    suspend fun clearTokensForTenant(tenantId: String) {
        context.tenantDataStore.edit { prefs ->
            prefs.remove(accessKey(tenantId))
            prefs.remove(refreshKey(tenantId))
            prefs.remove(expiryKey(tenantId))
        }
    }

    suspend fun clearAll() {
        context.tenantDataStore.edit { it.clear() }
    }

    // --- read per-tenant tokens ---
    fun accessTokenFlow(tenantId: String): Flow<String?> =
        context.tenantDataStore.data.map { it[accessKey(tenantId)] }

    fun refreshTokenFlow(tenantId: String): Flow<String?> =
        context.tenantDataStore.data.map { it[refreshKey(tenantId)] }

    fun accessTokenExpiryFlow(tenantId: String): Flow<Long?> =
        context.tenantDataStore.data.map { it[expiryKey(tenantId)] }

    // handy suspend getters
    suspend fun getAccessToken(tenantId: String): String? = accessTokenFlow(tenantId).first()
    suspend fun getRefreshToken(tenantId: String): String? = refreshTokenFlow(tenantId).first()
    suspend fun getAccessExpiry(tenantId: String): Long? = accessTokenExpiryFlow(tenantId).first()
}