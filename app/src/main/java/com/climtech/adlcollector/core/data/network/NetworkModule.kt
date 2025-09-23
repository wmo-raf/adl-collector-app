package com.climtech.adlcollector.core.data.network

import com.climtech.adlcollector.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {

    fun okHttpClient(
        authInterceptor: AuthInterceptor? = null,
        enableLogging: Boolean = BuildConfig.DEBUG
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()

        if (enableLogging) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        if (authInterceptor != null) {
            builder.addInterceptor(authInterceptor)
        }
        return builder.build()
    }

    fun retrofit(client: OkHttpClient, moshi: Moshi = Moshi.Builder().build()): Retrofit {
        return Retrofit.Builder()
            // Base URL is required but unused since we pass absolute @Url; keep placeholder.
            .baseUrl("https://placeholder.invalid/")
            .addConverterFactory(MoshiConverterFactory.create(moshi)).client(client).build()
    }
}