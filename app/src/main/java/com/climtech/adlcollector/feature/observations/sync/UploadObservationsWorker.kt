package com.climtech.adlcollector.feature.observations.sync

import android.Manifest
import android.R.drawable
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.climtech.adlcollector.core.util.Logger
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.net.NetworkException
import com.climtech.adlcollector.feature.observations.data.ObservationsRepository
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import javax.net.ssl.SSLException
import kotlin.math.min
import kotlin.math.pow

data class NetworkInfo(
    val isConnected: Boolean, val isMetered: Boolean, val isWifi: Boolean
)

data class UploadBatchResult(
    val successCount: Int,
    val permanentFailures: Int,
    val retriableFailures: Int,
    val hasMoreWork: Boolean
) {
    val totalProcessed: Int get() = successCount + permanentFailures + retriableFailures
    val shouldRetry: Boolean get() = retriableFailures > 0 || hasMoreWork
    val hasFailures: Boolean get() = permanentFailures > 0 || retriableFailures > 0
}

@HiltWorker
class UploadObservationsWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val repo: ObservationsRepository,
    private val tenantLocalStore: TenantLocalStore,
    private val moshi: Moshi
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "UploadObsWorker"

        // Input data keys
        private const val KEY_TENANT_ID = "tenantId"
        private const val KEY_ENDPOINT = "endpointUrl"
        private const val KEY_RETRY_COUNT = "retryCount"
        private const val KEY_ALLOW_METERED = "allowMetered"
        private const val KEY_IS_URGENT = "isUrgent"
        private const val KEY_CONSECUTIVE_FAILURES = "consecutiveFailures"

        // Retry configuration
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACKOFF_SECONDS = 30L
        private const val MAX_BACKOFF_SECONDS = 600L // 10 minutes
        private const val MAX_CONSECUTIVE_FAILURES_FOR_NOTIFICATION = 3

        // Notification
        private const val UPLOAD_FAILURE_NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "upload_failures"

        fun createWorkRequest(
            tenantId: String,
            endpointUrl: String,
            isUrgent: Boolean = false,
            allowMetered: Boolean = false
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(!isUrgent).setRequiresStorageNotLow(true).build()

            val inputData = Data.Builder().putString(KEY_TENANT_ID, tenantId)
                .putString(KEY_ENDPOINT, endpointUrl).putInt(KEY_RETRY_COUNT, 0)
                .putBoolean(KEY_ALLOW_METERED, allowMetered).putBoolean(KEY_IS_URGENT, isUrgent)
                .putInt(KEY_CONSECUTIVE_FAILURES, 0).build()

            return OneTimeWorkRequestBuilder<UploadObservationsWorker>().setInputData(inputData)
                .setConstraints(constraints).setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(INITIAL_BACKOFF_SECONDS)
                ).build()
        }

        // Legacy method for backward compatibility
        fun oneShot(tenantId: String, endpointUrl: String): OneTimeWorkRequest {
            return createWorkRequest(tenantId, endpointUrl)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        val tenantId = inputData.getString(KEY_TENANT_ID)
            ?: return Result.failure(createErrorData("Missing tenant ID"))

        val endpoint = inputData.getString(KEY_ENDPOINT)
            ?: return Result.failure(createErrorData("Missing endpoint URL"))

        val retryCount = inputData.getInt(KEY_RETRY_COUNT, 0)
        val allowMetered = inputData.getBoolean(KEY_ALLOW_METERED, false)
        val isUrgent = inputData.getBoolean(KEY_IS_URGENT, false)
        val consecutiveFailures = inputData.getInt(KEY_CONSECUTIVE_FAILURES, 0)

        Logger.d(TAG, "Starting upload: tenant=$tenantId, retry=$retryCount, urgent=$isUrgent")

        return try {
            // Check network conditions
            val networkInfo = getNetworkInfo()
            if (!isNetworkSuitable(networkInfo, allowMetered)) {
                Logger.d(TAG, "Network conditions not suitable for upload")
                return Result.retry()
            }

            // Check battery optimization (non-blocking)
            if (!isUrgent && isBatteryOptimized()) {
                Logger.d(TAG, "Battery optimization active, deferring non-urgent upload")
                return Result.retry()
            }

            // Get tenant configuration
            val tenant = tenantLocalStore.getTenantById(tenantId) ?: return Result.failure(
                createErrorData("Tenant configuration not found")
            )

            // Perform upload with exponential backoff on retries
            if (retryCount > 0) {
                val backoffDelay = calculateBackoffDelay(retryCount)
                Logger.d(TAG, "Applying backoff delay: ${backoffDelay}ms")
                delay(backoffDelay)
            }

            val uploadResult = repo.tryUploadBatch(tenant, endpoint)

            handleUploadResult(
                uploadResult,
                tenantId,
                endpoint,
                retryCount,
                consecutiveFailures,
                allowMetered,
                isUrgent
            )

        } catch (e: Exception) {
            Logger.e(TAG, "Upload worker failed", e)
            handleException(
                e, tenantId, endpoint, retryCount, consecutiveFailures, allowMetered, isUrgent
            )
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleUploadResult(
        result: UploadBatchResult,
        tenantId: String,
        endpoint: String,
        retryCount: Int,
        consecutiveFailures: Int,
        allowMetered: Boolean,
        isUrgent: Boolean
    ): Result {
        Logger.d(
            TAG,
            "Upload result: success=${result.successCount}, " + "permanent=${result.permanentFailures}, retriable=${result.retriableFailures}, " + "hasMore=${result.hasMoreWork}"
        )

        return when {
            // Complete success - no more work
            result.totalProcessed > 0 && !result.shouldRetry -> {
                Logger.i(TAG, "Upload completed successfully")
                Result.success(createSuccessData(result))
            }

            // Partial success or more work to do - continue
            result.successCount > 0 && result.hasMoreWork -> {
                Logger.d(TAG, "Partial success, scheduling continuation")
                scheduleNextBatch(
                    tenantId, endpoint, 0, allowMetered, isUrgent
                ) // Reset retry count on progress
            }

            // Retriable failures
            result.retriableFailures > 0 -> {
                if (retryCount >= MAX_RETRIES) {
                    Logger.w(TAG, "Max retries exceeded, marking as failure")
                    val newConsecutiveFailures = consecutiveFailures + 1
                    handleConsecutiveFailures(newConsecutiveFailures, tenantId)
                    Result.failure(createErrorData("Max retries exceeded"))
                } else {
                    Logger.d(TAG, "Retriable failures, scheduling retry")
                    scheduleRetry(
                        tenantId,
                        endpoint,
                        retryCount + 1,
                        consecutiveFailures,
                        allowMetered,
                        isUrgent
                    )
                }
            }

            // Only permanent failures
            result.permanentFailures > 0 && result.retriableFailures == 0 -> {
                Logger.w(TAG, "Only permanent failures, not retrying")
                Result.success(createSuccessData(result)) // Don't retry permanent failures
            }

            // No work done
            else -> {
                Logger.d(TAG, "No work to do")
                Result.success()
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleException(
        exception: Exception,
        tenantId: String,
        endpoint: String,
        retryCount: Int,
        consecutiveFailures: Int,
        allowMetered: Boolean,
        isUrgent: Boolean
    ): Result {
        return when {
            isPermanentError(exception) -> {
                Logger.e(TAG, "Permanent error, not retrying", exception)
                val newConsecutiveFailures = consecutiveFailures + 1
                handleConsecutiveFailures(newConsecutiveFailures, tenantId)
                Result.failure(createErrorData("Permanent error: ${exception.message}"))
            }

            retryCount >= MAX_RETRIES -> {
                Logger.e(TAG, "Max retries exceeded after exception", exception)
                val newConsecutiveFailures = consecutiveFailures + 1
                handleConsecutiveFailures(newConsecutiveFailures, tenantId)
                Result.failure(createErrorData("Max retries exceeded: ${exception.message}"))
            }

            else -> {
                Logger.w(TAG, "Retriable exception, scheduling retry", exception)
                scheduleRetry(
                    tenantId, endpoint, retryCount + 1, consecutiveFailures, allowMetered, isUrgent
                )
            }
        }
    }

    private fun scheduleRetry(
        tenantId: String,
        endpoint: String,
        newRetryCount: Int,
        consecutiveFailures: Int,
        allowMetered: Boolean,
        isUrgent: Boolean
    ): Result {
        val retryData =
            Data.Builder().putString(KEY_TENANT_ID, tenantId).putString(KEY_ENDPOINT, endpoint)
                .putInt(KEY_RETRY_COUNT, newRetryCount)
                .putInt(KEY_CONSECUTIVE_FAILURES, consecutiveFailures)
                .putBoolean(KEY_ALLOW_METERED, allowMetered).putBoolean(KEY_IS_URGENT, isUrgent)
                .build()

        return Result.retry()
    }

    private fun scheduleNextBatch(
        tenantId: String,
        endpoint: String,
        retryCount: Int,
        allowMetered: Boolean,
        isUrgent: Boolean
    ): Result {
        // For continuation, we reset consecutive failures since we made progress
        val nextData =
            Data.Builder().putString(KEY_TENANT_ID, tenantId).putString(KEY_ENDPOINT, endpoint)
                .putInt(KEY_RETRY_COUNT, retryCount)
                .putInt(KEY_CONSECUTIVE_FAILURES, 0) // Reset on progress
                .putBoolean(KEY_ALLOW_METERED, allowMetered).putBoolean(KEY_IS_URGENT, isUrgent)
                .build()

        return Result.retry()
    }

    private fun getNetworkInfo(): NetworkInfo {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

            NetworkInfo(
                isConnected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                isMetered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
                isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            )
        } else {
            @Suppress("DEPRECATION") val activeNetworkInfo = connectivityManager.activeNetworkInfo
            NetworkInfo(
                isConnected = activeNetworkInfo?.isConnected == true,
                isMetered = activeNetworkInfo?.type != ConnectivityManager.TYPE_WIFI,
                isWifi = activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            )
        }
    }

    private fun isNetworkSuitable(networkInfo: NetworkInfo, allowMetered: Boolean): Boolean {
        return networkInfo.isConnected && (allowMetered || !networkInfo.isMetered)
    }

    private fun isBatteryOptimized(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager =
                    appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.let { pm ->
                    !pm.isIgnoringBatteryOptimizations(appContext.packageName)
                } ?: false
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to check battery optimization status", e)
                false
            }
        } else {
            false
        }
    }

    private fun calculateBackoffDelay(retryCount: Int): Long {
        val exponentialDelay = INITIAL_BACKOFF_SECONDS * (2.0.pow(retryCount.toDouble())).toLong()
        val cappedDelay = min(exponentialDelay, MAX_BACKOFF_SECONDS)

        // Add jitter (±25%)
        val jitterRange = cappedDelay * 0.25
        val jitter = (Math.random() * jitterRange * 2) - jitterRange

        return ((cappedDelay + jitter) * 1000).toLong() // Convert to milliseconds
    }

    private fun isPermanentError(exception: Exception): Boolean {
        return when (exception) {
            // Data/Format errors - don't retry
            is JsonDataException -> true
            is JsonEncodingException -> true
            is IllegalArgumentException -> true
            is SecurityException -> true

            // Network/Connection errors - should retry
            is UnknownHostException -> false
            is ConnectException -> false
            is SocketTimeoutException -> false
            is SSLException -> false
            is java.io.IOException -> {
                // Some IOExceptions are permanent, others aren't
                when {
                    exception.message?.contains("certificate", ignoreCase = true) == true -> true
                    exception.message?.contains("SSL", ignoreCase = true) == true -> true
                    else -> false // Most IO errors are temporary
                }
            }

            // NetworkException types from our custom exceptions
            is NetworkException -> when (exception) {
                is NetworkException.Unauthorized -> true
                is NetworkException.Forbidden -> true
                is NetworkException.NotFound -> true
                is NetworkException.Client -> {
                    // 4xx errors are usually permanent except rate limiting
                    exception.code in 400..499 && exception.code != 429
                }

                is NetworkException.Server -> false // 5xx should retry
                is NetworkException.Offline -> false
                is NetworkException.Timeout -> false
                is NetworkException.EmptyBody -> false
                is NetworkException.Serialization -> true
                is NetworkException.UnexpectedBody -> true
                is NetworkException.LoginRedirect -> true
                is NetworkException.Unknown -> false
            }

            // Fallback: check HTTP status codes in message
            else -> exception.message?.let { msg ->
                when {
                    // Authentication/Authorization - permanent
                    msg.contains("401") || msg.contains("403") -> true
                    // Not Found - permanent
                    msg.contains("404") -> true
                    // Validation errors - permanent
                    msg.contains("422") || msg.contains("400") -> true
                    // Rate limiting - should retry
                    msg.contains("429") -> false
                    // Server errors - should retry
                    msg.contains("5") && (msg.contains("50") || msg.contains("51") || msg.contains("52") || msg.contains(
                        "53"
                    )) -> false

                    else -> false
                }
            } ?: false
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleConsecutiveFailures(consecutiveFailures: Int, tenantId: String) {
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES_FOR_NOTIFICATION) {
            showPersistentFailureNotification(tenantId, consecutiveFailures)
        }

        // Log for analytics/monitoring
        Logger.w(TAG, "Consecutive upload failures: $consecutiveFailures for tenant: $tenantId")
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showPersistentFailureNotification(tenantId: String, failureCount: Int) {
        try {
            val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(drawable.stat_notify_error)
                .setContentTitle("Observation Upload Failed")
                .setContentText("Unable to sync observations after $failureCount attempts. Check your connection and try again.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build()

            if (appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(appContext)
                    .notify(UPLOAD_FAILURE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to show notification", e)
        }
    }

    private fun createErrorData(message: String): Data {
        return Data.Builder().putString("error", message)
            .putLong("timestamp", System.currentTimeMillis()).build()
    }

    private fun createSuccessData(result: UploadBatchResult): Data {
        return Data.Builder().putInt("successCount", result.successCount)
            .putInt("permanentFailures", result.permanentFailures)
            .putInt("retriableFailures", result.retriableFailures)
            .putLong("timestamp", System.currentTimeMillis()).build()
    }
}