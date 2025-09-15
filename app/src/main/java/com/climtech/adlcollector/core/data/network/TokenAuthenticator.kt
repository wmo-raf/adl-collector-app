package com.climtech.adlcollector.core.data.network

import com.climtech.adlcollector.core.auth.AuthManager
import com.climtech.adlcollector.core.model.TenantConfig
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tenant: TenantConfig,
    private val authManager: AuthManager
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Give up if we've already attempted with an updated header
        val prior = response.request.header("Authorization")
        return try {
            val newToken = kotlinx.coroutines.runBlocking {
                authManager.getValidAccessToken(tenant) // will refresh if needed
            }
            val newHeader = "Bearer $newToken"
            if (prior == newHeader) null // already tried with refreshed token → give up
            else response.request.newBuilder()
                .header("Authorization", newHeader)
                .build()
        } catch (_: Throwable) {
            null // cannot refresh → give up
        }
    }
}