package com.climtech.adlcollector.feature.observations.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.observations.data.ObservationsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UploadObservationsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repo: ObservationsRepository,
    private val tenantLocalStore: TenantLocalStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val tenantId = inputData.getString(KEY_TENANT_ID) ?: return Result.failure()
        val endpoint = inputData.getString(KEY_ENDPOINT) ?: return Result.failure()

        Log.d("UploadObsWorker", "tenantId=$tenantId, endpoint=$endpoint")

        val tenant: TenantConfig = tenantLocalStore.getTenantById(tenantId) ?: return Result.retry()

        val progressed = repo.tryUploadBatch(tenant, endpoint)
        return if (progressed) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_TENANT_ID = "tenantId"
        private const val KEY_ENDPOINT = "endpointUrl"

        fun oneShot(tenantId: String, endpointUrl: String): OneTimeWorkRequest {
            val data = Data.Builder()
                .putString(KEY_TENANT_ID, tenantId)
                .putString(KEY_ENDPOINT, endpointUrl)
                .build()

            return OneTimeWorkRequestBuilder<UploadObservationsWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }
    }
}
