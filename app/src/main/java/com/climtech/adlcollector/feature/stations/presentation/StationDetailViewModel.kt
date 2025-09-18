package com.climtech.adlcollector.feature.stations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.core.data.db.ObservationDao
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.net.NetworkException
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.feature.stations.data.StationsRepository
import com.climtech.adlcollector.feature.stations.data.net.StationDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StationDetailUi(
    val loading: Boolean = false,       // only true on first load when no cache
    val detail: StationDetail? = null, // cached or fresh data
    val error: String? = null,          // only shown when no cached data
    val isOffline: Boolean = false,     // indicates using cached/stale data
    val lastRefreshFailed: Boolean = false, // background refresh failed
    val refreshing: Boolean = false     // background refresh in progress
)

@HiltViewModel
class StationDetailViewModel @Inject constructor(
    private val repo: StationsRepository, private val observationDao: ObservationDao
) : ViewModel() {

    private val _ui = MutableStateFlow(StationDetailUi())
    val ui: StateFlow<StationDetailUi> = _ui.asStateFlow()

    private val _recent = MutableStateFlow<List<ObservationEntity>>(emptyList())
    val recent: StateFlow<List<ObservationEntity>> = _recent.asStateFlow()

    private lateinit var tenant: TenantConfig
    private var stationId: Long = -1

    private var detailStreamJob: Job? = null
    private var recentStreamJob: Job? = null

    fun start(tenant: TenantConfig, stationId: Long) {
        this.tenant = tenant
        this.stationId = stationId

        // 1) Start streaming cached station detail immediately
        detailStreamJob?.cancel()
        detailStreamJob = viewModelScope.launch {
            repo.stationDetailStream(tenant.id, stationId).collectLatest { cached ->
                val currentState = _ui.value

                if (cached != null) {
                    // We have cached data - show it immediately
                    _ui.value = currentState.copy(
                        detail = cached, loading = false, error = null
                    )
                } else if (currentState.detail == null && !currentState.loading) {
                    // No cached data AND we haven't started loading yet - fetch from API
                    _ui.value = currentState.copy(loading = true, error = null)
                    reload(showFullLoading = true)
                }
            }
        }

        // 2) Stream recent observations for this station (top 3)
        recentStreamJob?.cancel()
        recentStreamJob = viewModelScope.launch {
            observationDao.streamForStation(tenant.id, stationId).map { list -> list.take(3) }
                .collectLatest { _recent.value = it }
        }
    }

    fun reload(showFullLoading: Boolean = false) {
        if (!::tenant.isInitialized || stationId <= 0) return

        viewModelScope.launch {
            val currentState = _ui.value

            // Show appropriate loading state
            if (showFullLoading && currentState.detail == null) {
                // First load with no cache - show full loading
                _ui.value = currentState.copy(loading = true, error = null)
            } else if (currentState.detail != null) {
                // Background refresh with cached data - show refresh indicator
                _ui.value = currentState.copy(refreshing = true, error = null)
            }

            when (val result = repo.refreshStationDetail(tenant, stationId)) {
                is Result.Ok -> {
                    _ui.value = _ui.value.copy(
                        loading = false,
                        refreshing = false,
                        error = null,
                        isOffline = false,
                        lastRefreshFailed = false
                    )
                }

                is Result.Err -> {
                    val hasDetailToShow = _ui.value.detail != null

                    if (hasDetailToShow) {
                        // We have cached data - show it with offline indicator
                        _ui.value = _ui.value.copy(
                            loading = false,
                            refreshing = false,
                            error = null, // Don't show error since we have data
                            isOffline = isOfflineError(result.error),
                            lastRefreshFailed = true
                        )
                    } else {
                        // No cached data - show error
                        _ui.value = _ui.value.copy(
                            loading = false,
                            refreshing = false,
                            error = result.error.toUserMessage(),
                            isOffline = isOfflineError(result.error),
                            lastRefreshFailed = true
                        )
                    }
                }
            }
        }
    }

    fun retryRefresh() {
        reload(showFullLoading = false)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    private fun isOfflineError(error: Throwable): Boolean {
        return when (error) {
            is NetworkException.Offline -> true
            is NetworkException.Timeout -> true
            else -> false
        }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is NetworkException.Offline -> "You're offline. Station details may be outdated."

        is NetworkException.Timeout -> "Request timed out. Please try again."

        is NetworkException.Unauthorized -> "Session expired. Please log in again."

        is NetworkException.Forbidden -> "You don't have permission to access this station."

        is NetworkException.NotFound -> "Station not found."

        is NetworkException.Server -> "Server error (${this.code}). Please try again later."

        is NetworkException.Client -> this.body ?: "Request error (${this.code})."

        is NetworkException.EmptyBody -> "Server returned no data."

        is NetworkException.Serialization -> "We couldn't read the server response."

        is NetworkException.UnexpectedBody -> "The server returned an unexpected response."

        else -> message ?: "Failed to load station details."
    }

    override fun onCleared() {
        super.onCleared()
        detailStreamJob?.cancel()
        recentStreamJob?.cancel()
    }
}