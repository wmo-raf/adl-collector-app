package com.climtech.adlcollector.feature.observations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climtech.adlcollector.core.data.db.ObservationDao
import com.climtech.adlcollector.core.data.db.ObservationEntity
import com.climtech.adlcollector.feature.observations.data.ObservationsRepository
import com.climtech.adlcollector.feature.stations.data.StationsRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject


data class ObservationVariable(
    val id: Long, val name: String, val unit: String, val value: Double
)

data class ObservationDetailUi(
    val loading: Boolean = true,
    val observation: ObservationEntity? = null,
    val variables: List<ObservationVariable> = emptyList(),
    val formattedObsTime: String = "",
    val formattedCreatedTime: String = "",
    val formattedUpdatedTime: String = "",
    val error: String? = null,
    val retrying: Boolean = false
)

@HiltViewModel
class ObservationDetailViewModel @Inject constructor(
    private val observationDao: ObservationDao,
    private val observationsRepository: ObservationsRepository,
    private val stationsRepository: StationsRepository,
    private val moshi: Moshi
) : ViewModel() {

    private val _ui = MutableStateFlow(ObservationDetailUi())
    val ui: StateFlow<ObservationDetailUi> = _ui.asStateFlow()

    private val mapAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    fun load(obsKey: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)

            try {
                val observation = observationDao.getByKey(obsKey)

                if (observation == null) {
                    _ui.value = _ui.value.copy(
                        loading = false, error = "Observation not found"
                    )
                    return@launch
                }

                val timeZone =
                    runCatching { ZoneId.of(observation.timezone) }.getOrElse { ZoneId.systemDefault() }

                // Try to get station detail for variable names
                val variables = parseVariablesWithNames(
                    observation.payloadJson, observation.tenantId, observation.stationId
                )

                _ui.value = _ui.value.copy(
                    loading = false,
                    observation = observation,
                    variables = variables,
                    formattedObsTime = formatDateTime(observation.obsTimeUtcMs, timeZone),
                    formattedCreatedTime = formatDateTime(observation.createdAtMs, timeZone),
                    formattedUpdatedTime = formatDateTime(observation.updatedAtMs, timeZone)
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    loading = false, error = "Failed to load observation: ${e.message}"
                )
            }
        }
    }

    fun retryUpload(tenantId: String) {
        val obs = _ui.value.observation ?: return

        viewModelScope.launch {
            _ui.value = _ui.value.copy(retrying = true)

            try {
                // Reset to queued status for retry
                observationDao.markStatus(
                    obs.obsKey,
                    ObservationEntity.SyncStatus.QUEUED,
                    null,
                    System.currentTimeMillis()
                )

                // Reload to show updated status
                load(obs.obsKey)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    retrying = false, error = "Failed to retry: ${e.message}"
                )
            }
        }
    }

    private suspend fun parseVariablesWithNames(
        payloadJson: String, tenantId: String, stationId: Long
    ): List<ObservationVariable> {
        return try {
            val payload = mapAdapter.fromJson(payloadJson) ?: return emptyList()
            val records = payload["records"] as? List<*> ?: return emptyList()

            // Try to get station detail for variable names
            val stationDetail = stationsRepository.stationDetailStream(tenantId, stationId).first()

            records.mapNotNull { record ->
                val recordMap = record as? Map<*, *> ?: return@mapNotNull null
                val id = (recordMap["variable_mapping_id"] as? Number)?.toLong()
                    ?: return@mapNotNull null
                val value = (recordMap["value"] as? Number)?.toDouble() ?: return@mapNotNull null

                // Look up variable name from station detail
                val variableMapping = stationDetail?.variable_mappings?.find { it.id == id }
                val name = variableMapping?.adl_parameter_name ?: "Variable $id"
                val unit = variableMapping?.obs_parameter_unit ?: "units"

                ObservationVariable(
                    id = id, name = name, unit = unit, value = value
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatDateTime(timestampMs: Long, timeZone: ZoneId): String {
        val instant = Instant.ofEpochMilli(timestampMs)
        val localDateTime = instant.atZone(timeZone)
        return DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm:ss").format(localDateTime)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }
}