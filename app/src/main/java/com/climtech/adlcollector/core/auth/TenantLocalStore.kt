package com.climtech.adlcollector.core.auth


import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.tenantDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TenantLocalStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val TENANT_ID_KEY = stringPreferencesKey("selected_tenant_id")
        private fun accessKey(tenantId: String) = stringPreferencesKey("access_token_$tenantId")
        private fun refreshKey(tenantId: String) = stringPreferencesKey("refresh_token_$tenantId")
        private fun expiryKey(tenantId: String) =
            longPreferencesKey("access_token_expiry_$tenantId")

        private fun tenantConfigKey(tenantId: String) = stringPreferencesKey("tenant_cfg_$tenantId")
    }

    // --- Moshi for (de)serializing TenantConfig ---
    private val moshi: Moshi = Moshi.Builder().build()
    private val tenantAdapter = moshi.adapter(TenantConfig::class.java)

    // ----------------------------
    // Selected tenant id
    // ----------------------------
    suspend fun saveSelectedTenantId(id: String) {
        context.tenantDataStore.edit { it[TENANT_ID_KEY] = id }
    }

    val selectedTenantId: Flow<String?> = context.tenantDataStore.data.map { it[TENANT_ID_KEY] }

    suspend fun getActiveTenantId(): String? = selectedTenantId.first()

    // ----------------------------
    // Per-tenant tokens
    // ----------------------------
    suspend fun saveTokens(
        tenantId: String, access: String?, refresh: String?, expiresAtMs: Long?
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

    fun accessTokenFlow(tenantId: String): Flow<String?> =
        context.tenantDataStore.data.map { it[accessKey(tenantId)] }

    fun refreshTokenFlow(tenantId: String): Flow<String?> =
        context.tenantDataStore.data.map { it[refreshKey(tenantId)] }

    fun accessTokenExpiryFlow(tenantId: String): Flow<Long?> =
        context.tenantDataStore.data.map { it[expiryKey(tenantId)] }

    suspend fun getAccessToken(tenantId: String): String? = accessTokenFlow(tenantId).first()
    suspend fun getRefreshToken(tenantId: String): String? = refreshTokenFlow(tenantId).first()
    suspend fun getAccessExpiry(tenantId: String): Long? = accessTokenExpiryFlow(tenantId).first()

    /**
     * Persist the full TenantConfig so workers (and offline code) can resolve it by ID.
     * called right after the user selects/logs into a tenant.
     */
    suspend fun saveTenantConfig(tenant: TenantConfig) {
        val json = tenantAdapter.toJson(tenant)
        context.tenantDataStore.edit { prefs ->
            prefs[tenantConfigKey(tenant.id)] = json
        }
    }

    /**
     * Load a TenantConfig by ID (returns null if not stored).
     */
    suspend fun getTenantById(id: String): TenantConfig? {
        val json = context.tenantDataStore.data.map { it[tenantConfigKey(id)] }.first()

        return json?.let { runCatching { tenantAdapter.fromJson(it) }.getOrNull() }
    }

    /**
     * Return the active tenant (selected id -> config).
     */
    suspend fun getActiveTenant(): TenantConfig? {
        val id = getActiveTenantId() ?: return null
        return getTenantById(id)
    }
}
