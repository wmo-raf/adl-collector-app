package com.climtech.adlcollector.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.climtech.adlcollector.tenantDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class TenantLocalStore(private val context: Context) {

    companion object {
        private val TENANT_ID_KEY = stringPreferencesKey("selected_tenant_id")
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")

        private val ACCESS_TOKEN_EXPIRY_KEY = longPreferencesKey("access_token_expiry")
    }

    suspend fun saveSelectedTenantId(id: String) {
        context.tenantDataStore.edit { it[TENANT_ID_KEY] = id }
    }

    val selectedTenantId: Flow<String?> = context.tenantDataStore.data.map { it[TENANT_ID_KEY] }

    suspend fun saveTokens(access: String?, refresh: String?, expiresAtMs: Long?) {
        context.tenantDataStore.edit { prefs ->
            if (access != null) prefs[ACCESS_TOKEN_KEY] = access else prefs.remove(ACCESS_TOKEN_KEY)
            if (refresh != null) prefs[REFRESH_TOKEN_KEY] = refresh else prefs.remove(
                REFRESH_TOKEN_KEY
            )
            if (expiresAtMs != null) prefs[ACCESS_TOKEN_EXPIRY_KEY] = expiresAtMs else prefs.remove(
                ACCESS_TOKEN_EXPIRY_KEY
            )
        }
    }

    val accessToken: Flow<String?> = context.tenantDataStore.data.map { it[ACCESS_TOKEN_KEY] }
    val refreshToken: Flow<String?> = context.tenantDataStore.data.map { it[REFRESH_TOKEN_KEY] }

    val accessTokenExpiry: Flow<Long?> =
        context.tenantDataStore.data.map { it[ACCESS_TOKEN_EXPIRY_KEY] }

    suspend fun clearAll() {
        context.tenantDataStore.edit { it.clear() }
    }
}