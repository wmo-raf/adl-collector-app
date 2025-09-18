package com.climtech.adlcollector.feature.stations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.net.NetworkException
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.feature.stations.data.StationsRepository
import com.climtech.adlcollector.feature.stations.data.net.Station
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StationsUiState(
    val loading: Boolean = false,       // network refresh in flight
    val stations: List<Station> = emptyList(),
    val error: String? = null,          // only shown when no cached data available
    val isOffline: Boolean = false,     // indicates stale data warning
    val lastRefreshFailed: Boolean = false  // background refresh failure
)

@HiltViewModel
class StationsViewModel @Inject constructor(
    private val repo: StationsRepository
) : ViewModel() {

    private var streamJob: Job? = null
    private val _state = MutableStateFlow(StationsUiState())
    val state: StateFlow<StationsUiState> = _state.asStateFlow()

    fun start(tenant: TenantConfig) {
        // Start streaming cache immediately
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            repo.stationsStream(tenant.id).collect { cached ->
                _state.update { currentState ->
                    currentState.copy(
                        stations = cached,
                        // Clear loading if we got cached data
                        loading = if (cached.isNotEmpty()) false else currentState.loading,
                        // Clear error if we have cached data to show
                        error = if (cached.isNotEmpty()) null else currentState.error
                    )
                }
            }
        }

        // Kick off background refresh
        val hasCache = _state.value.stations.isNotEmpty()
        refresh(tenant, showSpinner = !hasCache)
    }

    fun refresh(tenant: TenantConfig, showSpinner: Boolean = true) {
        viewModelScope.launch {
            val currentState = _state.value

            // Only show spinner if explicitly requested and no cached data
            if (showSpinner && currentState.stations.isEmpty()) {
                _state.update { it.copy(loading = true, error = null) }
            }

            val result = repo.refreshStations(tenant)

            _state.update { st ->
                when (result) {
                    is Result.Ok -> {
                        st.copy(
                            loading = false,
                            error = null,
                            isOffline = false,
                            lastRefreshFailed = false
                        )
                    }

                    is Result.Err -> {
                        val hasStationsToShow = st.stations.isNotEmpty()

                        if (hasStationsToShow) {
                            // We have cached data - show it with offline indicator
                            st.copy(
                                loading = false,
                                error = null, // Don't show error since we have data
                                isOffline = isOfflineError(result.error),
                                lastRefreshFailed = true
                            )
                        } else {
                            // No cached data - show error
                            st.copy(
                                loading = false,
                                error = result.error.toUserMessage(),
                                isOffline = isOfflineError(result.error),
                                lastRefreshFailed = true
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun retryRefresh(tenant: TenantConfig) {
        refresh(tenant, showSpinner = false)
    }

    private fun isOfflineError(error: Throwable): Boolean {
        return when (error) {
            is NetworkException.Offline -> true
            is NetworkException.Timeout -> true
            else -> false
        }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is NetworkException.Offline -> "You're offline. Check your connection and try again."

        is NetworkException.Timeout -> "Request timed out. Please try again."

        is NetworkException.Unauthorized -> "Session expired. Please log in again."

        is NetworkException.Forbidden -> "You don't have permission to access stations."

        is NetworkException.NotFound -> "Stations endpoint not found."

        is NetworkException.Server -> "Server error (${this.code}). Please try again later."

        is NetworkException.Client -> this.body ?: "Request error (${this.code})."

        is NetworkException.EmptyBody -> "Server returned no data."

        is NetworkException.Serialization -> "We couldn't read the server response."

        is NetworkException.UnexpectedBody -> "The server returned an unexpected response."

        else -> message ?: "Something went wrong."
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}