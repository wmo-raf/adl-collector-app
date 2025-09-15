package com.climtech.adlcollector.feature.stations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.feature.stations.data.net.Station
import com.climtech.adlcollector.feature.stations.data.StationsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StationsUiState(
    val loading: Boolean = false,       // network refresh in flight
    val stations: List<Station> = emptyList(),
    val error: String? = null           // last refresh error (cache still shown)
)

class StationsViewModel(
    private val repo: StationsRepository
) : ViewModel() {

    private var streamJob: Job? = null
    private val _state = MutableStateFlow(StationsUiState())
    val state: StateFlow<StationsUiState> = _state.asStateFlow()

    fun start(tenant: TenantConfig) {
        // Stream cache
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            repo.stationsStream(tenant.id).collect { cached ->
                _state.update { it.copy(stations = cached) }
            }
        }
        // Kick off background refresh
        refresh(tenant, showSpinner = _state.value.stations.isEmpty())
    }

    fun refresh(tenant: TenantConfig, showSpinner: Boolean = true) {
        viewModelScope.launch {
            if (showSpinner) _state.update { it.copy(loading = true, error = null) }
            val result = repo.refreshStations(tenant)
            _state.update {
                it.copy(
                    loading = false, error = result.exceptionOrNull()?.message
                )
            }
        }
    }
}
