package com.climtech.adlcollector.feature.observations.data

import android.util.Log
import com.climtech.adlcollector.core.auth.AuthManager
import com.climtech.adlcollector.core.data.db.AppDatabase
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.data.network.AuthInterceptor
import com.climtech.adlcollector.core.data.network.NetworkModule
import com.climtech.adlcollector.core.data.network.TokenAuthenticator
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.net.NetworkException
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.core.util.asResult
import com.climtech.adlcollector.feature.observations.data.net.ObservationPostRequest
import com.climtech.adlcollector.feature.observations.data.net.ObservationPostResponse
import com.climtech.adlcollector.feature.observations.data.net.ObservationsApi
import com.climtech.adlcollector.feature.observations.sync.UploadBatchResult
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class UploadAttemptResult(
    val success: Boolean,
    val isPermanentFailure: Boolean,
    val errorMessage: String? = null,
    val serverResponse: ObservationPostResponse? = null
)

class ObservationsRepository @Inject constructor(
    private val authManager: AuthManager, db: AppDatabase, private val moshi: Moshi
) {
    private val dao = db.observationDao()
    private val mapAdapter = moshi.adapter<Map<String, Any>>(Map::class.java)

    companion object {
        private const val TAG = "ObservationsRepository"
        private const val DEFAULT_BATCH_SIZE = 10
        private const val MAX_BATCH_SIZE = 50
    }

    private fun apiFor(tenant: TenantConfig): ObservationsApi {
        val authInterceptor = AuthInterceptor {
            val token = authManager.getValidAccessToken(tenant)
            tenant to token
        }
        val client = NetworkModule.okHttpClient(
            authInterceptor = authInterceptor, enableLogging = true
        ).newBuilder().authenticator(TokenAuthenticator(tenant, authManager)).build()
        val retrofit = NetworkModule.retrofit(client)
        return retrofit.create(ObservationsApi::class.java)
    }

    fun streamForStation(tenantId: String, stationId: Long): Flow<List<ObservationEntity>> =
        dao.streamForStation(tenantId, stationId)

    fun streamAllForTenant(tenantId: String): Flow<List<ObservationEntity>> =
        dao.streamAllForTenant(tenantId)

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
        return try {
            // Validate payload before storing
            if (!isValidPayload(payloadJson)) {
                return Result.Err(IllegalArgumentException("Invalid observation payload format"))
            }

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
            Log.d(TAG, "Queued observation: $key")
            Result.Ok(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue observation", e)
            Result.Err(e)
        }
    }

    /**
     * Batch upload with detailed result tracking
     */
    suspend fun tryUploadBatch(
        tenant: TenantConfig, endpointUrl: String, maxItems: Int = DEFAULT_BATCH_SIZE
    ): UploadBatchResult {
        val batchSize = maxItems.coerceIn(1, MAX_BATCH_SIZE)
        val pending = dao.nextPending(tenant.id, batchSize)

        if (pending.isEmpty()) {
            Log.d(TAG, "No pending observations to upload")
            return UploadBatchResult(0, 0, 0, false)
        }

        Log.d(TAG, "Starting batch upload: ${pending.size} observations for tenant ${tenant.id}")

        val api = apiFor(tenant)
        var successCount = 0
        var permanentFailures = 0
        var retriableFailures = 0

        for (observation in pending) {
            val result = uploadSingleObservation(api, endpointUrl, observation)

            when {
                result.success -> {
                    successCount++
                    dao.markSynced(
                        observation.obsKey, result.serverResponse?.id, System.currentTimeMillis()
                    )
                    Log.d(TAG, "Successfully uploaded observation: ${observation.obsKey}")
                }

                result.isPermanentFailure -> {
                    permanentFailures++
                    dao.markStatus(
                        observation.obsKey,
                        ObservationEntity.SyncStatus.FAILED,
                        "Permanent failure: ${result.errorMessage}",
                        System.currentTimeMillis()
                    )
                    Log.w(
                        TAG,
                        "Permanent failure for observation ${observation.obsKey}: ${result.errorMessage}"
                    )
                }

                else -> {
                    retriableFailures++
                    dao.markStatus(
                        observation.obsKey,
                        ObservationEntity.SyncStatus.FAILED,
                        "Retriable failure: ${result.errorMessage}",
                        System.currentTimeMillis()
                    )
                    Log.w(
                        TAG,
                        "Retriable failure for observation ${observation.obsKey}: ${result.errorMessage}"
                    )
                }
            }
        }

        // Check if there are more pending observations
        val hasMoreWork = dao.nextPending(tenant.id, 1).isNotEmpty()

        val batchResult =
            UploadBatchResult(successCount, permanentFailures, retriableFailures, hasMoreWork)

        Log.i(
            TAG,
            "Batch upload completed: success=$successCount, permanent=$permanentFailures, " + "retriable=$retriableFailures, hasMore=$hasMoreWork"
        )

        return batchResult
    }

    private suspend fun uploadSingleObservation(
        api: ObservationsApi, endpointUrl: String, observation: ObservationEntity
    ): UploadAttemptResult {
        return try {
            // Mark as uploading
            dao.markStatus(
                observation.obsKey,
                ObservationEntity.SyncStatus.UPLOADING,
                null,
                System.currentTimeMillis()
            )

            // Convert to server payload
            val payload = toPostPayload(observation)
            if (payload == null) {
                return UploadAttemptResult(
                    success = false,
                    isPermanentFailure = true,
                    errorMessage = "Invalid payload format - missing 'records' or 'metadata'"
                )
            }

            // Attempt upload
            val response = api.submitObservation(endpointUrl, payload).asResult()

            when (response) {
                is Result.Ok -> {
                    UploadAttemptResult(
                        success = true, isPermanentFailure = false, serverResponse = response.value
                    )
                }

                is Result.Err -> {
                    val isPermanent = isPermanentError(response.error)
                    UploadAttemptResult(
                        success = false,
                        isPermanentFailure = isPermanent,
                        errorMessage = response.error.message ?: "Unknown error"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during observation upload", e)
            UploadAttemptResult(
                success = false,
                isPermanentFailure = isPermanentError(e),
                errorMessage = e.message ?: "Upload exception"
            )
        }
    }

    private fun isPermanentError(error: Throwable): Boolean {
        return when (error) {
            is NetworkException.Unauthorized -> true
            is NetworkException.Forbidden -> true
            is NetworkException.NotFound -> true
            is NetworkException.Client -> error.code in 400..499 && error.code != 429 // Exclude rate limiting
            is JsonDataException -> true // Payload corruption
            is IllegalArgumentException -> true // Invalid data
            else -> false
        }
    }

    private fun isValidPayload(payloadJson: String): Boolean {
        return try {
            @Suppress("UNCHECKED_CAST") val parsed =
                mapAdapter.fromJson(payloadJson) as? Map<String, Any> ?: return false

            // Check required structure
            val records = parsed["records"] as? List<*> ?: return false
            val metadata = parsed["metadata"] as? Map<*, *> ?: return false

            // Validate records structure
            records.all { record ->
                val recordMap = record as? Map<*, *> ?: return@all false
                recordMap.containsKey("variable_mapping_id") && recordMap.containsKey("value")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Payload validation failed", e)
            false
        }
    }

    private fun toPostPayload(entity: ObservationEntity): ObservationPostRequest? {
        return try {
            @Suppress("UNCHECKED_CAST") val parsed =
                mapAdapter.fromJson(entity.payloadJson) as? Map<String, Any> ?: return null

            val recordsAny = parsed["records"] as? List<*> ?: return null
            val records = recordsAny.mapNotNull { row ->
                val m = row as? Map<*, *> ?: return@mapNotNull null
                val id = (m["variable_mapping_id"] as? Number)?.toLong() ?: return@mapNotNull null
                val valueNum = (m["value"] as? Number)?.toDouble() ?: return@mapNotNull null
                ObservationPostRequest.Record(
                    variable_mapping_id = id, value = valueNum
                )
            }

            if (records.isEmpty()) return null

            val metadataMap = parsed["metadata"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val duplicatePolicy = metadataMap["duplicate_policy"] as? String
            val reason = metadataMap["reason"] as? String
            val appVersion = (metadataMap["app_version"] as? String) ?: "1.0.0"

            // Generate deterministic idempotency key
            val idempotencyKey =
                java.util.UUID.nameUUIDFromBytes(entity.obsKey.toByteArray()).toString()

            val submissionIso = java.time.Instant.ofEpochMilli(entity.updatedAtMs).toString()
            val observationIso = java.time.Instant.ofEpochMilli(entity.obsTimeUtcMs).toString()

            ObservationPostRequest(
                idempotency_key = idempotencyKey,
                submission_time = submissionIso,
                observation_time = observationIso,
                station_link_id = entity.stationId,
                records = records,
                metadata = ObservationPostRequest.Metadata(
                    late = entity.late,
                    duplicate_policy = duplicatePolicy,
                    reason = reason,
                    app_version = appVersion
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert observation to POST payload", e)
            null
        }
    }

    /**
     * Get statistics about pending uploads for monitoring
     */
    suspend fun getUploadStats(tenantId: String): Map<String, Int> {
        val allPending = dao.nextPending(tenantId, Int.MAX_VALUE)

        return mapOf(
            "total_pending" to allPending.size,
            "queued" to allPending.count { it.status == ObservationEntity.SyncStatus.QUEUED },
            "uploading" to allPending.count { it.status == ObservationEntity.SyncStatus.UPLOADING },
            "failed" to allPending.count { it.status == ObservationEntity.SyncStatus.FAILED })
    }

    /**
     * Reset failed observations back to queued status for retry
     */
    suspend fun retryFailedObservations(tenantId: String): Int {
        val failedObservations = dao.nextPending(tenantId, Int.MAX_VALUE)
            .filter { it.status == ObservationEntity.SyncStatus.FAILED }

        var retryCount = 0
        for (obs in failedObservations) {
            dao.markStatus(
                obs.obsKey, ObservationEntity.SyncStatus.QUEUED, null, System.currentTimeMillis()
            )
            retryCount++
        }

        Log.i(TAG, "Reset $retryCount failed observations to queued status for tenant $tenantId")
        return retryCount
    }
}