package com.climtech.adlcollector.feature.observations.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.climtech.adlcollector.core.auth.TenantLocalStore
import com.climtech.adlcollector.core.data.db.ObservationDao
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.feature.observations.sync.UploadObservationsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ObservationSummary(
    val syncedToday: Int = 0,
    val pendingToday: Int = 0,
    val failedToday: Int = 0
)

data class ObservationsUiState(
    val isLoading: Boolean = false,
    val observations: List<ObservationEntity> = emptyList(),
    val summary: ObservationSummary = ObservationSummary(),
    val hasPendingObservations: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ObservationsViewModel @Inject constructor(
    private val observationDao: ObservationDao,
    private val tenantLocalStore: TenantLocalStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ObservationsUiState())
    val uiState: StateFlow<ObservationsUiState> = _uiState.asStateFlow()

    private var currentTenantId: String? = null

    fun start(tenantId: String) {
        if (currentTenantId == tenantId) return
        currentTenantId = tenantId

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                // Stream all observations for this tenant
                observationDao.streamAllForTenant(tenantId).collectLatest { observations ->
                    val summary = calculateSummary(observations)
                    val hasPending = observations.any {
                        it.status == ObservationEntity.SyncStatus.QUEUED ||
                                it.status == ObservationEntity.SyncStatus.UPLOADING
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        observations = observations.sortedByDescending { it.obsTimeUtcMs },
                        summary = summary,
                        hasPendingObservations = hasPending,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load observations: ${e.message}"
                )
            }
        }
    }

    private fun calculateSummary(observations: List<ObservationEntity>): ObservationSummary {
        val today = LocalDate.now()
        val todayObservations = observations.filter { obs ->
            val obsDate = Instant.ofEpochMilli(obs.obsTimeUtcMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            obsDate == today
        }

        return ObservationSummary(
            syncedToday = todayObservations.count { it.status == ObservationEntity.SyncStatus.SYNCED },
            pendingToday = todayObservations.count {
                it.status == ObservationEntity.SyncStatus.QUEUED ||
                        it.status == ObservationEntity.SyncStatus.UPLOADING
            },
            failedToday = todayObservations.count { it.status == ObservationEntity.SyncStatus.FAILED }
        )
    }

    fun refresh() {
        currentTenantId?.let { tenantId ->
            start(tenantId) // Restart data flow
        }
    }

    fun syncNow() {
        currentTenantId?.let { tenantId ->
            viewModelScope.launch {
                try {
                    // Get the current tenant config
                    val tenant = tenantLocalStore.getTenantById(tenantId)
                    if (tenant != null) {
                        // Set syncing state to true
                        _uiState.value = _uiState.value.copy(isSyncing = true)

                        // Build the submit URL for this tenant
                        val submitUrl = tenant.api(
                            "plugins", "api", "adl-collector", "manual-obs", "submit",
                            trailingSlash = true
                        ).toString()

                        // Trigger sync worker to upload pending observations
                        val workRequest = UploadObservationsWorker.oneShot(tenantId, submitUrl)
                        val workManager = WorkManager.getInstance(context)
                        workManager.enqueue(workRequest)

                        // Monitor the work status to update syncing state
                        workManager.getWorkInfoByIdLiveData(workRequest.id)
                            .observeForever { workInfo ->
                                when (workInfo?.state) {
                                    WorkInfo.State.SUCCEEDED,
                                    WorkInfo.State.FAILED,
                                    WorkInfo.State.CANCELLED -> {
                                        _uiState.value = _uiState.value.copy(isSyncing = false)
                                    }

                                    else -> {
                                        // Still running (ENQUEUED, BLOCKED, RUNNING)
                                        // Keep syncing = true
                                    }
                                }
                            }


                        // Note: The UI will automatically update when the worker completes
                        // because we're streaming data from the database and the worker
                        // updates observation statuses in the database

                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Tenant configuration not found",
                            isSyncing = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to start sync: ${e.message}",
                        isSyncing = false
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}