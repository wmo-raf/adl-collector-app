package com.climtech.adlcollector.core.data.network

import com.climtech.adlcollector.core.model.TenantConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds OAuth2 Bearer token for the current tenant.
 * Caller must provide the TenantConfig when building the Retrofit for that feature call.
 */
class AuthInterceptor(
    private val authManagerProvider: suspend () -> Pair<TenantConfig, String>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // We need a blocking call to fetch token in an OkHttp interceptor.
        val (tenant, token) = runCatching {
            kotlinx.coroutines.runBlocking { authManagerProvider() }
        }.getOrElse { throw it }

        val request = chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        return chain.proceed(request)
    }
}
