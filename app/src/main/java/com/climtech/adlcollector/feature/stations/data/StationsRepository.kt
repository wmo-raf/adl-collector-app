package com.climtech.adlcollector.feature.stations.data


import com.climtech.adlcollector.core.auth.AuthManager
import com.climtech.adlcollector.core.data.db.AppDatabase
import com.climtech.adlcollector.core.data.db.StationEntity
import com.climtech.adlcollector.core.data.network.ApiGuardInterceptor
import com.climtech.adlcollector.core.data.network.AuthInterceptor
import com.climtech.adlcollector.core.data.network.NetworkModule
import com.climtech.adlcollector.core.data.network.TokenAuthenticator
import com.climtech.adlcollector.core.data.network.UnexpectedBodyIOException
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.net.NetworkException
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.core.util.asResult
import com.climtech.adlcollector.core.util.retryNetwork
import com.climtech.adlcollector.feature.stations.data.net.AdlApi
import com.climtech.adlcollector.feature.stations.data.net.Station
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
            authInterceptor = authInterceptor, enableLogging = true
        ).newBuilder().addInterceptor(ApiGuardInterceptor())
            .authenticator(TokenAuthenticator(tenant, authManager)).build()

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

        val res: Result<List<Station>> = retryNetwork {
            try {
                // --- normalize 204 / null body to empty list ---
                val raw = api.getStations(url)
                val normalized =
                    if (raw.isSuccessful && (raw.code() == 204 || raw.body() == null)) {
                        retrofit2.Response.success(emptyList<Station>())
                    } else raw

                normalized.asResult()
            } catch (e: UnexpectedBodyIOException) {
                Result.Err(NetworkException.UnexpectedBody(e.mime, e.snippet))
            } catch (e: UnknownHostException) {
                Result.Err(NetworkException.Offline)
            } catch (e: SocketTimeoutException) {
                Result.Err(NetworkException.Timeout)
            } catch (e: JsonEncodingException) {
                Result.Err(NetworkException.Serialization(e))
            } catch (e: JsonDataException) {
                Result.Err(NetworkException.Serialization(e))
            } catch (e: IOException) {
                Result.Err(NetworkException.Unknown(e))
            } catch (e: Throwable) {
                Result.Err(NetworkException.Unknown(e))
            }
        }

        return when (res) {
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
                // --- transactional replace (clears when list is empty) ---
                dao.replaceForTenant(tenant.id, entities)
                Result.Ok(Unit)
            }

            is Result.Err -> Result.Err(res.error) // keep old cache on error
        }
    }
}