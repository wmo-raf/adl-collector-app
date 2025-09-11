package com.climtech.adlcollector.net

import com.climtech.adlcollector.TenantConfig
import com.climtech.adlcollector.TenantLocalStore
import com.climtech.adlcollector.db.AppDatabase
import com.climtech.adlcollector.db.StationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi

class StationsRepository(
    private val tenantLocal: TenantLocalStore,
    db: AppDatabase
) {
    private val dao = db.stationDao()

    // Retrofit that uses absolute @Url
    private val retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        val moshi = Moshi.Builder().build() // using Moshi code-gen
        Retrofit.Builder()
            .baseUrl("https://placeholder.invalid/") // ignored; we pass full @Url
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
    }
    private val api by lazy { retrofit.create(AdlApi::class.java) }

    /** UI reads this Flow for instant offline data. */
    fun stationsStream(tenantId: String): Flow<List<Station>> =
        dao.observeStations(tenantId).map { list ->
            list.map { Station(id = it.stationId, name = it.name) }
        }

    /** Refresh from network and update cache. Safe to call in background. */
    suspend fun refreshStations(tenant: TenantConfig): Result<Unit> {
        val access = tenantLocal.accessToken.first()
            ?: return Result.failure(IllegalStateException("Not logged in (no access token)"))

        val url = tenant.stationLinkEndpoint.toString()
        val resp: Response<List<Station>> = api.getStations(url, "Bearer $access")

        return if (resp.isSuccessful && resp.body() != null) {
            val now = System.currentTimeMillis()
            val entities = resp.body()!!.map { s ->
                StationEntity(
                    key = "${tenant.id}:${s.id}",
                    tenantId = tenant.id,
                    stationId = s.id,
                    name = s.name,
                    updatedAt = now
                )
            }
            // Replace tenant's cache atomically
            dao.clearForTenant(tenant.id)
            dao.upsertAll(entities)
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "HTTP ${resp.code()}: ${
                        resp.errorBody()?.string() ?: "Unknown error"
                    }"
                )
            )
        }
    }
}
