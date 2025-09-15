package com.climtech.adlcollector.core.di

import android.content.Context
import com.climtech.adlcollector.core.auth.AuthManager
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.data.db.AppDatabase
import com.climtech.adlcollector.core.data.network.NetworkModule
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideTenantLocalStore(@ApplicationContext ctx: Context): TenantLocalStore =
        TenantLocalStore(ctx)

    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext ctx: Context, local: TenantLocalStore): AuthManager =
        AuthManager(ctx, local)

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(): OkHttpClient =
        NetworkModule.okHttpClient(
            authInterceptor = null,
            enableLogging = true
        )

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        AppDatabase.get(ctx)
}