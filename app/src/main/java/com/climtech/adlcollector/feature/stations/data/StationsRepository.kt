package com.climtech.adlcollector.feature.stations.data

import com.climtech.adlcollector.core.auth.AuthManager
import com.climtech.adlcollector.core.data.db.AppDatabase
import com.climtech.adlcollector.core.data.db.StationEntity
import com.climtech.adlcollector.core.data.network.AuthInterceptor
import com.climtech.adlcollector.core.data.network.NetworkModule
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.core.util.asResult
import com.climtech.adlcollector.feature.stations.data.net.AdlApi
import com.climtech.adlcollector.feature.stations.data.net.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StationsRepository @Inject constructor(
    private val authManager: AuthManager, db: AppDatabase
) {
    private val dao = db.stationDao()

    // Build an API client whose interceptor will fetch a valid token for the given tenant.
    private fun apiFor(tenant: TenantConfig): AdlApi {
        val authInterceptor = AuthInterceptor {
            val token = authManager.getValidAccessToken(tenant)
            tenant to token
        }

        val client = NetworkModule.okHttpClient(
            authInterceptor = authInterceptor
        )
        val retrofit = NetworkModule.retrofit(client)
        return retrofit.create(AdlApi::class.java)
    }

    /** UI reads this Flow for instant offline data. */
    fun stationsStream(tenantId: String): Flow<List<Station>> =
        dao.observeStations(tenantId).map { list ->
            list.map { Station(id = it.stationId, name = it.name) }
        }

    /** Refresh from network and update cache. Safe to call in background. */
    suspend fun refreshStations(tenant: TenantConfig): Result<Unit> {
        val url = tenant.stationLinkEndpoint.toString()
        val api = apiFor(tenant)

        return when (val res = api.getStations(url).asResult()) {
            is Result.Ok -> {
                val now = System.currentTimeMillis()
                val entities = res.value.map { s ->
                    StationEntity(
                        key = "${tenant.id}:${s.id}",
                        tenantId = tenant.id,
                        stationId = s.id,
                        name = s.name,
                        updatedAt = now
                    )
                }
                dao.clearForTenant(tenant.id)
                dao.upsertAll(entities)
                Result.Ok(Unit)
            }

            is Result.Err -> Result.Err(res.error)
        }
    }
}