package com.climtech.adlcollector.feature.observations.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.data.db.ObservationDao
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.util.Logger
import com.climtech.adlcollector.core.util.NotificationHelper
import com.climtech.adlcollector.feature.observations.data.ObservationsRepository
import com.climtech.adlcollector.feature.observations.sync.UploadObservationsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ObservationSummary(
    val syncedToday: Int = 0,
    val pendingToday: Int = 0,
    val failedToday: Int = 0,
    val totalPending: Int = 0,
    val oldestPendingHours: Int? = null
)

data class UploadStatus(
    val isActive: Boolean = false,
    val progress: String? = null,
    val lastAttempt: Long? = null,
    val consecutiveFailures: Int = 0
)

data class ObservationsUiState(
    val isLoading: Boolean = false,
    val observations: List<ObservationEntity> = emptyList(),
    val summary: ObservationSummary = ObservationSummary(),
    val uploadStatus: UploadStatus = UploadStatus(),
    val selectedDate: LocalDate = LocalDate.now(),
    val availableDates: List<LocalDate> = emptyList(), // dates with observations in the last 30 days
    val error: String? = null
) {
    // Computed property for backward compatibility
    val isSyncing: Boolean get() = uploadStatus.isActive
    val hasPendingObservations: Boolean get() = summary.totalPending > 0
}

@HiltViewModel
class ObservationsViewModel @Inject constructor(
    private val observationDao: ObservationDao,
    private val observationsRepository: ObservationsRepository,
    private val tenantLocalStore: TenantLocalStore,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ObservationsUiState())
    val uiState: StateFlow<ObservationsUiState> = _uiState.asStateFlow()

    private var currentTenantId: String? = null
    private val workManager = WorkManager.getInstance(context)

    private var monitoringJob: Job? = null

    companion object {
        private const val LOOKBACK_DAYS = 30L
    }

    fun start(tenantId: String) {
        if (currentTenantId == tenantId) return
        currentTenantId = tenantId

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        // Cancel previous monitoring job
        monitoringJob?.cancel()

        monitoringJob = viewModelScope.launch {
            try {
                // Load available dates first
                loadAvailableDates(tenantId)

                // Combine observations stream for selected date with upload status monitoring
                combine(
                    observationDao.streamAllForTenant(tenantId),
                    monitorUploadWork()
                ) { allObservations, uploadStatus ->
                    val selectedDate = _uiState.value.selectedDate
                    val filteredObservations =
                        filterObservationsByDate(allObservations, selectedDate)
                    val summary = calculateSummary(allObservations, tenantId)

                    ObservationsUiState(
                        isLoading = false,
                        observations = filteredObservations.sortedByDescending { it.obsTimeUtcMs },
                        summary = summary,
                        uploadStatus = uploadStatus,
                        selectedDate = selectedDate,
                        availableDates = _uiState.value.availableDates,
                        error = null
                    )
                }.collectLatest { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = "Failed to load observations: ${e.message}"
                )
            }
        }

        // Clean up stuck uploads on start
        viewModelScope.launch {
            cleanupStuckUploads(tenantId)
        }
    }

    private suspend fun loadAvailableDates(tenantId: String) {
        try {
            // Get observations from the last 30 days
            val now = LocalDate.now()
            val thirtyDaysAgo = now.minusDays(LOOKBACK_DAYS)
            val startMs =
                thirtyDaysAgo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMs =
                now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val observations = observationDao.getObservationsInDateRange(tenantId, startMs, endMs)

            // Extract unique dates from observations
            val availableDates = observations
                .map { obs ->
                    Instant.ofEpochMilli(obs.obsTimeUtcMs)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }
                .distinct()
                .sorted()
                .reversed() // Most recent first

            _uiState.value = _uiState.value.copy(availableDates = availableDates)
        } catch (e: Exception) {
            // Log error but don't fail the whole screen
            Logger.e("ObservationsViewModel", "Failed to load available dates", e)
        }
    }

    fun selectDate(date: LocalDate) {
        if (date == _uiState.value.selectedDate) return

        _uiState.value = _uiState.value.copy(selectedDate = date)

        // Reload observations for the selected date
        currentTenantId?.let { tenantId ->
            viewModelScope.launch {
                try {
                    val allObservations = observationDao.streamAllForTenant(tenantId).first()
                    val filteredObservations = filterObservationsByDate(allObservations, date)

                    _uiState.value = _uiState.value.copy(
                        observations = filteredObservations.sortedByDescending { it.obsTimeUtcMs }
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to load observations for selected date: ${e.message}"
                    )
                }
            }
        }
    }

    private fun filterObservationsByDate(
        observations: List<ObservationEntity>,
        selectedDate: LocalDate
    ): List<ObservationEntity> {
        return observations.filter { obs ->
            val obsDate = Instant.ofEpochMilli(obs.obsTimeUtcMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            obsDate == selectedDate
        }
    }

    private suspend fun calculateSummary(
        observations: List<ObservationEntity>, tenantId: String
    ): ObservationSummary {
        val today = LocalDate.now()
        val todayObservations = observations.filter { obs ->
            val obsDate =
                Instant.ofEpochMilli(obs.obsTimeUtcMs).atZone(ZoneId.systemDefault()).toLocalDate()
            obsDate == today
        }

        val totalPending = observationDao.countPending(tenantId)
        val oldestPending = observationDao.getOldestPending(tenantId)
        val oldestPendingHours = oldestPending?.let { obs ->
            val ageMs = System.currentTimeMillis() - obs.createdAtMs
            (ageMs / (1000 * 60 * 60)).toInt()
        }

        return ObservationSummary(
            syncedToday = todayObservations.count { it.status == ObservationEntity.SyncStatus.SYNCED },
            pendingToday = todayObservations.count {
                it.status == ObservationEntity.SyncStatus.QUEUED || it.status == ObservationEntity.SyncStatus.UPLOADING
            },
            failedToday = todayObservations.count { it.status == ObservationEntity.SyncStatus.FAILED },
            totalPending = totalPending,
            oldestPendingHours = oldestPendingHours
        )
    }

    private fun monitorUploadWork() = kotlinx.coroutines.flow.flow {
        while (true) {
            try {
                val uploadWorks = workManager.getWorkInfosByTagFlow("upload_observations").first()

                val activeWork = uploadWorks.firstOrNull {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }

                val lastFailedWork = uploadWorks.filter { it.state == WorkInfo.State.FAILED }
                    .maxByOrNull { it.outputData.getLong("timestamp", 0) }

                val consecutiveFailures =
                    uploadWorks.sortedByDescending { it.outputData.getLong("timestamp", 0) }
                        .takeWhile { it.state == WorkInfo.State.FAILED }.size

                val uploadStatus = UploadStatus(
                    isActive = activeWork != null,
                    progress = activeWork?.progress?.getString("status"),
                    lastAttempt = lastFailedWork?.outputData?.getLong("timestamp", 0),
                    consecutiveFailures = consecutiveFailures
                )

                emit(uploadStatus)
            } catch (e: Exception) {
                emit(UploadStatus())
            }

            kotlinx.coroutines.delay(2000)
        }
    }

    fun syncNow(allowMetered: Boolean = false, isUrgent: Boolean = false) {
        currentTenantId?.let { tenantId ->
            viewModelScope.launch {
                try {
                    val tenant = tenantLocalStore.getTenantById(tenantId)
                    if (tenant != null) {
                        val submitUrl = tenant.api(
                            "plugins",
                            "api",
                            "adl-collector",
                            "manual-obs",
                            "submit",
                            trailingSlash = true
                        ).toString()

                        val workRequest = UploadObservationsWorker.createWorkRequest(
                            tenantId = tenantId,
                            endpointUrl = submitUrl,
                            isUrgent = isUrgent,
                            allowMetered = allowMetered
                        )

                        workManager.enqueue(workRequest)

                        if (isUrgent) {
                            notificationHelper.showUploadProgress(0, 1)
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Tenant configuration not found"
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to start sync: ${e.message}"
                    )
                }
            }
        }
    }

    fun retryFailedObservations() {
        currentTenantId?.let { tenantId ->
            viewModelScope.launch {
                try {
                    val retryCount = observationsRepository.retryFailedObservations(tenantId)
                    if (retryCount > 0) {
                        notificationHelper.clearUploadFailure()
                        syncNow(isUrgent = false)
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to retry observations: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun cleanupStuckUploads(tenantId: String) {
        try {
            val timeoutMs = System.currentTimeMillis() - (10 * 60 * 1000)
            observationDao.resetStuckUploading(tenantId, timeoutMs, System.currentTimeMillis())
        } catch (e: Exception) {
            Logger.w("ObservationsViewModel", "Failed to cleanup stuck uploads", e)
        }
    }

    fun getUploadStats() {
        currentTenantId?.let { tenantId ->
            viewModelScope.launch {
                try {
                    val stats = observationsRepository.getUploadStats(tenantId)
                    Logger.d("ObservationsViewModel", "Upload stats: $stats")
                } catch (e: Exception) {
                    Logger.e("ObservationsViewModel", "Failed to get upload stats", e)
                }
            }
        }
    }

    fun refresh() {
        currentTenantId?.let { tenantId ->
            start(tenantId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        monitoringJob?.cancel()
        notificationHelper.hideUploadProgress()
    }
}