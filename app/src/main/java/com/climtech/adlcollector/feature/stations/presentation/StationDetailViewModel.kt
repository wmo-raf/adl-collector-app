package com.climtech.adlcollector.feature.stations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.core.data.db.ObservationDao
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.core.model.TenantConfig
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
    val loading: Boolean = true,       // true until first cache OR network result
    val detail: StationDetail? = null, // null until cache/network returns
    val error: String? = null          // last refresh error (cache still shown)
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

        // 1) Observe cached station detail
        detailStreamJob?.cancel()
        detailStreamJob = viewModelScope.launch {
            repo.stationDetailStream(tenant.id, stationId).collectLatest { cached ->
                val existing = _ui.value
                _ui.value = existing.copy(
                    // keep showing loading=true only until we have *any* cache
                    loading = existing.loading && cached == null, detail = cached ?: existing.detail
                    // keep previous error until next refresh result
                )
            }
        }

        // 2) Stream most recent observations for this station (top 3)
        recentStreamJob?.cancel()
        recentStreamJob = viewModelScope.launch {
            observationDao.streamForStation(
                    tenant.id,
                    stationId
                ) // already ORDER BY obsTimeUtcMs DESC
                .map { list -> list.take(3) }.collectLatest { _recent.value = it }
        }

        // 3) Kick a refresh in parallel (network)
        reload()
    }

    fun reload() {
        if (!::tenant.isInitialized || stationId <= 0) return

        // Show full-screen spinner only if there is no cached detail yet
        _ui.value = _ui.value.copy(loading = _ui.value.detail == null)

        viewModelScope.launch {
            when (val res = repo.refreshStationDetail(tenant, stationId)) {
                is Result.Ok -> {
                    _ui.value = _ui.value.copy(loading = false, error = null)
                }

                is Result.Err -> {
                    _ui.value = _ui.value.copy(
                        loading = false,
                        // If we have cache, keep showing it and surface the error softly.
                        error = res.error.message ?: "Failed to refresh station detail"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        detailStreamJob?.cancel()
        recentStreamJob?.cancel()
    }
}
