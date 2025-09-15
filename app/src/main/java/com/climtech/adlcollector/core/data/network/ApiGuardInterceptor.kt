package com.climtech.adlcollector.core.data.network

import okhttp3.Interceptor
import okhttp3.Response

class ApiGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder().header("Accept", "application/json").build()

        val resp = chain.proceed(req)

        // Block HTML masquerading as 200 JSON before it reaches Moshi
        val contentType = resp.header("Content-Type")?.lowercase()
        if (resp.isSuccessful && contentType?.contains("application/json") != true) {
            val peek = resp.peekBody(1024).string().trimStart()
            val looksHtml = contentType?.contains("text/html") == true || peek.startsWith(
                "<!DOCTYPE",
                true
            ) || peek.startsWith("<html", true)
            if (looksHtml) {
                resp.close()
                throw UnexpectedBodyIOException(contentType, peek.take(200))
            }
        }

        return resp
    }
}
