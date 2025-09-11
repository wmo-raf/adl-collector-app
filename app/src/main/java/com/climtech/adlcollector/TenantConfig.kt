package com.climtech.adlcollector

import android.net.Uri
import androidx.core.net.toUri

/**
 * Minimal tenant config sourced from Firestore.
 * All endpoints are derived from baseUrl.
 */
data class TenantConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val clientId: String,
    val enabled: Boolean = true,          // NEW
    val visible: Boolean = true,
    // allow app-level defaults; can be overridden later added to Firestore
    val scopes: List<String> = listOf("adl.read", "adl.write"),
    val redirectUri: String = "com.climtech.adlcollector://oauth2redirect"
) {
    /** Normalized base URI (force https if missing, keep host + any base path, ignore query/fragment). */
    val baseUri: Uri by lazy { normalizeBaseUrl(baseUrl) }

    /** OAuth endpoints (Django OAuth Toolkit style). */
    val authorizeEndpoint: Uri get() = endpoint("o", "authorize")
    val tokenEndpoint: Uri get() = endpoint("o", "token")

    // (Optionals you may enable later)
    val revokeEndpoint: Uri get() = endpoint("o", "revoke")
    val introspectEndpoint: Uri get() = endpoint("o", "introspect")

    // ADL Collector stations for the logged-in user
    val stationLinkEndpoint: Uri
        get() = endpoint(
            "plugins",
            "api",
            "adl-collector",
            "station-link",
            trailingSlash = false
        )

    /**
     * Build a URI under the tenant base, joining path segments safely.
     * trailingSlash=true matches typical Django style (/path/).
     */
    fun endpoint(
        vararg segments: String,
        trailingSlash: Boolean = true,
        query: Map<String, String> = emptyMap()
    ): Uri {
        return buildUnderBase(baseUri, segments, trailingSlash, query)
    }

    /** Same as endpoint() but for your general REST API paths. */
    fun api(
        vararg segments: String,
        trailingSlash: Boolean = true,
        query: Map<String, String> = emptyMap()
    ): Uri {
        return buildUnderBase(baseUri, segments, trailingSlash, query)
    }

    // --- helpers ---

    private fun normalizeBaseUrl(input: String): Uri {
        var s = input.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://$s"
        }
        val u = s.toUri()
        // ensure we keep scheme + authority; path may exist if base is hosted under a sub-path
        return u.buildUpon().fragment(null).query(null).build()
    }

    private fun buildUnderBase(
        base: Uri, segments: Array<out String>, trailingSlash: Boolean, query: Map<String, String>
    ): Uri {
        val b = base.buildUpon()
        // Combine existing base path with new segments
        val combinedPath = joinPaths(base.encodedPath ?: "/", segments, trailingSlash)
        b.encodedPath(combinedPath)
        // Clear and append query
        query.forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build()
    }

    private fun joinPaths(
        basePath: String?, segments: Array<out String>, trailingSlash: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (!basePath.isNullOrBlank()) {
            parts += basePath.trim('/').split('/').filter { it.isNotBlank() }
        }
        segments.forEach { seg ->
            parts += seg.trim('/').split('/').filter { it.isNotBlank() }
        }
        var path = if (parts.isEmpty()) "/" else "/${parts.joinToString("/")}"
        if (trailingSlash && path != "/") path += "/"
        return path
    }
}
