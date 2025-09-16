package com.climtech.adlcollector.feature.observations.data

import com.climtech.adlcollector.core.auth.AuthManager
import com.climtech.adlcollector.core.data.db.AppDatabase
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.data.network.AuthInterceptor
import com.climtech.adlcollector.core.data.network.NetworkModule
import com.climtech.adlcollector.core.data.network.TokenAuthenticator
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.core.util.asResult
import com.climtech.adlcollector.feature.observations.data.net.ObservationPostRequest
import com.climtech.adlcollector.feature.observations.data.net.ObservationPostResponse
import com.climtech.adlcollector.feature.observations.data.net.ObservationsApi
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

open class ObservationsRepository @Inject constructor(
    private val authManager: AuthManager,
    db: AppDatabase,
    private val moshi: Moshi
) {
    private val dao = db.observationDao()
    private val mapAdapter = moshi.adapter<Map<String, Any>>(Map::class.java)

    private fun apiFor(tenant: TenantConfig): ObservationsApi {
        val authInterceptor = AuthInterceptor {
            val token = authManager.getValidAccessToken(tenant)
            tenant to token
        }
        val client = NetworkModule.okHttpClient(
            authInterceptor = authInterceptor,
            enableLogging = true
        ).newBuilder()
            .authenticator(TokenAuthenticator(tenant, authManager))
            .build()
        val retrofit = NetworkModule.retrofit(client)
        return retrofit.create(ObservationsApi::class.java)
    }

    fun streamForStation(tenantId: String, stationId: Long): Flow<List<ObservationEntity>> =
        dao.streamForStation(tenantId, stationId)

    suspend fun queueSubmit(
        tenant: TenantConfig,
        stationId: Long,
        stationName: String,
        timezone: String,
        scheduleMode: String,
        obsIsoUtc: String,
        payloadJson: String,
        late: Boolean,
        locked: Boolean
    ): Result<Unit> {
        val obsInstant = java.time.Instant.parse(obsIsoUtc)
        val key = "${tenant.id}:$stationId:${obsInstant.toEpochMilli()}"
        val now = System.currentTimeMillis()
        val entity = ObservationEntity(
            obsKey = key,
            tenantId = tenant.id,
            stationId = stationId,
            stationName = stationName,
            timezone = timezone,
            obsTimeUtcMs = obsInstant.toEpochMilli(),
            createdAtMs = now,
            updatedAtMs = now,
            late = late,
            locked = locked,
            scheduleMode = scheduleMode,
            payloadJson = payloadJson,
            status = ObservationEntity.SyncStatus.QUEUED
        )
        dao.upsert(entity)
        return Result.Ok(Unit)
    }

    suspend fun tryUploadBatch(
        tenant: TenantConfig,
        endpointUrl: String,
        maxItems: Int = 10
    ): Boolean {
        val pending = dao.nextPending(tenant.id, maxItems)
        if (pending.isEmpty()) return false

        val api = apiFor(tenant)
        var progressed = false

        for (item in pending) {
            progressed = true
            dao.markStatus(
                item.obsKey,
                ObservationEntity.SyncStatus.UPLOADING,
                null,
                System.currentTimeMillis()
            )

            // --- refactor: avoid continue inside inline lambda ---
            val payload = toPostPayload(item)
            if (payload == null) {
                dao.markStatus(
                    item.obsKey,
                    ObservationEntity.SyncStatus.FAILED,
                    "Invalid payloadJson (expected 'records' + 'metadata')",
                    System.currentTimeMillis()
                )
                continue
            }
            // -----------------------------------------------------

            val res: Result<ObservationPostResponse> =
                api.submitObservation(endpointUrl, payload).asResult()
            when (res) {
                is Result.Ok -> dao.markSynced(
                    item.obsKey,
                    res.value.id,
                    System.currentTimeMillis()
                )

                is Result.Err -> dao.markStatus(
                    item.obsKey,
                    ObservationEntity.SyncStatus.FAILED,
                    res.error.message,
                    System.currentTimeMillis()
                )
            }
        }
        return progressed
    }

    private fun toPostPayload(entity: ObservationEntity): ObservationPostRequest? {
        @Suppress("UNCHECKED_CAST")
        val parsed = runCatching { mapAdapter.fromJson(entity.payloadJson) as Map<String, Any> }
            .getOrNull() ?: return null

        val recordsAny = parsed["records"] as? List<*> ?: return null
        val records = recordsAny.mapNotNull { row ->
            val m = row as? Map<*, *> ?: return@mapNotNull null
            val id = (m["variable_mapping_id"] as? Number)?.toLong() ?: return@mapNotNull null
            val valueNum = (m["value"] as? Number)?.toDouble() ?: return@mapNotNull null
            ObservationPostRequest.Record(
                variable_mapping_id = id,
                value = valueNum
            )
        }
        if (records.isEmpty()) return null

        val metadataMap = parsed["metadata"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val dup = metadataMap["duplicate_policy"] as? String
        val reason = metadataMap["reason"] as? String
        val appVersion = (metadataMap["app_version"] as? String) ?: "0.1.0"

        val idempotencyKey = java.util.UUID
            .nameUUIDFromBytes(entity.obsKey.toByteArray())
            .toString()

        val submissionIso = java.time.Instant.ofEpochMilli(entity.updatedAtMs).toString()
        val observationIso = java.time.Instant.ofEpochMilli(entity.obsTimeUtcMs).toString()

        return ObservationPostRequest(
            idempotency_key = idempotencyKey,
            submission_time = submissionIso,
            observation_time = observationIso,
            station_link_id = entity.stationId,
            records = records,
            metadata = ObservationPostRequest.Metadata(
                late = entity.late,                    // authoritative late flag from entity
                duplicate_policy = dup,
                reason = reason,
                app_version = appVersion
            )
        )
    }
}
