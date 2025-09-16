package com.climtech.adlcollector.feature.stations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.core.model.TenantConfig
import com.climtech.adlcollector.core.util.Result
import com.climtech.adlcollector.feature.stations.data.StationsRepository
import com.climtech.adlcollector.feature.stations.data.net.StationDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StationDetailUi(
    val loading: Boolean = true,             // true until first cache OR network result
    val detail: StationDetail? = null,       // null until cache/network returns
    val error: String? = null                // last refresh error (cache still shown)
)

@HiltViewModel
class StationDetailViewModel @Inject constructor(
    private val repo: StationsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(StationDetailUi())
    val ui = _ui.asStateFlow()

    private lateinit var tenant: TenantConfig
    private var stationId: Long = -1
    private var streamJob: Job? = null

    fun start(tenant: TenantConfig, stationId: Long) {
        this.tenant = tenant
        this.stationId = stationId
        // 1) Start observing cache
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            repo.stationDetailStream(tenant.id, stationId).collectLatest { cached ->
                val existing = _ui.value
                _ui.value = existing.copy(
                    loading = existing.loading && cached == null, // loading=true until something shows
                    detail = cached ?: existing.detail,
                    // keep previous error (if any) until next refresh result
                )
            }
        }
        // 2) Kick a refresh in parallel
        reload()
    }

    fun reload() {
        if (!::tenant.isInitialized || stationId <= 0) return
        _ui.value =
            _ui.value.copy(loading = _ui.value.detail == null) // show spinner only if no cache
        viewModelScope.launch {
            when (val res = repo.refreshStationDetail(tenant, stationId)) {
                is Result.Ok -> _ui.value = _ui.value.copy(loading = false, error = null)
                is Result.Err -> _ui.value = _ui.value.copy(
                    loading = false,
                    // If we have cache, keep showing it and surface the error softly.
                    error = res.error.message ?: "Failed to refresh station detail"
                )
            }
        }
    }
}
